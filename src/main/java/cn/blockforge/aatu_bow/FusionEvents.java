package cn.blockforge.aatu_bow;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Handles anvil fusion and skill activation for weapons carrying a fusion marker. */
public final class FusionEvents {
    private FusionEvents() {
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!event.getOutput().isEmpty()) {
            return;
        }

        ItemStack target = event.getLeft();
        ItemStack catalyst = event.getRight();
        String skillType = null;
        if (catalyst.is(AatuBow.AATU_BOW.get()) && FusionService.canFuseBow(target)) {
            skillType = FusionService.BOW_SKILL;
        } else if (catalyst.is(AatuBow.AATU_SWORD.get()) && FusionService.canFuseSword(target)) {
            skillType = FusionService.SWORD_SKILL;
        }
        if (skillType == null) {
            return;
        }

        ItemStack output = FusionService.createFusedOutput(target, skillType);
        applyRequestedName(event, target, output);
        event.setOutput(output);
        event.setCost(AatuConfig.FUSION_LEVEL_COST.get());
        event.setMaterialCost(1);
    }

    public static void onArrowNock(ArrowNockEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !FusionService.hasFusedBowSkill(event.getBow())
                || event.hasAmmo()) {
            return;
        }

        Player player = event.getEntity();
        player.startUsingItem(event.getHand());
        event.setAction(InteractionResultHolder.sidedSuccess(event.getBow(),
                event.getLevel().isClientSide()));
    }

    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack weapon = event.getItem();
        if (!(weapon.getItem() instanceof BowItem) || !FusionService.hasBowSkill(weapon)) {
            return;
        }

        int chargedTicks = weapon.getUseDuration(player) - event.getDuration();
        if (chargedTicks >= 20 && player.getProjectile(weapon).isEmpty()) {
            CherryProjectileService.scheduleAmmoFreeArrow(event, player, weapon);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !FusionService.hasFusedSwordSkill(event.getItemStack())
                || ModList.get().isLoaded("spell_engine")) {
            return;
        }

        // Without Spell Engine, retain the original fallback. With it, the hotbar owns
        // activation so the host weapon's right-click cannot also fire the fused skill.
        if (event.getEntity() instanceof ServerPlayer player
                && SkillService.isAatuSwordReady(player, event.getItemStack())
                && !SkillService.isAatuSwordOnCooldown(player, event.getItemStack())) {
            SkillService.tryActivate(player, event.getItemStack(), SkillService.AATU_SWORD);
        }
    }

    private static void applyRequestedName(AnvilUpdateEvent event, ItemStack target, ItemStack output) {
        String requestedName = event.getName();
        if (requestedName == null) {
            return;
        }
        if (requestedName.isBlank()) {
            if (target.has(DataComponents.CUSTOM_NAME)) {
                output.remove(DataComponents.CUSTOM_NAME);
            }
        } else if (!requestedName.equals(target.getHoverName().getString())) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(requestedName));
        }
    }
}
