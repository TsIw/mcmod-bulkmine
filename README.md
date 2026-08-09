# BulkMine

Minecraft 26.2 / Fabric 用の一括破壊Modです。土や石を立方範囲でまとめて掘り、木を一括で伐採します。キーひとつで切り替えでき、現在の状態は常に画面に表示されます。

追加アイテムもレシピもありません。既存のツールの挙動を拡張するだけです。

---

# 利用者向け

## 動作環境

| | |
|---|---|
| Minecraft | 26.2 |
| Mod Loader | Fabric Loader 0.19.3 以上 |
| 必須Mod | Fabric API 0.156.0+26.2 以上 |
| Java | 25 以上（Minecraft 26.2 の要件） |

## 導入

`bulkmine-<バージョン>.jar` を `mods` フォルダに入れるだけです。

**マルチプレイではサーバー側にも必須です。** 破壊処理と設定はすべてサーバーが持っているため、クライアントにだけ入れた状態でサーバーに接続すると、HUDが「非対応」と表示され機能は動きません。シングルプレイでは意識する必要はありません。

## 使い方

初期状態ではOFFです。**`V`キー**で切り替えます（キー設定から変更できます）。

画面の隅に現在の状態が常に表示されます。

| 表示 | 意味 |
|---|---|
| `一括ON:3` | 有効。数字は立方サイズ |
| `一括OFF` | 無効 |
| `非対応` | 接続先のサーバーにBulkMineが入っていない |

F1（UI非表示）とF3（デバッグ画面）の間は隠れます。

## 破壊される範囲

### 石・土系（立方範囲）

カーソルのブロックを中心とした **N×N×N から、カーソルより下の層をすべて除いた範囲**です。既定のサイズ3なら 3×3×2 の18ブロックになります。

視線の向きでは回転しません。常にワールドの軸に沿うので、平らな床を上から掘れば 3×3×1、垂直な壁を横から掘れば 幅3×高2×奥2 になります。

サイズは `/bulkmine config size <奇数>` で**プレイヤーごとに**変更でき、再ログイン後も保持されます。

### 木材系（一括伐採）

斜め方向を含む26方向をたどり、**同一種類の原木のみ**を伐採します。オークを掘っても隣接するシラカバは残ります。起点より下には伸びないため、地面に埋まった部分は掘り返しません。

葉も既定で伐採対象です（距離6以内）。プレイヤーが設置した葉かどうかは区別しません。

原木は立方範囲の対象にはなりません。石を掘っている隣に木が立っていても、幹が巻き込まれることはありません。

## 対象になるツール

バニラのつるはし・斧・シャベルは常に認識されます。

**他のModのツールは自動では登録されません。** 手に持って以下を実行してください。

```
/bulkmine tool add hand all
```

たとえば The True Paxels のPaxelは7種類あるので、それぞれ手に持って1回ずつ実行します。ロールを分けたい場合は `all` の代わりに `pickaxe|shovel` のように指定できます。

Paxelのように複数ロールを持つツールは、カテゴリをまたいで採掘します。石と土が混在していても1回の破壊でまとめて掘れます。

## 対象にならないブロック

**ドロップが得られないブロックはスキップされます。** 石のつるはしでダイヤ鉱石を掘っても、カーソルのブロックはゲーム本体が壊しますが、周囲の鉱石は残ります。無駄に消し飛ばさないための仕様です（`requireCorrectTool` で無効化できます）。

対象ブロックは設定ファイルにすべて明示されており、行を消すだけで除外できます。ブラックリストに入れたブロックは、ホワイトリストに含まれていてもスキップされます。

## ツールの耐久

耐久が2以上残っているツールは**残り1で止まり、壊れません**。それ以降は耐久を消費せずに範囲破壊を続けます。

耐久1のツールで掘った場合はツールが壊れますが、**その場合も範囲破壊は最後まで実行されます**。

## コマンド

### 全プレイヤーが使えるもの

| コマンド | 内容 |
|---|---|
| `/bulkmine toggle` | ON/OFF切替（キーバインドと同じ） |
| `/bulkmine config` | 自分の設定とサーバー設定を表示 |
| `/bulkmine config size <1-15>` | 自分の立方サイズを変更（奇数のみ） |

