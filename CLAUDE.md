# ArsPaper

リポジトリ：https://github.com/Klee319/ArsPaper.git
開発環境：Paper 1.21.4 (Java 21+)　適宜公式リファレンスやskillを適宜参照すること

## 概要
Ars Nouveau MODの機能をバニラ+Geyser互換で再現するPaperプラグイン。

## 技術制約
- 新規アイテムID不可 → PDC + CustomModelData でカスタムアイテム識別
- Geyser互換 → Display EntityではなくArmorStand (Marker:1) ベース
- GUI → ボタン式Inventory GUI（クリックイベントベース）

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

## コマンド
- `/ars give <itemId>` - カスタムアイテムを取得 (admin)
- `/ars mana` - マナ情報を表示
- `/ars spell list` - 利用可能なグリフ一覧
- `/ars spell set <slot> <Name>:<form> <effects...>` - スペル設定

## カスタムアイテム一覧
| ID | ベース | 説明 |
|----|--------|------|
| spell_book_novice | BOOK | スペルブック（右クリック発動、スニーク+右で切替、スニーク+左でGUI） |
| dominion_wand | STICK | Source接続ツール（右クリックで選択→接続） |
| scribing_table | LECTERN | グリフアンロック台 |
| source_jar | BARREL | Source貯蔵（最大10,000） |
| volcanic_sourcelink | FURNACE | 燃料消費でSource生成 |
| mycelial_sourcelink | SMOKER | 食料消費でSource生成 |

## PDCキー (namespace: arspaper)
- `custom_item_id` - カスタムアイテム識別子
- `spell_recipe` / `spell_slots` / `spell_slot` - スペルデータ
- `book_tier` - スペルブックティア
- `current_mana` / `mana_bonus` / `regen_rate` / `unlocked_glyphs` - マナ関連
- `custom_block_id` / `custom_data` / `source_amount` - カスタムブロック関連
- `block_display` - ArmorStand識別マーカー
