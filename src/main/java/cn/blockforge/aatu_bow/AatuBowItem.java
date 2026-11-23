package cn.blockforge.aatu_bow;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class AatuBowItem extends BowItem {
    private static final List<String> LORE_LINES = List.of(
            "", "border", "title", "border", "",
            "effects", "charge", "cast", "damage", "protection", "fusion", "",
            "quality", "quality_value", "");

    public AatuBowItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return GradientText.bowName(super.getName(stack));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack projectile = player.getProjectile(stack);
        if (!projectile.isEmpty() || player.getAbilities().instabuild) {
            return super.use(level, player, hand);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
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
            Component text = Component.translatable("item.aatu_bow.aatu_bow.lore." + line);
            tooltip.add(line.equals("effects") || line.equals("quality")
                    ? GradientText.loreHeading(text) : GradientText.bowLore(text));
        }
    }
}
