# ArsPaper Public API 仕様書

- **Plugin**: ArsPaper (Paper 1.21.11+ / Java 21+)
- **API package**: `com.arspaper.api`
- **API version**: 1.0.0 (commit `b8fa41d` 時点)
- **対象読者**: ValhallaMMO / EliteMobs などの外部プラグイン作者
- **設計原則**: 下流非依存 (内部実装に直接依存しない)、加算可能 modifier、既存挙動の保全
- **参考**: `ARS_SPEC.md` (本実装の元仕様)

---

## 1. クイックスタート

### 1.1 依存関係

ArsPaper jar を `lib/` または Maven local に配置し、`compileOnly` 依存にする:

```kotlin
// build.gradle.kts
dependencies {
    compileOnly(files("libs/ArsPaper-1.0.0.jar"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
```

### 1.2 plugin.yml / paper-plugin.yml

```yaml
depend: [ArsPaper]
# または soft depend:
softdepend: [ArsPaper]
```

### 1.3 最小例

```java
import com.arspaper.api.ArsAPI;
import com.arspaper.api.modifier.ModifierType;
import org.bukkit.NamespacedKey;

public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // プレイヤーに "fire" グリフを解放
        Player p = /* ... */;
        ArsAPI.unlockGlyph(p, "fire");

        // 最大マナ +50 (key 別管理なので後で除去可能)
        ArsAPI.addManaModifier(p,
            NamespacedKey.fromString("myplugin:mana_pool_1"),
            ModifierType.MAX_MANA, 50.0);
    }
}
```

---

## 2. ArsAPI 静的ファサード

`com.arspaper.api.ArsAPI` の全公開メソッド。

### 2.1 初期化チェック

```java
public static boolean isInitialized();
```

`ArsPaper` の `onEnable` 完了後に `true`。`isInitialized() == false` で他のAPIを呼ぶと `IllegalStateException`。
他プラグインの `onEnable` で呼び出す場合は `depend: [ArsPaper]` が必須 (これにより読み込み順が保証される)。

### 2.2 グリフ (Glyph)

| メソッド | 説明 |
|---|---|
| `boolean isGlyphUnlocked(Player p, String glyphId)` | 解放済みかチェック |
| `void unlockGlyph(Player p, String glyphId)` | 解放する。post-event `ArsGlyphUnlockedEvent` が発火 |
| `void lockGlyph(Player p, String glyphId)` | 取り消す。post-event `ArsGlyphLockedEvent` が発火 |
| `Set<String> getUnlockedGlyphs(Player p)` | プレイヤーの解放済みグリフ集合 (plain key) |
| `Set<String> getAllGlyphIds()` | 全グリフID集合 (plain key) |
| `@Nullable String getGlyphIdFromItem(ItemStack item)` | アイテムからグリフIDを逆引き (現状常に `null`、将来用) |

**`glyphId` の形式**:
- 入力: `"fire"` (plain) でも `"arspaper:fire"` (NamespacedKey toString形式) でも受け付ける。内部で正規化される。
- 出力 (`getUnlockedGlyphs` / `getAllGlyphIds`): plain key (`"fire"`)。
- イベント payload: plain key (`"fire"`)。

**永続化**: `arspaper:unlocked_glyphs` PDC (JSON配列、各要素は内部形式 `"arspaper:<key>"`)。`plugins/ArsPaper/playerdata/<uuid>.yml` にも `unlocked-glyphs:` としてミラーされる (PDC が権威データ)。

### 2.3 儀式 / レシピのアンロック

| メソッド | 説明 |
|---|---|
| `boolean isRitualUnlocked(Player p, String ritualId)` | |
| `void unlockRitual(Player p, String ritualId)` | |
| `void lockRitual(Player p, String ritualId)` | |
| `boolean isRecipeUnlocked(Player p, String recipeId)` | |
| `void unlockRecipe(Player p, String recipeId)` | |
| `void lockRecipe(Player p, String recipeId)` | |
| `Set<String> getAllRitualIds()` | `rituals.yml` で定義された全儀式ID |
| `Set<String> getAllRecipeIds()` | 登録済みクラフトレシピID (`arspaper:<id>` 形式の NamespacedKey 文字列) |

