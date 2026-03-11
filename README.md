# ArsPaper

Minecraft MOD「Ars Nouveau」の魔法システムを、バニラクライアント＆Geyser（統合版）互換で再現する Paper プラグインです。

## 動作環境

- Paper 1.21.4 以降
- Java 21+
- Geyser 経由の統合版クライアント対応

## ビルド

```bash
./gradlew build
```

成果物: `build/libs/ArsPaper-<version>.jar`

---

## 機能一覧

### 1. スペルシステム

スペルは **Form（形態）→ Effect（効果）→ Augment（増強）** の連鎖で構成されます。

#### Form（発動方法）

| Form | 説明 | マナコスト | ティア |
|------|------|-----------|-------|
| Projectile | 飛び道具を発射し、命中地点で発動 | 5 | 1 |
| Touch | 5ブロック先までのレイトレースで対象に発動 | 3 | 1 |
| Self | 術者自身に発動 | 2 | 1 |
| Underfoot | 足元のブロックに発動 | 2 | 1 |

#### Effect（効果）

| Effect | 説明 | マナコスト | ティア |
|--------|------|-----------|-------|
| Break | ブロックを破壊（保護プラグイン互換） | 10 | 1 |
| Harm | 対象にダメージ（基礎5.0 + Amplify毎+2.5） | 15 | 1 |
| Heal | 対象を回復（基礎4.0 + Amplify毎+2.0） | 15 | 1 |
| Grow | 作物の成長を促進 | 10 | 1 |
| Light | ブロックに光源設置 / エンティティに暗視+発光付与 | 5 | 1 |
| Speed | 対象に移動速度上昇を付与 | 10 | 1 |
| Blink | 向いている方向にテレポート（基礎8 + Amplify毎+4ブロック、最大32） | 20 | 2 |
| Bounce | 対象を上方に打ち上げ（基礎1.2 + Amplify毎+0.5、最大4.0） | 10 | 1 |

#### Augment（修飾）

| Augment | 説明 | マナコスト | ティア |
|---------|------|-----------|-------|
| Amplify | 直後のEffectの威力を増加 | 10 | 1 |
| AOE | 直後のEffectを範囲3ブロックに拡大（スタック可能） | 15 | 1 |
| Extend Time | 直後のEffectの持続時間を+10秒（スタック可能） | 8 | 2 |
| Pierce | Projectileの貫通回数を+1（スタック可能） | 12 | 2 |

**重要**:
- Augmentは直後のEffectにのみ適用され、次のEffectには引き継がれません。
- ティア2以上のグリフは、対応するティアのスペルブックが必要です。

#### スペルの例

| スペル名 | 構成 | 合計マナ | 効果 |
|----------|------|---------|------|
| Fire Bolt | Projectile + Harm + Amplify | 30 | 強化ダメージ弾 |
| Heal Self | Self + Heal | 17 | 自己回復 |
| Farm | Touch + Grow + AOE | 28 | 範囲作物成長 |
| Speed Boost | Self + Speed + Amplify + Extend Time | 30 | 強化・延長移動速度 |
| Piercing Shot | Projectile + Harm + Pierce | 32 | 貫通ダメージ弾 |
| Blink | Self + Blink | 22 | 前方テレポート |

### 2. マナシステム

- 全プレイヤーにマナプール（初期上限100）
- BossBar でリアルタイム表示
- 毎秒自動回復（デフォルト2/秒）
- グリフをアンロックするたびに最大マナが増加（+5/グリフ）
- メイジアーマー装備でマナボーナス追加
- スペル発動でマナを消費、不足時は発動失敗

### 3. スペルブック（3ティア）

ティアに応じてスロット数・使用可能グリフティアが変わります。

| ティア | アイテム名 | スロット数 | 使用可能グリフティア | CustomModelData |
|--------|-----------|-----------|-------------------|-----------------|
| 1 | Novice Spell Book | 3 | Tier 1 まで | 100001 |
| 2 | Apprentice Spell Book | 6 | Tier 2 まで | 100002 |
| 3 | Archmage Spell Book | 10 | Tier 3 まで | 100003 |

#### スペルブックの操作

| 操作 | アクション |
|------|----------|
| 右クリック | 選択中スロットのスペルを発動 |
| スニーク + 右クリック | スペルスロットを切り替え |
| スニーク + 左クリック | スペル組み立てGUIを開く |

### 4. スペル組み立てGUI

スニーク+左クリックで開くGUI画面：

- **上段**: 現在のスペル構成（最大8グリフ）。クリックでグリフ除去
- **中段**: 利用可能なグリフパレット。クリックで追加（ティア制限あり）
- **下段ボタン**: Forms/Effects/Augments タブ切替、Undo、Clear All、Save

