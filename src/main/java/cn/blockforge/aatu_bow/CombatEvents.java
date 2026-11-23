package cn.blockforge.aatu_bow;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

public final class CombatEvents {
    private CombatEvents() {
    }

    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player) {
            return;
        }
        if (event.getNewDamage() <= 0.0F
                || player.getPersistentData().getBoolean(SkillService.SKILL_DAMAGE_FLAG)) {
            return;
        }
        ItemStack weapon = player.getWeaponItem();
        if (FusionService.hasSwordSkill(weapon)) {
            SkillService.recordAatuSwordHit(player, weapon);
        }
    }

    /** The mark grants its caster 25% less final damage from that marked entity. */
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer recipient)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker)
                || event.getNewDamage() <= 0.0F
                || !LingeringSkillService.isMarkedBy(attacker, recipient)) {
            return;
        }
        event.setNewDamage(event.getNewDamage() * 0.75F);
    }
}
