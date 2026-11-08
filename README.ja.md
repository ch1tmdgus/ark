<p align="center">
  <a href="README.md">English</a> · <a href="README.ko.md">한국어</a> · <b>日本語</b>
</p>

# ARK

**ARK**は、ルート権限で動作するAndroid向けのバックアップ・復元アプリです。アプリのAPK（スプリットAPKを含む）とアプリデータをまとめてアーカイブし、端末内、好きなフォルダ、または自分で運用しているWebDAVクラウドに保存します。

- パッケージ名: `app.n4c.ark`
- 最小SDK: 26 (Android 8.0)、ターゲットSDK 34
- UI: Jetpack Compose + Material 3
- 対応言語: English、한국어、日本語（システム言語に従います）

> **ルート権限が必要です。** ARKは`/data/data`にある他のアプリの非公開データを読み取りますが、これはスーパーユーザー権限がなければ不可能です。

## ダウンロード

最新のAPKは[Releases](https://github.com/ch1tmdgus/ark/releases)ページから入手できます。`v`で始まるタグ（例: `v1.0`）をプッシュすると、GitHub Actionsが自動でビルドしてそこに公開します。

## 主な機能

- **柔軟なバックアップ** – APKのみ、データのみ、または両方を選べます。
- **3つの保存先**
  - この端末 (`/sdcard/ARK`)
  - 選択した任意のフォルダ（Storage Access Framework）
  - WebDAVクラウド（設定でクラウドを接続したときのみ表示）
- **バックグラウンド動作** – フォアグラウンドサービスとして実行され、進行率（%）とログ1行を表示し、結果は通知でお知らせします。アプリを閉じても大丈夫です。
- **バックアップのメタデータ** – バックアップ元の端末、Androidバージョン、アプリのバージョン、日付と時刻を記録し、復元前に表示します。
- **かんたん復元** – 復元元（この端末、クラウド、任意のファイル）を選んでバックアップをタップするだけです。APKを再インストールし、データを元に戻します。
- **初回セットアップ** – ウェルカム画面、ルートと権限の確認を経てすぐに使い始められます。表示されるのは一度だけです。

## 必要なもの

- ルート化された端末（Magisk、KernelSU、APatchなど）
- 長時間のバックアップのために、通知の許可とバッテリー制限の解除を推奨します
- ソースからビルドするには、Android Studio（Koala以降）とJDK 17

## ビルド

```bash
git clone https://github.com/ch1tmdgus/ark.git
cd ark
```

1. Android Studioでフォルダを開き、Gradle Syncの完了を待ちます。
2. USBデバッグを有効にしたルート化済みのスマートフォンを接続して**Run**を押すか、**Build ▸ Build APK(s)**でAPKを作成します。
3. 初回起動時に、ルート管理アプリでARKにスーパーユーザー権限を許可します。

依存関係はGoogle、Maven Central、JitPack（libsu）から取得されます。

## 使い方

1. **バックアップ** – アプリをタップし、バックアップする内容、次に保存先を選びます。
2. **復元** – **復元**ボタンを押して復元元を選び、バックアップを選択して確認します。
3. **クラウド** – **設定**（歯車アイコン）でWebDAVのURL、ユーザー名、（アプリ）パスワードを入力し、**接続**をタップします。

WebDAVのURL例:

| サーバー | URL |
| --- | --- |
| Nextcloud | `https://example.com/remote.php/dav/files/ユーザー名` |
| Synology WebDAV Server | `https://NASのアドレス:5006`（HTTPは`5005`） |

バックアップはサーバーの`ARK`フォルダに保存されます。対応しているのはHTTP Basic認証のみです。2段階認証を使っているサービスでは、アプリパスワードを発行して使用してください。

## バックアップ形式

1回のバックアップは、同じ名前の2つのファイルで構成されます。

```
<パッケージ名>_<yyyyMMdd_HHmmss>.ark    # tarアーカイブ
├── meta.json                           # 端末、Androidバージョン、アプリのバージョン、日付、内容
├── apk/                                # base.apkとスプリットAPK   (APK / APK + データ)
├── data.tar                            # /data/data/<パッケージ名>  (データ / APK + データ)
└── ext.tar                             # /sdcard/Android/data/<パッケージ名>（ある場合のみ）

<パッケージ名>_<yyyyMMdd_HHmmss>.json   # 一覧を素早く表示するためのmeta.jsonのコピー
```

## 仕組み

ARKは、グローバルなマウント名前空間で動作するルートシェル（[libsu](https://github.com/topjohnwu/libsu)）を通じてシステムとやり取りするため、すべてのアプリの`/data/data`が見えます。データは`tar`でまとめ、APKは`pm install-create / install-write / install-commit`でインストールし、データの復元後は`chown`で所有者を、`restorecon`でSELinuxコンテキストを整えます。WebDAVは[OkHttp](https://square.github.io/okhttp/)で実装しています。

## 制限事項

- データが端末に紐づいているアプリ（Android Keystore、ハードウェアベースの認証情報、一部の銀行アプリ）は、復元後に動作しない場合があります。
- ランタイム権限はバックアップに含まれないため、復元後に再度許可が必要になることがあります。
- バックアップは**暗号化されません。** ログインセッションなどの機密データが含まれることがあるため、クラウドに保存したファイルは適切に保護してください。
- 復元すると、アプリの現在のデータは**上書きされます。**

## 免責事項

ルート権限でアプリデータをバックアップ・復元すると、データの損失やアプリの不具合が起こる可能性があります。ご利用は自己責任でお願いします。まずは重要でないアプリでテストしてください。

## プロジェクト構成

```
app/src/main/java/app/n4c/ark/
├── MainActivity.kt      # エントリーポイント、テーマ、オンボーディング/ホストの切り替え
├── Onboarding.kt        # 初回のウェルカムと権限ページ
├── Screens.kt           # ホーム画面とダイアログ
├── Sheets.kt            # バックアップ・復元・設定のボトムシート
├── Components.kt        # 共通UIコンポーネント
├── Theme.kt             # Material 3のカラースキーム
├── BackupEngine.kt      # ルートによるバックアップ/復元ロジック
├── ArkService.kt        # フォアグラウンドサービスと共有タスク状態
├── WebDavClient.kt      # WebDAVクライアント
├── Prefs.kt, Models.kt, Utils.kt
```

## クレジット

- [libsu](https://github.com/topjohnwu/libsu)（topjohnwu）
- [OkHttp](https://github.com/square/okhttp)（Square）
