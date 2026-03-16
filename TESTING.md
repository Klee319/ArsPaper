# ArsPaper 網羅的動作確認手順書

## 前提条件

- Paper 1.21.1+ サーバーが起動済み
- `build/libs/ArsPaper-<version>.jar` を `plugins/` に配置しサーバー再起動済み
- OP権限を持つプレイヤーでログイン
- テスト用ワールドはクリエイティブモード推奨（素材確保のため）
- **サバイバルモードで確認が必要な項目には `[SV]` マークを付記**

---

## 1. プラグイン起動・設定ファイル確認

### 1.1 起動確認
- [ ] サーバーログに `ArsPaper enabled!` が表示される
- [ ] エラーやWarningが出ていないことを確認
- [ ] `plugins/ArsPaper/config.yml` が生成される
- [ ] `plugins/ArsPaper/recipes.yml` が生成される
- [ ] `plugins/ArsPaper/rituals.yml` が生成される
- [ ] `plugins/ArsPaper/glyphs.yml` が生成される
- [ ] サーバーログに `Loaded N recipes from recipes.yml` が表示される
- [ ] サーバーログに `Loaded N ritual recipes from rituals.yml` が表示される

### 1.2 設定ファイル確認
- [ ] `config.yml` のデフォルト値確認:
  - `default-max: 100`
  - `default-regen-rate: 2`
  - `regen-interval-ticks: 20`
  - `per-glyph-unlock-bonus: 5`
- [ ] `glyphs.yml` に全グリフ（Form 7種 + Effect 50種以上 + Augment 18種）の定義がある

### 1.3 リロードコマンド
- [ ] `/ars reload` で「ArsPaper設定をリロードしました (Xms)」メッセージが表示される
- [ ] `glyphs.yml` のparams値を変更 → `/ars reload` → 次回スペル発動で新しい値が反映される
- [ ] `rituals.yml` を変更 → `/ars reload` → 新レシピが使用可能になる
- [ ] 非OPプレイヤーが `/ars reload` → 実行不可

---

## 2. マナシステム

### 2.1 BossBar表示
- [ ] ログイン後にBossBarが表示される（「マナ: 100 / 100」）
- [ ] `/ars mana` で「マナ: 100 / 100」のようなメッセージが表示される

### 2.2 マナ回復
**手順:** サバイバルモードに切替 → スペルを発動してマナ消費 → 回復を観察
- [ ] マナを消費後、約1秒ごとに2ずつ回復することを確認
- [ ] BossBarの値がリアルタイムで更新される
- [ ] 最大マナまで回復したら回復が停止する

### 2.3 スレッドボーナスによるリジェン増加
- [ ] MANA_REGENスレッド付き防具を装備 → リジェンレートが+1/tick
- [ ] MANA_BOOSTスレッド付き防具を装備 → 最大マナが+20/部位

### 2.4 再ログイン永続性
- [ ] ログアウト→ログインでマナ値が保持される
- [ ] BossBarが再表示される
- [ ] `/ars mana` で保持された値が表示される

---

## 3. カスタムアイテム取得（/ars give）

### 3.1 全アイテム取得テスト
以下の各コマンドでアイテムが取得できることを確認:

