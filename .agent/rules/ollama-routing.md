---
trigger: always_on
---

---

trigger: always_on

description: Route simple tasks to Ollama to save tokens

---



# Ollama Routing Rules

以下のタスクはOllamaツール（ollama_run または ollama_chat_completion）を使用すること：

- コードのフォーマット・整形

- 単純なリネーム・変数名変更

- コメントの追加

- 簡単な型定義の生成

- ファイル内容の要約

- テストコードの雛形（ボイラープレート）作成

- データ構造の変換（JSONからDataclassなど）

- 簡単な正規表現の作成・解説

- UI文言やドキュメントの簡単な翻訳

- Gitのコミットメッセージ案の生成

- 標準ライブラリ等の基本的な使い方の調査



以下はGemini（自分）が処理すること：

- アーキテクチャ設計

- バグ修正

- 複雑なロジック実装

- コードレビュー

Ollamaで使用するモデル: qwen3:8b