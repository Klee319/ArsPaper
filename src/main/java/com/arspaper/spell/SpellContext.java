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

            // AOE展開（水平・垂直分離）
            if (!group.effect.handlesAoeInternally()) {
                double hRadius = Math.min(aoeLevel, MAX_AOE_RADIUS);
                double vRadius = Math.min(aoeVerticalLevel, MAX_AOE_RADIUS);
                double searchRadius = Math.max(hRadius, vRadius);
                if (searchRadius > 0) {
                    Location center = target.getLocation();
                    center.getNearbyLivingEntities(searchRadius).stream()
                        .filter(e -> !e.equals(target) && !e.equals(caster))
                        .filter(e -> isValidAoeTarget(e, caster))
                        .filter(e -> {
                            double dx = e.getLocation().getX() - center.getX();
                            double dy = e.getLocation().getY() - center.getY();
                            double dz = e.getLocation().getZ() - center.getZ();
                            double hDist = Math.sqrt(dx * dx + dz * dz);
                            return (hRadius <= 0 || hDist <= hRadius)
                                && (vRadius <= 0 || Math.abs(dy) <= vRadius);
                        })
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

            // Linger増強: 後続グループをゾーンで定期適用（Rune以外の効果用）
            if (lingerPattern) {
                group.effect.applyToBlock(this, effectBlockLoc);
                startLingerZoneFromAugment(groups, i + 1, effectBlockLoc.clone().add(0.5, 0.5, 0.5));
                return;
            }

            group.effect.applyToBlock(this, effectBlockLoc);

            // AOE展開（wall/通常）
            if (!group.effect.handlesAoeInternally()) {
                if (wallPattern && caster != null) {
                    // 投影パターン: ヒット面に沿って効果を投影する
                    // hitFaceがない場合は視線の水平方向で代用
                    int r1 = aoeLevel;
                    int r2 = aoeVerticalLevel;
                    Location projBase = effectBlockLoc;

                    // 投影: hitFaceがあればヒット面基準、なければ視線基準
                    org.bukkit.block.BlockFace projFace = hitFace;
                    if (projFace == null) {
                        // hitFaceがない場合は視線から推定
                        org.bukkit.util.Vector lookDir = caster.getLocation().getDirection();
                        if (Math.abs(lookDir.getY()) > 0.7) {
                            projFace = lookDir.getY() > 0 ? org.bukkit.block.BlockFace.UP : org.bukkit.block.BlockFace.DOWN;
                        } else {
                            double absX = Math.abs(lookDir.getX());
                            double absZ = Math.abs(lookDir.getZ());
                            if (absX > absZ) {
                                projFace = lookDir.getX() > 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST;
                            } else {
                                projFace = lookDir.getZ() > 0 ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
                            }
                        }
                    }

                    org.bukkit.util.Vector look = caster.getLocation().getDirection();
                    boolean isHorizontalFace = Math.abs(projFace.getDirection().getBlockY()) > 0;

                    if (isHorizontalFace) {
                        // 地面/天井ヒット → 視線方向に壁を投影
                        // 水平=視線方向×横の正方形壁面、垂直=視線方向の厚み
                        org.bukkit.util.Vector lookH = look.clone().setY(0);
                        if (lookH.lengthSquared() < 0.01) lookH = new org.bukkit.util.Vector(0, 0, 1);
                        boolean fwdIsZ = Math.abs(lookH.getZ()) >= Math.abs(lookH.getX());
                        int fwdX = fwdIsZ ? 0 : (lookH.getX() > 0 ? 1 : -1);
                        int fwdZ = fwdIsZ ? (lookH.getZ() > 0 ? 1 : -1) : 0;
                        int rightX = fwdIsZ ? (lookH.getZ() > 0 ? -1 : 1) : 0;
                        int rightZ = fwdIsZ ? 0 : (lookH.getX() > 0 ? 1 : -1);
                        // 横は両側展開、高さは上方向に片側展開
                        int wallHeight = r1 * 2;
                        for (int w = -r1; w <= r1; w++) {
                            for (int h = 0; h <= wallHeight; h++) {
                                for (int d = 0; d <= r2; d++) {
                                    if (w == 0 && h == 0 && d == 0) continue;
                                    Location nearby = projBase.clone()
                                        .add(rightX * w, h, rightZ * w)
                                        .add(fwdX * d, 0, fwdZ * d);
                                    group.effect.applyToBlock(this, nearby.getBlock().getLocation());
                                }
                            }
                        }
                    } else {
                        // 側面ヒット → 法線方向に水平面を投影（橋・床が手前に伸びる）
                        // 水平=法線方向×横の正方形水平面、垂直=下方向の厚み
                        int pnx = projFace.getDirection().getBlockX();
                        int pnz = projFace.getDirection().getBlockZ();
                        boolean isXFace = Math.abs(pnx) > 0;
                        // 法線方向（手前）に片側展開、横は両側展開
                        int span = r1 * 2;
                        for (int depth = 0; depth <= span; depth++) {
                            for (int lateral = -r1; lateral <= r1; lateral++) {
                                for (int h = 0; h <= r2; h++) {
                                    if (depth == 0 && lateral == 0 && h == 0) continue;
                                    int dx = isXFace ? pnx * depth : lateral;
                                    int dz = isXFace ? lateral : pnz * depth;
                                    Location nearby = projBase.clone().add(dx, -h, dz);
                                    group.effect.applyToBlock(this, nearby.getBlock().getLocation());
                                }
                            }
                        }
                    }
                } else if (group.effect.getAoeMode() != SpellEffect.AoeMode.FIXED && hitFace != null) {
                    // ヒット面依存AOE: ヒット面に沿って展開
                    boolean inward = group.effect.getAoeMode() == SpellEffect.AoeMode.HIT_FACE_INWARD;
                    // 水平 = 面に沿った広がり、垂直 = 法線方向の奥行き
                    // 上/下面: 水平=XZ平面, 垂直=Y方向（深掘り）
                    // 東/西面: 水平=面横(Z)+面縦(Y), 垂直=X方向（奥掘り）
                    // 南/北面: 水平=面横(X)+面縦(Y), 垂直=Z方向（奥掘り）
                    int hAoe = (int) Math.min(aoeLevel, MAX_AOE_RADIUS);
                    int vAoe = (int) Math.min(aoeVerticalLevel, MAX_AOE_RADIUS);
                    if (hAoe > 0 || vAoe > 0) {
                        org.bukkit.util.Vector normal = hitFace.getDirection();
                        int nx = normal.getBlockX();
                        int ny = normal.getBlockY();
                        int nz = normal.getBlockZ();

                        // inward: 奥行き=法線の逆方向（ブロック内部へ、破壊用）
                        // outward: 奥行き=法線と同方向（手前へ、設置用）
                        int sign = inward ? -1 : 1;

                        // INWARDは元のblockLocation基準、OUTWARDはeffectBlockLoc基準
                        Location aoeLoc = inward ? blockLocation : effectBlockLoc;
                        if (Math.abs(ny) > 0) {
                            // 上/下面: XZ=水平, Y=垂直
                            int depthDir = sign * ny;
                            for (int dx = -hAoe; dx <= hAoe; dx++) {
                                for (int dz = -hAoe; dz <= hAoe; dz++) {
                                    for (int d = 0; d <= vAoe; d++) {
                                        if (dx == 0 && dz == 0 && d == 0) continue;
                                        group.effect.applyToBlock(this,
                                            aoeLoc.clone().add(dx, depthDir * d, dz));
                                    }
                                }
                            }
                        } else {
                            // 側面: 面上=水平(横+Y), 法線方向=垂直
                            boolean isXFace = Math.abs(nx) > 0;
                            int depthDirX = sign * nx;
                            int depthDirZ = sign * nz;
                            for (int a = -hAoe; a <= hAoe; a++) {
                                for (int dy = -hAoe; dy <= hAoe; dy++) {
                                    for (int d = 0; d <= vAoe; d++) {
                                        if (a == 0 && dy == 0 && d == 0) continue;
                                        int dx = isXFace ? depthDirX * d : a;
                                        int dz = isXFace ? a : depthDirZ * d;
                                        group.effect.applyToBlock(this,
                                            aoeLoc.clone().add(dx, dy, dz));
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 通常AOE（水平・垂直分離）
                    int hAoe = (int) Math.min(aoeLevel, MAX_AOE_RADIUS);
                    int vAoe = (int) Math.min(aoeVerticalLevel, MAX_AOE_RADIUS);
                    if (hAoe > 0 || vAoe > 0) {
                        int hr = Math.max(hAoe, 0);
                        int vr = Math.max(vAoe, 0);
                        for (int dx = -hr; dx <= hr; dx++) {
                            for (int dy = -vr; dy <= vr; dy++) {
                                for (int dz = -hr; dz <= hr; dz++) {
                                    if (dx == 0 && dy == 0 && dz == 0) continue;
                                    Location nearby = effectBlockLoc.clone().add(dx, dy, dz);
                                    group.effect.applyToBlock(this, nearby);
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