```
/ars give spell_book_novice       → 初級スペルブック
/ars give spell_book_apprentice   → 中級スペルブック
/ars give spell_book_archmage     → 上級スペルブック
/ars give dominion_wand           → ドミニオンワンド
/ars give wand_novice             → 初級スペルワンド
/ars give wand_apprentice         → 中級スペルワンド
/ars give wand_archmage           → 上級スペルワンド
/ars give scribing_table          → 筆記台
/ars give source_jar              → ソースジャー
/ars give creative_source_jar     → クリエイティブソースジャー
/ars give volcanic_sourcelink     → ヴォルカニックソースリンク
/ars give mycelial_sourcelink     → マイセリアルソースリンク
/ars give ritual_core             → 儀式の核
/ars give pedestal                → 台座
/ars give source_gem              → ソースジェム
/ars give source_berry            → ソースベリー
/ars give magebloom_fiber         → マジックファイバー
/ars give sourcestone             → ソースストーン
/ars give mage_novice_helmet      → 初級メイジヘルメット
/ars give mage_novice_chestplate  → 初級メイジチェストプレート
/ars give mage_novice_leggings    → 初級メイジレギンス
/ars give mage_novice_boots       → 初級メイジブーツ
/ars give mage_apprentice_helmet     → 中級メイジヘルメット
/ars give mage_apprentice_chestplate → 中級メイジチェストプレート
/ars give mage_apprentice_leggings   → 中級メイジレギンス
/ars give mage_apprentice_boots      → 中級メイジブーツ
/ars give mage_archmage_helmet       → 上級メイジヘルメット
/ars give mage_archmage_chestplate   → 上級メイジチェストプレート
/ars give mage_archmage_leggings     → 上級メイジレギンス
/ars give mage_archmage_boots        → 上級メイジブーツ
```

- [ ] 各アイテムに正しい日本語名と色が付いている
- [ ] 各アイテムにLore（説明文）が日本語で付いている
- [ ] タブ補完で全アイテムIDが表示される
- [ ] 不明なアイテムID `/ars give unknown_item` → エラーメッセージ

---

## 4. スペルワンド（3ティア）

### 4.1 基本動作
- [ ] `/ars give wand_novice` → BLAZE_RODベースのカスタムアイテム
- [ ] 右クリック → スペル発動（1スロットのみ）
- [ ] スニーク+右クリック → スペル設定GUI
- [ ] Lore: ティア・スロット数1・最大グリフティア表示

### 4.2 ワンド昇格儀式
- [ ] wand_novice作成: コア=BLAZE_ROD, 台座=アメジスト×2+ソースジェム, Source=100
- [ ] upgrade_wand_apprentice: コア=wand_novice → wand_apprentice、スペルデータ保持
- [ ] upgrade_wand_archmage: コア=wand_apprentice → wand_archmage、スペルデータ保持

---

## 5. スペルブック（ティア別動作）

### 5.1 初級スペルブック (Tier 1)
- [ ] Lore に「ティア: 初級」「スロット数: 3」「最大グリフティア: 1」が表示
- [ ] 右クリック → 空スロットメッセージ
- [ ] スニーク+右クリック → スロット切替（3スロットでループ）
- [ ] スニーク+左クリック → スペル作成GUIが開く

### 5.2 中級スペルブック (Tier 2)
- [ ] スロット切替が6スロットでループ
- [ ] Tier 2グリフが使用可能

### 5.3 上級スペルブック (Tier 3)
- [ ] スロット切替が10スロットでループ
- [ ] 全グリフが使用可能

---

## 6. スペル作成GUI（SpellCraftingGui）

### 6.1 GUI構造確認
1. スペルブックを手に持ってスニーク+左クリック
- [ ] 6段のGUIが開く
- [ ] 上段(行0): 黒ガラスパネル枠内にスペル構成エリア（固定8スロット）
- [ ] 行1: 灰色ガラス仕切り、左端(スロット9)に前ページ矢印、右端(スロット17)に次ページ矢印
- [ ] 中段(行2-4): グリフパレット
- [ ] 下段(行5): 操作ボタン

### 6.2 タブ切替
- [ ] 「形態」ボタン → Form一覧（飛翔体, 接触, 自己, 足元, 軌道）
- [ ] 「効果」ボタン → Effect一覧（残留, 破壊, 害, 凍結, 光明, ...40種以上）
- [ ] 「増強」ボタン → Augment一覧（増幅, 弱化, 範囲, 延長, 遅延, 炸裂, 壁, 初期化, ...17種）

### 6.3 スロット管理（ギャップ保持）
1. Projectile → Harm → Amplify の順で追加
2. 構成エリアのHarm(2番目)をクリックして除去
   - [ ] スロット2が空になり、Amplifyはスロット3に残る（詰めない）
3. 新しいEffectを追加
   - [ ] 空いているスロット2に入る（最初の空きスロットから埋める）