シングルプレイではチート無効のままでも使えます。

### 管理用

シングルプレイでは常に、サーバーでは権限レベル2以上で使えます。

| コマンド | 内容 |
|---|---|
| `/bulkmine tool add <アイテム\|hand> <ロール>` | ツールを登録 |
| `/bulkmine tool remove <アイテム\|hand> [ロール]` | ツール登録を解除 |
| `/bulkmine tool list` | 登録済みツール一覧 |
| `/bulkmine block add <ブロック\|hand> <ロール>` | 対象ブロックを追加 |
| `/bulkmine block remove <ブロック\|hand>` | 対象ブロックから除外 |
| `/bulkmine block deny <ブロック\|hand>` | ブラックリストに追加 |
| `/bulkmine block allow <ブロック\|hand>` | ブラックリストから解除 |
| `/bulkmine block list` | 各一覧の件数とブラックリストを表示 |
| `/bulkmine reload` | 設定ファイルを再読込 |

ロールは `pickaxe` / `axe` / `shovel` / `all` で、`|` または `,` で連結できます（例: `pickaxe|shovel`）。

コマンドで変更した内容は設定ファイルへ即座に書き戻されます。

## 設定ファイル

`config/bulkmine.json` に初回起動時、既定値の入ったファイルが生成されます。ファイル内のコメント行でサーバー設定とクライアント設定が明示的に分かれています。

サーバー設定はマルチプレイでは接続先サーバーの値が使われ、手元の値は無視されます。クライアント設定（HUDの表示位置）はその逆で、常に自分の設定が使われます。

| 主な項目 | 既定 | 内容 |
|---|---|---|
| `defaultCubeSize` | 3 | サイズ未設定のプレイヤーの初期値 |
| `maxCubeSize` | 9 | プレイヤーが設定できる上限 |
| `breakLeaves` / `leafDistance` | true / 6 | 伐採時の葉の処理 |
| `aggregateDrops` | true | ドロップを起点ブロックに集約 |
| `allowWhileSneaking` | true | スニーク中も動作 |
| `requireCorrectTool` | true | 適正ツールでないブロックをスキップ |
| `tools` | 空 | 追加登録したツール |
| `blocks.pickaxe` / `shovel` / `fellable` / `leaves` | 各一覧 | 対象ブロック |
| `blocks.blacklist` | 空 | 常にスキップ（最優先） |
| `client.hudCorner` | `BOTTOM_RIGHT` | HUDの表示位置（四隅） |

ブロックの指定は `minecraft:stone` のようなIDのほか、`#c:ores` のようなタグ参照も書けます。

設定ファイルは読み込み時にテンプレート経由で書き戻されます。古い形式のファイルは自動で移行し、値は保持されます。**手書きで追加したコメントは失われます。**

## 配布プロファイルに同梱する場合

設定ファイルは引き継がれないため、`config/bulkmine.json` も一緒に配布しないと、受け取った側は**ツールが1つも登録されていない状態**から始まります。CurseForgeのプロファイルをエクスポートする場合は、overridesに設定ファイルも含めてください。

---

# 開発者向け

## 環境

JDK 25 が必要です（Minecraft 26.2 の要件）。

`gradle.properties` の `org.gradle.java.home` に **JDK 25 の絶対パスがハードコードされています**。別のマシンで作業する場合は書き換えるか、PATHが通っているなら行ごと削除してください。

同ファイルの `org.gradle.jvmargs` にある `-Duser.language=en` は、javacのエラーメッセージが日本語ロケールで文字化けするのを避けるためのものです。

## ビルド

```
build.bat
```

