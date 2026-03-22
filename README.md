# Latency Check

ネットワークのレイテンシ（応答速度）を定期的に計測し、その際の通信環境（基地局情報、電波強度など）と位置情報を記録・可視化するためのAndroidアプリです。バックグラウンドでの計測、マップ表示、履歴管理、CSVエクスポートなどの機能を備えています。

## ビルド手順

### 必要な環境

- Android Studio（最新版推奨）
- Android SDK（API 24以上）
- JDK 17以上

### リポジトリのクローン

```bash
git clone <repository-url>
cd Latencycheck
```

### ビルド方法

#### デバッグAPKのビルド

```bash
./gradlew assembleDebug
```

ビルド成功後、`app/build/outputs/apk/debug/app-debug.apk` が生成されます。

#### リリースAPKのビルド

```bash
./gradlew assembleRelease
```

#### App Bundle（AAB）のビルド

```bash
./gradlew bundleRelease
```

#### インストール（接続したデバイス/エミュレーターに直接）

```bash
./gradlew installDebug
```

### トラブルシューティング

#### Gradleのクリーン

ビルドエラーが発生した場合：

```bash
./gradlew clean
./gradlew cleanBuildCache
./gradlew assembleDebug
```

#### パーミッションの付与（Android 6.0以上）

初回起動時に以下のパーミッションを許可してください：
- インターネット（計測用）
- 位置情報（計測地点の記録）
- 電話状態（基地局情報取得）

---

## プロジェクト構成とファイル役割

### 1. プロジェクト概要

## 2. ディレクトリ・ファイル構成と役割

### UI 層 (`com.example.latencycheck.ui`)
Jetpack Compose を使用した画面構成です。
*   **MainScreen.kt**: アプリのメイン画面。ボトムナビゲーションによる画面切り替えを管理します。
*   **MapScreen.kt**: `osmdroid` を使用し、計測結果を地図上にプロットします。レイテンシの値に応じてマーカーの色が変化します。
*   **HistoryScreen.kt**: 計測ログをリスト形式で表示します。
*   **SummaryScreen.kt**: 統計情報（平均値、最大・最小値など）を表示します。
*   **SettingsScreen.kt**: 計測間隔、ターゲットURL、閾値などの設定画面。
*   **ColorSettingsScreen.kt**: マップ上の色分け（レイテンシに応じた色）をカスタマイズする画面。
*   **ColorUtils.kt**: レイテンシの数値から表示色を決定するロジック。

### ViewModel 層 (`com.example.latencycheck.viewmodel`)
*   **MainViewModel.kt**: UIの状態管理、設定情報の保持、CSVのインポート/エクスポート処理、バックグラウンドサービスの開始/停止指示を行います。

### Service 層 (`com.example.latencycheck.service`)
*   **MeasureService.kt**: フォアグラウンドサービス。`WakeLock` を使用してバックグラウンド・スリープ中も計測ループを維持し、データを Room データベースに保存します。
*   **NetworkInfoHelper.kt**: `TelephonyCallback` 等を利用し、5G SA/NSA の判別、物理チャンネル設定（CA状態）の監視、NRARFCN からの 5G バンド名変換、RSRP、帯域幅、近隣セル、タイミングアドバンス(TA)などの詳細情報を取得します。
*   **LocationHelper.kt**: `FusedLocationProviderClient` を用いた高精度な位置情報の取得と、逆ジオコーディングによる場所名（駅名やランドマーク優先）の取得を行います。

### Network 層 (`com.example.latencycheck.network`)
*   **NetworkMonitor.kt**: 指定されたURLに対して実際に通信を行い、レイテンシ（ミリ秒）を測定します。

### Data 層 (`com.example.latencycheck.data`)
*   **AppDatabase.kt / RecordDao.kt / MeasurementRecord.kt**: Roomライブラリを使用したローカルデータベース。計測データ（時刻、レイテンシ、電波情報、位置）を保存します。
*   **AppPreferences.kt**: DataStore を使用し、ユーザー設定（計測間隔、ターゲットURLなど）を永続化します。

### DI (Dependency Injection) (`com.example.latencycheck.di`)
*   **NetworkModule.kt / DatabaseModule.kt**: Hilt を使用した依存関係の注入設定。

### その他
*   **MainActivity.kt**: アプリの起動エントリーポイント。位置情報や電話情報取得のためのパーミッション要求を管理します。
*   **LatencyApp.kt**: `HiltAndroidApp` を継承したアプリケーションクラス。

## 3. 主要な処理フロー
1.  **計測開始**: `MainViewModel` から `MeasureService` を開始。
2.  **定期実行**: `MeasureService` 内のコルーチンが設定された間隔で `NetworkMonitor`（レイテンシ）、`NetworkInfoHelper`（電波）、`LocationHelper`（位置）からデータを集約。
3.  **保存**: 集約されたデータを `MeasurementRecord` として Room DB に保存。
4.  **表示**: `MainViewModel` が DB の変更を監視し、`HistoryScreen` や `MapScreen` にリアルタイムで反映。