アンロック済みかつスペルブックのティア以下のグリフのみ使用可能。先頭は必ずFormである必要があります。

### 5. メイジアーマー（3ティア）

革防具ベースのカスタム防具。装備するとマナボーナスが付与されます。

| ティア | マナボーナス（1部位） | 全身装備時ボーナス | 色 |
|--------|---------------------|-------------------|-----|
| Novice | +5 | +20 | 紫 |
| Apprentice | +15 | +60 | 濃紫 |
| Archmage | +30 | +120 | 金 |

各ティア × 4部位（ヘルメット、チェストプレート、レギンス、ブーツ）= 12アイテム

防具の着脱時にマナボーナスが自動再計算されます。

### 6. Scribing Table（グリフ研究）

書見台ベースのカスタムブロック。設置して右クリックでGUIを開きます。

**アンロックコスト:**

| ティア | レベル | ラピスラズリ |
|--------|-------|-------------|
| Tier 1 | 5 | 1個 |
| Tier 2 | 10 | 4個 |
| Tier 3 | 20 | 8個 |

アンロック時に最大マナボーナス（デフォルト+5）が付与されます。

### 7. Source システム

ワールド内の魔法エネルギー「Source」の生成・貯蔵・輸送システム。

#### Source Jar（貯蔵）
- 樽ベースのカスタムブロック
- 最大10,000 Source を貯蔵
- 右クリックで貯蔵量を確認

#### Sourcelink（生成）
- **Volcanic Sourcelink**: かまどベース、燃料を消費してSource生成（50/tick）
- **Mycelial Sourcelink**: 燻製器ベース、食料を消費してSource生成（30/tick）
- 隣接するSource Jarに自動供給

#### Source Relay（輸送）
- Dominion Wand で送信元→送信先を接続
- 最大30ブロックの距離まで接続可能
- 2秒ごとに最大100 Sourceを自動転送
- 接続データは `source-network.yml` に永続化

### 8. Dominion Wand の操作

| 操作 | アクション |
|------|----------|
| カスタムブロックを右クリック | 1回目: 送信元を選択（パーティクル表示） |
| 別のカスタムブロックを右クリック | 2回目: 接続確立（接続パーティクル表示） |
| 空中を右クリック | 選択中ブロックの接続先一覧を表示 |
| スニーク + 右クリック | 選択を解除 |

### 9. 儀式システム

Ritual Core と Pedestal を使ったマルチブロッククラフティング。

#### ブロック

| ブロック | ベース | 説明 |
|---------|-------|------|
| Ritual Core | Lodestone | 儀式の中心ブロック。右クリックで発動 |
| Pedestal | Brewing Stand | 素材を置く台座。右クリックで素材設置/取得 |

#### 儀式の手順

1. Ritual Core を設置
2. 半径5ブロック以内に Pedestal を配置
3. 各 Pedestal に必要素材を右クリックで設置
4. 近隣に十分な Source を蓄えた Source Jar を配置
5. Ritual Core を右クリックで儀式発動

#### デフォルト儀式レシピ

| 儀式名 | 素材 | Source | 結果 |
|--------|------|--------|------|
| Apprentice Spell Book | Diamond×2, Gold×2, Lapis×2, Book, Amethyst Shard | 500 | Apprentice Spell Book |
| Archmage Spell Book | Nether Star, Diamond×2, Emerald×2, Echo Shard×2, Book | 2000 | Archmage Spell Book |
| Apprentice Armor Set | Gold×4, Lapis×2, Leather×2 | 300 | Apprentice Mage Chestplate |
| Archmage Armor Set | Diamond×4, Emerald×2, Nether Star, Blaze Rod | 1500 | Archmage Mage Chestplate |
| Enchanted Source Jar | Glass×4, Amethyst Shard | 100 | Source Jar |
| Ritual Core | Lodestone, Diamond×2, Lapis×2, Obsidian×2, Blaze Powder | 1000 | Ritual Core |

儀式レシピは `rituals.yml` で追加・変更できます。

### 10. クラフトレシピ

全カスタムアイテムにクラフトレシピが設定されています。`recipes.yml` で自由にカスタマイズ可能。

#### レシピ設定形式

```yaml
recipes:
  my_item:
    type: shaped       # shaped or shapeless
    result: "custom:spell_book_novice"  # "custom:<id>" or "MATERIAL_NAME"
    amount: 1
    shape:
      - " D "
      - "LBL"
      - " R "
    ingredients:
      D: DIAMOND
      L: LAPIS_LAZULI
      B: BOOK
      R: REDSTONE
```

### 11. カスタムブロックの仕組み

