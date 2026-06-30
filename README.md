# PIP Viewer

AndroidでPIP（ピクチャインピクチャ）再生できる、シンプルで美しい動画ビューア。
タブレット（Xiaomi Pad 6 など）での利用を主眼に、Apple ライクな上質で軽快な UX を目指しています。

> Kotlin + Jetpack Compose (Material 3) + Media3 (ExoPlayer)

---

## 特長

### プレイヤー
- **ジェスチャー操作**
  - 画面 **左半分**を上下スワイプ → **明るさ** 調整（一番下まで下げると **Auto＝端末設定に追従**）
  - 画面 **右半分**を上下スワイプ → **音量** 調整
  - 1本指 **ダブルタップ**（左/右）→ 10秒 戻し / 早送り
  - シングルタップ → コントロール表示/非表示
- **PIP（ピクチャインピクチャ）対応** — ボタン、またはホームに戻るだけで自動的に小窓再生へ（Android 12 以降は自動遷移、11 以下も対応）。小窓内に 再生/一時停止・10秒スキップ のボタン付き。
- カスタムコントロール（シーク、10秒スキップ、前/次、画面ロック）。
- 音声フォーカス制御・スリープ防止・バックグラウンド継続。

### ライブラリ（アプリ単体利用）
- **フォルダ選択**（SAF）— ストレージ権限不要で、内部ストレージ / SD カード / クラウドのフォルダを追加。
- **★ お気に入り** — 任意のフォルダ（ルート・サブフォルダ問わず）に星を付けてトップに固定。
- 指定フォルダ配下の **サブフォルダ＆動画を一覧表示**（サムネイル・長さ・サイズ）。
- **タブレット最適化** — 画面幅に応じて列数が自動で増減するレスポンシブなグリッド。
- ライト/ダーク自動対応、日本語/英語自動切替（端末言語に追従）。

### 他アプリからの呼び出し
- 「他のアプリで開く」「共有」などから `video/*` の URL を受け取って再生（`ACTION_VIEW`）。
- これがメインの使い方を想定。ファイラーやブラウザの動画を本アプリで開けます。

---

## 動作環境
- 最小: **Android 10 (API 29)** / ターゲット: **Android 15 (API 35)**
- PIP は API 26 以降が必須のため、本アプリ（min 29）では全対応端末で利用可能。

---

## ビルド

### GitHub Actions（推奨・APK 自動生成）
`main` および各ブランチへの push で CI が走り、デバッグ APK を生成します。

1. リポジトリの **Actions** タブ → 対象の実行を開く
2. **Artifacts** の `pip-viewer-debug-apk` をダウンロード
3. 端末にインストール（提供元不明アプリの許可が必要な場合あり）

### ローカル（Android Studio）
- Android Studio（Koala 以降推奨）で本リポジトリを開く
- `compileSdk 35` / JDK 17
- Run、または `./gradlew assembleDebug`（出力: `app/build/outputs/apk/debug/app-debug.apk`）

---

## 既定の動画プレイヤーにする
インストール後、ファイラー等で動画を開く際に「PIP Viewer」を選択（必要なら「常時」）。
`PlayerActivity` が `video/*` の `ACTION_VIEW` を受けます。

---

## 技術構成
- **UI**: Jetpack Compose / Material 3（iOS ライクに調整したカラー・タイポグラフィ・角丸）
- **再生**: AndroidX Media3 (ExoPlayer)
- **ストレージ**: Storage Access Framework（永続化された tree URI を ContentResolver で直接列挙）
- **設定保存**: DataStore Preferences（追加フォルダ・★ を保存）
- **サムネ**: `MediaMetadataRetriever` による遅延生成 + メモリキャッシュ（並列数を絞り軽量動作）

主要クラス:
- `MainActivity` — ライブラリ（ホーム／フォルダ閲覧）
- `PlayerActivity` — 再生・PIP・外部インテント受け口
- `ui/player/PlayerScreen` — ジェスチャー・カスタムコントロール
- `data/` — `FolderRepository`（保存）, `MediaRepository`（SAF 列挙）
