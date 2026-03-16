# ArsPaper

Minecraft MOD「Ars Nouveau」の魔法システムを、バニラクライアント＆Geyser（統合版）互換で再現する Paper プラグインです。

## 動作環境

- Paper 1.21.1 以降
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
全グリフは日本語表示に対応し、各グリフに説明文が付属します。

#### Form（発動方法）

| Form | 日本語名 | 説明 | マナ | Tier |
|------|---------|------|------|------|
| Projectile | 投射 | 魔力弾を発射し、命中した対象に効果を適用 | 5 | 1 |
| Touch | 接触 | 5ブロック先までのレイトレースで対象に発動 | 3 | 1 |
| Self | 自己 | 術者自身に即座に発動 | 2 | 1 |
| Underfoot | 足元 | 足元のブロックに発動 | 2 | 1 |
| Orbit | 軌道 | 術者の周囲を回る弾が敵にダメージ | 10 | 2 |
| Beam | 照射 | 視線方向にビームを照射 | 8 | 2 |
| Wall | 壁 | 術者の前方に壁状に効果を展開 | 10 | 2 |

#### Effect（効果）

| Effect | 日本語名 | 説明 | マナ | Tier |
|--------|---------|------|------|------|
| Break | 破壊 | ブロックを破壊する | 10 | 1 |
| Harm | 害悪 | 対象にダメージを与える（基礎5.0 + Amplify毎+2.5） | 15 | 1 |
| Heal | 回復 | 対象を回復する（基礎4.0 + Amplify毎+2.0） | 15 | 1 |
| Grow | 成長 | 作物の成長を促進する | 10 | 1 |
| Light | 光明 | ブロックに光源設置 / エンティティに暗視+発光付与 | 5 | 1 |
| Speed | 迅速 | 対象に移動速度上昇を付与する | 10 | 1 |
| Bounce | 跳躍 | 対象を上方に打ち上げる | 10 | 1 |
| Ignite | 着火 | 対象を炎上させる | 10 | 1 |
| Knockback | 吹飛 | 対象を吹き飛ばす | 8 | 1 |
| Snare | 拘束 | 対象の移動を封じる | 12 | 1 |
| Launch | 打上 | 対象を上空に打ち上げる | 15 | 1 |
| Flare | 閃光 | 暗視を付与する | 10 | 1 |
| Blink | 瞬間移動 | 向いている方向にテレポートする | 20 | 2 |
| Freeze | 氷結 | 対象を凍結させる | 15 | 2 |
| Shield | 盾 | ダメージ耐性を付与する | 20 | 2 |
| Explosion | 爆発 | 対象位置で小爆発を起こす | 25 | 2 |
| Gravity | 重力 | 対象を浮遊させる | 20 | 2 |
| Windshear | 烈風 | 視線方向に吹き飛ばす | 20 | 2 |
| Lightning | 雷撃 | 対象に雷を落とす | 30 | 3 |
| Wither | 衰弱 | 対象を衰弱させる | 35 | 3 |

#### Augment（増強）

| Augment | 日本語名 | 説明 | マナ | Tier |
|---------|---------|------|------|------|
| Amplify | 増幅 | 直後のEffectの威力を増加する | 10 | 1 |
| Dampen | 減衰 | 直後のEffectの威力を軽減する | 0 | 1 |
| AOE | 範囲(XZ) | 直後のEffectを水平方向に範囲拡大する | 35 | 2 |
| AOE Vertical | 範囲(Y) | 直後のEffectを垂直方向に範囲拡大する | 35 | 2 |
| Extend Time | 延長 | 直後のEffectの持続時間を延長する | 10 | 2 |
| Duration Down | 短縮 | 直後のEffectの持続時間を短縮する | 10 | 2 |
| Pierce | 貫通 | Projectileの貫通回数を+1 | 12 | 2 |
| Accelerate | 加速 | Projectileの速度を増加する | 10 | 2 |
| Decelerate | 減速 | Projectileの速度を低下させる | 10 | 2 |
| Extract | 抽出 | シルクタッチ効果を付与する | 30 | 2 |
| Fortune | 幸運 | ドロップ量を増加させる | 30 | 2 |
| Randomize | 無作為 | 効果にランダム性を付与する | 15 | 2 |
| Delay | 遅延 | 後続の効果を遅延させる（複数積みで加算） | 10 | 1 |
| Reset | 初期化 | 増強状態をリセットする | 0 | 1 |
| Wall | 壁 | ルーン/設置系の壁状展開 | 20 | 2 |
| Linger | 残留 | ルーンのトリガー後も維持する | 20 | 2 |
| Trail | 軌跡 | ビーム軌道上に効果を適用（照射専用） | 15 | 2 |
| Split | 分裂 | Projectileを複数発射する（最大8発） | 20 | 3 |