**永続化**: `plugins/ArsPaper/playerdata/<uuid>.yml` の `unlocked-rituals:` / `unlocked-recipes:` セクション。

**注意**: 現状 ArsPaper 内部の儀式/レシピ実行パスでは「unlock 必須」チェックは行っていない。これは将来拡張用フックで、現バージョンではマーカー的に使う。

### 2.4 マナ Modifier

| メソッド | 説明 |
|---|---|
| `double getMaxMana(Player p)` | base + 全 modifier 加算後の値 |
| `double getManaRegenRate(Player p)` | 同上 |
| `void addManaModifier(Player p, NamespacedKey key, ModifierType type, double value)` | 加算/置換。post-event `ArsManaModifierChangeEvent` |
| `void removeManaModifier(Player p, NamespacedKey key, ModifierType type)` | 除去 |
| `@Nullable Double getManaModifier(Player p, NamespacedKey key, ModifierType type)` | 単一参照 |
| `Map<NamespacedKey, Double> listModifiers(Player p, ModifierType type)` | 当該 type の全 modifier |

**`key`**: 呼び出し元プラグイン固有の識別子。同じ `(player, key, type)` への `addManaModifier` は上書き。後で `removeManaModifier` で除去できるよう、意味のある名前を付けること (例: `"valhallammo:mana_pool_1"`)。

**`ModifierType` の合算規則**:

| Type | 合算 | 反映 |
|---|---|---|
| `MAX_MANA` | 単純加算 | `round(sum)` で最大マナに加算 |
| `REGEN_RATE` | 単純加算 | `round(sum)` で 1tick あたり回復量に加算 |
| `MANA_COST_MULT` | 単純加算 | `final = max(0, 1 - sum)` を消費量に乗算 (`0.05` 加算で 5% 削減) |
| `MANA_COST_REDUCTION_CHANCE` | 単純加算 | `clamp(0, 1)` で確率判定、当たれば消費 0 |
| `MATERIAL_REDUCTION` | 単純加算 | (現状 ArsPaper 内部では未消費パスに反映、Event listener 側で利用想定) |
| `MATERIAL_REDUCTION_CHANCE` | 単純加算 | (同上) |

**重要**: `MAX_MANA` / `REGEN_RATE` は ArsPaper の最大マナ・回復量が整数管理のため、合計を `round()` で整数に丸める。0.5 などの fractional 加算は意味を成さないことに注意。

### 2.5 アイテムレジストリ

| メソッド | 説明 |
|---|---|
| `@Nullable ItemStack getItem(String itemId)` | `"thread_empty"` / `"source_jar"` 等のID → ItemStack |
| `Set<String> getItemRegistry()` | 全カスタムアイテムID |
| `@Nullable String getItemId(ItemStack stack)` | アイテムから逆引き (PDC `arspaper:custom_item_id`) |

主要 `itemId` 一覧は `CLAUDE.md` の「カスタムアイテム一覧」を参照。

### 2.6 アイテム品質 (ItemQuality)

| メソッド | 説明 |
|---|---|
| `int getItemQuality(ItemStack stack)` | 0 (なし) ~ 5 |
| `void setItemQuality(ItemStack stack, int quality)` | 0-5 を設定、lore末尾を自動更新 |

**ラベル**: 1=Poor / 2=Fine / 3=Superior / 4=Exceptional / 5=Masterwork
**PDC キー**: `arspaper:quality` (INTEGER)
**lore 行**: `Quality: <ラベル>` (色は品質に応じて変化、Italic OFF)

### 2.7 マジックダメージマーカー

スペル由来ダメージの判別用。EliteMobs などで magic_resistance 適用に使う。

