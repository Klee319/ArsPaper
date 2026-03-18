package com.arspaper.spell;

import com.arspaper.ArsPaper;
import com.arspaper.spell.effect.LingerEffect;
import com.arspaper.spell.effect.RuneEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * スペル発動時の実行コンテキスト。
 * Ars Nouveau準拠: AugmentはEffectの後ろに配置し、そのEffectを強化する。
 *
 * 例: [Proj] [Harm] [Amplify] [AOE] [Heal] [ExtendTime]
 *   → Harm に Amplify+AOE、Heal に ExtendTime が適用される。
 *
 * SpellStats (Ars Nouveau準拠):
 * - amplifyLevel: Amplify(+1)/Dampen(-1) で変動
 * - aoeLevel: AOE(+1) で変動
 * - durationLevel: ExtendTime(+1)/DurationDown(-1) で変動
 * - acceleration: Accelerate(+1.0)/Decelerate(-0.5) で変動
 * - pierceCount: Pierce buff count
 * - splitCount: Split buff count
 * - sensitive: Sensitive flag
 * - randomizing: Randomize flag
 * - extractCount: Extract buff count (Fortuneと排他)
 * - fortuneLevel: Fortune buff count (Extractと排他)
 */
public class SpellContext {

    private final UUID casterUuid;
    private final SpellRecipe recipe;
    private final List<SpellComponent> components;

    // === Augment統計値（Effect適用後にリセット） ===
    private int amplifyLevel = 0;
    private int aoeLevel = 0;
    private int durationLevel = 0;     // ExtendTime(+1) / DurationDown(-1)
    private double acceleration = 0.0; // Accelerate(+1.0) / Decelerate(-0.5)
    private int pierceCount = 0;
    private int splitCount = 0;
    private boolean sensitive = false;
    private boolean randomizing = false;
    private int extractCount = 0;
    private int fortuneLevel = 0;

    // パターン増強用
    private boolean wallPattern = false;    // 壁パターン（壁状AOE）
    private boolean lingerPattern = false;  // 残留パターン（ルーン持続化）
    private int delayTicks = 0;             // 遅延ティック
    private int rapidFireLevel = 0;         // 連射レベル（CT短縮）
    private boolean traceActive = false;     // 軌跡（経路上に効果適用）

    // Form-augment用（ProjectileForm等が参照）
    private double projectileSpeedMultiplier = 1.0;

    // ヒット面情報（ブロックAOE展開で使用）
    private org.bukkit.block.BlockFace hitFace = null;
    public org.bukkit.block.BlockFace getHitFace() { return hitFace; }
    public void setHitFace(org.bukkit.block.BlockFace hitFace) { this.hitFace = hitFace; }

    public SpellContext(Player caster, SpellRecipe recipe) {
        this.casterUuid = caster.getUniqueId();
        this.recipe = recipe;
        this.components = recipe.getComponents();
    }

    private SpellContext(SpellContext other) {
        this.casterUuid = other.casterUuid;
        this.recipe = other.recipe;
        this.components = other.components;
    }

    public Player getCaster() {
        return Bukkit.getPlayer(casterUuid);
    }

    public UUID getCasterUuid() {
        return casterUuid;
    }

    public SpellRecipe getRecipe() {
        return recipe;
    }

    // === Amplify/Dampen ===
    // 減衰は増幅の半分の効果（2回積んで-1レベル）
    private int dampenAccum = 0;
    private int durationDownAccum = 0;

    public int getAmplifyLevel() { return amplifyLevel; }
    public void setAmplifyLevel(int amplifyLevel) { this.amplifyLevel = amplifyLevel; }
    public void applyDampen() {
        dampenAccum++;
        if (dampenAccum % 2 == 0) {
            amplifyLevel--;
        }
    }

