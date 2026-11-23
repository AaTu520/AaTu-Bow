package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CherryRainService {
    private static final double VISUAL_RANGE = 96.0D;
    private static final Map<ServerLevel, List<RainArea>> ACTIVE = new IdentityHashMap<>();

    private CherryRainService() {}

    static void begin(ServerLevel level, ServerPlayer player, ItemStack weapon, double attackDamage,
                      Vec3 impact, Vec3 flightDirection) {
        RainArea rain = new RainArea(level, player, weapon.copy(), attackDamage, impact);
        if (!rain.validSource()) return;
        Vec3 horizontal = new Vec3(flightDirection.x, 0.0D, flightDirection.z);
        horizontal = horizontal.lengthSqr() < 0.0001D ? new Vec3(0.0D, 0.0D, 1.0D) : horizontal.normalize();
        int visualTicks = Math.max(2, Math.min(600,
                (int) Math.round(AatuConfig.AATU_BOW_PARTICLE_LIFETIME_SECONDS.get() * 20.0D)));
        PacketDistributor.sendToPlayersNear(level, null, impact.x, impact.y, impact.z, VISUAL_RANGE,
                new CherryRainPayload(impact, horizontal, rain.radius, rain.height, visualTicks));
        rain.initialHit();
        if (rain.validSource()) ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>()).add(rain);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        List<RainArea> effects = ACTIVE.get(level);
        if (effects == null) return;
        for (RainArea rain : List.copyOf(effects)) {
            if (effects.contains(rain) && !rain.tick()) effects.remove(rain);
        }
        if (effects.isEmpty()) ACTIVE.remove(level, effects);
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private static final class RainArea {
        private final ServerLevel level;
        private final ServerPlayer source;
        private final ItemStack weapon;
        private final double attackDamage;
        private final Vec3 center;
        private final double radius;
        private final double height;
        private final int intervalTicks;
        private final DamageSource damageSource;
        private final Map<UUID, Float> damageByTarget = new HashMap<>();
        private final Set<UUID> marked = new HashSet<>();
        private int remainingHits;
        private int pulseIndex;
        private long nextHitAt;

        private RainArea(ServerLevel level, ServerPlayer source, ItemStack weapon, double attackDamage, Vec3 center) {
            this.level = level;
            this.source = source;
            this.weapon = weapon;
            this.attackDamage = attackDamage;
            this.center = center;
            this.radius = AatuConfig.BOW_RAIN_RADIUS.get();
            this.height = AatuConfig.BOW_RAIN_HEIGHT.get();
            this.remainingHits = AatuConfig.BOW_CHAIN_HIT_COUNT.get();
            this.intervalTicks = AatuConfig.BOW_CHAIN_INTERVAL_TICKS.get();
            this.nextHitAt = level.getGameTime() + intervalTicks;
            this.damageSource = LingeringSkillService.bypassingCooldown(source, weapon);
        }

        private void initialHit() {
            for (LivingEntity target : targets()) {
                if (!validSource()) break;
                hit(target);
            }
        }

        private boolean tick() {
            if (!validSource()) return false;
            long now = level.getGameTime();
            if (now < nextHitAt) return true;
            List<Vec3> struck = new ArrayList<>();
            for (LivingEntity target : targets()) {
                if (!validSource()) return false;
                Vec3 position = target.getBoundingBox().getCenter();
                if (hit(target)) struck.add(position);
            }
            if (!struck.isEmpty()) sendLightning(struck);
            pulseIndex++;
            remainingHits--;
            nextHitAt = now + intervalTicks;
            return remainingHits > 0;
        }

        private boolean hit(LivingEntity target) {
            if (target.level() != level || !isInside(target) || !SkillService.canDamageTarget(source, target)) {
                return false;
            }
            float damage = damageByTarget.computeIfAbsent(target.getUUID(), ignored ->
                    SkillService.skillDamage(level, target, weapon, damageSource, attackDamage));
            boolean applied = SkillService.applySkillDamage(level, source, target, weapon, damageSource, damage);
            if (applied && damage > 0.0F && target.isAlive() && !target.isRemoved() && marked.add(target.getUUID())) {
                LingeringSkillService.markBowProtection(level, source, target, weapon);
            }
            return applied;
        }

        private List<LivingEntity> targets() {
            double halfHeight = height * 0.5D;
            AABB bounds = new AABB(center.x - radius, center.y - halfHeight, center.z - radius,
                    center.x + radius, center.y + halfHeight, center.z + radius);
            return level.getEntitiesOfClass(LivingEntity.class, bounds,
                    target -> isInside(target) && SkillService.canDamageTarget(source, target));
        }

        private boolean isInside(LivingEntity target) {
            AABB box = target.getBoundingBox();
            double halfHeight = height * 0.5D;
            if (box.maxY <= center.y - halfHeight || box.minY >= center.y + halfHeight) return false;
            double nearestX = Math.max(box.minX, Math.min(center.x, box.maxX));
            double nearestZ = Math.max(box.minZ, Math.min(center.z, box.maxZ));
            double dx = nearestX - center.x;
            double dz = nearestZ - center.z;
            return dx * dx + dz * dz <= radius * radius;
        }

        private boolean validSource() {
            return source.level() == level && source.isAlive() && !source.isRemoved() && !source.isSpectator()
                    && level.getServer().getPlayerList().getPlayer(source.getUUID()) == source;
        }

        private void sendLightning(List<Vec3> struck) {
            int count = Math.min(CherryLightningPayload.MAX_TARGETS, struck.size());
            int offset = (int) ((long) pulseIndex * CherryLightningPayload.MAX_TARGETS % struck.size());
            List<Vec3> visible = new ArrayList<>(count);
            for (int index = 0; index < count; index++) visible.add(struck.get((offset + index) % struck.size()));
            PacketDistributor.sendToPlayersNear(level, null, center.x, center.y, center.z, VISUAL_RANGE,
                    new CherryLightningPayload(center, visible));
        }
    }
}
