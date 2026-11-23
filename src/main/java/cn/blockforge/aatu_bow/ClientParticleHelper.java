package cn.blockforge.aatu_bow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 仅由客户端技能数据包处理流程调用。 */
public final class ClientParticleHelper {
    static final int LEGACY_MAX_LINE_DENSITY = 4;
    private static final double LEGACY_DENSE_LINE_STEP = 0.14D;

    private ClientParticleHelper() {
    }

    public static double lineParticleStep(int density) {
        // Preserve the original four presets, then increase sampling proportionally.
        return switch (density) {
            case 1 -> 0.34D;
            case 2 -> 0.24D;
            case 3 -> 0.18D;
            default -> LEGACY_DENSE_LINE_STEP * LEGACY_MAX_LINE_DENSITY / density;
        };
    }

    public static void addStationaryParticle(Level level, ParticleOptions particleOptions,
                                             Vec3 position, int lifetimeTicks) {
        addParticle(level, particleOptions, position, Vec3.ZERO, lifetimeTicks, 1.0F);
    }

    public static void addMovingParticle(Level level, ParticleOptions particleOptions,
                                         Vec3 position, Vec3 velocity, int lifetimeTicks, float scale) {
        addParticle(level, particleOptions, position, velocity, lifetimeTicks, scale);
    }

    public static void addEnchantmentDiffusionParticle(Level level, Vec3 position, Vec3 displacement) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level != level) {
            return;
        }
        // ENCHANT takes a full offset from its destination, not a per-tick velocity.
        // Reverse that offset so the glyph starts beside the slash and travels outward.
        Vec3 destination = position.add(displacement);
        Particle particle = minecraft.particleEngine.createParticle(ParticleTypes.ENCHANT,
                destination.x, destination.y, destination.z,
                -displacement.x, -displacement.y, -displacement.z);
        if (particle != null) {
            particle.setLifetime(32);
            particle.scale(2.0F);
            particle.setColor(0.9F, 0.95F, 1.0F);
        }
    }

    private static void addParticle(Level level, ParticleOptions particleOptions,
                                    Vec3 position, Vec3 velocity, int lifetimeTicks, float scale) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level) {
            return;
        }
        Particle particle = minecraft.particleEngine.createParticle(
                particleOptions,
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z);
        if (particle != null) {
            particle.setParticleSpeed(velocity.x, velocity.y, velocity.z);
            particle.setLifetime(Math.max(1, lifetimeTicks));
            particle.scale(Math.max(0.1F, scale));
        }
    }
}