| メソッド | 説明 |
|---|---|
| `boolean isMagicDamage(EntityDamageEvent e)` | true なら caster あり |
| `@Nullable Player getCaster(EntityDamageEvent e)` | caster Player を返す (オフライン/未ヒットなら null) |
| `@Nullable Player getCaster(LivingEntity victim)` | 直接 entity から (PDC 参照) |

**TTL**: 5 tick (250ms)。スペル由来ダメージの直前に target PDC に attach され、5tick 後に削除される。
**PDC キー**: `arspaper:magic_damage_caster` (STRING UUID) / `arspaper:magic_damage_expire_tick` (LONG)

`ArsSpellDamageEvent` リスナーから target PDC を参照する場合、attach は event 発火 **前** に完了しているため、リスナー内で `isMagicDamage` が `true` を返すことが保証される。

### 2.8 ライフサイクル

| メソッド | 説明 |
|---|---|
| `void reloadPlayerData(Player p)` | 当該プレイヤーの yml を unload → reload |
| `void savePlayerData(Player p)` | 即時保存 |
| `void saveAllPlayerData()` | 全オンラインプレイヤー |

通常は `PlayerJoinEvent` / `PlayerQuitEvent` で自動 load/save されるため、明示呼び出しは不要。

---

## 3. Custom Bukkit Event (10種)

すべて `com.arspaper.api.event` パッケージ。`@EventHandler` で受け取る。

### 3.1 ArsSpellCastEvent (cancellable, PlayerEvent)

**発火タイミング**: `SpellCaster.cast()` の冒頭、マナ消費前。

| メソッド | 戻り | 説明 |
|---|---|---|
| `getActiveGlyphs()` | `List<String>` | スペル構成グリフ (plain key、Form/Effect/Augment 含む順序保持) |
| `getPrimaryEffectId()` | `String` | 最初の Effect の plain key (例: `"harm"`)。なければ `""` |
| `getManaCost()` | `double` | 計算済みコスト (THREAD_COST_REDUCTION 適用後) |
| `setManaCost(double)` | - | 0 で無料化、`max(0,...)` でクランプ |
| `getDamage()` | `double` | (Effect実行前は 0、現時点では未使用) |
| `setDamage(double)` | - | |
| `getPlayer()` | `Player` | caster |

cancel → 詠唱中止 (マナ消費なし、戻り値 `false`)。

### 3.2 ArsSpellDamageEvent (cancellable, Event)

**発火タイミング**: ダメージ系 Effect (`HarmEffect`, `LightningEffect` 等) が `LivingEntity.damage()` を呼ぶ **直前**。発火**前**に target PDC へ magic damage marker (5tick TTL) が attach 済み。

| メソッド | 戻り | 説明 |
|---|---|---|
| `getTarget()` | `LivingEntity` | 被弾エンティティ |
| `getCaster()` | `@Nullable Player` | 術者 (オフライン等で null になり得る) |
| `getActiveGlyphs()` | `List<String>` | (plain key) |
| `getEffectId()` | `String` | (plain key、例: `"harm"`) |
| `getDamage()` | `double` | 計算済み (`SpellContext.calculateSpellDamage` 適用後) |
| `setDamage(double)` | - | |

**未フック Effect**: 現バージョンでは `HarmEffect`、`LightningEffect` のみ event を発火。他のダメージ Effect (Wither、Sonic Boom、Crush 等) は今後対応予定。

### 3.3 ArsGlyphUnlockRequestEvent (cancellable, PlayerEvent)

**発火タイミング**: 筆記台でグリフ解放を試みた直後、消費前。

| メソッド | 戻り | 説明 |
|---|---|---|
| `getGlyphId()` | `String` | plain key |
| `getMaterialsToConsume()` | `List<ItemStack>` | 消費予定の素材 (防御コピー返却) |
| `setMaterialsToConsume(List<ItemStack>)` | - | 削減/置換可能。**反映される**。Material別の集計後に消費 |
| `getLevelCost()` | `int` | 経験値レベル消費量 |
| `setLevelCost(int)` | - | **反映される** |

