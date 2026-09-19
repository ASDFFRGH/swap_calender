# Swap Calendar

GMO外貨の公開スワップカレンダーを、月別に保存して閲覧するAndroidアプリです。AndroidアプリはAPIから取得したデータをRoomへ保存し、オフライン時は保存済みデータを表示します。公式ページの取得はKtorサーバーだけが行います。

## 開発起動

前提: Android Studio、JDK 21、Docker。

```bash
docker compose up -d postgres
export DATABASE_URL=jdbc:postgresql://localhost:5433/swap_calendar
export DATABASE_USER=swap_calendar
export DATABASE_PASSWORD=swap_calendar
export FETCH_TOKEN=local-development-token
./gradlew :server:run
```

別のターミナルで1か月分を取り込みます。

```bash
curl -X POST \
  -H 'Authorization: Bearer local-development-token' \
  'http://localhost:18080/api/internal/fetch?month=2026-09'
```

Android Studioでdebugビルドを起動します。エミュレーターのdebugアプリは `http://10.0.2.2:18080` に接続します。releaseのAPI URLは配布環境のHTTPS URLへ変更してください。

## 検証

```bash
./gradlew test :app:assembleDebug
```

## GitHub Release

`v`で始まるタグをpushすると、GitHub ActionsがテストとLintを実行し、GitHub Secretsに保存した鍵でrelease APKを署名してGitHub Releaseへ添付します。本番API URLはリポジトリ変数 `API_BASE_URL` から注入します。

必要なActions Secretsは `RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` です。署名鍵はリポジトリへコミットしません。

実装方針と未決事項は [docs/implementation-plan.md](docs/implementation-plan.md) を参照してください。