    // === AOE 視線基準3軸 ===
    // 横: 視線に対して左右
    public int getAoeWidth() { return aoeLevel; }
    public void setAoeWidth(int w) { this.aoeLevel = w; }
    // 縦: 視線に対して上下
    private int aoeHeightLevel = 0;
    public int getAoeHeight() { return aoeHeightLevel; }
    public void setAoeHeight(int h) { this.aoeHeightLevel = h; }
    // 奥: 視線方向
    private int aoeVerticalLevel = 0;
    public int getAoeDepth() { return aoeVerticalLevel; }
    public void setAoeDepth(int d) { this.aoeVerticalLevel = d; }

    // 後方互換
    public int getAoeLevel() { return aoeLevel; }
    public void setAoeLevel(int aoeLevel) { this.aoeLevel = aoeLevel; }
    public int getAoeVerticalLevel() { return aoeVerticalLevel; }
    public void setAoeVerticalLevel(int v) { this.aoeVerticalLevel = v; }

    // === AOE (半径) - 内部AOE処理エフェクト用（召喚数/爆発威力/拾い範囲等） ===
    private int aoeRadiusLevel = 0;
    public int getAoeRadiusLevel() { return aoeRadiusLevel; }
    public void setAoeRadiusLevel(int aoeRadiusLevel) { this.aoeRadiusLevel = aoeRadiusLevel; }

    // === Duration (ExtendTime/DurationDown) ===
    // 短縮は延長の半分の効果（2回積んで-1レベル）
    public int getDurationLevel() { return durationLevel; }
    public void setDurationLevel(int durationLevel) { this.durationLevel = durationLevel; }
    public void applyDurationDown() {
        durationDownAccum++;
        if (durationDownAccum % 2 == 0) {
            durationLevel--;
        }
    }

    /** 後方互換: 旧durationTicks互換。各Effectが自分で解釈すべき。 */
    public int getDurationTicks() { return durationLevel * 200; }

    /**
     * スレッドによるスペル威力倍率を返す。
     * @return 1.0 + (スペルパワー% / 100)。例: 15% → 1.15
     */
    public double getSpellPowerMultiplier() {
        Player caster = getCaster();
        if (caster == null) return 1.0;
        int spellPower = caster.getPersistentDataContainer()
            .getOrDefault(com.arspaper.mana.ManaKeys.THREAD_SPELL_POWER,
                org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        return 1.0 + spellPower / 100.0;
    }

    // === Acceleration ===
    public double getAcceleration() { return acceleration; }
    public void setAcceleration(double acceleration) { this.acceleration = acceleration; }

    // === Pierce ===
    public int getPierceCount() { return pierceCount; }
    public void setPierceCount(int pierceCount) { this.pierceCount = pierceCount; }

    // === Projectile Speed (Form augment) ===
    public double getProjectileSpeedMultiplier() { return projectileSpeedMultiplier; }
    public void setProjectileSpeedMultiplier(double v) { this.projectileSpeedMultiplier = v; }

    // === Split ===
    public int getSplitCount() { return splitCount; }
    public void setSplitCount(int splitCount) { this.splitCount = splitCount; }

    // === Sensitive ===
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }

    // === Randomize ===
    public boolean isRandomizing() { return randomizing; }
    public void setRandomizing(boolean randomizing) { this.randomizing = randomizing; }

    // === Extract ===
    public int getExtractCount() { return extractCount; }
    public void setExtractCount(int extractCount) { this.extractCount = extractCount; }

    // === Fortune ===
    public int getFortuneLevel() { return fortuneLevel; }
    public void setFortuneLevel(int fortuneLevel) { this.fortuneLevel = fortuneLevel; }

    // === Wall Pattern ===
    public boolean isWallPattern() { return wallPattern; }
    public void setWallPattern(boolean wallPattern) { this.wallPattern = wallPattern; }

    // === Linger Pattern ===
    public boolean isLingerPattern() { return lingerPattern; }
    public void setLingerPattern(boolean lingerPattern) { this.lingerPattern = lingerPattern; }

    // === Delay ===
    public int getDelayTicks() { return delayTicks; }
    public void setDelayTicks(int delayTicks) { this.delayTicks = delayTicks; }