### 6.4 ダークアウト表示
- [ ] 未解放グリフ → BARRIER + 暗灰色テキスト
- [ ] ティア制限超過 → GRAY_DYE + 灰色テキスト + 理由表示
- [ ] 構成が空の時にEffect/Augmentタブ → 「最初に形態を選択してください」
- [ ] 重複Effect → 「同じ効果は重複できません」
- [ ] 非互換Augment → 「直前の効果に対応していません」

### 6.5 操作ボタン
- [ ] 「元に戻す」(にんじん付き釣り竿) → 最後の非null要素を除去
- [ ] 「全消去」(TNT) → 全スロットnullに
- [ ] 「スペル保存」→ nullを詰めてからSpellRecipeに保存、GUI閉じる

### 6.6 ページネーション
- [ ] グリフ数がページを超える場合、行1の左端(スロット9)/右端(スロット17)に矢印表示
- [ ] 矢印クリックでページ切替
- [ ] 行1中央(スロット13)にページ数表示

---

## 7. スペル発動テスト

### 7.1 基本発動 [SV]
- [ ] スペルブックを手に持って右クリック → スペル発動
- [ ] マナが正しく減少する（BossBar更新）
- [ ] 0.2秒以内に再クリック → 発動しない（クールダウン200ms）
- [ ] マナ不足時 → 「マナが不足しています！」

### 7.2 各Form動作テスト

| Form | 構成例 | 確認項目 |
|------|--------|---------|
| 飛翔体 | Projectile + Harm | [ ] Snowball発射→命中でダメージ |
| 接触 | Touch + Break | [ ] 5ブロック以内のブロック破壊 |
| 自己 | Self + Heal | [ ] 自分にHP回復 |
| 足元 | Underfoot + Grow | [ ] 足元の作物が成長 |
| 軌道 | Orbit + Harm | [ ] 術者周囲を回る弾が敵にダメージ |

### 7.3 新Effects（今回追加・修正分）

| 構成 | テスト手順 | 確認項目 |
|------|-----------|---------|
| Touch + Light | 空気ブロックを見て使用 | [ ] 光源（LIGHT）が設置される（レベル15） |
| Projectile + Launch | Mobに命中 | [ ] モブが上方に打ち上げられる（AI一時無効化） |
| Self + Leap | 前方を向いて使用 | [ ] 視線方向にジャンプ（デフォルト速度1.0、抑えめ） |
| Projectile + Pull | Mobに命中 | [ ] 術者方向に引き寄せ（上方向控えめ、minY=0.15） |
| Touch + Rotate | ブロックを見て使用 | [ ] 90度回転 |
| Touch + Rotate + Amplify | ブロックを見て使用 | [ ] 180度回転（Amplify=1で2ステップ） |
| Self + Rotate + ExtendTime | Mobに使用 | [ ] 向き固定（AI一時無効化） |
| Projectile + Toss | 使用（オフハンドにアイテム保持） | [ ] オフハンドのアイテムがドロップ |
| Touch + PlaceBlock | 空気ブロック（オフハンドにブロック保持） | [ ] オフハンドのブロックが設置、1個消費 |
| Projectile + Rune | ブロックに命中 | [ ] ルーン設置→15秒で自動消滅 |
| Projectile + Rune | Mobがルーン上を通過 | [ ] トリガー発動、音量控えめ（0.5f） |
| Projectile + Bounce | 使用 | [ ] JUMP_BOOST付与 |
| Projectile + Exchange (entity) | プレイヤー/Mobに命中 | [ ] 位置入れ替え |
| Projectile + Exchange (block) | ブロックに命中 | [ ] ホットバーのブロックと交換 |
| Projectile + Interact | ドアに命中 | [ ] ドア開閉 |

### 7.4 残留Effect（LingerEffect）
**構成:** `Projectile + Linger + Harm`
- [ ] 投射体が着弾 → 持続効果領域生成（リング状パーティクル）
- [ ] ゾーン内のエンティティに定期的にダメージ（1秒間隔）
- [ ] 5秒後にゾーン消滅
- [ ] 術者自身もゾーン内ではダメージを受ける（本家準拠）

