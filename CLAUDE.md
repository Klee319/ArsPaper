# ArsPaper

リポジトリ：https://github.com/Klee319/ArsPaper.git
開発環境：Paper 1.21.1+ (Java 21+)　適宜公式リファレンスやskillを適宜参照すること

## 概要
Ars Nouveau MODの機能をバニラ+Geyser互換で再現するPaperプラグイン。

## 技術制約
- 新規アイテムID不可 → PDC + CustomModelData でカスタムアイテム識別
- Geyser互換 → Display EntityではなくArmorStand (Marker:1) ベース
- GUI → ボタン式Inventory GUI（クリックイベントベース）
- カスタムエンチャント → PDCベース（Bootstrap不使用、全Paperバージョン対応）

## パッケージ構成
```
com.arspaper/
├── ArsPaper.java           # メインクラス
├── item/                   # カスタムアイテムフレームワーク
├── spell/                  # スペルシステム (Form/Effect/Augment)
├── mana/                   # マナシステム (PDC + BossBar)
├── block/                  # カスタムブロック永続化 (TileState)
├── source/                 # Sourceネットワーク & Sourcelink
├── gui/                    # Inventory GUI (Geyser互換)
├── enchant/                # カスタムエンチャント (PDCベース)
├── ritual/                 # 儀式システム (RitualEffect/RitualRecipe)
├── command/                # Brigadierコマンド
└── util/                   # ユーティリティ
```

## 実装フェーズ
- [x] Phase 1: 基盤 (Gradle, アイテムフレームワーク, コマンド)
- [x] Phase 2: スペルシステム (Form/Effect/Augment, Serializer)
- [x] Phase 3: マナシステム (ManaManager, BossBar)
- [x] Phase 4: カスタムブロック (TileState同期, BlockDisplayModule)
- [x] Phase 5: GUI & Geyser互換 (SpellCraftingGui, ScribingTableGui)
- [x] Phase 6: Sourceネットワーク (Relay, Sourcelink, Dominion Wand)
- [x] Phase 7: 防具スレッドシステム (マルチスロット, 10種のスレッド, ThreadGui)
- [x] Phase 8: カスタムエンチャント (PDCベース, 金床適用, マナ加速/上昇)
- [x] Phase 9: 追加儀式エフェクト (飛行/月落とし/日の出/修復/帰還/封じ込め/動物召喚)

## コマンド
- `/ars give <itemId>` - カスタムアイテムを取得 (admin)
- `/ars mana` - マナ情報を表示
- `/ars spell list` - 利用可能なグリフ一覧
- `/ars spell set <slot> <Name>:<form> <effects...>` - スペル設定
- `/ars reload` - 設定ファイル再読込 (admin)
- `/ars cleanup` - 残留ArmorStand除去 (admin)
- `/ars debug mana` - マナ無限モードトグル (admin)

## カスタムアイテム一覧
| ID | ベース | 説明 |
|----|--------|------|
| spell_book_novice/apprentice/archmage | BOOK | スペルブック（右クリック発動、スニーク+右で切替、スニーク+左でGUI） |
| wand_novice/apprentice/archmage | BLAZE_ROD | スペルワンド（1スロット、右クリック発動、スニーク+右でGUI） |
| dominion_wand | STICK | Source接続ツール |
| scribing_table | LECTERN | グリフアンロック台 |
| source_jar | DECORATED_POT | Source貯蔵（最大10,000） |
| creative_source_jar | DECORATED_POT | 無限Source（クリエイティブ用） |
| volcanic_sourcelink | FURNACE | 燃料消費でSource生成 |
| mycelial_sourcelink | SMOKER | 食料消費でSource生成 |
| ritual_core | ENCHANTING_TABLE | 儀式の中心ブロック |
| pedestal | BREWING_STAND | 儀式の素材台座 |
| source_gem / magebloom_fiber / sourcestone | 各種 | 中間素材 |
| source_berry | GLOW_BERRIES | マナ25即時回復 |
| mage_<tier>_<slot> | LEATHER_* | メイジアーマー（3ティア×4部位） |
| thread_empty/mana_regen/mana_boost/speed/... | STRING | スレッドアイテム（16種） |

## スペルシステム
- Form 7種: projectile, touch, self, underfoot, orbit, beam, wall
- Effect 50+種: harm, heal, break, freeze, launch, rune, animate, ...
- Augment 18種: amplify, dampen, aoe(XZ), aoe_vertical(Y), extend_time, delay, ...

## 防具スレッドシステム
- スロット数はarmors.yml依存（設定ベース防具のthread_slotsで定義）
- 防具手持ちスニーク+右クリックでThreadGui
- 16種: mana_regen, mana_boost, health_boost, hit_mana_recovery, damage_mana_recovery, spell_cost_down, backpack, speed, jump_boost, night_vision, fire_resistance, dolphins_grace, conduit_power, hero_of_the_village, flight, empty

## PDCキー (namespace: arspaper)
- `custom_item_id` - カスタムアイテム識別子
- `spell_recipe` / `spell_slots` / `spell_slot` - スペルデータ
- `book_tier` / `wand_tier` / `armor_tier` - ティア情報
- `thread_slots` - スレッドスロット（JSON配列）
- `thread_item_type` - スレッドアイテムのタイプID
- `enchant_mana_regen` / `enchant_mana_boost` - カスタムエンチャントレベル
- `current_mana` / `glyph_mana_bonus` / `armor_mana_bonus` - マナ関連
- `thread_mana_bonus` / `thread_regen_bonus` / `thread_spell_power` / `thread_cost_reduction` - スレッドボーナス
- `enchant_mana_bonus` / `enchant_regen_bonus` - エンチャントボーナス
- `recall_point` - 帰還ポイント（JSON）
- `custom_block_id` / `custom_data` / `source_amount` - カスタムブロック関連
- `block_display` - ArmorStand識別マーカー
