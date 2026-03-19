# 詳細設計 (Detailed Design)

## 1. 開発環境・アーキテクチャ
- **開発言語**: Kotlin
- **最小SDK / ターゲットSDK**: Min SDK 26 (Android 8.0) / Target SDK 34 (Android 14)
- **UIフレームワーク**: Jetpack Compose
- **アーキテクチャ**: MVI (Model-View-Intent) または MVVM
- **主要ライブラリ**:
  - `Room`: ローカルデータベース
  - `WorkManager` & `Foreground Service`: バックグラウンド定期処理用
  - `Retrofit` / `OkHttp`: Webアクセス（レイテンシ計測）
  - `Play Services Location`: 位置情報取得
  - `Hilt` / `Dagger`: 依存性注入 (DI)
  - `Kotlin Coroutines` & `Flow`: 非同期処理

## 2. 画面設計 (UI Design)

### 2.1. メイン画面 (Dashboard / History List)
- **ヘッダー/ステータス**: 現在の計測状態（待機中、実行中、停止中）と最新の計測結果サマリー。
- **トグルスイッチ**: 定期計測処理の開始/停止を切り替えるスイッチ。
- **履歴リスト**: 過去の計測結果をスクロール可能なリスト(`LazyColumn`)で表示。
  - 各リスト要素項目: 実行時刻、レイテンシ(ms)、バンド(Band 3/n78 等)、緯度・経度。
- **設定ボタン**: 設定画面へ遷移するためのApp Bar上のアイコン。

### 2.2. 設定画面 (Settings)
- **計測先URL入力**: `TextField` でURLを入力し、ローカル(`DataStore` または `SharedPreferences`) に保存。
- **計測間隔設定**: プルダウン(DropdownMenu)による選択肢（例: 15分, 30分, 1時間）。

## 3. データベース設計 (Database Schema)

### Entity: `MeasurementRecord`
Roomデータベースを用いて、計測結果を保存するテーブル。

| カラム名 | 型 | 制約・説明 |
|---|---|---|
| `id` | Int | Primary Key (AutoGenerate) |
| `timestamp` | Long | UNIXエポックミリ秒 |
| `latencyMs` | Int | ミリ秒単位のレイテンシ (HTTP RTT) |
| `networkType` | String | "LTE", "NR", "WIFI", "UNKNOWN" 等 |
| `bandInfo` | String | 取得したバンド情報 (例: "Band 1", "n77") |
| `latitude` | Double | 取得した緯度 |
| `longitude` | Double | 取得した経度 |

## 4. バックグラウンド計測機構 (Background Processing Mechanism)

### 4.1. フォアグラウンドサービス (Foreground Service) + AlarmManager
- **選定理由**: ユーザーの操作なしに、数十分ごとの高い精度で長期間バックグラウンドで動作させるため。Android 8.0以降の厳格なバックグラウンド実行制限（Dozeモード等）や位置情報の取得制限を回避するには、Foreground Service（Locationタイプの指定）を利用して通知（Notification）を常駐させる方針がもっとも確実。
- **フロー**:
  1. トグルON時、`MeasureService` (Foreground Service) を起動。
  2. `AlarmManager` または `Kotlin Coroutines` のループを用いて、指定間隔で計測タスクを実行する。
  3. タスク完了後、次の実行をスケジュールして待機。

### 4.2. WorkManager (代替または併用案)
- `PeriodicWorkRequestBuilder`を使って最低15分間隔でキューに積む設計も可能だが、Dozeモードに入ると実行タイミングが遅延することが多い。定期的な即時性が厳格に求められない場合はこちらの方がバッテリーには優しい。

## 5. センサー・API 取得設計

### 5.1. ネットワーク・バンド情報の取得
- `TelephonyManager` の `getAllCellInfo()` メソッドを利用。
- 取得したセル情報から、接続中（`isRegistered == true`）の `CellInfoLte`、`CellInfoNr` などを見つけ、対応する `CellIdentity` から バンド情報(`earfcn`, `bands`) を取得・算出する。
- 権限: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`, `READ_PHONE_STATE`.

### 5.2. 位置情報の取得
- `FusedLocationProviderClient(context).getCurrentLocation()` もしくは `getLastLocation()` を利用（より精度の高い現在位置なら`getCurrentLocation`）。
- Foreground Serviceで稼働している間は `ACCESS_BACKGROUND_LOCATION` に大きく依存しなくても済む場合があるが、要求仕様によってはバックグラウンド権限もマニフェストに追加する。

### 5.3. レイテンシの計測
- `OkHttpClient` を用いて、設定されたURLに対して `HTTP HEAD`（あるいはユーザー指定のメソッド）を送信。
- リクエスト送信直前のシステム時刻（`SystemClock.elapsedRealtime()`）と、レスポンス到着時の時刻との差分を `latencyMs` とする。

## 6. クラス・モジュール構成 (簡略)
- **`com.example.latencycheck.ui`**: Composeを用いた画面定義 (`MainActivity`, `HistoryScreen`, `SettingsScreen`)
- **`com.example.latencycheck.viewmodel`**: 状態管理 (`MainViewModel`, `SettingsViewModel`)
- **`com.example.latencycheck.data`**: DB (`AppDatabase`, `RecordDao`), `DataStore` (設定保存)
- **`com.example.latencycheck.service`**: `MeasureService` (バックグラウンドタスク)、`NetworkHelper`, `LocationHelper`

## 7. マニフェストと権限定義 (`AndroidManifest.xml`)
```xml
<!-- 通信関連 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- 電話・通信状態関連 -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<!-- 位置情報関連 -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<!-- フォアグラウンドサービス関連 (Android 14対応) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<!-- 推奨プッシュ通知権限 (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```
