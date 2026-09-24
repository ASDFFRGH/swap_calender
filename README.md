# Swap Calendar

GMO外貨の公開スワップカレンダーを、月別に保存して閲覧するAndroidアプリです。Androidアプリは取得したデータをRoomへ保存し、オフライン時は保存済みデータを表示します。本番API URLが設定されている場合はKtorサーバーを利用し、未設定の場合は公式ページをAndroid側で直接取得して、年月・列数・重複・値を検証してから保存します。

カレンダーは日曜始まり（土曜終わり）で、選択中の通貨ペアに含まれるSP日数を具体的に表示します。複数の通貨ペアへ保有数量を設定でき、保有中の通貨ペアは表示順の先頭へ移動します。カレンダーにはSP日数に代えて、表示フィルターにかかわらず全保有通貨ペアのその日の受払額と月間合計を表示します。月間合計では未発表を含む日を除外し、金額が確定している日だけを合算します。設定画面では通貨ペアの表示順・表示有無・保有通貨数量・買売ポジションを保存できます。掲載値は1万通貨あたりとして、GMO外貨の端数処理に合わせて円単位へ丸めます。

ハンバーガーメニューの「保有数量計算」では、入金額・含み損・目標レバレッジとGMO外貨から取得した為替相場を使い、目標レバレッジに収まる保有通貨数量と推定ロスカットレートを計算できます。クロス円以外の通貨ペアも基準通貨・決済通貨の対円レートで円換算します。ロスカット計算は個人口座の維持証拠金率4%と証拠金維持率50%を前提とする参考値です。
入力した通貨ペア・金額・売買方向・目標レバレッジは端末内に保存され、アプリを終了しても次回起動時に復元されます。

アプリ起動時にはGitHub Releasesの最新版を確認します。インストール中のバージョンより新しい正式リリースがある場合は、更新ページを開くダイアログを表示します。確認に失敗した場合でも通常のカレンダー表示は継続します。

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

`v`で始まるタグをpushすると、GitHub ActionsがテストとLintを実行し、GitHub Secretsに保存した鍵でrelease APKを署名してGitHub Releaseへ添付します。本番API URLはリポジトリ変数 `API_BASE_URL` から注入できます。変数が未設定でも、Android側の公式ページ取得によって動作します。

必要なActions Secretsは `RELEASE_KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` です。署名鍵はリポジトリへコミットしません。

実装方針と未決事項は [docs/implementation-plan.md](docs/implementation-plan.md) を参照してください。
