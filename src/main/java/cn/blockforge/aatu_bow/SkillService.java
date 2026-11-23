package cn.blockforge.aatu_bow;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SkillService {
    public static final byte AATU_BOW = 0;
    public static final byte AATU_SWORD = 1;
    public static final String AATU_SWORD_HITS_TAG = "aatu_bow_aatu_sword_hits";
    private static final String FUSED_SWORD_COOLDOWN_UNTIL_TAG =
            "aatu_bow_fused_sword_cooldown_until";
    public static final String SKILL_DAMAGE_FLAG = "aatu_bow_skill_damage";

    private SkillService() {
    }

    public static boolean isAatuSwordReady(Player player) {
        return isAatuSwordReady(player, player.getWeaponItem());
    }

    public static boolean isAatuSwordReady(Player player, ItemStack weapon) {
        if (FusionService.hasFusedSwordSkill(weapon)) {
            return FusionService.swordHits(weapon) >= FusionService.MAX_SWORD_HITS;
        }
        return player.getPersistentData().getInt(AATU_SWORD_HITS_TAG)
                >= FusionService.MAX_SWORD_HITS;
    }

    public static int aatuSwordHits(Player player) {
        return aatuSwordHits(player, player.getWeaponItem());
    }

    public static int aatuSwordHits(Player player, ItemStack weapon) {
        if (FusionService.hasFusedSwordSkill(weapon)) {
            return FusionService.swordHits(weapon);
        }
        return Math.min(FusionService.MAX_SWORD_HITS,
                Math.max(0, player.getPersistentData().getInt(AATU_SWORD_HITS_TAG)));
    }

    public static void recordAatuSwordHit(ServerPlayer player) {
        recordAatuSwordHit(player, player.getWeaponItem());
    }

    public static void recordAatuSwordHit(ServerPlayer player, ItemStack weapon) {
        if (!FusionService.hasSwordSkill(weapon)) {
            return;
        }
        int hits = aatuSwordHits(player, weapon);
        if (hits < FusionService.MAX_SWORD_HITS) {
            hits++;
            if (FusionService.hasFusedSwordSkill(weapon)) {
                FusionService.setSwordHits(weapon, hits);
            } else {
                player.getPersistentData().putInt(AATU_SWORD_HITS_TAG, hits);
            }
            if (hits == FusionService.MAX_SWORD_HITS) {
                player.displayClientMessage(
                        Component.translatable(FusionService.hasFusedSwordSkill(weapon)
                                ? "message.aatu_bow.fused_sword_ready"
                                : "message.aatu_bow.aatu_sword_ready"), true);
            }
        }
    }

    /**
     * A fused weapon's skill cooldown belongs to the player rather than Minecraft's item
     * cooldown list. This preserves the host weapon's own right-click attack and skill.
     */
    public static boolean isAatuSwordOnCooldown(ServerPlayer player, ItemStack weapon) {
        if (FusionService.hasFusedSwordSkill(weapon)) {
            return player.getPersistentData().getLong(FUSED_SWORD_COOLDOWN_UNTIL_TAG)
                    > player.level().getGameTime();
        }
        return player.getCooldowns().isOnCooldown(weapon.getItem());
    }

    public static boolean tryActivate(ServerPlayer player, byte weaponType) {
        return tryActivate(player, player.getWeaponItem(), weaponType);
    }

    public static boolean tryActivate(ServerPlayer player, ItemStack weapon, byte weaponType) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (weaponType != AATU_SWORD) {
            return false;
        }
        if (weapon.isEmpty() || !FusionService.hasSwordSkill(weapon)) {
            return false;
        }
        ItemCooldowns cooldowns = player.getCooldowns();
        boolean fusedSwordSkill = FusionService.hasFusedSwordSkill(weapon);
        if (isAatuSwordOnCooldown(player, weapon)) {
            player.displayClientMessage(Component.translatable("message.aatu_bow.cooldown"), true);
            return false;
        }
        if (!isAatuSwordReady(player, weapon)) {
            int remaining = Math.max(0, FusionService.MAX_SWORD_HITS - aatuSwordHits(player, weapon));
            player.displayClientMessage(
                    Component.translatable("message.aatu_bow.aatu_sword_not_ready", remaining), true);
            return false;
        }

        Vec3 direction = player.getLookAngle().normalize();
        Vec3 right = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 0.0001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(direction).normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(1.0D));
        int length = AatuConfig.SKILL_LENGTH.get();
        double halfWidth = AatuConfig.SKILL_WIDTH.get() * 0.5D;
        double halfHeight = AatuConfig.SKILL_HEIGHT.get() * 0.5D;
        Vec3 end = start.add(direction.scale(length));
        AABB searchArea = new AABB(start, end)
                .inflate(halfWidth + 2.0D, halfHeight + 2.0D, halfWidth + 2.0D);
        List<Entity> candidates = level.getEntities(player, searchArea,
                entity -> entity instanceof LivingEntity living
                        && canDamageTarget(player, living));
        DamageSource damageSource = level.damageSources().playerAttack(player);
        ItemStack lingeringWeapon = weapon.copy();

        CompoundTag playerData = player.getPersistentData();
        boolean previousDamageFlag = playerData.getBoolean(SKILL_DAMAGE_FLAG);
        playerData.putBoolean(SKILL_DAMAGE_FLAG, true);
        try {
            for (Entity candidate : candidates) {
                if (!(candidate instanceof LivingEntity target)) {
                    continue;
                }
                Vec3 relative = target.getBoundingBox().getCenter().subtract(start);
                double forwardDistance = relative.dot(direction);
                if (forwardDistance < 0.0D || forwardDistance > length) {
                    continue;
                }
                double lateralDistance = Math.abs(relative.dot(right));
                double verticalDistance = Math.abs(relative.dot(up));
                double lateralAllowance = halfWidth + Math.max(0.35D, target.getBbWidth() * 0.5D);
                double verticalAllowance = halfHeight + Math.max(0.6D, target.getBbHeight() * 0.35D);
                if (lateralDistance <= lateralAllowance && verticalDistance <= verticalAllowance) {
                    float damage = skillDamage(level, player, target, weapon, damageSource);
                    if (applySkillDamage(level, player, target, weapon, damageSource, damage)
                            && target.isAlive() && !target.isRemoved()) {
                        LingeringSkillService.mark(level, player, target, lingeringWeapon, weaponType, damage);
                    }
                }
            }
        } finally {
            playerData.putBoolean(SKILL_DAMAGE_FLAG, previousDamageFlag);
        }

        if (fusedSwordSkill) {
            playerData.putLong(FUSED_SWORD_COOLDOWN_UNTIL_TAG,
                    level.getGameTime() + AatuConfig.AATU_SWORD_COOLDOWN_TICKS.get());
            FusionService.setSwordHits(weapon, 0);
        } else {
            cooldowns.addCooldown(weapon.getItem(), AatuConfig.AATU_SWORD_COOLDOWN_TICKS.get());
            playerData.putInt(AATU_SWORD_HITS_TAG, 0);
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new SkillEffectPayload(player.getId(), weaponType, length,
                        AatuConfig.SKILL_WIDTH.get(), AatuConfig.SKILL_HEIGHT.get(), 0));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.IRON_GOLEM_DEATH, SoundSource.PLAYERS, 1.0F, 0.85F);
        return true;
    }

    /** Shared target policy for every AaTu skill, including fused weapons and active marks. */
    static boolean canDamageTarget(ServerPlayer source, LivingEntity target) {
        if (target == source || !target.isAlive() || target.isRemoved()
                || target.isSpectator() || !source.canAttack(target)) {
            return false;
        }
        if (AatuConfig.SKILL_DAMAGE_PLAYERS_AND_NEUTRAL_MOBS.get()) {
            return true;
        }
        if (target instanceof Player) {
            return false;
        }
        if (target instanceof OwnableEntity ownable && ownable.getOwner() instanceof Player) {
            return false;
        }
        // Match SlashBlade's Enemy classification without extra neutral-species exclusions.
        return target instanceof Enemy;
    }

    private static float skillDamage(ServerLevel level, ServerPlayer player, LivingEntity target,
                                     ItemStack weapon, DamageSource damageSource) {
        return skillDamage(level, target, weapon, damageSource,
                player.getAttributeValue(Attributes.ATTACK_DAMAGE));
    }

    static float skillDamage(ServerLevel level, LivingEntity target, ItemStack weapon,
                             DamageSource damageSource, double capturedAttackDamage) {
        double configuredMinimum = AatuConfig.SKILL_ADDITIONAL_DAMAGE_MIN.get();
        double configuredMaximum = AatuConfig.SKILL_ADDITIONAL_DAMAGE_MAX.get();
        double minimum = Math.min(configuredMinimum, configuredMaximum);
        double maximum = Math.max(configuredMinimum, configuredMaximum);
        double additionalDamageMultiplier = minimum
                + level.getRandom().nextDouble() * (maximum - minimum);
        float weaponDamage = (float) Math.max(0.0D, capturedAttackDamage);
        double additionalDamage = weaponDamage * additionalDamageMultiplier;
        float damageBeforeEnchantments = (float) (weaponDamage + additionalDamage);
        return EnchantmentHelper.modifyDamage(level, weapon, target, damageSource,
                damageBeforeEnchantments);
    }

    /** Shared by the opening slash and its follow-up hits; neither can charge the sword. */
    static boolean applySkillDamage(ServerLevel level, ServerPlayer player, LivingEntity target,
                                    ItemStack weapon, DamageSource damageSource, float damage) {
        // Recheck the live setting before each hit, including the ten lingering hits.
        if (!canDamageTarget(player, target)) {
            return false;
        }
        CompoundTag playerData = player.getPersistentData();
        boolean previousDamageFlag = playerData.getBoolean(SKILL_DAMAGE_FLAG);
        playerData.putBoolean(SKILL_DAMAGE_FLAG, true);
        try {
            if (!target.hurt(damageSource, damage)) {
                return false;
            }
            applyWeaponEnchantments(level, target, weapon, damageSource);
            return true;
        } finally {
            playerData.putBoolean(SKILL_DAMAGE_FLAG, previousDamageFlag);
        }
    }

    private static void applyWeaponEnchantments(ServerLevel level, LivingEntity target,
                                                ItemStack weapon, DamageSource damageSource) {
        EnchantmentHelper.doPostAttackEffectsWithItemSource(level, target, damageSource, weapon);

        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        int fireAspectLevel = EnchantmentHelper.getItemEnchantmentLevel(
                enchantments.getHolderOrThrow(Enchantments.FIRE_ASPECT), weapon);
        int flameLevel = EnchantmentHelper.getItemEnchantmentLevel(
                enchantments.getHolderOrThrow(Enchantments.FLAME), weapon);
        int fireSeconds = Math.max(fireAspectLevel * 4, flameLevel * 5);
        if (fireSeconds > 0) {
            target.igniteForSeconds(fireSeconds);
        }
    }
}
