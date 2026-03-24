# 更新履歴 (Change History)

## バージョン履歴

### 2025-03-24 - Cell Summary テーブル表示・デュアルSIM対応
**コミット**: `6a13c5c` - Implement Cell Summary table view with dual SIM and unregistered cell support

#### 新機能
- **Cell Summary テーブル表示**: 基地局情報を表形式で表示
  - バンド（B1, B3, n78等）ごとのグループ化と展開表示
  - ARFCN/NRARFCN列の分離表示（従来はバンド情報と統合）
  - テーブル形式での一覧表示（バンド、ARFCN、タイプ、セル数、記録数、Reg/Unreg、SIM）

- **デュアルSIM対応**:
  - データベースに `subscriptionId` フィールドを追加（SIM1=0, SIM2=1）
  - 両方のSIMからの基地局情報を取得・記録
  - Cell Summary画面でSIM1/SIM2の使用カウントを表示

- **未登録セル（Neighbor Cells）対応**:
  - データベースに `isRegistered` フィールドを追加
  - 登録中のセルだけでなく近隣の未登録セルも記録
  - Cell Summary画面で登録/未登録のカウントを表示（R:xx/U:xx形式）

#### 技術的変更
- **データベース移行**: Version 4 → 5
  - `earfcn`: EARFCN（LTE）またはNRARFCN（NR）を保存
  - `bandNumber`: バンド番号（B1, n78等）を個別に保存
  - `isRegistered`: セルの登録状態（true/false）
  - `subscriptionId`: SIMカード識別子

- **新規データクラス**:
  - `CellData`: 詳細なセル情報を保持するデータクラス
  - `BandArfcnSummary`: バンド→ARFCN階層の集計用
  - `ArfcnDetail`: ARFCNごとの詳細集計
  - `CellIdDetailSummary`: Cell IDごとの集計（SIM別カウント対応）

- **UIリニューアル**:
  - `CellSummaryScreen.kt` を完全書き換え
  - カード形式からテーブル形式へ変更
  - 展開可能なバンドグループUI
  - SIMアイコンと登録状態バッジの追加

#### 修正された問題
- SIM2側利用時にバンド情報が取得できない問題を修正
  - `NetworkInfoHelper.getAllCellDataList()` で全サブスクリプションを走査
  - デフォルトのSubscriptionIdに依存しない実装に変更

---

### 2025-03-23 - Cell Summary 初期実装
**コミット**: `465ddec` - Add Cell Summary screen with ARFCN/CellID aggregation

#### 新機能
- **Cell Summary画面**: 基地局情報の集計表示機能を追加
  - ARFCN/bandInfoごとの集計表示
  - Cell IDごとの詳細表示
  - 検索機能（バンド、CellID、ネットワークタイプで検索可能）

---

### 2025-03-20 - SA/NSA検出修正とデバッグ機能
**コミット**: `a9a5022` - Fix SA/NSA detection and increase marker sizes
**コミット**: `ca4a3ce` - Add debug screen for telephony information

#### 変更内容
- 5G SA/NSAの判別ロジックを改善
- マップマーカーのサイズを拡大
- 電話情報確認用のデバッグ画面を追加

---

### 2025-03-18 - 駅名表示対応とREADME追加
**コミット**: `1a61cce` - Update location display to prioritize station names when near stations
**コミット**: `5262b15` - Add build instructions to README

#### 変更内容
- 位置情報表示を駅名優先に変更（駅付近の場合）
- READMEにビルド手順を追加

---

### 2025-03-16 - 詳細なネットワーク指標の追加
**コミット**: `567d203` - Update station summary with SA/NSA/LTE rates and add horizontal scrolling to lists
**コミット**: `4f00ede` - Enhance Network Monitoring: Add detailed network metrics, full-screen map, and improved summary statistics

#### 新機能
- **ネットワーク指標の拡張**:
  - RSSI、RSRP、RSRQ、SINRの記録
  - Cell ID、PCI、帯域幅の記録
  - 近隣セル情報の取得
  - TA（タイミングアドバンス）の記録

- **UI改善**:
  - フルスクリーンマップ表示
  - リストの横スクロール対応
  - SA/NSA/LTE比率の統計表示

---

### 2025-03-15 - 5G NR検出の修正
**コミット**: `2e30a7d` - Fix NR Check/bandwidth
**コミット**: `b4040ba` - Fix NetworkTypes
**コミット**: `cd1b156` - Change NetworkType Check

#### 修正内容
- 5G NRの検出ロジックを修正
- ネットワークタイプ判定の改善
- 帯域幅取得処理の修正

---

### 2025-03-14 - 初回コミット
**コミット**: `b9b058c` - first commit

#### 基本機能
- レイテンシ定期計測（フォアグラウンドサービス）
- ネットワーク情報記録（バンド、電波強度）
- 位置情報記録（緯度・経度）
- 履歴表示（リスト形式）
- マップ表示（osmdroid使用）
- CSVエクスポート/インポート
- 設定画面（URL、計測間隔、色設定）

---

## データベースバージョン履歴

| バージョン | 日付 | 変更内容 |
|-----------|------|---------|
| 5 | 2025-03-24 | `earfcn`, `bandNumber`, `isRegistered`, `subscriptionId` カラムを追加。デュアルSIM・未登録セル対応。 |
| 4 | 2025-03-16 | RSSI、RSRP、RSRQ、SINR、Cell ID、PCI、帯域幅、TA、近隣セル情報を追加。 |
| 1-3 | 2025-03-14 | 初期スキーマ（レイテンシ、ネットワークタイプ、位置情報） |

---

## 今後の予定

- [ ] データ同期機能（クラウドバックアップ）
- [ ] 高度なフィルタリング（日付範囲ト、バンド指定など）
- [ ] 統計グラフの強化（時系列グラフ、ヒートマップ）
- [ ] エクスポート形式の拡張（JSON, KML）