`external-unlock-only: true` (`glyphs.yml`) のグリフは ArsPaper 内部で必ず cancel される (この event は発火しない)。

### 3.4 ArsGlyphUnlockedEvent / ArsGlyphLockedEvent (post, PlayerEvent)

| メソッド | 戻り |
|---|---|
| `getGlyphId()` | `String` (plain key) |
| `getSource()` | `UnlockSource` / `LockSource` |

**`UnlockSource` enum**: `PLAYER_CRAFT` (筆記台) / `API` (`ArsAPI.unlockGlyph`) / `COMMAND` / `OTHER`
**`LockSource` enum**: `API` / `COMMAND` / `OTHER`

### 3.5 ArsRitualPreEvent (cancellable, PlayerEvent)

**発火タイミング**: 儀式マッチ後、アニメーション開始前、Source 消費前。

| メソッド | 戻り | 説明 |
|---|---|---|
| `getRitualId()` | `String` | `rituals.yml` の id (例: `"thread_application"`) |
| `getLocation()` | `Location` | コアの位置 |
| `getRequiredMaterials()` | `List<ItemStack>` | 必要素材 (台座素材を ItemStack 変換したもの) |
| `setRequiredMaterials(List<ItemStack>)` | - | **反映される**。override 時は元レシピと内容が同一か比較し、異なれば消費パスで override が優先 |

### 3.6 ArsRitualPostEvent (post, PlayerEvent)

**発火タイミング**: 儀式完了 (成功時) または失敗確定時。

| メソッド | 戻り |
|---|---|
| `getRitualId()` | `String` |
| `getLocation()` | `Location` |
| `isSuccessful()` | `boolean` |

### 3.7 ArsRecipeCraftPreEvent (cancellable, PlayerEvent)

**発火タイミング**: 作業台で `PrepareItemCraftEvent` 発火時。`arspaper:<id>` namespace のレシピのみ対象 (バニラレシピは無視)。

| メソッド | 戻り | 説明 |
|---|---|---|
| `getRecipeId()` | `String` | `"arspaper:source_jar"` 等 (NamespacedKey toString形式) |
| `getResultPreview()` | `ItemStack` | プレビュー (clone) |
| `getIngredients()` | `List<ItemStack>` | 投入素材 (clone) |
| `setIngredients(List<ItemStack>)` | - | (現状未実装) |

cancel すると result スロットが null 化されクラフト不可になる。

### 3.8 ArsItemCraftedEvent (post, PlayerEvent)

**発火タイミング**: `CraftItemEvent` 発火時、`arspaper:<id>` namespace のレシピのみ。

| メソッド | 戻り | 説明 |
|---|---|---|
| `getRecipeId()` | `String` | |
| `getResultStack()` | `ItemStack` | 結果アイテム (clone) |
| `setResultStack(ItemStack)` | - | 個数倍化・品質付与・置換が **反映される** |
| `getRollSeed()` | `int` | ランダム判定用シード (`currentTimeMillis ^ uuid.hashCode`) |

### 3.9 ArsManaModifierChangeEvent (post, PlayerEvent)

**発火タイミング**: `addManaModifier` / `removeManaModifier` 直後。

| メソッド | 戻り |
|---|---|
| `getKey()` | `NamespacedKey` |
| `getType()` | `ModifierType` |
| `getOldValue()` | `@Nullable Double` (新規追加なら null) |
| `getNewValue()` | `@Nullable Double` (除去なら null) |

---

## 4. 設定 (`config.yml`)

```yaml
# ArsAPI (外部プラグイン連携: ValhallaMMO/EliteMobs等)
api:
  # API有効化フラグ (将来用、現バージョンでは未参照)
  enabled: true
  # デバッグ: event発火を全部ログ出力 (未実装)
  event-log: false
  # Modifierをymlに永続化するか
  # false: 起動時クリア (ValhallaMMO等が再ログイン時に再適用する想定 — 推奨)
  # true:  yml に保存され再起動を跨いでも維持される
  modifier-persistence: false
```

