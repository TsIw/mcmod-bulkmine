## BulkMine

※日本語の説明はページ下部にあります。

A bulk-breaking mod that digs stone and dirt in bulk and fells whole trees.

- **One key**: each press of `V` cycles ON (3) → ON (5) → OFF.
- **Always know the state**: a readout in the corner always shows "Bulk ON:3" or "Bulk OFF", so you never keep digging with it on by mistake.
- **Tools don't suddenly break**: while bulk mining, durability stops at 1 and you get a warning and a sound.
- **Only what you meant**: blocks that would not drop anything (diamond ore with a stone pickaxe, for example) are left in place. Neighbouring trees of another kind and leaves you placed as decoration are never caught up.
- **No new items or recipes**: it only extends how your existing tools behave.

---

### ✨ Features

#### ⛏️ Cube mining

With a pickaxe or shovel, mines a cube centred on the block you hit (3×3×2 by default), leaving out the layers below that block. Turn on `cubeBelow` to take the whole cube (3×3×3).

#### 🪓 Tree felling

Hit a log with an axe to fell that tree's logs and leaves together.

- Works on every vanilla tree, huge red and brown mushrooms, and crimson and warped huge fungi.
- Trees are told apart by kind: cutting an oak leaves the birch next to it intact.
- Giant spruce and giant jungle trees around 30 blocks tall come down from the base.
- Nothing below the block you hit is cut. Turn on `treeBelow` to take the stump and roots too.

#### 🍃 Leaf clearing

Hit leaves with a hoe or shears to clear the connected leaves of the same tree. Nothing below the block you hit is cleared.

#### 🌿 Mangrove roots

Hit a root with an axe: if it connects to the trunk, the whole tree comes down; otherwise just the roots are cleared.

#### 🧩 Other mod support

Tools and blocks from other mods can be added with a command.

---

### 📦 Installation

- Requires Fabric Loader and **Fabric API**.
- **On a multiplayer server, the server needs it too.** With the mod on the client only, the readout shows "Unsupported" and nothing happens. In single player there is nothing to worry about.

---

### ⚙️ Configuration

`config/bulkmine.json` controls the size steps, durability cost, felling range, target blocks and more. Operators can add or remove tools and target blocks with `/bulkmine` commands.

---

### 💬 Commands

BulkMine's settings can be changed with commands.

#### 👤 Everyone

Works in single player even with cheats off.

| Command | Description |
|---|---|
| `/bulkmine config` | Show your own settings and the server settings |
| `/bulkmine config size <1-15>` | Change your cube size (odd numbers only) |
| `/bulkmine config cubeBelow <on/off>` | Also dig the layers below the cursor (off by default) |
| `/bulkmine config treeBelow <on/off>` | Also fell and clear below the block you hit (off by default) |

#### 🛡️ Operators

Always in single player; permission level 2 or higher on a server.

| Command | Description |
|---|---|
| `/bulkmine tool add <item> <roles>` | Register a tool |
| `/bulkmine tool remove <item> [roles]` | Unregister a tool |
| `/bulkmine tool list` | List registered tools |
| `/bulkmine block add <block> <kind>` | Add a target block |
| `/bulkmine block remove <block>` | Remove a target block |
| `/bulkmine block deny <block>` | Blacklist a block (never bulk-broken) |
| `/bulkmine block allow <block>` | Take a block off the blacklist |
| `/bulkmine block list` | Show target block counts and the blacklist |
| `/bulkmine reload` | Reload the config file |

- Write `hand` instead of `<item>` or `<block>` to use whatever you are holding. For a tool from another mod, the easiest way is to hold it and run `/bulkmine tool add hand all`.
- Roles are `pickaxe` / `axe` / `shovel` / `all`, and can be combined like `pickaxe|shovel`.
- Block kinds are `pickaxe` / `shovel` (cube mining) and `log` / `leaves` (logs and leaves of trees you add).
- Changes made by command are saved to the config file right away.

---

## BulkMine（日本語）

石や土をまとめて掘り、木を丸ごと伐採する一括破壊Modです。

