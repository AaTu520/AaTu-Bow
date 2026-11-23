package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Particle lightning avoids real lightning entities and their unintended damage. */
public final class ClientCherryEffects {
    private static final int MAX_ACTIVE_RAINS = 16;
    private static final int LIGHTNING_SEGMENTS = 6;
    private static final int LIGHTNING_PARTICLE_LIFETIME_TICKS = 3;
    private static final List<Rain> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;

    private ClientCherryEffects() {}

    public static void handleRain(Player viewer, CherryRainPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (viewer == null || minecraft.level == null || viewer.level() != minecraft.level) return;
        useLevel(minecraft.level);
        double radius = Math.max(1.0D, Math.min(32.0D, payload.radius()));
        double height = Math.max(1.0D, Math.min(64.0D, payload.height()));
        double rangeScale = AatuConfig.BOW_EFFECT_RANGE_SCALE.get();
        int lifetime = Math.max(2, Math.min(600, payload.lifetimeTicks()));
        if (ACTIVE.size() >= MAX_ACTIVE_RAINS) ACTIVE.remove(0);
        double rainHeight = Math.max(4.0D, Math.min(16.0D, height)) * rangeScale;
        ACTIVE.add(new Rain(payload.center(), radius * rangeScale, rainHeight, lifetime));
    }

    public static void handleLightning(Player viewer, CherryLightningPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (viewer == null || minecraft.level == null || viewer.level() != minecraft.level) return;
        int particleCount = Math.max(0, Math.min(128, AatuConfig.BOW_LIGHTNING_PARTICLE_COUNT.get()));
        if (particleCount == 0) return;
        Random random = new Random();
        Vec3 previous = payload.origin().add(0.0D, 0.5D, 0.0D);
        for (Vec3 target : payload.targets()) {
            lightning(minecraft.level, previous, target, random, particleCount);
            previous = target;
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        useLevel(minecraft.level);
        if (activeLevel == null || minecraft.isPaused()) return;
        for (Iterator<Rain> iterator = ACTIVE.iterator(); iterator.hasNext();) {
            Rain rain = iterator.next();
            rain.spawn(activeLevel);
            if (--rain.remainingTicks <= 0) iterator.remove();
        }
    }

    private static void useLevel(ClientLevel level) {
        if (activeLevel != level) {
            ACTIVE.clear();
            activeLevel = level;
        }
    }

    private static void lightning(ClientLevel level, Vec3 from, Vec3 to, Random random, int particleCount) {
        Vec3 delta = to.subtract(from);
        if (delta.lengthSqr() < 0.0001D) return;
        Vec3 axis = delta.normalize();
        Vec3 side = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 0.0001D) side = axis.cross(new Vec3(1.0D, 0.0D, 0.0D));
        side = side.normalize();
        Vec3 up = side.cross(axis).normalize();
        Vec3[] path = new Vec3[LIGHTNING_SEGMENTS + 1];
        path[0] = from;
        for (int segment = 1; segment <= LIGHTNING_SEGMENTS; segment++) {
            Vec3 next = from.add(delta.scale((double) segment / LIGHTNING_SEGMENTS));
            if (segment < LIGHTNING_SEGMENTS) {
                next = next.add(side.scale((random.nextDouble() - 0.5D) * 0.8D))
                        .add(up.scale((random.nextDouble() - 0.5D) * 0.8D));
            }
            path[segment] = next;
        }
        for (int sample = 0; sample < particleCount; sample++) {
            double progress = particleCount == 1 ? LIGHTNING_SEGMENTS
                    : (double) sample * LIGHTNING_SEGMENTS / (particleCount - 1);
            int segment = Math.min((int) progress, LIGHTNING_SEGMENTS - 1);
            Vec3 start = path[segment];
            Vec3 position = start.add(path[segment + 1].subtract(start).scale(progress - segment));
            ClientParticleHelper.addStationaryParticle(level, ParticleTypes.CHERRY_LEAVES, position,
                    LIGHTNING_PARTICLE_LIFETIME_TICKS);
        }
    }

    private static final class Rain {
        private final Vec3 center;
        private final double radius;
        private final double height;
        private final Random random = new Random();
        private int remainingTicks;

        private Rain(Vec3 center, double radius, double height, int remainingTicks) {
            this.center = center;
            this.radius = radius;
            this.height = height;
            this.remainingTicks = remainingTicks;
        }

        private void spawn(ClientLevel level) {
            int particles = Math.max(1, Math.min(32, AatuConfig.BOW_RAIN_PARTICLE_COUNT.get()));
            for (int index = 0; index < particles; index++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(random.nextDouble()) * radius;
                Vec3 position = center.add(Math.cos(angle) * distance,
                        1.0D + random.nextDouble() * height, Math.sin(angle) * distance);
                Vec3 velocity = new Vec3((random.nextDouble() - 0.5D) * 0.03D,
                        -0.10D - random.nextDouble() * 0.14D, (random.nextDouble() - 0.5D) * 0.03D);
                ClientParticleHelper.addMovingParticle(level, ParticleTypes.CHERRY_LEAVES, position, velocity,
                        40, (float) (0.75D + random.nextDouble() * 0.4D));
            }
        }
    }
}
