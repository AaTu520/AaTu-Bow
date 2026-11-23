package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Locally anchors a bounded set of particles to each marked entity as it moves. */
public final class ClientLingeringEffects {
    private static final int MAX_PARTICLES_PER_MARK = 512;
    private static final int LEGACY_MAX_SAMPLES_PER_LINE = 48;
    private static final int ENTITY_SPAWN_GRACE_TICKS = 20;
    private static final Map<MarkKey, AttachedMark> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private ClientLingeringEffects() {
    }

    public static void handle(Player viewer, LingeringEffectPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (viewer == null || minecraft.level == null || viewer.level() != minecraft.level) {
            return;
        }
        if (payload.weaponType() != SkillService.AATU_BOW && payload.weaponType() != SkillService.AATU_SWORD) {
            return;
        }
        useLevel(minecraft.level);
        MarkKey key = new MarkKey(payload.targetUuid(), payload.sourceUuid(), payload.weaponType());
        AttachedMark previous = ACTIVE.remove(key);
        if (previous != null) {
            previous.removeParticles();
        }
        if (payload.remainingTicks() > 0) {
            ACTIVE.put(key, new AttachedMark(payload.targetId(), key,
                    Math.min(payload.remainingTicks(), LingeringSkillService.DURATION_TICKS),
                    Math.max(1, Math.min(payload.lineCount(), 32))));
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        useLevel(minecraft.level);
        if (activeLevel == null || ACTIVE.isEmpty() || minecraft.isPaused()) {
            return;
        }
        for (Iterator<AttachedMark> iterator = ACTIVE.values().iterator(); iterator.hasNext();) {
            AttachedMark mark = iterator.next();
            if (!mark.tick(minecraft)) {
                mark.removeParticles();
                iterator.remove();
            }
        }
    }

    private static void useLevel(ClientLevel level) {
        if (activeLevel != level) {
            ACTIVE.values().forEach(AttachedMark::removeParticles);
            ACTIVE.clear();
            activeLevel = level;
        }
    }

    private record MarkKey(UUID targetUuid, UUID sourceUuid, byte weaponType) {
    }

    private static final class AttachedMark {
        private final int targetId;
        private final MarkKey key;
        private final int lineCount;
        private final double rangeScale;
        private final List<AttachedPoint> points = new ArrayList<>();
        private final ParticleOptions particleType;
        private int remainingTicks;
        private int missingEntityTicks;
        private boolean resolvedEntity;

        private AttachedMark(int targetId, MarkKey key, int remainingTicks, int lineCount) {
            this.targetId = targetId;
            this.key = key;
            this.remainingTicks = remainingTicks;
            this.lineCount = lineCount;
            this.rangeScale = key.weaponType() == SkillService.AATU_BOW
                    ? AatuConfig.BOW_EFFECT_RANGE_SCALE.get() : AatuConfig.SWORD_EFFECT_RANGE_SCALE.get();
            this.particleType = key.weaponType() == SkillService.AATU_BOW
                    ? ParticleTypes.CHERRY_LEAVES : ParticleTypes.END_ROD;
        }

        private boolean tick(Minecraft minecraft) {
            if (remainingTicks-- <= 0) {
                return false;
            }
            Entity entity = activeLevel.getEntity(targetId);
            if (entity == null) {
                // A tracking notification can reach the client just before its entity spawn.
                return !resolvedEntity && ++missingEntityTicks <= ENTITY_SPAWN_GRACE_TICKS;
            }
            if (!(entity instanceof LivingEntity target) || !key.targetUuid().equals(target.getUUID())
                    || !target.isAlive() || target.isRemoved() || target.isSpectator()) {
                return false;
            }
            resolvedEntity = true;
            double width = Math.max(0.8D, target.getBbWidth()) * rangeScale;
            double height = Math.max(1.0D, target.getBbHeight()) * rangeScale;
            if (points.isEmpty()) {
                createPoints(width, height);
            }
            Vec3 center = target.getBoundingBox().getCenter();
            for (AttachedPoint point : points) {
                Vec3 offset = point.offset;
                Vec3 position = center.add(offset.x * width, offset.y * height, offset.z * width);
                if (point.particle == null || !point.particle.isAlive()) {
                    point.particle = minecraft.particleEngine.createParticle(particleType,
                            position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
                    if (point.particle != null) {
                        point.particle.setLifetime(remainingTicks + 2);
                        point.particle.scale(0.8F);
                    }
                }
                if (point.particle != null) {
                    // Reuse existing particles; no per-tick burst and no server-side positions.
                    point.particle.setParticleSpeed(0.0D, 0.0D, 0.0D);
                    point.particle.setPos(position.x, position.y, position.z);
                }
            }
            return true;
        }

        private void createPoints(double width, double height) {
            int density = AatuConfig.getParticleDensity();
            double step = ClientParticleHelper.lineParticleStep(density);
            // Let higher densities use more of the existing per-mark particle budget.
            int perLineLimit = LEGACY_MAX_SAMPLES_PER_LINE
                    * Math.max(ClientParticleHelper.LEGACY_MAX_LINE_DENSITY, density)
                    / ClientParticleHelper.LEGACY_MAX_LINE_DENSITY;
            int maxSamples = Math.min(perLineLimit, MAX_PARTICLES_PER_MARK / lineCount - 1);
            double seed = key.targetUuid().hashCode() ^ key.sourceUuid().hashCode();
            for (int line = 0; line < lineCount; line++) {
                double angle = Math.PI * 2.0D * line / lineCount + unitFraction(seed + line * 17.0D);
                double bottomAngle = angle + Math.PI * (0.6D + unitFraction(seed + line * 31.0D) * 0.8D);
                Vec3 top = new Vec3(Math.cos(angle) * 0.75D, 0.65D, Math.sin(angle) * 0.75D);
                Vec3 bottom = new Vec3(Math.cos(bottomAngle) * 0.75D, -0.65D,
                        Math.sin(bottomAngle) * 0.75D);
                Vec3 delta = bottom.subtract(top);
                double length = new Vec3(delta.x * width, delta.y * height, delta.z * width).length();
                int samples = Math.min(maxSamples, Math.max(2, (int) Math.ceil(length / step)));
                for (int sample = 0; sample <= samples; sample++) {
                    points.add(new AttachedPoint(top.add(delta.scale((double) sample / samples))));
                }
            }
        }

        private void removeParticles() {
            for (AttachedPoint point : points) {
                if (point.particle != null) {
                    point.particle.remove();
                }
            }
            points.clear();
        }
    }

    private static final class AttachedPoint {
        private final Vec3 offset;
        private Particle particle;

        private AttachedPoint(Vec3 offset) {
            this.offset = offset;
        }
    }

    private static double unitFraction(double value) {
        double noise = Math.sin(value * 12.9898D) * 43758.5453D;
        return noise - Math.floor(noise);
    }
}