**重要**:
- Augmentは直後のEffectにのみ適用され、次のEffectの前にリセットされます
- Formは1つまで、同一Effectの重複は不可
- Tier 2以上のグリフは対応するティアのスペルブックが必要です

#### スペルの例

| スペル名 | 構成 | 合計マナ | 効果 |
|----------|------|---------|------|
| 火炎弾 | 投射 + 害悪 + 増幅 | 30 | 強化ダメージ弾 |
| 自己回復 | 自己 + 回復 | 17 | 自己回復 |
| 範囲農業 | 接触 + 成長 + 範囲 | 28 | 範囲作物成長 |
| 加速バフ | 自己 + 迅速 + 増幅 + 延長 | 30 | 強化・延長移動速度 |
| 貫通弾 | 投射 + 害悪 + 貫通 | 32 | 貫通ダメージ弾 |
| 瞬間移動 | 自己 + 瞬間移動 | 22 | 前方テレポート |
| 雷撃弾 | 投射 + 雷撃 + 範囲 | 50 | 範囲雷撃弾 |
| 烈風砲 | 投射 + 烈風 + 増幅 | 35 | 強化吹き飛ばし |

### 2. マナシステム

- 全プレイヤーにマナプール（初期上限100）
- BossBar でリアルタイム表示
- 毎秒自動回復（デフォルト2/秒）
- グリフをアンロックするたびに最大マナが増加（+5/グリフ）
- メイジアーマー装備でマナボーナス追加
- ソースベリーを食べてマナ25即時回復
- スペル発動でマナを消費、不足時は発動失敗

### 3. スペルブック（3ティア）

ティアに応じてスロット数・使用可能グリフティアが変わります。

| ティア | アイテム名 | スロット | 使用可能Tier | 入手方法 |
|--------|-----------|---------|-------------|---------|
| Novice | 初心者の魔導書 | 3 | Tier 1 | クラフト |
| Apprentice | 見習い魔導書 | 6 | Tier 2 | 儀式 |
| Archmage | 大魔導書 | 10 | Tier 3 | 儀式 |

#### スペルブックの操作

| 操作 | アクション |
|------|----------|
| 右クリック | 選択中スロットのスペルを発動 |
| スニーク + 右クリック | スペルスロットを切り替え |
| スニーク + 左クリック | スペル組み立てGUIを開く |

### 4. スペル組み立てGUI

スニーク+左クリックで開くGUI画面：

- **上段**: 現在のスペル構成（最大8グリフ）。クリックでグリフ除去
- **中段**: 利用可能なグリフパレット（日本語名+説明文付き）。クリックで追加
- **下段ボタン**: Forms/Effects/Augments タブ切替、Undo、Clear All、Save

**バリデーション**:
- Formは1つまでしか追加できません
- 同一Effectの重複は不可
- アンロック済みかつスペルブックのティア以下のグリフのみ使用可能

### 5. Scribing Table（グリフ研究）

書見台ベースのカスタムブロック。設置して右クリックでGUIを開きます。

各グリフには固有の解放コスト（経験値レベル + 素材）が設定されています。
本家Ars Nouveauのレシピを参考に、バニラ素材でテーマ性を再現しています。

**解放コストの例:**

| グリフ | 必要レベル | 必要素材 |
|--------|----------|---------|
| 投射 | 5 | 矢×8, ラピスラズリ×1 |
| 害悪 | 5 | 鉄の剣×3, レッドストーン×4 |
| 回復 | 5 | きらめくスイカ×4, 金のリンゴ×1 |
| 瞬間移動 | 10 | エンダーパール×4, コーラスフルーツ×2 |
| 雷撃 | 20 | 避雷針×3, 海洋の心×1 |
| 分裂 | 20 | プリズマリンの欠片×8, エンドクリスタル×1 |

