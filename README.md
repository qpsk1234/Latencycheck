# プロジェクト構成とファイル役割

## 1. プロジェクト概要
ネットワークのレイテンシ（応答速度）を定期的に計測し、その際の通信環境（基地局情報、電波強度など）と位置情報を記録・可視化するためのツールです。バックグラウンドでの計測、マップ表示、履歴管理、CSVエクスポートなどの機能を備えています。

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
*   **MeasureService.kt**: フォアグラウンドサービス。アプリがバックグラウンドにいても定期的に計測ループを実行し、データベースに保存します。
*   **NetworkInfoHelper.kt**: 接続中のネットワーク種別（LTE/5G）、バンド、RSRP（電波強度）、帯域幅、近隣セル、タイミングアドバンス(TA)などの詳細情報を取得します。
*   **LocationHelper.kt**: GPSを用いた位置情報の取得と、緯度経度から場所名（逆ジオコーディング）を取得します。

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
