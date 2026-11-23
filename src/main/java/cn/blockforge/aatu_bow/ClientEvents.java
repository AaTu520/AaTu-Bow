package cn.blockforge.aatu_bow;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = AatuBow.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SkillEffects.tickClient();
        ClientLingeringEffects.tick();
        ClientCherryEffects.tick();
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (FusionService.hasFusedBowSkill(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("item.aatu_bow.fused_bow_skill")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (FusionService.hasFusedSwordSkill(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("item.aatu_bow.fused_sword_skill")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDefaultWeaponNameTooltip(ItemTooltipEvent event) {
        if (!event.getToolTip().isEmpty()) {
            // The first line is the completed name, including modifier prefixes and wrappers.
            // Tooltip generation has already run Modifiers-Neo's getHoverName changes here.
            Component name = event.getToolTip().get(0);
            event.getToolTip().set(0, GradientText.defaultWeaponName(event.getItemStack(), name));
        }
    }
}