全グリフの詳細は `glyphs.yml` で確認・カスタマイズできます。

### 6. メイジアーマー（3ティア）

革防具ベースのカスタム防具。装備するとマナボーナスが付与されます。

| ティア | マナボーナス（1部位） | 全身装備時 | 色 | 入手方法 |
|--------|---------------------|-----------|-----|---------|
| Novice | +5 | +20 | 紫 | クラフト（革 + ラピスラズリ） |
| Apprentice | +15 | +60 | 濃紫 | クラフト（マジックファイバー + ラピスラズリ） |
| Archmage | +30 | +120 | 金 | クラフト（マジックファイバー + ソースジェム） |

#### スレッドシステム

防具にはティアに応じたスレッドスロットがあり、効果付きスレッドをセットできます。
スレッドは儀式で空スレッドを効果付きスレッドに変換して入手します。

| ティア | スロット数 |
|--------|----------|
| Novice | 1 |
| Apprentice | 2 |
| Archmage | 3 |

防具を手に持ってスニーク+右クリックでスレッドGUIが開きます。

| スレッド | 効果 | 種別 |
|---------|------|------|
| マナリジェン | マナ回復 +1/tick | マナ系 |
| マナブースト | 最大マナ +20 | マナ系 |
| 迅速 | 移動速度上昇（常時） | ポーション系 |
| 跳躍 | 跳躍力上昇（常時） | ポーション系 |
| 暗視 | 暗視（常時） | ポーション系 |
| 耐火 | 火炎耐性（常時） | ポーション系 |
| 水中呼吸 | 水中呼吸（常時） | ポーション系 |
| 魔力増幅 | スペル威力 +15% | スペル系 |
| 詠唱効率 | マナコスト -10% | スペル系 |

#### カスタムエンチャント（PDCベース）

儀式でエンチャント本を作成し、金床でメイジアーマーに適用します。

| エンチャント | 最大Lv | 効果/Lv |
|------------|--------|---------|
| マナ加速 | 3 | マナ回復 +1/tick |
| マナ上昇 | 3 | 最大マナ +15/+30/+50 |

### 7. 儀式システム

Ritual Core と Pedestal を使ったマルチブロッククラフティング。
本家Ars NouveauのEnchanting Apparatusを再現しています。

#### ブロック

| ブロック | ベース | 説明 |
|---------|-------|------|
| Ritual Core | Lodestone | 儀式の中心ブロック。右クリックで発動 |
| Pedestal | Brewing Stand | 素材を置く台座。上面にアイテム表示あり |

#### 儀式の手順

1. Ritual Core を設置
2. 隣接1ブロック以内に Pedestal を配置
3. 各 Pedestal に必要素材を右クリックで設置（アイテムフレームで視覚表示）
4. 近隣に十分な Source を蓄えた Source Jar を配置
5. Ritual Core を右クリックで儀式発動

**儀式アニメーション**: 発動すると3秒間のエンチャントパーティクル演出が再生され、完了時に結果アイテムがドロップします。

#### 儀式レシピ一覧

Source Gem は魔力触媒として上位儀式レシピの素材に使用されます。

**中間素材（Intermediate Materials）**

| 儀式名 | 素材 | Source | 結果 |
|--------|------|--------|------|
| ソースジェム精製 | Amethyst Shard×4, Lapis Lazuli×2 | 200 | Source Gem |
| マジックファイバー精製 | Wheat Seeds×2, Source Gem×2 | 100 | Magebloom Fiber |
| ソースストーン精製 | Stone×4, Source Gem | 100 | Sourcestone |

**スペルブック**

| 儀式名 | 素材 | Source | 結果 |
|--------|------|--------|------|
| 見習い魔導書への昇格 | Diamond×3, Quartz Block×2, Blaze Rod×2, Source Gem | 500 | Apprentice Spell Book |
| 大魔導書への昇格 | Nether Star, Emerald×2, Ender Pearl×2, Totem of Undying, Source Gem×2 | 2000 | Archmage Spell Book |

**インフラストラクチャ**