---

## 5. グリフ設定の external-unlock-only

`glyphs.yml` の各グリフエントリに以下を追加できる:

```yaml
glyphs:
  fire:
    tier: 1
    mana-cost: 10
    external-unlock-only: false   # NEW: trueなら筆記台アンロック不可
    unlock-cost:
      level: 5
      materials:
        BLAZE_POWDER: 4
```

`external-unlock-only: true` のグリフは筆記台 GUI で「このグリフは通常の方法では解放できません」と表示され、unlock が拒否される。`ArsAPI.unlockGlyph()` 経由でのみ解放可能。

---

## 6. 永続化スキーマ

### 6.1 プレイヤーデータ (`plugins/ArsPaper/playerdata/<uuid>.yml`)

```yaml
unlocked-glyphs:
  - fire
  - water
unlocked-rituals:
  - ritual_of_warding
unlocked-recipes:
  - arspaper:thread_recipe
# api.modifier-persistence=true のときのみ
modifiers:
  - key: "valhallammo:mana_pool_1"
    type: MAX_MANA
    value: 50.0
  - key: "valhallammo:thrift_1"
    type: MANA_COST_REDUCTION_CHANCE
    value: 0.05
```

### 6.2 主要 PDC キー (`arspaper:*`)

| Key | Type | 場所 | 用途 |
|---|---|---|---|
| `arspaper:unlocked_glyphs` | STRING (JSON配列) | Player | 解放済みグリフ (権威データ) |
| `arspaper:current_mana` | INTEGER | Player | 現在マナ |
| `arspaper:quality` | INTEGER | Item | アイテム品質 0-5 |
| `arspaper:magic_damage_caster` | STRING (UUID) | LivingEntity | 直近スペルダメージの caster (5tick TTL) |
| `arspaper:magic_damage_expire_tick` | LONG | LivingEntity | TTL 終了 tick |
| `arspaper:custom_item_id` | STRING | Item | カスタムアイテム識別子 |

完全なリストは `CLAUDE.md` 参照。

---

## 7. ValhallaMMO 連携例

```java
import com.arspaper.api.ArsAPI;
import com.arspaper.api.modifier.ModifierType;
import com.arspaper.api.event.ArsItemCraftedEvent;

public class ArsIntegration implements Listener {

    @EventHandler
    public void onPerkUnlock(PerkUnlockEvent e) {
        Player p = e.getPlayer();
        switch (e.getPerk().getId()) {
            case "magic.fire_glyph" -> ArsAPI.unlockGlyph(p, "fire");
            case "magic.mana_pool_1" -> ArsAPI.addManaModifier(p,
                NamespacedKey.fromString("valhallammo:mana_pool_1"),
                ModifierType.MAX_MANA, 50.0);
            case "magic.thrift_1" -> ArsAPI.addManaModifier(p,
                NamespacedKey.fromString("valhallammo:thrift_1"),
                ModifierType.MANA_COST_REDUCTION_CHANCE, 0.05);
            case "magic.ritual_warding" -> ArsAPI.unlockRitual(p, "ritual_of_warding");
        }
    }

    @EventHandler
    public void onArsItemCrafted(ArsItemCraftedEvent e) {
        Player p = e.getPlayer();
        ItemStack result = e.getResultStack();
        String itemId = ArsAPI.getItemId(result);

        if ("thread_empty".equals(itemId) && hasPerk(p, "magic.thread_master")) {
            result.setAmount(result.getAmount() * 2);
            e.setResultStack(result);
        }
        if (hasPerk(p, "magic.quality_craft")) {
            int q = rollQuality(p); // perk level に応じて 1-5
            ArsAPI.setItemQuality(result, q);
        }
    }

    @EventHandler
    public void onSpellCast(ArsSpellCastEvent e) {
        Player p = e.getPlayer();
        // 「火 (fire)」を含むスペルのコスト 10% 削減
        if (e.getActiveGlyphs().contains("fire") && hasPerk(p, "magic.fire_mastery")) {
            e.setManaCost(e.getManaCost() * 0.9);
        }
    }
}
```