    // === Trail ===
    public int getRapidFireLevel() { return rapidFireLevel; }
    public void setRapidFireLevel(int rapidFireLevel) { this.rapidFireLevel = rapidFireLevel; }

    public boolean isTraceActive() { return traceActive; }
    public void setTraceActive(boolean traceActive) { this.traceActive = traceActive; }

    /**
     * コンテキストコピー（Split等で独立コンテキストが必要な場合）。
     */
    public SpellContext copy() {
        SpellContext copy = new SpellContext(this);
        copy.amplifyLevel = this.amplifyLevel;
        copy.aoeLevel = this.aoeLevel;
        copy.aoeHeightLevel = this.aoeHeightLevel;
        copy.aoeVerticalLevel = this.aoeVerticalLevel;
        copy.aoeRadiusLevel = this.aoeRadiusLevel;
        copy.traceActive = this.traceActive;
        copy.hitFace = this.hitFace;
        copy.durationLevel = this.durationLevel;
        copy.acceleration = this.acceleration;
        copy.pierceCount = this.pierceCount;
        copy.projectileSpeedMultiplier = this.projectileSpeedMultiplier;
        copy.splitCount = this.splitCount;
        copy.sensitive = this.sensitive;
        copy.randomizing = this.randomizing;
        copy.extractCount = this.extractCount;
        copy.fortuneLevel = this.fortuneLevel;
        copy.wallPattern = this.wallPattern;
        copy.lingerPattern = this.lingerPattern;
        copy.delayTicks = this.delayTicks;
        copy.rapidFireLevel = this.rapidFireLevel;
        return copy;
    }

    /**
     * Formに影響するAugment（Accelerate, Split, Pierce等）を先行適用する。
     */
    public void applyFormAugments() {
        boolean pastForm = false;
        for (SpellComponent comp : components) {
            if (comp instanceof SpellForm) {
                pastForm = true;
                continue;
            }
            if (!pastForm) continue;
            if (comp instanceof SpellEffect) break;
            if (comp instanceof SpellAugment augment) {
                augment.modify(this);
            }
        }
    }

    private static final double MAX_AOE_RADIUS = 10.0;

    /**
     * EffectGroupを構築する。
     * Ars Nouveau準拠: Effectの後ろに続くAugmentがそのEffectを強化する。
     */
    private List<EffectGroup> buildEffectGroups() {
        List<EffectGroup> groups = new ArrayList<>();
        boolean pastForm = false;

        for (SpellComponent comp : components) {
            if (comp instanceof SpellForm) {
                pastForm = true;
                continue;
            }
            if (!pastForm) continue;

            if (comp instanceof SpellEffect effect) {
                groups.add(new EffectGroup(effect));
            } else if (comp instanceof SpellAugment augment) {
                if (!groups.isEmpty()) {
                    EffectGroup currentGroup = groups.get(groups.size() - 1);
                    // 互換性チェック: GlyphConfigで定義されたaugmentsに含まれるかどうか
                    String effectKey = currentGroup.effect.getId().getKey();
                    String augmentKey = augment.getId().getKey();
                    GlyphConfig glyphConfig = ArsPaper.getInstance().getGlyphConfig();
                    if (glyphConfig.isAugmentCompatible(effectKey, augmentKey)) {
                        // 最大スタック数チェック
                        int maxStack = glyphConfig.getMaxAugmentStack(effectKey, augmentKey);
                        long currentCount = currentGroup.augments.stream()
                            .filter(a -> a.getId().getKey().equals(augmentKey)).count();
                        if (currentCount < maxStack) {
                            currentGroup.augments.add(augment);
                        }
                    }
                }
            }
        }
        return groups;
    }

    /**
     * ヒット対象にEffectチェーンを実行する（エンティティ対象、AOE拡張なし）。
     * Form側が独自にエンティティスキャンを行う場合に使用する。
     * resolveOnEntity()との二重AOEを防止する。
     */
    public void resolveOnEntityNoAoe(LivingEntity target) {
        Player caster = getCaster();
        if (caster == null) return;

        List<EffectGroup> groups = buildEffectGroups();
        resolveGroupsOnEntityNoAoe(groups, 0, target);
    }

