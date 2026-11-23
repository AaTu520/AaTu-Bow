package cn.blockforge.aatu_bow;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class AatuSwordItem extends SwordItem {
    private static final List<String> LORE_LINES = List.of(
            "", "border", "title", "border", "",
            "effects", "charge", "charged", "cast", "damage", "protection", "fusion", "",
            "quality", "quality_value", "");

    public AatuSwordItem(Tier tier, Item.Properties properties) {
        super(tier, properties.attributes(SwordItem.createAttributes(tier, 4, -2.2F)));
    }

    @Override
    public Component getName(ItemStack stack) {
        return GradientText.swordName(super.getName(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SkillService.isAatuSwordReady(serverPlayer, stack)) {
            SkillService.tryActivate(serverPlayer, stack, SkillService.AATU_SWORD);
            return InteractionResultHolder.fail(stack);
        }
        return SkillService.tryActivate(serverPlayer, stack, SkillService.AATU_SWORD)
                ? InteractionResultHolder.sidedSuccess(stack, false)
                : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        for (String line : LORE_LINES) {
            if (line.isEmpty()) {
                tooltip.add(Component.empty());
                continue;
            }
            Component text = Component.translatable("item.aatu_bow.aatu_sword.lore." + line);
            tooltip.add(line.equals("effects") || line.equals("quality")
                    ? GradientText.loreHeading(text) : GradientText.swordLore(text));
        }
    }
}