### 7.5 新Augment（今回追加分）

| 構成 | テスト手順 | 確認項目 |
|------|-----------|---------|
| Projectile + Harm + Delay | Mobに命中 | [ ] Harmが即時発動後、後続effectが遅延実行 |
| Projectile + Harm + Delay + Delay | Mobに命中 | [ ] 遅延が加算される（2秒） |
| Touch + PhantomBlock + Wall | ブロックを見て使用 | [ ] 術者の視線に垂直な壁状にガラス設置 |
| Projectile + Harm + Reset | Mobに命中 | [ ] 直前のAugment値がリセットされる |
| Projectile + Harm + AOE | Mobに命中 | [ ] 水平方向に範囲拡大 |
| Projectile + Harm + AOE_Vertical | Mobに命中 | [ ] 垂直方向に範囲拡大 |
| Projectile + Rune + Launch + Delay + Gravity | ルーントリガー | [ ] 打上→遅延→重力の順で実行 |

### 7.6 召喚系Effect修正確認

| 構成 | テスト手順 | 確認項目 |
|------|-----------|---------|
| Self + SummonSteed | 使用 | [ ] 馬が術者の位置に召喚、騎乗 |
| Self + SummonWolves | 使用 | [ ] オオカミ召喚、術者を攻撃しない |
| Projectile + SummonUndead | ブロックに命中 | [ ] ブロック上（+1Y）に召喚 |
| Self + SummonDecoy + AOE | 使用 | [ ] 召喚数=(aoeLevel+1)体のみ（外部AOEで増えない） |
| 召喚モブを倒す | | [ ] 装備ドロップなし、経験値ドロップなし |
| 召喚モブ消滅 | 持続時間経過 | [ ] mob.remove()でクリーンに消える |

### 7.7 仮想ブロック修正確認
**構成:** `Projectile + PhantomBlock`
- [ ] ガラスブロックが設置される
- [ ] 10秒後にガラスが自動除去される
- [ ] **サーバー停止時に未消滅の仮想ブロックが全てAIRに戻る**

**構成:** `Projectile + PhantomBlock + Amplify`（永続化）
- [ ] ガラスブロックが永続設置される
- [ ] シルクタッチで破壊してもガラスがドロップしない
- [ ] 通常破壊でもガラスがドロップしない

---

## 8. Equipment Threads（防具スレッド）

### 8.1 スレッドアイテム作成
- [ ] empty_thread儀式: コア=STRING, 台座=マジックファイバー+ソースジェム → 空のスレッド
- [ ] thread_mana_regen儀式: コア=空のスレッド, 台座=ガストの涙×2+ソースジェム×2 → マナリジェンスレッド
- [ ] thread_mana_boost儀式: コア=空のスレッド, 台座=海の心臓+ソースジェム×2+ファイバー×2 → マナブーストスレッド
- [ ] thread_speed/jump_boost/night_vision/fire_resistance/water_breathing 各儀式
- [ ] thread_spell_power/spell_cost_down 各儀式

### 8.2 スレッドGUI
- [ ] メイジアーマーを手に持ってスニーク+右クリック → ThreadGUIが開く
- [ ] ティアに応じたスロット数表示（Novice=1, Apprentice=2, Archmage=3）
- [ ] ティア外スロットはBARRIER表示
- [ ] 空スロットクリック → インベントリからスレッドアイテムを検索して挿入
- [ ] 埋まったスロットクリック → スレッドアイテムがインベントリに返却
- [ ] GUI閉じ時にマナボーナスが再計算される

### 8.3 スレッドボーナス
- [ ] マナ系スレッド装備 → リジェンレート/最大マナ増加
- [ ] ポーション系スレッド装備 → 該当ポーション効果が常時付与
- [ ] スペル系スレッド装備 → スペル威力UP/コスト削減
- [ ] 防具を外す → ボーナスが正しく減少、ポーション効果解除
- [ ] 現在マナ > 新最大マナの場合クランプされる