- **キーひとつで操作**：`V` キーを押すたびに ON(3) → ON(5) → OFF と切り替わります。
- **状態が常に見える**：画面の隅に「一括ON:3」「一括OFF」が常に表示されるので、うっかりONのまま掘ることがありません。
- **ツールがいきなり壊れない**：一括破壊中は耐久が残り1で止まり、警告と効果音で知らせます。
- **余計なものを壊さない**：ドロップが出ないブロック（石のつるはしでダイヤ鉱石など）は掘らずに残します。隣の別の木や、プレイヤーが設置した葉の飾りも巻き込みません。
- **追加アイテム・レシピなし**：既存のツールの挙動を拡張するだけです。

---

### ✨ 機能

#### ⛏️ 立方範囲の採掘

叩いたブロックより下の層を除き、つるはし・シャベルで、カーソルを中心とした立方範囲（既定 3×3×2）をまとめて掘ります。`cubeBelow` をONにすると下の層も含めて掘ります（3×3×3）。

#### 🪓 木の一括伐採

斧で原木を叩くと、その木の原木と葉をまとめて伐採します。

- バニラの木すべてと、巨大キノコ（赤・茶）、ネザーの巨大キノコ（真紅・歪んだ）に対応しています。
- 木は種類ごとに区別されます。オークを切っても隣のシラカバは削れません。
- 高さ30前後の巨大トウヒや巨大ジャングルも根元から倒れます。
- 叩いた高さより下は残します。`treeBelow` をONにすると切り株や根も刈ります。

#### 🍃 葉の一括刈り

クワやハサミで葉を叩くと、つながった同じ木の葉をまとめて刈ります。叩いた高さより下は刈りません。

#### 🌿 マングローブの根

斧で根を叩くと、幹までつながっていれば木全体を、つながっていなければ根だけを刈ります。

#### 🧩 他Modへの対応

他Modのツールやブロックも、コマンドで登録すれば対象にできます。

---

### 📦 導入

- Fabric Loader と **Fabric API が必要**です。
- **マルチプレイではサーバーにも導入が必要です。** クライアントだけに入れた場合、画面に「非対応」と表示され、機能は動きません。シングルプレイでは気にする必要はありません。

---

### ⚙️ 設定

`config/bulkmine.json` で、立方サイズの段階、耐久の消費率、伐採の範囲、対象ブロックなどを変更できます。管理者は `/bulkmine` コマンドからツールや対象ブロックを追加・除外できます。

---

### 💬 コマンド

BulkMineの設定はコマンドから変更することができます。

#### 👤 全プレイヤー

シングルプレイではチート無効のままでも使えます。

| コマンド | 内容 |
|---|---|
| `/bulkmine config` | 自分の設定とサーバー設定を表示 |
| `/bulkmine config size <1-15>` | 自分の立方サイズを変更（奇数のみ） |
| `/bulkmine config cubeBelow <on/off>` | 立方範囲でカーソルより下の層も掘る（既定 off） |
| `/bulkmine config treeBelow <on/off>` | 伐採・葉・根で叩いた位置より下も刈る（既定 off） |

#### 🛡️ 管理用

シングルプレイでは常に、サーバーでは権限レベル2以上で使えます。

| コマンド | 内容 |
|---|---|
| `/bulkmine tool add <アイテム> <ロール>` | ツールを登録 |
| `/bulkmine tool remove <アイテム> [ロール]` | ツールの登録を解除 |
| `/bulkmine tool list` | 登録済みツールの一覧 |
| `/bulkmine block add <ブロック> <種類>` | 対象ブロックを追加 |
| `/bulkmine block remove <ブロック>` | 対象ブロックから除外 |
| `/bulkmine block deny <ブロック>` | ブラックリストに追加（一括破壊しない） |
| `/bulkmine block allow <ブロック>` | ブラックリストから解除 |
| `/bulkmine block list` | 対象ブロックの件数とブラックリストを表示 |
| `/bulkmine reload` | 設定ファイルを再読み込み |

- `<アイテム>` や `<ブロック>` の代わりに `hand` と書くと、手に持っているものが対象になります。他Modのツールは手に持って `/bulkmine tool add hand all` を実行するのが簡単です。
- ロールは `pickaxe` / `axe` / `shovel` / `all` です。`pickaxe|shovel` のように連結できます。
- ブロックの種類は `pickaxe` / `shovel`（立方範囲）と `log` / `leaves`（追加した木の原木・葉）です。
- コマンドで変更した内容は設定ファイルにすぐ保存されます。
