package cn.blockforge.aatu_bow;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AatuConfig {
    public static final int MIN_PARTICLE_DENSITY = 1;
    public static final int DEFAULT_PARTICLE_DENSITY = 6;
    public static final int MAX_PARTICLE_DENSITY = 16;
    public static final ModConfigSpec.IntValue AATU_SWORD_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue SKILL_DAMAGE_PLAYERS_AND_NEUTRAL_MOBS;
    public static final ModConfigSpec.DoubleValue SKILL_ADDITIONAL_DAMAGE_MIN;
    public static final ModConfigSpec.DoubleValue SKILL_ADDITIONAL_DAMAGE_MAX;
    public static final ModConfigSpec.IntValue SKILL_LENGTH;
    public static final ModConfigSpec.IntValue SKILL_WIDTH;
    public static final ModConfigSpec.IntValue SKILL_HEIGHT;
    public static final ModConfigSpec.DoubleValue BOW_RAIN_RADIUS;
    public static final ModConfigSpec.DoubleValue BOW_RAIN_HEIGHT;
    public static final ModConfigSpec.IntValue BOW_CHAIN_HIT_COUNT;
    public static final ModConfigSpec.IntValue BOW_CHAIN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue FUSION_LEVEL_COST;
    public static final ModConfigSpec.DoubleValue BOW_EFFECT_RANGE_SCALE;
    public static final ModConfigSpec.DoubleValue SWORD_EFFECT_RANGE_SCALE;
    public static final ModConfigSpec.IntValue PARTICLE_DENSITY;
    public static final ModConfigSpec.IntValue SWORD_SLASH_LINE_COUNT;
    public static final ModConfigSpec.IntValue BOW_RAIN_PARTICLE_COUNT;
    public static final ModConfigSpec.IntValue BOW_LIGHTNING_PARTICLE_COUNT;
    public static final ModConfigSpec.IntValue LINGERING_LINE_COUNT;
    public static final ModConfigSpec.IntValue SWORD_LINE_DIFFUSION_PARTICLE_COUNT;
    public static final ModConfigSpec.DoubleValue SWORD_ARC_FLIGHT_SPEED;
    public static final ModConfigSpec.DoubleValue SWORD_ARC_LENGTH;
    public static final ModConfigSpec.DoubleValue SWORD_ARC_WIDTH;
    public static final ModConfigSpec.BooleanValue SHOW_SWORD_ARCS;
    public static final ModConfigSpec.DoubleValue AATU_BOW_PARTICLE_LIFETIME_SECONDS;
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("AaTu bow and sword skills. Distances are in blocks; cooldowns are in ticks.").push("skill");
        AATU_SWORD_COOLDOWN_TICKS = builder
                .comment("Sword skill cooldown in ticks.")
                .defineInRange("cooldownTicks", 5, 1, 2400);
        SKILL_DAMAGE_PLAYERS_AND_NEUTRAL_MOBS = builder
                .comment("Allow bow, sword and fused skills to damage players and neutral or friendly creatures.",
                        "When disabled, skills target enemy mobs and protect player-owned pets.")
                .define("damagePlayersAndNeutralMobs", false);
        SKILL_ADDITIONAL_DAMAGE_MIN = builder
                .comment("Minimum random bonus damage multiplier, applied to the weapon's attack damage.")
                .defineInRange("additionalDamageMin", 1.0D, 1.0D, 99.0D);
        SKILL_ADDITIONAL_DAMAGE_MAX = builder
                .comment("Maximum random bonus damage multiplier, applied to the weapon's attack damage.")
                .defineInRange("additionalDamageMax", 50.0D, 1.0D, 99.0D);
        SKILL_LENGTH = builder
                .comment("Sword skill damage corridor length along the player's view direction, in blocks.")
                .defineInRange("length", 64, 1, 128);
        SKILL_WIDTH = builder
                .comment("Sword skill damage corridor width, in blocks.")
                .defineInRange("width", 16, 1, 32);
        SKILL_HEIGHT = builder
                .comment("Sword skill damage corridor height, in blocks.")
                .defineInRange("height", 16, 1, 32);
        builder.pop();
        builder.comment("Bow cherry rain and chain hits.").push("bowRain");
        BOW_RAIN_RADIUS = builder
                .comment("Cherry rain radius around the arrow impact, in blocks.")
                .defineInRange("radius", 8.0D, 1.0D, 32.0D);
        BOW_RAIN_HEIGHT = builder
                .comment("Total height of the damage area around the arrow impact, in blocks.")
                .defineInRange("height", 16.0D, 1.0D, 64.0D);
        BOW_CHAIN_HIT_COUNT = builder
                .comment("Number of chain hits after the initial cherry rain hit.")
                .defineInRange("chainHitCount", 10, 1, 64);
        BOW_CHAIN_INTERVAL_TICKS = builder
                .comment("Interval between chain hits, in ticks. 20 ticks equal about one second.")
                .defineInRange("chainIntervalTicks", 2, 1, 40);
        builder.pop();
        builder.comment("Anvil skill fusion.").push("fusion");
        FUSION_LEVEL_COST = builder
                .comment("Experience levels required for one anvil fusion. 0 makes fusion free.")
                .defineInRange("fusionLevelCost", 80, 0, 1000);
        builder.pop();
        // Keep the section name so existing configuration values remain valid.
        // As part of the SERVER config, its values are synchronized to connected clients.
        builder.comment("Particle visuals.").push("client");
        BOW_EFFECT_RANGE_SCALE = builder
                .comment("Bow particle effect range multiplier. 1 keeps the original visual size.")
                .defineInRange("bowEffectRangeScale", 1.0D, 0.25D, 2.0D);
        SWORD_EFFECT_RANGE_SCALE = builder
                .comment("Sword particle effect range multiplier. 1 keeps the original visual size.")
                .defineInRange("swordEffectRangeScale", 1.0D, 0.25D, 2.0D);
        PARTICLE_DENSITY = builder
                .comment("Particle density for sword slash lines and target-attached rays. Higher values make denser lines and increase client load.")
                .defineInRange("particleDensity", DEFAULT_PARTICLE_DENSITY, MIN_PARTICLE_DENSITY, MAX_PARTICLE_DENSITY);
        SWORD_SLASH_LINE_COUNT = builder
                .translation("config.aatu_bow.sword_slash_line_count")
                .comment("Number of slash lines generated inside the sword skill corridor.")
                .defineInRange("slashLineCount", 32, 1, 128);
        BOW_RAIN_PARTICLE_COUNT = builder
                .translation("config.aatu_bow.bow_rain_particle_count")
                .comment("Petals spawned per tick for each bow cherry rain. Higher values increase client load.")
                .defineInRange("bowRainParticleCount", 16, 1, 32);
        BOW_LIGHTNING_PARTICLE_COUNT = builder
                .translation("config.aatu_bow.bow_lightning_particle_count")
                .comment("Cherry particles between each pair of bow chain lightning points. 0 hides these particles.")
                .defineInRange("bowLightningParticleCount", 31, 0, 128);
        LINGERING_LINE_COUNT = builder
                .comment("Number of particle rays attached to each surviving target after a skill hit.")
                .defineInRange("lingeringLineCount", 3, 1, 32);
        SWORD_LINE_DIFFUSION_PARTICLE_COUNT = builder
                .comment("Diffusion particles per sword slash line. 0 disables diffusion; 1 or 2 uses 3 to include all particle types. Higher values increase client load.")
                .defineInRange("swordLineDiffusionParticleCount", 32, 0, 64);
        SWORD_ARC_FLIGHT_SPEED = builder
                .comment("Base speed of the three white sword arcs, in blocks per tick. Raised when needed to pass the slash lines first.")
                .defineInRange("swordArcFlightSpeed", 4.5D, 0.01D, 100.0D);
        SWORD_ARC_LENGTH = builder
                .comment("Travel distance of the three white sword arcs, in blocks. Raised when needed to pass the slash lines.")
                .defineInRange("swordArcLength", 64.0D, 1.0D, 128.0D);
        SWORD_ARC_WIDTH = builder
                .comment("Width of the three nearly overlapping white sword arcs, in blocks.")
                .defineInRange("swordArcWidth", 23.3D, 0.5D, 64.0D);
        SHOW_SWORD_ARCS = builder
                .comment("Show the three white sword arcs.")
                .define("showSwordArcs", true);
        AATU_BOW_PARTICLE_LIFETIME_SECONDS = builder
                .comment("Duration of the bow cherry rain visuals, in seconds.")
                .defineInRange("aatuBowParticleLifetimeSeconds", 4.0D, 0.1D, 30.0D);
        builder.pop();
        SPEC = builder.build();
    }

    public static int getParticleDensity() {
        return Math.max(MIN_PARTICLE_DENSITY, Math.min(PARTICLE_DENSITY.get(), MAX_PARTICLE_DENSITY));
    }

    private AatuConfig() {
    }
}