### 8.4 カスタムエンチャント
- [ ] enchant_book_mana_regen/boost儀式: コア=BOOK → エンチャント本生成
- [ ] 金床: スロット0=メイジアーマー, スロット1=エンチャント本 → 結果プレビュー表示
- [ ] 結果取り出し → 経験値1消費、エンチャント適用
- [ ] 同等以上のレベルが既にある場合 → 適用不可
- [ ] エンチャント台からは出ない

---

## 9. World-Affecting Rituals（ワールド儀式）

### 9.1 豊穣の儀式 (ritual_fertility)
- [ ] 台座: 骨粉×4 + ソースジェム, Source=300
- [ ] 発動 → 半径10ブロック内の全作物が最大成長
- [ ] 成長した作物の数がメッセージで表示される
- [ ] 個別パーティクル（HAPPY_VILLAGER）

### 9.2 天候操作の儀式 (ritual_cloudshaping)
- [ ] 台座: プリズマリン×2 + 避雷針×2 + ソースジェム×2, Source=1000
- [ ] 晴れ時に発動 → 雨に変化
- [ ] 雨時に発動 → 晴れに変化

---

## 10. 儀式システム（既存+新規）

### 10.1 既存儀式の回帰テスト
- [ ] ソースジェム精製（中間素材）
- [ ] マジックファイバー精製
- [ ] スペルブック昇格（スペルデータ保持確認）
- [ ] メイジアーマー作成（3ティア×4部位）

### 10.2 新規儀式
- [ ] ワンド作成・昇格（スペルデータ保持確認）
- [ ] スレッドアイテム作成（空スレッド→各効果付きスレッド）
- [ ] ワールド効果（ritual_fertility, ritual_cloudshaping）
- [ ] 飛行の儀式（300秒間飛行付与）
- [ ] 月落とし/日の出の儀式（時刻変更）
- [ ] 修復の儀式（コア上アイテム耐久回復）
- [ ] 帰還ポイント設定 + 帰還の儀式（テレポート）
- [ ] 封じ込めの儀式（半径内スポーン抑制）
- [ ] 動物召喚の儀式（パッシブモブ召喚）
- [ ] エンチャント本儀式（マナ加速/マナ上昇 各3レベル）

### 10.3 儀式失敗ケース
- [ ] 台座が空 → エラーメッセージ
- [ ] 素材不一致 → エラーメッセージ
- [ ] ソース不足 → エラーメッセージ（ソースは消費されない）
- [ ] 儀式中に素材を抜き取る → アニメーション後に再検証で失敗

---

## 11. ブロックパーティクルとチャンク管理

### 11.1 パーティクル表示
- [ ] 各カスタムブロックに固有のパーティクルが表示される
  - 筆記台: ENCHANT
  - ソースジャー: END_ROD
  - ソースリンク: FLAME / SPORE_BLOSSOM_AIR
  - 儀式の核: WITCH
  - 台座: DUST(紫)

### 11.2 チャンク再ロード後のパーティクル
- [ ] カスタムブロックのあるチャンクから離れて戻る → パーティクル復活
- [ ] サーバー再起動後 → パーティクル復活
- [ ] ブロック破壊後 → パーティクルが停止

---

## 12. Sourceシステム

### 12.1 ソースジャー
- [ ] 設置・右クリックで現在ソース量表示
- [ ] 破壊でカスタムアイテムとしてドロップ

### 12.2 ソースリンク
- [ ] ヴォルカニックソースリンク: 燃料消費でSource生成
- [ ] マイセリアルソースリンク: 食料消費でSource生成
- [ ] 隣接ソースジャーへの自動転送

### 12.3 Sourceネットワーク（ドミニオンワンド）
- [ ] ブロック選択 → 接続 → 自動転送
- [ ] 30ブロック超え → 範囲外エラー
- [ ] スニーク+右クリック → 選択解除

---

## 13. 筆記台（グリフアンロック）

- [ ] 右クリックでGUI表示
- [ ] 未アンロックグリフ: コスト表示（レベル+素材）
- [ ] アンロック実行: レベル・素材消費、最大マナ+5
- [ ] アンロック失敗: レベル不足、素材不足、既解放

