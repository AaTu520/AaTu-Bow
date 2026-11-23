package cn.blockforge.aatu_bow;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;
import net.minecraft.world.item.ItemStack;

/** Styles default weapon names and lore without changing item data. */
public final class GradientText {
    private GradientText() {
    }

    public static Component bowName(Component text) {
        return apply(text, 0xFF69B4, 0xFFC9DE);
    }

    public static Component swordName(Component text) {
        return apply(text, 0x2F80ED, 0xA6E9FF);
    }

    public static Component bowLore(Component text) {
        return bowName(plainLore(text));
    }

    public static Component swordLore(Component text) {
        return swordName(plainLore(text));
    }

    public static Component loreHeading(Component text) {
        return plainLore(text).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent plainLore(Component text) {
        // Lore uses its own uniform style, independent of translated text formatting.
        return Component.literal(StringDecomposer.getPlainText(text))
                .withStyle(style -> style.withBold(false).withItalic(false));
    }

    public static Component defaultWeaponName(ItemStack stack, Component text) {
        // Only CUSTOM_NAME represents an anvil rename. ITEM_NAME is an alternate base name.
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return text;
        }
        if (stack.getItem() instanceof AatuBowItem) {
            return bowName(text);
        }
        if (stack.getItem() instanceof AatuSwordItem) {
            return swordName(text);
        }
        return text;
    }

    public static Component apply(Component text, int startColor, int endColor) {
        // Count rendered characters, excluding embedded legacy formatting codes.
        String plainText = StringDecomposer.getPlainText(text);
        int steps = Math.max(1, plainText.codePointCount(0, plainText.length()) - 1);
        MutableComponent result = Component.empty().withStyle(style -> style.withItalic(false));
        int[] index = {0};
        // Traverse translations, wrappers and siblings with their resolved styles intact.
        StringDecomposer.iterateFormatted(text, Style.EMPTY.withItalic(false), (offset, style, codePoint) -> {
            double progress = (double) index[0]++ / steps;
            int red = interpolateChannel(startColor >> 16 & 0xFF, endColor >> 16 & 0xFF, progress);
            int green = interpolateChannel(startColor >> 8 & 0xFF, endColor >> 8 & 0xFF, progress);
            int blue = interpolateChannel(startColor & 0xFF, endColor & 0xFF, progress);
            int color = red << 16 | green << 8 | blue;
            // Explicit child colors take precedence over the tooltip's rarity color.
            result.append(Component.literal(new String(Character.toChars(codePoint)))
                    .setStyle(style.withColor(color)));
            return true;
        });
        return result;
    }

    private static int interpolateChannel(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }
}