    private void resolveGroupsOnEntityNoAoe(List<EffectGroup> groups, int startIndex, LivingEntity target) {
        for (int i = startIndex; i < groups.size(); i++) {
            EffectGroup group = groups.get(i);
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }

            // Delay増強
            if (delayTicks > 0) {
                int delay = delayTicks;
                group.effect.applyToEntity(this, target);
                final int nextIndex = i + 1;
                final SpellContext ctx = this;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ctx.resolveGroupsOnEntityNoAoe(groups, nextIndex, target);
                    }
                }.runTaskLater(ArsPaper.getInstance(), delay);
                return;
            }

            // Rune: エンティティの足元にルーン設置（後続エフェクトを引き渡す）
            if (group.effect instanceof RuneEffect runeEffect) {
                List<SpellEffect> effects = new ArrayList<>();
                List<List<SpellAugment>> augmentLists = new ArrayList<>();
                for (int j = i + 1; j < groups.size(); j++) {
                    effects.add(groups.get(j).effect);
                    augmentLists.add(new ArrayList<>(groups.get(j).augments));
                }
                runeEffect.placeRuneWithEffects(this, target.getLocation(), effects, augmentLists);
                return;
            }

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerPattern) {
                group.effect.applyToEntity(this, target);
                startLingerZoneFromAugment(groups, i + 1, target.getLocation());
                return;
            }

            group.effect.applyToEntity(this, target);
        }
        resetAugmentState();
    }

    /**
     * ヒット対象にEffectチェーンを実行する（エンティティ対象）。
     */
    public void resolveOnEntity(LivingEntity target) {
        Player caster = getCaster();
        if (caster == null) return;

        List<EffectGroup> groups = buildEffectGroups();
        resolveGroupsOnEntity(groups, 0, target);
    }

    private void resolveGroupsOnEntity(List<EffectGroup> groups, int startIndex, LivingEntity target) {
        Player caster = getCaster();
        if (caster == null) return;

        for (int i = startIndex; i < groups.size(); i++) {
            EffectGroup group = groups.get(i);
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }

            // Delay増強: 後続グループを遅延実行
            if (delayTicks > 0) {
                int delay = delayTicks;
                group.effect.applyToEntity(this, target);
                final int nextIndex = i + 1;
                final SpellContext ctx = this;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ctx.resolveGroupsOnEntity(groups, nextIndex, target);
                    }
                }.runTaskLater(ArsPaper.getInstance(), delay);
                return;
            }

            // Rune: エンティティの足元にルーン設置（後続エフェクトを引き渡す）
            if (group.effect instanceof RuneEffect runeEffect) {
                List<SpellEffect> effects = new ArrayList<>();
                List<List<SpellAugment>> augmentLists = new ArrayList<>();
                for (int j = i + 1; j < groups.size(); j++) {
                    effects.add(groups.get(j).effect);
                    augmentLists.add(new ArrayList<>(groups.get(j).augments));
                }
                runeEffect.placeRuneWithEffects(this, target.getLocation(), effects, augmentLists);
                return;
            }

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerPattern) {
                group.effect.applyToEntity(this, target);
                startLingerZoneFromAugment(groups, i + 1, target.getLocation());
                return;
            }

            group.effect.applyToEntity(this, target);

            // エンティティAOE展開（半径増加ベース）
            if (!group.effect.handlesAoeInternally()) {
                double radius = Math.min(aoeRadiusLevel, MAX_AOE_RADIUS);
                if (radius > 0) {
                    Location center = target.getLocation();
                    center.getNearbyLivingEntities(radius).stream()
                        .filter(e -> !e.equals(target) && !e.equals(caster))
                        .filter(e -> isValidAoeTarget(e, caster))
                        .forEach(e -> group.effect.applyToEntity(this, e));
                }
            }
        }
        resetAugmentState();
    }

    /**
     * ヒット対象にEffectチェーンを実行する（ブロック対象、AOE拡張なし）。
     * WallForm等、Form側が独自にブロック走査を行う場合に使用。
     */
    public void resolveOnBlockNoAoe(Location blockLocation) {
        Player caster = getCaster();
        if (caster == null) return;

        List<EffectGroup> groups = buildEffectGroups();
        for (EffectGroup group : groups) {
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }
            group.effect.applyToBlock(this, blockLocation);
        }
        resetAugmentState();
    }

    /**
     * ヒット対象にEffectチェーンを実行する（ブロック対象）。
     */
    public void resolveOnBlock(Location blockLocation) {
        Player caster = getCaster();
        if (caster == null) return;

        List<EffectGroup> groups = buildEffectGroups();
        resolveGroupsOnBlock(groups, 0, blockLocation);
    }

    private void resolveGroupsOnBlock(List<EffectGroup> groups, int startIndex, Location blockLocation) {
        Player caster = getCaster();

        for (int i = startIndex; i < groups.size(); i++) {
            EffectGroup group = groups.get(i);
            resetAugmentState();
            for (SpellAugment aug : group.augments) {
                aug.modify(this);
            }

            // Delay増強: 後続グループを遅延実行
            if (delayTicks > 0) {
                int delay = delayTicks;
                group.effect.applyToBlock(this, blockLocation);
                final int nextIndex = i + 1;
                final SpellContext ctx = this;
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        ctx.resolveGroupsOnBlock(groups, nextIndex, blockLocation);
                    }
                }.runTaskLater(ArsPaper.getInstance(), delay);
                return;
            }

            // Rune: 後続グループをルーントリガー時に遅延実行（設置時は発動しない）
            // ※ Rune+Linger = ルーン持続化はRuneEffect内部で処理するため、Runeを先に判定
            if (group.effect instanceof RuneEffect runeEffect) {
                List<SpellEffect> effects = new ArrayList<>();
                List<List<SpellAugment>> augmentLists = new ArrayList<>();
                for (int j = i + 1; j < groups.size(); j++) {
                    effects.add(groups.get(j).effect);
                    augmentLists.add(new ArrayList<>(groups.get(j).augments));
                }
                runeEffect.placeRuneWithEffects(this, blockLocation, effects, augmentLists);
                return;
            }

            // 設置系エフェクト: ソリッドブロックに着弾した場合、ヒット面の隣接空気ブロックに変換
            Location effectBlockLoc = blockLocation;
            if (group.effect.getAoeMode() == SpellEffect.AoeMode.HIT_FACE_OUTWARD
                    && hitFace != null
                    && !blockLocation.getBlock().getType().isAir()) {
                Location adjacent = blockLocation.getBlock()
                    .getRelative(hitFace).getLocation();
                if (adjacent.getBlock().getType().isAir()) {
                    effectBlockLoc = adjacent;
                }
            }

            // 設置系: 設置予定位置のいずれかにキャスター自身がいたらスペル全体をキャンセル
            if (group.effect.getAoeMode() == SpellEffect.AoeMode.HIT_FACE_OUTWARD && caster != null) {
                if (isPlacementBlockedByCaster(caster, effectBlockLoc, blockLocation)) {
                    caster.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§c設置先に自分がいます"));
                    return;
                }
            }

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerPattern) {
                group.effect.applyToBlock(this, effectBlockLoc);
                startLingerZoneFromAugment(groups, i + 1, effectBlockLoc.clone().add(0.5, 0.5, 0.5));
                return;
            }

            group.effect.applyToBlock(this, effectBlockLoc);

            // === AOE展開（新3軸: 幅/上下/奥行き + 壁グリフ） ===
            if (!group.effect.handlesAoeInternally()) {
                int width = (int) Math.min(aoeLevel, MAX_AOE_RADIUS);       // 幅
                int height = (int) Math.min(aoeHeightLevel, MAX_AOE_RADIUS); // 上下
                int depth = (int) Math.min(aoeVerticalLevel, MAX_AOE_RADIUS); // 奥行き

                if (width > 0 || height > 0 || depth > 0) {
                    boolean inward = group.effect.getAoeMode() == SpellEffect.AoeMode.HIT_FACE_INWARD;
                    Location aoeLoc = inward ? blockLocation : effectBlockLoc;

                    // ヒット面から座標系を決定
                    org.bukkit.block.BlockFace face = hitFace;
                    if (face == null) {
                        face = org.bukkit.block.BlockFace.UP; // デフォルト=床
                    }

                    int ny = Math.abs(face.getDirection().getBlockY());
                    int nx = face.getDirection().getBlockX();
                    int nz = face.getDirection().getBlockZ();

                    // 法線方向の符号（破壊=奥へ、設置=手前へ）
                    int depthSign = inward ? -1 : 1;

                    if (ny > 0) {
                        // --- 床/天井ヒット ---
                        org.bukkit.util.Vector lookH = caster.getLocation().getDirection().setY(0);
                        if (lookH.lengthSquared() < 0.01) lookH = new org.bukkit.util.Vector(0, 0, 1);
                        boolean fwdZ = Math.abs(lookH.getZ()) >= Math.abs(lookH.getX());
                        int fX = fwdZ ? 0 : (lookH.getX() > 0 ? 1 : -1);
                        int fZ = fwdZ ? (lookH.getZ() > 0 ? 1 : -1) : 0;
                        int rX = fwdZ ? 1 : 0;
                        int rZ = fwdZ ? 0 : 1;
                        int dY = depthSign * face.getDirection().getBlockY();
                        boolean outward = !inward;

                        for (int w = -width; w <= width; w++) {
                            for (int h = -height; h <= height; h++) {
                                for (int d = 0; d <= depth; d++) {
                                    if (w == 0 && h == 0 && d == 0) continue;
                                    int dx = rX * w + fX * h;
                                    int dz = rZ * w + fZ * h;

                                    if (outward && dY > 0) {
                                        // 設置系 + 上面ヒット: 各XZ位置で地表に合わせる
                                        Location columnBase = aoeLoc.clone().add(dx, 0, dz);
                                        Location surfaceLoc = findSurfaceUp(columnBase, aoeLoc.getBlockY());
                                        if (surfaceLoc == null) continue; // 既にブロックがある
                                        Location placeLoc = surfaceLoc.clone().add(0, d, 0);
                                        if (!placeLoc.getBlock().getType().isAir()) continue;
                                        group.effect.applyToBlock(this, placeLoc);
                                    } else {
                                        int dy = dY * d;
                                        group.effect.applyToBlock(this,
                                            aoeLoc.clone().add(dx, dy, dz));
                                    }
                                }
                            }
                        }
                    } else {
                        // --- 壁ヒット ---
                        // 幅=左右、上下=Y方向、奥行き=法線方向
                        boolean isXFace = Math.abs(nx) > 0;

                        for (int w = -width; w <= width; w++) {
                            for (int h = -height; h <= height; h++) {
                                for (int d = 0; d <= depth; d++) {
                                    if (w == 0 && h == 0 && d == 0) continue;
                                    int ddx = isXFace ? nx * d * depthSign : w;
                                    int ddy = h;
                                    int ddz = isXFace ? w : nz * d * depthSign;
                                    group.effect.applyToBlock(this,
                                        aoeLoc.clone().add(ddx, ddy, ddz));
                                }
                            }
                        }
                    }
                }
            }
        }
        resetAugmentState();
    }

    /**
     * LingerAugmentからゾーンを開始する。後続グループを定期的に適用する。
     */
    private void startLingerZoneFromAugment(List<EffectGroup> groups, int startIndex, Location center) {
        List<SpellEffect> effects = new ArrayList<>();
        List<List<SpellAugment>> augmentLists = new ArrayList<>();
        for (int j = startIndex; j < groups.size(); j++) {
            effects.add(groups.get(j).effect);
            augmentLists.add(new ArrayList<>(groups.get(j).augments));
        }
        if (!effects.isEmpty()) {
            LingerEffect.startZoneStatic(ArsPaper.getInstance(), this, center, effects, augmentLists);
        }
    }

    private static class EffectGroup {
        final SpellEffect effect;
        final List<SpellAugment> augments = new ArrayList<>();
        EffectGroup(SpellEffect effect) { this.effect = effect; }
    }

    /**
     * 設置系エフェクトの設置予定位置にキャスター自身がいるかチェック。
     * 起点ブロック位置のみ簡易チェック（AOE展開位置は起点から広がるため、
     * キャスターが起点にいればほぼ確実に重なる）。
     */
    /**
     * 指定XZ位置で基準Y付近の地表（空気ブロック）を探す。
     * 基準Yから上下3ブロック以内で探索。
     * 空気ブロックが見つからない（全てソリッド）場合はnullを返す。
     */
    private Location findSurfaceUp(Location columnBase, int referenceY) {
        // 基準Yから下に探索して最初のソリッドブロックの上面を見つける
        for (int dy = 3; dy >= -3; dy--) {
            Location check = columnBase.clone();
            check.setY(referenceY + dy);
            if (!check.getBlock().getType().isAir()) {
                // このブロックの上が空気なら、そこが地表
                Location above = check.clone().add(0, 1, 0);
                if (above.getBlock().getType().isAir()) {
                    return above;
                }
            }
        }
        // 全部空気の場合は基準Yをそのまま使用
        Location fallback = columnBase.clone();
        fallback.setY(referenceY);
        if (fallback.getBlock().getType().isAir()) {
            return fallback;
        }
        return null; // 設置不可
    }

    private boolean isPlacementBlockedByCaster(Player caster, Location effectBlockLoc, Location blockLocation) {
        // 起点チェック
        if (SpellFxUtil.isPlayerOccupying(effectBlockLoc, caster)) return true;
        // 元のヒットブロック位置もチェック（隣接変換前）
        if (SpellFxUtil.isPlayerOccupying(blockLocation, caster)) return true;
        return false;
    }

    public boolean isValidAoeTarget(LivingEntity entity, Player caster) {
        // PvP無効時はプレイヤーをスペル対象から除外
        if (entity instanceof Player && entity != caster) {
            boolean pvpEnabled = ArsPaper.getInstance().getConfig().getBoolean("pvp.enabled", true);
            if (!pvpEnabled || !entity.getWorld().getPVP()) {
                return false;
            }
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            if (caster.equals(tameable.getOwner())) return false;
        }
        return true;
    }

    /**
     * Augment状態をリセットする（公開版、LingerEffect等が使用）。
     */
    public void resetPublicAugmentState() {
        resetAugmentState();
    }

    /**
     * Effect-level Augment状態をリセットする。
     * Form-level値（pierce, split, projectileSpeed, acceleration）は
     * スペル全体のライフサイクルで維持するためリセットしない。
     */
    private void resetAugmentState() {
        amplifyLevel = 0;
        dampenAccum = 0;
        durationDownAccum = 0;
        aoeLevel = 0;
        aoeHeightLevel = 0;
        aoeVerticalLevel = 0;
        aoeRadiusLevel = 0;
        durationLevel = 0;
        // acceleration はForm-levelでも使用するためリセットしない
        // pierceCount はProjectileHitListenerが参照し続けるためリセットしない
        // projectileSpeedMultiplier はForm-levelのためリセットしない
        // splitCount はForm-levelのためリセットしない
        sensitive = false;
        randomizing = false;
        extractCount = 0;
        fortuneLevel = 0;
        wallPattern = false;
        lingerPattern = false;
        delayTicks = 0;
        // rapidFireLevel はForm-levelのためリセットしない
    }
}
