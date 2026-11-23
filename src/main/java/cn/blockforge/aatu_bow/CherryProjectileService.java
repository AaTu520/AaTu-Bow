package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;

/** Shot data travels with the arrow so unloading a chunk does not lose the skill. */
public final class CherryProjectileService {
    private static final String SHOT_DATA = "aatu_bow_cherry_shot";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, Map<UUID, PendingShot>> SHOTS = new IdentityHashMap<>();
    private static final Map<ServerLevel, List<FreeArrow>> FREE_ARROWS = new IdentityHashMap<>();
    private static final Map<ServerLevel, List<Impact>> IMPACTS = new IdentityHashMap<>();

    private CherryProjectileService() {}

    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                || player.getUsedItemHand() != InteractionHand.MAIN_HAND || event.getCharge() < 20
                || !FusionService.hasBowSkill(event.getBow())) return;
        SHOTS.computeIfAbsent(level, ignored -> new HashMap<>()).put(player.getUUID(),
                new PendingShot(event, event.getBow().copy(), player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                        level.getGameTime()));
    }

    static void scheduleAmmoFreeArrow(LivingEntityUseItemEvent.Stop event, ServerPlayer player, ItemStack weapon) {
        if (!(player.level() instanceof ServerLevel level)) return;
        FREE_ARROWS.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new FreeArrow(event, player,
                weapon.copy(), player.getAttributeValue(Attributes.ATTACK_DAMAGE), player.getXRot(), player.getYRot()));
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof AbstractArrow arrow)
                || !(arrow.getOwner() instanceof ServerPlayer player)
                || arrow.getPersistentData().contains(SHOT_DATA)) return;
        List<FreeArrow> freeArrows = FREE_ARROWS.get(level);
        if (freeArrows != null) {
            for (FreeArrow shot : List.copyOf(freeArrows)) {
                if (shot.player != player || shot.event.isCanceled() || !matchesArrow(arrow, shot.weapon)) continue;
                freeArrows.remove(shot);
                markArrow(arrow, player, shot.weapon, shot.attackDamage, false);
                return;
            }
        }
        Map<UUID, PendingShot> shots = SHOTS.get(level);
        if (shots == null) return;
        PendingShot shot = shots.get(player.getUUID());
        if (shot == null || shot.event.isCanceled() || shot.event.getCharge() < 20
                || level.getGameTime() - shot.tick > 1) return;
        if (!matchesArrow(arrow, shot.weapon)) return;
        shots.remove(player.getUUID());
        markArrow(arrow, player, shot.weapon, shot.attackDamage, false);
    }

    private static boolean matchesArrow(AbstractArrow arrow, ItemStack weapon) {
        ItemStack firingWeapon = arrow.getWeaponItem();
        return firingWeapon == null || firingWeapon.isEmpty()
                ? arrow.isCritArrow() : ItemStack.isSameItem(firingWeapon, weapon);
    }

    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) return;
        CompoundTag root = arrow.getPersistentData();
        if (!root.contains(SHOT_DATA)) return;
        CompoundTag data = root.getCompound(SHOT_DATA);
        if (data.getBoolean("used") || !data.hasUUID("owner")) return;
        data.putBoolean("used", true);
        root.put(SHOT_DATA, data);
        boolean free = data.getBoolean("free");
        if (free) {
            event.setCanceled(true);
            arrow.discard();
        }
        IMPACTS.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new Impact(event, arrow, data.copy(),
                event.getRayTraceResult().getLocation(), arrow.getDeltaMovement(), free));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Map<UUID, PendingShot> shots = SHOTS.get(level);
        if (shots != null) {
            shots.values().removeIf(shot -> shot.event.isCanceled() || level.getGameTime() - shot.tick > 1);
            if (shots.isEmpty()) SHOTS.remove(level);
        }
        List<FreeArrow> freeArrows = FREE_ARROWS.remove(level);
        if (freeArrows != null) for (FreeArrow shot : freeArrows) spawnFreeArrow(level, shot);
        List<Impact> impacts = IMPACTS.remove(level);
        if (impacts != null) for (Impact impact : impacts) resolveImpact(level, impact);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            SHOTS.remove(level); FREE_ARROWS.remove(level); IMPACTS.remove(level);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SHOTS.clear(); FREE_ARROWS.clear(); IMPACTS.clear();
    }

    private static void spawnFreeArrow(ServerLevel level, FreeArrow shot) {
        if (shot.event.isCanceled() || !validPlayer(level, shot.player)) return;
        Arrow arrow = new Arrow(level, shot.player, new ItemStack(Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setCritArrow(true);
        arrow.shootFromRotation(shot.player, shot.pitch, shot.yaw, 0.0F, 3.0F, 1.0F);
        if (!markArrow(arrow, shot.player, shot.weapon, shot.attackDamage, true)) return;
        if (level.addFreshEntity(arrow)) {
            level.playSound(null, shot.player.getX(), shot.player.getY(), shot.player.getZ(),
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    private static boolean markArrow(AbstractArrow arrow, ServerPlayer player, ItemStack weapon,
                                     double attackDamage, boolean free) {
        try {
            CompoundTag data = new CompoundTag();
            data.putUUID("owner", player.getUUID());
            data.putDouble("attack", Double.isFinite(attackDamage) ? Math.max(0.0D, attackDamage) : 0.0D);
            data.putBoolean("free", free);
            data.put("weapon", weapon.save(arrow.level().registryAccess()));
            arrow.getPersistentData().put(SHOT_DATA, data);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not attach the cherry skill to an arrow; the original shot is unchanged", exception);
            return false;
        }
    }

    private static void resolveImpact(ServerLevel level, Impact impact) {
        if (!impact.free && impact.event.isCanceled()) {
            CompoundTag root = impact.arrow.getPersistentData();
            CompoundTag data = root.getCompound(SHOT_DATA);
            data.putBoolean("used", false);
            root.put(SHOT_DATA, data);
            return;
        }
        if (!impact.data.contains("weapon", Tag.TAG_COMPOUND)) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(impact.data.getUUID("owner"));
        ItemStack weapon = ItemStack.parse(level.registryAccess(), impact.data.get("weapon")).orElse(ItemStack.EMPTY);
        if (!validPlayer(level, player) || weapon.isEmpty()) return;
        CherryRainService.begin(level, player, weapon, impact.data.getDouble("attack"), impact.position, impact.direction);
    }

    private static boolean validPlayer(ServerLevel level, ServerPlayer player) {
        return player != null && player.level() == level && player.isAlive() && !player.isRemoved()
                && !player.isSpectator() && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private record PendingShot(ArrowLooseEvent event, ItemStack weapon, double attackDamage, long tick) {}
    private record FreeArrow(LivingEntityUseItemEvent.Stop event, ServerPlayer player, ItemStack weapon,
                             double attackDamage, float pitch, float yaw) {}
    private record Impact(ProjectileImpactEvent event, AbstractArrow arrow, CompoundTag data,
                          Vec3 position, Vec3 direction, boolean free) {}
}