ゲームテストを実行し、通ったら `dist\bulkmine-<バージョン>.jar` を生成します。テストが落ちた場合 `dist\` は更新しません。バージョンは `gradle.properties` の `mod_version` から読み取ります。

テストを飛ばす場合は `build.bat --no-tests` です。

個別のGradleタスク:

| タスク | 内容 |
|---|---|
| `gradlew runClient` | 開発用クライアント起動 |
| `gradlew runGametest` | ゲームテスト（サーバー起動〜自動実行） |
| `gradlew build` | jarのビルドのみ |

`run/` は開発用クライアントのゲームフォルダです。ワールド、ログ、Modが書き出す設定、テスト用に入れた他のModがすべてここに入るため、リポジトリからは除外しています。

## ソース構成

Loomの `splitEnvironmentSourceSets()` により、サーバー／クライアントの分離がコンパイル時に強制されます。

| ディレクトリ | 内容 |
|---|---|
| `src/main` | サーバー・共通。破壊処理、設定、コマンド、永続化。`src/client` を参照できない |
| `src/client` | クライアント専用。HUD、キーバインド、受信したステートの保持 |
| `src/gametest` | ゲームテスト。**別Mod（`bulkmine-gametest`）として構成** |

ゲームテストを別Modに分けているのは、`fabric-gametest` エントリポイントを製品版の `fabric.mod.json` に書いてしまうと、テストクラスを含まない配布jarでは `ClassNotFoundException` で起動不能になるためです。

クライアントは状態を自分で決めません。トグルキーはサーバーへ要求を送るだけで、表示するのはサーバーから配信された値です。

## テスト

```
gradlew runGametest
```

28件のゲームテストが、実際の破壊経路 `ServerPlayerGameMode.destroyBlock` を通して形状・ホワイトリスト・ロール・伐採・耐久・ドロップ位置を検証します。結果は `build/junit.xml` に出ます。

Fabricのモックプレイヤーは `gameMode()` がCREATIVE固定です。そのままではドロップも耐久消費も発生しないため、テスト側で `abilities.instabuild` を落としています。製品コード側も `isCreative()` ではなく、バニラ自身が使う `preventsBlockDrops()` を見ています。

## 生成物の再生成

`src/main/resources/default-config.json` は手書きせず、`ConfigWriter` から生成します。ゲームテストが両者の一致を検証しているので、`ConfigWriter` を変更したら必ず再生成してください。

```
javac -d build/tmp/gen src/main/java/io/github/tsiw/bulkmine/config/BulkMineConfig.java src/main/java/io/github/tsiw/bulkmine/config/BlockDefaults.java src/main/java/io/github/tsiw/bulkmine/config/ConfigWriter.java tools/DumpDefaultConfig.java
java -cp build/tmp/gen io.github.tsiw.bulkmine.config.DumpDefaultConfig
```

アイコンも同様に生成物です。

```
java tools/GenIcon.java
```

## 実装上の注意点

過去に実際に踏んだ問題です。いずれも症状が出るまで時間がかかる種類のものなので、該当箇所を触る際は注意してください。

**設定のID解決はサーバー起動時まで遅らせる。** `ToolRegistry` / `BlockWhitelist` の解決を `onInitialize()` で行うと他Modのアイテム登録と競合し、登録済みのツールが起動のたびに黙って捨てられます。`ServerLifecycleEvents.SERVER_STARTING` で行っています。

**アタッチメント型はMod初期化で明示的に登録する。** 登録はstatic初期化子にありますが、参照がラムダの中だけだとクラスが初めて触られるのが「最初のプレイヤー参加時」になり、プレイヤーデータ読込に間に合いません。保存済みの設定が毎回破棄されます。`PlayerSettings.init()` の呼び出しがそのためだけに存在します。

**耐久はAFTERイベントの発火順に注意。** FabricのAFTERイベントは、バニラが起点ブロック分の耐久を消費する**前**に発火します。残り1まで削るとその後バニラが最後の1を取ってツールが壊れます。BEFOREで取ったスナップショットと比較して、未消費なら2を残しています。

**ユーザーが読むファイルはASCIIのみ。** 設定ファイルとログ出力は英語です。ゲーム外のコンソールやエディタがUTF-8とは限らず、手編集する設定ファイルが文字化けするのは英語表記より悪いためです。日本語は `lang/ja_jp.json` にのみ存在します（MinecraftがUTF-8として読むことが仕様で保証されている唯一の場所）。

## ライセンス

[MIT License](LICENSE) です。
