package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SkillEffects {
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    /** 剑气由三道横向弯弧组成。 */
    private static final int SWORD_ARC_COUNT = 3;
    /** 旧画面基准的最小深度（格），视觉倍率在此基础上应用。 */
    private static final double MIN_DEPTH = 4.0D;
    /** 原版粒子默认阻尼，用于让延迟计算与剑气的实际位移保持一致。 */
    private static final double PARTICLE_FRICTION = 0.96D;
    /** 剑气越过斩线前端时预留的保守距离比例。 */
    private static final double STATIC_LINE_FORWARD_LEAN = 0.08D;
    private static final double ARC_START_FORWARD_OFFSET = 1.8D;
    private static final double ARC_OVERLAP_FORWARD_OFFSET = 0.10D;
    /** 剑气越过斩线最前端后预留的可见距离。 */
    private static final double SWORD_ARC_LEAD_GAP = 1.0D;
    private static final int DEFAULT_PARTICLE_LIFETIME_TICKS = 50;
    /** 弯弧至少先显示的时间；剑的实际延迟会按飞行距离自动加长。 */
    private static final int MIN_SLASH_LINE_DELAY_TICKS = 6;
    /** 剑气默认在此时长内越过斩线前端，避免斩线等待过久。 */
    private static final int TARGET_SWORD_SLASH_LINE_DELAY_TICKS = 8;
    /** 所有斩线以随机顺序快速构建完成的总时长。 */
    private static final int SLASH_LINE_BUILD_TICKS = 5;
    /** 单道斩线逐段补全所用的时长。 */
    private static final int SLASH_LINE_DRAW_TICKS = 2;
    private static final int MAX_SWORD_RAY_SAMPLES = 512;
    private static final double SWORD_LINE_MIN_LENGTH_FRACTION = 0.40D;
    private static final double SWORD_LINE_MAX_LENGTH_FRACTION = 0.95D;
    private static final double SWORD_LINE_DEPTH_TILT = 0.25D;
    /** 每道剑斩线分别生成瞬间效果、附魔符文和烟花火花。 */
    private static final int SWORD_LINE_DIFFUSION_TYPE_COUNT = 3;
    private static final List<PendingSlashLines> PENDING_SLASH_LINES = new ArrayList<>();
    private static final List<PendingSlashLineBuild> PENDING_SLASH_LINE_BUILDS = new ArrayList<>();

    private SkillEffects() {
    }

    public static void spawnFromPacket(Player viewer, int entityId, byte weaponType,
                                       int length, int width, int height, int particleLifetimeTicks) {
        if (viewer == null || !viewer.level().isClientSide()) {
            return;
        }
        Entity source = viewer.level().getEntity(entityId);
        if (source == null) {
            return;
        }
        if (weaponType != SkillService.AATU_SWORD) {
            return;
        }

        double rangeScale = AatuConfig.SWORD_EFFECT_RANGE_SCALE.get();
        int safeLength = Math.max(1, (int) Math.round(Math.max(MIN_DEPTH, Math.min(length, 128)) * rangeScale));
        double safeWidth = Math.max(1.0D, Math.min(width, 32)) * rangeScale;
        double safeHeight = Math.max(1.0D, Math.min(height, 32)) * rangeScale;
        int density = AatuConfig.getParticleDensity();
        int slashLineCount = Math.max(1, Math.min(AatuConfig.SWORD_SLASH_LINE_COUNT.get(), 128));
        int safeLifetimeTicks = particleLifetimeTicks > 0
                ? Math.max(2, Math.min(particleLifetimeTicks, 600))
                : DEFAULT_PARTICLE_LIFETIME_TICKS;

        Level level = viewer.level();
        Vec3 direction = source.getLookAngle();
        if (direction.lengthSqr() < 0.0001D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            direction = direction.normalize();
        }

        Vec3 right = direction.cross(WORLD_UP);
        if (right.lengthSqr() < 0.0001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }

        Vec3 start = source.getEyePosition().add(direction.scale(1.1D));
        ParticleOptions lineParticle = ParticleTypes.END_ROD;
        int slashLineDelayTicks = 0;
        if (AatuConfig.SHOW_SWORD_ARCS.get()) {
            slashLineDelayTicks = spawnSwordArcs(level, start, direction, right, safeLength,
                    safeLifetimeTicks, rangeScale);
        }
        if (slashLineDelayTicks > 0) {
            scheduleSlashLines(level, start, direction, right, safeLength, safeWidth, safeHeight,
                    density, slashLineCount, safeLifetimeTicks, lineParticle, slashLineDelayTicks);
        } else {
            beginSlashLineBuild(level, start, direction, right, safeLength,
                    safeWidth, safeHeight, density, slashLineCount, safeLifetimeTicks, lineParticle);
        }
    }

    /** 由客户端刻事件调用，让飞行弯弧有短暂的先手表现。 */
    public static void tickClient() {
        for (Iterator<PendingSlashLines> iterator = PENDING_SLASH_LINES.iterator();
             iterator.hasNext();) {
            PendingSlashLines pending = iterator.next();
            pending.remainingTicks--;
            if (pending.remainingTicks > 0) {
                continue;
            }
            iterator.remove();
            if (pending.level.isClientSide()) {
                beginSlashLineBuild(pending.level, pending.start, pending.direction, pending.right,
                        pending.length, pending.width, pending.height, pending.density,
                        pending.slashLineCount, pending.lifetimeTicks, pending.particle);
            }
        }

        for (Iterator<PendingSlashLineBuild> iterator = PENDING_SLASH_LINE_BUILDS.iterator();
             iterator.hasNext();) {
            PendingSlashLineBuild build = iterator.next();
            if (!build.level.isClientSide()) {
                iterator.remove();
                continue;
            }
            spawnSlashLineBuildFrame(build);
            build.ageTicks++;
            if (build.ageTicks >= SLASH_LINE_BUILD_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void scheduleSlashLines(Level level, Vec3 start, Vec3 direction, Vec3 right,
                                           int length, double width, double height, int density,
                                           int slashLineCount,
                                           int lifetimeTicks, ParticleOptions particle, int delayTicks) {
        PENDING_SLASH_LINES.add(new PendingSlashLines(level, start, direction, right, length,
                width, height, density, slashLineCount, lifetimeTicks, particle,
                Math.max(1, delayTicks)));
    }

    private static void beginSlashLineBuild(Level level, Vec3 start, Vec3 direction, Vec3 right,
                                            int length, double width, double height, int density,
                                            int slashLineCount,
                                            int lifetimeTicks, ParticleOptions lineParticle) {
        PENDING_SLASH_LINE_BUILDS.add(new PendingSlashLineBuild(level, start, direction, right,
                length, width, height, density, slashLineCount, lifetimeTicks, lineParticle));
    }

    private static void spawnSlashLineBuildFrame(PendingSlashLineBuild build) {
        double sampleStep = ClientParticleHelper.lineParticleStep(build.density);
        spawnSwordSlashLineBuildFrame(build, sampleStep);
    }

    private static void spawnSwordSlashLineBuildFrame(PendingSlashLineBuild build, double sampleStep) {
        for (int index = 0; index < build.swordLines.size(); index++) {
            SwordSlashLine line = build.swordLines.get(index);
            int lineAge = build.ageTicks - line.startTick();
            if (lineAge < 0 || lineAge >= SLASH_LINE_DRAW_TICKS) continue;
            Vec3 delta = line.end().subtract(line.start());
            double segmentStart = (double) lineAge / SLASH_LINE_DRAW_TICKS;
            double segmentEnd = (double) (lineAge + 1) / SLASH_LINE_DRAW_TICKS;
            // Range scaling must not create an unbounded number of particles per line.
            double boundedStep = Math.max(sampleStep, delta.length() / MAX_SWORD_RAY_SAMPLES);
            spawnLine(build.level, build.particle, line.start().add(delta.scale(segmentStart)),
                    line.start().add(delta.scale(segmentEnd)), boundedStep, build.lifetimeTicks);
            if (lineAge == 0) {
                spawnSwordLineDiffusion(build.level, line.start(), line.end(), build.direction, build.right,
                        build.lifetimeTicks, index);
            }
        }
    }

    // Generate once so the two drawing ticks always join into the same straight line.
    private static List<SwordSlashLine> createSwordSlashLines(PendingSlashLineBuild build) {
        Random random = new Random();
        List<SwordSlashLine> lines = new ArrayList<>(build.slashLineCount);
        double halfWidth = build.width * 0.5D;
        double halfHeight = build.height * 0.5D;
        double depth = Math.max(1.0D, build.length);
        Vec3 up = build.right.cross(build.direction).normalize();
        int latestStartTick = Math.max(0, SLASH_LINE_BUILD_TICKS - SLASH_LINE_DRAW_TICKS);
        for (int index = 0; index < build.slashLineCount; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double lateral = Math.cos(angle);
            double vertical = Math.sin(angle);
            double availableLength = Math.min(build.width / Math.max(1.0E-9D, Math.abs(lateral)),
                    build.height / Math.max(1.0E-9D, Math.abs(vertical)));
            double lengthFraction = SWORD_LINE_MIN_LENGTH_FRACTION
                    + random.nextDouble() * (SWORD_LINE_MAX_LENGTH_FRACTION - SWORD_LINE_MIN_LENGTH_FRACTION);
            double halfLength = availableLength * lengthFraction * 0.5D;
            double halfLateral = lateral * halfLength;
            double halfVertical = vertical * halfLength;
            double maxHalfForward = Math.min(depth * 0.5D, availableLength * lengthFraction * SWORD_LINE_DEPTH_TILT);
            double halfForward = randomBetween(random, -maxHalfForward, maxHalfForward);
            double centerLateral = randomBetween(random, -halfWidth + Math.abs(halfLateral),
                    halfWidth - Math.abs(halfLateral));
            double centerVertical = randomBetween(random, -halfHeight + Math.abs(halfVertical),
                    halfHeight - Math.abs(halfVertical));
            double centerForward = randomBetween(random, Math.abs(halfForward), depth - Math.abs(halfForward));
            Vec3 center = build.start.add(build.direction.scale(centerForward))
                    .add(build.right.scale(centerLateral)).add(up.scale(centerVertical));
            Vec3 offset = build.right.scale(halfLateral).add(up.scale(halfVertical))
                    .add(build.direction.scale(halfForward));
            lines.add(new SwordSlashLine(center.subtract(offset), center.add(offset), random.nextInt(latestStartTick + 1)));
        }
        return lines;
    }

    private static double randomBetween(Random random, double minimum, double maximum) {
        return minimum + random.nextDouble() * (maximum - minimum);
    }

    private static void spawnLine(Level level, ParticleOptions particle, Vec3 from, Vec3 to,
                                  double sampleStep, int lifetimeTicks) {
        Vec3 delta = to.subtract(from);
        int samples = Math.max(1, (int) Math.ceil(delta.length() / sampleStep));
        for (int i = 0; i <= samples; i++) {
            Vec3 position = from.add(delta.scale((double) i / samples));
            ClientParticleHelper.addStationaryParticle(level, particle, position, lifetimeTicks);
        }
    }

    private static void spawnSwordLineDiffusion(Level level, Vec3 from, Vec3 to,
                                                Vec3 direction, Vec3 right, int lifetimeTicks,
                                                int lineIndex) {
        int configuredCount = Math.max(0, Math.min(
                AatuConfig.SWORD_LINE_DIFFUSION_PARTICLE_COUNT.get(), 64));
        if (configuredCount == 0) {
            return;
        }
        // A nonzero setting must include all three particle types on every slash line.
        int particleCount = Math.max(SWORD_LINE_DIFFUSION_TYPE_COUNT, configuredCount);
        Vec3 up = right.cross(direction).normalize();
        Vec3 delta = to.subtract(from);
        int diffusionLifetime = Math.min(lifetimeTicks, 18);
        for (int particleIndex = 0; particleIndex < particleCount; particleIndex++) {
            int particleType = particleIndex % SWORD_LINE_DIFFUSION_TYPE_COUNT;
            double seed = lineIndex * 43.0D + particleIndex * 17.0D;
            double progress = 0.10D + unitFraction(seed + 3.0D) * 0.80D;
            double angle = unitFraction(seed + 11.0D) * Math.PI * 2.0D;
            Vec3 radialDirection = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            // Keep the small particles from being hidden by the bright END_ROD line.
            Vec3 position = from.add(delta.scale(progress)).add(radialDirection.scale(0.2D));
            if (particleType == 1) {
                Vec3 displacement = radialDirection.scale(1.0D + unitFraction(seed + 29.0D) * 0.6D)
                        .add(direction.scale((unitFraction(seed + 47.0D) - 0.5D) * 0.3D));
                ClientParticleHelper.addEnchantmentDiffusionParticle(level, position, displacement);
                continue;
            }
            double speed = 0.06D + unitFraction(seed + 29.0D) * 0.05D;
            double forwardSpeed = (unitFraction(seed + 47.0D) - 0.5D) * 0.035D;
            Vec3 velocity = radialDirection.scale(speed)
                    .add(direction.scale(forwardSpeed));
            float scale = particleType == 2
                    ? (float) (0.55D + unitFraction(seed + 61.0D) * 0.3D)
                    : (float) (0.8D + unitFraction(seed + 61.0D) * 0.2D);
            // FIREWORK remains a single scattered spark, without a burst or extra trails.
            ParticleOptions particle = particleType == 2 ? ParticleTypes.FIREWORK : ParticleTypes.INSTANT_EFFECT;
            ClientParticleHelper.addMovingParticle(level, particle,
                    position, velocity, diffusionLifetime, scale);
        }
    }

    /**
     * 从玩家前方发射三道几乎重叠的横向白色弯弧。每道弧线左右展开、中心向前凸出，
     * 弯弧本体向前飞行；斩线会在三道弧线越过其前端后再出现。
     */
    private static int spawnSwordArcs(Level level, Vec3 start, Vec3 direction, Vec3 right,
                                      int staticLineLength, int lifetimeTicks, double rangeScale) {
        double arcWidth = AatuConfig.SWORD_ARC_WIDTH.get() * rangeScale;
        double configuredArcLength = AatuConfig.SWORD_ARC_LENGTH.get() * rangeScale;
        // 剑气必须比静态斩线更远，避免玩家看到剑气停在斩线区域内。
        double arcLength = Math.max(configuredArcLength,
                staticLineFrontDistance(staticLineLength) + SWORD_ARC_LEAD_GAP);
        double configuredFlightSpeed = flightSpeedForArc(AatuConfig.SWORD_ARC_FLIGHT_SPEED.get() * rangeScale,
                arcLength, lifetimeTicks);
        // 让剑气在较短的先手时间内越过斩线前端；配置速度仍可把它调得更快。
        double promptLeadSpeed = velocityForDistance(
                swordArcTravelToClearStaticLines(staticLineLength, arcWidth),
                TARGET_SWORD_SLASH_LINE_DELAY_TICKS) * 1.01D;
        double flightSpeed = Math.max(configuredFlightSpeed, promptLeadSpeed);
        Vec3 arcVelocity = direction.scale(flightSpeed);
        spawnFlyingArcs(level, start, direction, right, arcWidth, arcVelocity, lifetimeTicks);
        return swordSlashLineDelayTicks(staticLineLength, arcWidth, flightSpeed, lifetimeTicks);
    }

    private static void spawnFlyingArcs(Level level, Vec3 start, Vec3 direction, Vec3 right,
                                        double arcWidth, Vec3 arcVelocity, int lifetimeTicks) {
        Vec3 up = right.cross(direction).normalize();
        double forwardBend = Math.max(0.5D, Math.min(arcWidth * 0.24D, 4.0D));
        int samples = Math.max(16, (int) Math.ceil(arcWidth / 0.18D));
        for (int arc = 0; arc < SWORD_ARC_COUNT; arc++) {
            double overlapOffset = (arc - 1.0D) * ARC_OVERLAP_FORWARD_OFFSET;
            double verticalOffset = (arc - 1.0D) * 0.04D;
            double widthScale = 1.0D + (arc - 1.0D) * 0.035D;
            for (int sample = 0; sample <= samples; sample++) {
                double progress = (double) sample / samples;
                double horizontalProgress = progress * 2.0D - 1.0D;
                double horizontal = horizontalProgress * arcWidth * 0.5D * widthScale;
                double forwardCurve = (1.0D - horizontalProgress * horizontalProgress) * forwardBend;
                Vec3 position = start.add(direction.scale(ARC_START_FORWARD_OFFSET + overlapOffset + forwardCurve))
                        .add(right.scale(horizontal))
                        .add(up.scale(verticalOffset));
                float scale = (float) (0.75D + unitFraction(arc * 97.0D + sample * 11.0D) * 0.3D);
                ClientParticleHelper.addMovingParticle(level, ParticleTypes.END_ROD,
                        position, arcVelocity, lifetimeTicks, scale);
            }
        }
    }

    private static double velocityForDistance(double distance, int lifetimeTicks) {
        int ticks = Math.max(1, lifetimeTicks);
        double travelPerSpeed = travelDistance(1.0D, ticks);
        return distance / travelPerSpeed;
    }

    /** 计算三道剑气全部到达斩线前端后，斩线应出现的客户端 tick。 */
    private static int swordSlashLineDelayTicks(int staticLineLength, double arcWidth,
                                                double flightSpeed, int lifetimeTicks) {
        return Math.max(MIN_SLASH_LINE_DELAY_TICKS,
                ticksToTravel(swordArcTravelToClearStaticLines(staticLineLength, arcWidth),
                        flightSpeed, lifetimeTicks));
    }

    private static double swordArcTravelToClearStaticLines(int staticLineLength, double arcWidth) {
        double forwardBend = Math.max(0.5D, Math.min(arcWidth * 0.24D, 4.0D));
        // 以三道弧中最靠后的那一道为准，确保三道剑气都会在斩线前方。
        double trailingArcStart = ARC_START_FORWARD_OFFSET - ARC_OVERLAP_FORWARD_OFFSET
                + forwardBend;
        return Math.max(0.0D, staticLineFrontDistance(staticLineLength)
                + SWORD_ARC_LEAD_GAP - trailingArcStart);
    }

    private static double staticLineFrontDistance(int staticLineLength) {
        return Math.max(1.0D, staticLineLength) * (1.0D + STATIC_LINE_FORWARD_LEAN);
    }

    private static int ticksToTravel(double distance, double initialSpeed, int maxTicks) {
        if (distance <= 0.0D) {
            return 0;
        }
        int safeMaxTicks = Math.max(1, maxTicks);
        double safeSpeed = Math.max(0.01D, initialSpeed);
        for (int ticks = 1; ticks <= safeMaxTicks; ticks++) {
            if (travelDistance(safeSpeed, ticks) >= distance) {
                return ticks;
            }
        }
        return safeMaxTicks;
    }

    private static double travelDistance(double initialSpeed, int ticks) {
        int safeTicks = Math.max(1, ticks);
        return initialSpeed * (1.0D - Math.pow(PARTICLE_FRICTION, safeTicks))
                / (1.0D - PARTICLE_FRICTION);
    }

    private static double flightSpeedForArc(double configuredSpeed, double arcLength,
                                            int lifetimeTicks) {
        double safeConfiguredSpeed = Math.max(0.01D, Math.min(configuredSpeed, 100.0D));
        return Math.max(safeConfiguredSpeed, velocityForDistance(arcLength, lifetimeTicks));
    }

    private record SwordSlashLine(Vec3 start, Vec3 end, int startTick) {}

    private static final class PendingSlashLines {
        private final Level level;
        private final Vec3 start;
        private final Vec3 direction;
        private final Vec3 right;
        private final int length;
        private final double width;
        private final double height;
        private final int density;
        private final int slashLineCount;
        private final int lifetimeTicks;
        private final ParticleOptions particle;
        private int remainingTicks;

        private PendingSlashLines(Level level, Vec3 start, Vec3 direction, Vec3 right,
                                  int length, double width, double height, int density,
                                  int slashLineCount,
                                  int lifetimeTicks, ParticleOptions particle, int remainingTicks) {
            this.level = level;
            this.start = start;
            this.direction = direction;
            this.right = right;
            this.length = length;
            this.width = width;
            this.height = height;
            this.density = density;
            this.slashLineCount = slashLineCount;
            this.lifetimeTicks = lifetimeTicks;
            this.particle = particle;
            this.remainingTicks = remainingTicks;
        }
    }

    private static final class PendingSlashLineBuild {
        private final Level level;
        private final Vec3 start;
        private final Vec3 direction;
        private final Vec3 right;
        private final int length;
        private final double width;
        private final double height;
        private final int density;
        private final int slashLineCount;
        private final int lifetimeTicks;
        private final ParticleOptions particle;
        private final List<SwordSlashLine> swordLines;
        private int ageTicks;

        private PendingSlashLineBuild(Level level, Vec3 start, Vec3 direction, Vec3 right,
                                      int length, double width, double height, int density,
                                      int slashLineCount, int lifetimeTicks,
                                      ParticleOptions particle) {
            this.level = level;
            this.start = start;
            this.direction = direction;
            this.right = right;
            this.length = length;
            this.width = width;
            this.height = height;
            this.density = density;
            this.slashLineCount = slashLineCount;
            this.lifetimeTicks = lifetimeTicks;
            this.particle = particle;
            this.swordLines = createSwordSlashLines(this);
        }
    }

    private static double unitFraction(double value) {
        double valueWithNoise = Math.sin(value * 12.9898D) * 43758.5453D;
        return valueWithNoise - Math.floor(valueWithNoise);
    }
}