- 設置時: アイテムのPDCデータ → ブロックのTileState PDCへ転送
- 破壊時: TileState PDCデータ → ドロップアイテムのPDCへ復元
- 不可視ArmorStand (Marker:1) でカスタム見た目を表示（Geyser互換）
- 保護プラグイン（WorldGuard等）と互換

---

## カスタムアイテム一覧

全てバニラアイテム + PDC + CustomModelData で実現。新規アイテムIDは使用しません。

| アイテム | ベース | 取得コマンド |
|----------|-------|-------------|
| Novice Spell Book | 本 | `/ars give spell_book_novice` |
| Apprentice Spell Book | 本 | `/ars give spell_book_apprentice` |
| Archmage Spell Book | 本 | `/ars give spell_book_archmage` |
| Dominion Wand | 棒 | `/ars give dominion_wand` |
| Scribing Table | 書見台 | `/ars give scribing_table` |
| Source Jar | 樽 | `/ars give source_jar` |
| Volcanic Sourcelink | かまど | `/ars give volcanic_sourcelink` |
| Mycelial Sourcelink | 燻製器 | `/ars give mycelial_sourcelink` |
| Ritual Core | 磁石石 | `/ars give ritual_core` |
| Pedestal | 醸造台 | `/ars give pedestal` |
| Novice Mage Helmet | 革ヘルメット | `/ars give mage_novice_helmet` |
| Novice Mage Chestplate | 革チェストプレート | `/ars give mage_novice_chestplate` |
| Novice Mage Leggings | 革レギンス | `/ars give mage_novice_leggings` |
| Novice Mage Boots | 革ブーツ | `/ars give mage_novice_boots` |
| Apprentice Mage (各部位) | 革防具 | `/ars give mage_apprentice_<部位>` |
| Archmage Mage (各部位) | 革防具 | `/ars give mage_archmage_<部位>` |

---

## コマンド

| コマンド | 権限 | 説明 |
|---------|------|------|
| `/ars give <itemId>` | `arspaper.admin` | カスタムアイテムを取得 |
| `/ars mana` | `arspaper.use` | マナ情報を表示 |
| `/ars spell list` | `arspaper.use` | 利用可能なグリフ一覧 |
| `/ars spell set <slot> <Name>:<form> <effects...>` | `arspaper.use` | コマンドでスペルを設定 |

### コマンドでのスペル設定例

```
/ars spell set 1 FireBolt:projectile harm amplify
/ars spell set 2 HealSelf:self heal
/ars spell set 3 FarmGrow:touch grow aoe
/ars spell set 4 Warp:self blink amplify
```

---

## 権限

| 権限ノード | デフォルト | 説明 |
|-----------|----------|------|
| `arspaper.use` | 全プレイヤー | 基本機能の使用 |
| `arspaper.admin` | OP | 管理コマンド（give等） |

---

## 設定ファイル

### config.yml

```yaml
mana:
  # プレイヤーのマナ上限の初期値
  default-max: 100
  # 毎回の回復量
  default-regen-rate: 2
  # 回復間隔（tick単位, 20tick = 1秒）
  regen-interval-ticks: 20
  # グリフアンロック1回あたりのマナ上限ボーナス
  per-glyph-unlock-bonus: 5
```

### recipes.yml

クラフトレシピの設定。Shaped（定形）/ Shapeless（不定形）をサポート。
`result` に `custom:<item_id>` を指定するとカスタムアイテムを結果にできます。

### rituals.yml

儀式レシピの設定。

```yaml
rituals:
  my_ritual:
    name: "My Ritual"
    pedestal-items:
      - DIAMOND
      - EMERALD
    source: 500
    result: "custom:spell_book_archmage"
```

---

## データ保存

| データ | 保存先 | 形式 |
|--------|-------|------|
| プレイヤーマナ | Player PDC | INTEGER |
| グリフマナボーナス | Player PDC (`glyph_mana_bonus`) | INTEGER |
| 防具マナボーナス | Player PDC (`armor_mana_bonus`) | INTEGER |
| アンロック済みグリフ | Player PDC | JSON STRING |
| スペル構成 | ItemStack PDC | JSON STRING |
| カスタムブロックデータ | TileState PDC | STRING/INTEGER |
| Source接続ネットワーク | `source-network.yml` | YAML |
| クラフトレシピ | `recipes.yml` | YAML |
| 儀式レシピ | `rituals.yml` | YAML |

---

## Geyser/統合版 互換性

- Display Entity ではなく ArmorStand (Marker:1) でカスタムブロックの見た目を表示
- GUI は全てボタン式（クリックイベントベース）。ドラッグ操作不要
- カスタムアイテムは CustomModelData で見た目を変更（リソースパック連携）