---

## 14. メイジアーマー

- [ ] 3ティア×4部位のマナボーナス (5/15/30 per piece)
- [ ] フルセット: 初級+20、中級+60、上級+120
- [ ] 着脱時のBossBar即時更新
- [ ] 現在マナのクランプ

---

## 15. glyphs.yml パラメータテスト

### 15.1 デフォルト値確認
以下のエフェクトがデフォルト値で動作することを確認:

| グリフ | パラメータ | デフォルト値 |
|--------|-----------|-------------|
| leap | base-velocity | 1.0 |
| leap | amplify-bonus | 0.3 |
| leap | max-velocity | 3.0 |
| pull | base-force | 0.8 |
| pull | min-y | 0.15 |
| bounce | base-duration | 600 |
| rune | base-lifetime | 300 (15秒) |
| rune | trigger-radius | 1.5 |

### 15.2 パラメータ変更テスト
1. `glyphs.yml` にparams追加:
```yaml
leap:
  params:
    base-velocity: 2.0
```
2. `/ars reload`
3. Leapスペル発動
- [ ] 速度がデフォルト(1.0)から変更(2.0)に反映される

---

## 16. コマンド全般

### 16.1 /ars spell list
- [ ] 形態(Form): 飛翔体, 接触, 自己, 足元, 軌道 が表示
- [ ] 効果(Effect): 残留, 破壊, 害, 凍結, 光明, ...が表示
- [ ] 増強(Augment): 増幅, 弱化, 範囲, 延長, 遅延, 炸裂, 壁, 初期化, ...が表示

### 16.2 /ars spell set
- [ ] スペルブック持ちで実行 → 設定成功
- [ ] スペルブック未持ちで実行 → エラー

### 16.3 /ars debug mana
- [ ] マナ無限モード ON/OFF トグル

### 16.4 /ars cleanup
- [ ] ArmorStand表示エンティティの除去

### 16.5 /ars reload
- [ ] config.yml, glyphs.yml, rituals.yml, recipes.yml をリロード
- [ ] 実行時間ms表示
- [ ] 非OPプレイヤー → 実行不可

---

## 17. シャットダウンクリーンアップ

- [ ] サーバー停止時: 仮想ブロック(PhantomBlock)が全てAIRに戻る
- [ ] サーバー停止時: 残留ゾーン(LingerEffect)のタスクがキャンセルされる
- [ ] サーバー停止時: 進行中の儀式のロックが解放される
- [ ] サーバー停止時: BossBarが全プレイヤーから非表示になる

---

## 18. エッジケース・回帰テスト

### 18.1 データ永続性
- [ ] サーバー再起動後、マナ値が保持される
- [ ] サーバー再起動後、アンロック済みグリフが保持される
- [ ] サーバー再起動後、Sourceネットワーク接続が保持される
- [ ] サーバー再起動後、スペルブック内のスペルが保持される
- [ ] サーバー再起動後、ソースジャーの貯蔵量が保持される

### 18.2 同時操作
- [ ] 複数プレイヤーが同時にスペルを発動してもクラッシュしない
- [ ] 複数プレイヤーが同時にGUIを操作してもクラッシュしない
- [ ] 複数プレイヤーが同じ台座を同時に操作しても二重取得しない

### 18.3 異常系
- [ ] 空のスペルブックで右クリック → エラーメッセージ
- [ ] 不正なスペルを `/ars spell set` で設定 → エラー
- [ ] 大量のカスタムブロック設置してもTPS低下しない

---

## 19. Geyser/統合版互換性テスト

> ※Geyser環境がある場合のみ

- [ ] 統合版クライアントからカスタムブロックのArmorStandが見える
- [ ] GUIが正常に操作できる（クリックベース）
- [ ] スペルブック/ワンドの操作が動作
- [ ] BossBarが表示される

---

## チェックリスト集計

完了後、以下を確認:
- 全 `[ ]` が `[x]` になっていること
- 発見した問題は Issue として記録
- テスト環境のバージョン情報（Paper/Java/Geyser）を記録

**テスト項目数: 約220項目**
