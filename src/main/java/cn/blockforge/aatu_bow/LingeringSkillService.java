package cn.blockforge.aatu_bow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side target bookkeeping and damage only; all ray geometry is client-side. */
public final class LingeringSkillService {
    public static final int HIT_COUNT = 10;
    public static final int HIT_INTERVAL_TICKS = 10;
    public static final int DURATION_TICKS = HIT_COUNT * HIT_INTERVAL_TICKS;

    private static final Map<ServerLevel, Map<MarkKey, Mark>> ACTIVE = new IdentityHashMap<>();

    private LingeringSkillService() {
    }

    public static void mark(ServerLevel level, ServerPlayer source, LivingEntity target,
                            ItemStack weapon, byte weaponType, float damage) {
        if (!SkillService.canDamageTarget(source, target) || damage <= 0.0F) {
            return;
        }
        registerMark(level, source, target, weapon, weaponType, damage, true);
    }

    static void markBowProtection(ServerLevel level, ServerPlayer source, LivingEntity target, ItemStack weapon) {
        if (!SkillService.canDamageTarget(source, target)) return;
        registerMark(level, source, target, weapon, SkillService.AATU_BOW, 0.0F, false);
    }

    private static void registerMark(ServerLevel level, ServerPlayer source, LivingEntity target,
                                     ItemStack weapon, byte weaponType, float damage, boolean followupHits) {
        MarkKey key = new MarkKey(target.getUUID(), source.getUUID(), weaponType);
        Mark mark = new Mark(key, source, target, weapon.copy(), damage,
                AatuConfig.LINGERING_LINE_COUNT.get(), level.getGameTime(), followupHits);
        // One active mark per caster, target and skill. Recasting refreshes, never stacks it.
        ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>()).put(key, mark);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, mark.payload(DURATION_TICKS));
    }

    /** Returns whether this exact player currently has either skill's mark on the target. */
    public static boolean isMarkedBy(LivingEntity target, ServerPlayer source) {
        if (!(target.level() instanceof ServerLevel level) || source.level() != level) {
            return false;
        }
        Map<MarkKey, Mark> marks = ACTIVE.get(level);
        if (marks == null) {
            return false;
        }
        long now = level.getGameTime();
        Mark bowMark = marks.get(new MarkKey(target.getUUID(), source.getUUID(), SkillService.AATU_BOW));
        Mark swordMark = marks.get(new MarkKey(target.getUUID(), source.getUUID(), SkillService.AATU_SWORD));
        return isActiveMark(bowMark, level, now) || isActiveMark(swordMark, level, now);
    }

    private static boolean isActiveMark(Mark mark, ServerLevel level, long now) {
        return mark != null && mark.expiresAt >= now && mark.isValid(level);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Map<MarkKey, Mark> marks = ACTIVE.get(level);
        if (marks == null) {
            return;
        }
        long now = level.getGameTime();
        List<Mark> due = new ArrayList<>();
        for (Iterator<Mark> iterator = marks.values().iterator(); iterator.hasNext();) {
            Mark mark = iterator.next();
            if (!mark.isValid(level) || now > mark.expiresAt) {
                iterator.remove();
                stop(level, mark);
            } else if (mark.remainingHits > 0 && now >= mark.nextHitAt) {
                due.add(mark);
            }
        }
        // Damage events may trigger other mods. Do not hold a map iterator during hurt().
        for (Mark mark : due) {
            if (marks.get(mark.key) != mark) {
                continue;
            }
            if (mark.isValid(level)) {
                SkillService.applySkillDamage(level, mark.source, mark.target, mark.weapon,
                        mark.damageSource, mark.damage);
                mark.remainingHits--;
                mark.nextHitAt += HIT_INTERVAL_TICKS;
            }
            if (((mark.followupHits && mark.remainingHits <= 0) || !mark.isValid(level))
                    && marks.remove(mark.key, mark)) {
                stop(level, mark);
            }
        }
        if (marks.isEmpty()) {
            ACTIVE.remove(level);
        }
    }

    /** Players entering tracking range midway through a mark also see its remaining effect. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)
                || !(event.getTarget().level() instanceof ServerLevel level)) {
            return;
        }
        Map<MarkKey, Mark> marks = ACTIVE.get(level);
        if (marks == null) {
            return;
        }
        long now = level.getGameTime();
        for (Mark mark : marks.values()) {
            if (mark.target == event.getTarget() && mark.isValid(level) && mark.expiresAt > now) {
                PacketDistributor.sendToPlayer(viewer, mark.payload((int) (mark.expiresAt - now)));
            }
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ACTIVE.remove(level);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private static void stop(ServerLevel level, Mark mark) {
        if (mark.target.level() == level && !mark.target.isRemoved()) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(mark.target, mark.payload(0));
        }
    }

    static DamageSource bypassingCooldown(ServerPlayer source, ItemStack weapon) {
        return new LingeringDamageSource(source, weapon);
    }

    private record MarkKey(UUID targetUuid, UUID sourceUuid, byte weaponType) {
    }

    private static final class Mark {
        private final MarkKey key;
        private final ServerPlayer source;
        private final LivingEntity target;
        private final ItemStack weapon;
        private final float damage;
        private final int lineCount;
        private final boolean followupHits;
        private final DamageSource damageSource;
        private final long expiresAt;
        private long nextHitAt;
        private int remainingHits;

        private Mark(MarkKey key, ServerPlayer source, LivingEntity target, ItemStack weapon,
                     float damage, int lineCount, long now, boolean followupHits) {
            this.key = key;
            this.source = source;
            this.target = target;
            this.weapon = weapon;
            this.damage = damage;
            this.lineCount = lineCount;
            this.followupHits = followupHits;
            this.remainingHits = followupHits ? HIT_COUNT : 0;
            this.damageSource = new LingeringDamageSource(source, weapon);
            this.expiresAt = now + DURATION_TICKS;
            this.nextHitAt = now + HIT_INTERVAL_TICKS;
        }

        private boolean isValid(ServerLevel level) {
            return source.level() == level && target.level() == level
                    && source.isAlive() && !source.isRemoved() && !source.isSpectator()
                    && level.getServer().getPlayerList().getPlayer(source.getUUID()) == source
                    && SkillService.canDamageTarget(source, target);
        }

        private LingeringEffectPayload payload(int ticks) {
            return new LingeringEffectPayload(target.getId(), key.targetUuid(), key.sourceUuid(),
                    key.weaponType(), ticks, lineCount);
        }
    }

    /** Preserve player-attack behavior and the cast weapon; bypass only the hurt cooldown. */
    private static final class LingeringDamageSource extends DamageSource {
        private final ItemStack weapon;

        private LingeringDamageSource(ServerPlayer source, ItemStack weapon) {
            super(source.damageSources().playerAttack(source).typeHolder(), source);
            this.weapon = weapon;
        }

        @Override
        public boolean is(TagKey<DamageType> tag) {
            return DamageTypeTags.BYPASSES_COOLDOWN.equals(tag) || super.is(tag);
        }

        @Override
        public ItemStack getWeaponItem() {
            return weapon;
        }
    }
}