| 儀式名 | 素材 | Source | 結果 |
|--------|------|--------|------|
| 儀式のコア | Lodestone, Diamond×2, Lapis×2, Obsidian×2, Source Gem | 1000 | Ritual Core |
| ヴォルカニックソースリンク | Furnace, Netherrack×2, Magma Block×2, Blaze Powder×2, Source Gem | 300 | Volcanic Sourcelink |
| マイセリアルソースリンク | Smoker, Mycelium×2, Brown/Red Mushroom, Bone Meal, Source Gem×2 | 300 | Mycelial Sourcelink |

**消耗品**

| 儀式名 | 素材 | Source | 結果 |
|--------|------|--------|------|
| ソースベリー | Glow Berries×2, Amethyst Shard×2 | 50 | Source Berry |
| ソースベリー（大量） | Glow Berries×4, Amethyst Shard×4 | 150 | Source Berry |

**ワールド効果儀式**

| 儀式名 | 効果 | Source |
|--------|------|--------|
| 飛行の儀式 | 300秒間のクリエイティブ飛行 | 2000 |
| 月落としの儀式 | 時刻を夜に変更 | 500 |
| 日の出の儀式 | 時刻を朝に変更 | 500 |
| 修復の儀式 | コア上アイテムの耐久値全回復 | 800 |
| 帰還ポイント設定 | テレポート先を記録 | 300 |
| 帰還の儀式 | 記録地点にテレポート | 1500 |
| 封じ込めの儀式 | 半径30ブロック内のスポーン抑制（5分） | 1000 |
| 動物召喚の儀式 | ランダムパッシブモブ5匹召喚 | 400 |

儀式レシピは `rituals.yml` で追加・変更できます。

### 8. Source システム

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

### 9. ソースベリー

グロウベリーベースのカスタムアイテム。食べるとマナを25即時回復します。

- 儀式で作成（Glow Berries + Amethyst Shard）
- 満腹でも食べられる（グロウベリーの仕様）
- 回復時にパーティクル＋サウンドエフェクト
- マナが満タンの場合は「マナは満タンです」と通知

### 10. カスタムブロック表示

設置済みカスタムブロックはブロック種別ごとのパーティクルで視覚的に識別できます。

| ブロック | パーティクル |
|---------|-----------|
| Scribing Table | ENCHANT |
| Source Jar | END_ROD |
| Volcanic Sourcelink | FLAME |
| Mycelial Sourcelink | SPORE_BLOSSOM_AIR |
| Ritual Core | WITCH |
| Pedestal | 紫色 DUST |

プレイヤーの32ブロック範囲内で20tick間隔で表示されます。

### 11. Dominion Wand の操作

| 操作 | アクション |
|------|----------|
| カスタムブロックを右クリック | 1回目: 送信元を選択（パーティクル表示） |
| 別のカスタムブロックを右クリック | 2回目: 接続確立（接続パーティクル表示） |
| 空中を右クリック | 選択中ブロックの接続先一覧を表示 |
| スニーク + 右クリック | 選択を解除 |

### 12. 中間素材

儀式で精製する中間素材は、上位クラフトの基盤となります。

| 素材 | 作成方法 | 用途 |
|------|---------|------|
| Source Gem | 儀式（Amethyst Shard×4 + Lapis×2） | Scribing Table, Source Jar, Archmage防具, 儀式触媒 |
| Magebloom Fiber | 儀式（Wheat Seeds×2 + Source Gem×2） | Apprentice/Archmage防具 |
| Sourcestone | 儀式（Stone×4 + Source Gem） | Pedestal |

### 13. クラフトレシピ

基本ブロック・アイテムはクラフトテーブルで作成可能。上位装備・スペルブックは儀式専用です。

`craft-method` フィールドでレシピの入手方法を制御できます:
- `workbench`（デフォルト）: クラフトテーブルのみ
- `ritual`: 儀式のみ
- `both`: 両方