---

## 8. EliteMobs 連携例

```java
import com.arspaper.api.ArsAPI;
import com.arspaper.api.event.ArsSpellDamageEvent;

public class ArsResistanceHook implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onArsSpellDamage(ArsSpellDamageEvent e) {
        LivingEntity target = e.getTarget();
        if (!EliteEntity.isEliteMob(target)) return;

        EliteEntity elite = EliteEntity.from(target);
        double magicResist = elite.getMagicResistance(); // 0.0-1.0
        double finalDamage = e.getDamage() * (1.0 - magicResist);
        e.setDamage(finalDamage);
    }

    // 別経路 — generic EntityDamageEvent から caster 判別
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!ArsAPI.isMagicDamage(e)) return;
        Player caster = ArsAPI.getCaster(e);
        if (caster == null) return;
        // EliteMobs 専用処理 (loot 加算等)
    }
}
```

---

## 9. 実装ノート

### 9.1 スレッドセーフティ

- `ModifierStore` は `synchronized` メソッドで保護される。`addManaModifier` 等は任意スレッドから呼び出し可だが、**event 発火と PDC 操作は main thread 必須**のため、呼び出しは main thread を推奨。
- `ArsPlayerDataStore` の yml save は main thread で同期実行 (大量の player では遅延に注意。`api.modifier-persistence: false` 推奨)。
- `MagicDamageMarker.attach` 内の delayed remove タスクは Bukkit Scheduler 経由なので安全。

### 9.2 イベント呼び出し順序

```
PlayerInteractEvent (詠唱トリガー)
  → SpellCaster.cast()
    → グリフ unlock チェック → ワールドBANチェック
    → 基本コスト計算
    → ArsSpellCastEvent 発火 (cancellable, manaCost 改竄可)
    → consumeMana (内部で MANA_COST_MULT / MANA_COST_REDUCTION_CHANCE 適用)
    → form.cast() → effect.applyToEntity()
      → ダメージEffect の場合:
        → calculateSpellDamage で各種補正
        → MagicDamageMarker.attach (5tick TTL)  [event 発火前]
        → ArsSpellDamageEvent 発火 (cancellable, damage 改竄可)
        → target.damage()
```

### 9.3 後方互換性

- ArsAPI が初期化前 (`isInitialized() == false`) の場合、ArsPaper 内部の hook は modifier を 0、event を発火しない fallback を採る。よって API 未導入環境でも ArsPaper 1.0.0 と完全同一の挙動になる。
- 既存 PDC の `arspaper:unlocked_glyphs` は NamespacedKey toString形式 (`"arspaper:fire"`) で保存されており、ArsAPI は plain key (`"fire"`) との相互変換を透過的に行う。

### 9.4 既知の未実装事項

- `ArsItemCraftedEvent#getRollSeed` は `currentTimeMillis ^ uuid.hashCode()` で生成 (cryptographic ではない、再現性なし)。
- `MATERIAL_REDUCTION` / `MATERIAL_REDUCTION_CHANCE` は ArsAPI で集計値を返すが、ArsPaper 内部の儀式/クラフトには未適用。listener 側で `setRequiredMaterials` / `setIngredients` を使って削減する設計。
- 一部ダメージ Effect (Wither、Sonic Boom 等) は `ArsSpellDamageEvent` を発火しない (`HarmEffect`、`LightningEffect` のみ対応)。
- `ArsRecipeCraftPreEvent#setIngredients` は呼び出し可能だが、実消費パスには未反映 (cancel のみ有効)。

---

## 10. バージョン履歴

| Version | Commit | 変更 |
|---|---|---|
| 1.0.0 | `b8fa41d` | 初版リリース。ArsAPI facade + 10 events + Modifier + ItemQuality + MagicDamageMarker + PlayerData yml |