```yaml
recipes:
  my_item:
    type: shaped
    craft-method: workbench  # workbench, ritual, or both
    result: "custom:spell_book_novice"
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

---

## カスタムアイテム一覧

全てバニラアイテム + PDC + CustomModelData で実現。新規アイテムIDは使用しません。

| アイテム | ベース | 取得コマンド |
|----------|-------|-------------|
| 初心者の魔導書 | 本 | `/ars give spell_book_novice` |
| 見習い魔導書 | 本 | `/ars give spell_book_apprentice` |
| 大魔導書 | 本 | `/ars give spell_book_archmage` |
| ドミニオンワンド | 棒 | `/ars give dominion_wand` |
| Scribing Table | 書見台 | `/ars give scribing_table` |
| Source Jar | 樽 | `/ars give source_jar` |
| Volcanic Sourcelink | かまど | `/ars give volcanic_sourcelink` |
| Mycelial Sourcelink | 燻製器 | `/ars give mycelial_sourcelink` |
| Ritual Core | 磁石石 | `/ars give ritual_core` |
| Pedestal | 醸造台 | `/ars give pedestal` |
| ソースベリー | グロウベリー | `/ars give source_berry` |
| ソースジェム | プリズマリンの欠片 | `/ars give source_gem` |
| マジックファイバー | 糸 | `/ars give magebloom_fiber` |
| ソースストーン | 磨かれた石 | `/ars give sourcestone` |
| Novice Mage Armor (各部位) | 革防具 | `/ars give mage_novice_<部位>` |
| Apprentice Mage Armor (各部位) | 革防具 | `/ars give mage_apprentice_<部位>` |
| Archmage Mage Armor (各部位) | 革防具 | `/ars give mage_archmage_<部位>` |
| 初級スペルワンド | ブレイズロッド | `/ars give wand_novice` |
| 中級スペルワンド | ブレイズロッド | `/ars give wand_apprentice` |
| 上級スペルワンド | ブレイズロッド | `/ars give wand_archmage` |
| 空のスレッド | 糸 | `/ars give thread_empty` |
| マナリジェンスレッド | 糸 | `/ars give thread_mana_regen` |
| マナブーストスレッド | 糸 | `/ars give thread_mana_boost` |
| 迅速のスレッド | 糸 | `/ars give thread_speed` |
| 跳躍のスレッド | 糸 | `/ars give thread_jump_boost` |
| 暗視のスレッド | 糸 | `/ars give thread_night_vision` |
| 耐火のスレッド | 糸 | `/ars give thread_fire_resistance` |
| 水中呼吸のスレッド | 糸 | `/ars give thread_water_breathing` |
| 魔力増幅のスレッド | 糸 | `/ars give thread_spell_power` |
| 詠唱効率のスレッド | 糸 | `/ars give thread_spell_cost_down` |

---

## コマンド

| コマンド | 権限 | 説明 |
|---------|------|------|
| `/ars give <itemId>` | `arspaper.admin` | カスタムアイテムを取得 |
| `/ars mana` | `arspaper.use` | マナ情報を表示 |
| `/ars spell list` | `arspaper.use` | 利用可能なグリフ一覧 |
| `/ars spell set <slot> <Name>:<form> <effects...>` | `arspaper.use` | コマンドでスペルを設定 |
| `/ars cleanup` | `arspaper.admin` | 残留ArmorStandを除去 |

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
| `arspaper.admin` | OP | 管理コマンド（give, cleanup等） |

---

## 設定ファイル

### config.yml

```yaml
mana:
  default-max: 100          # マナ上限の初期値
  default-regen-rate: 2     # 毎回の回復量
  regen-interval-ticks: 20  # 回復間隔（20tick = 1秒）
  per-glyph-unlock-bonus: 5 # グリフアンロック1回あたりの最大マナボーナス
```

### glyphs.yml

各グリフのティア・マナコスト・解放コスト（必要レベル+素材）を定義。
サーバー管理者がバランスを自由にカスタマイズできます。

```yaml
glyphs:
  harm:
    tier: 1
    mana-cost: 15
    unlock-cost:
      level: 5
      materials:
        IRON_SWORD: 3
        REDSTONE: 4
```

### recipes.yml

クラフトレシピの設定。Shaped / Shapeless をサポート。
`craft-method` で workbench/ritual/both を指定可能。

### rituals.yml

儀式レシピの設定。ペデスタル素材・必要Source・結果アイテムを定義。

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
| グリフ設定 | `glyphs.yml` | YAML |

---

## Geyser/統合版 互換性

- カスタムブロックの視覚表示はパーティクルベース（Geyser完全対応）
- GUI は全てボタン式（クリックイベントベース）。ドラッグ操作不要
- カスタムアイテムは CustomModelData で見た目を変更（リソースパック連携）
- 全パーティクル・サウンドはバニラ準拠で統合版でも動作
- 儀式ブロックのアイテム表示は Glow Item Frame を使用
