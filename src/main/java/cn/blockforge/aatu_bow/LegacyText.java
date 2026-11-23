package cn.blockforge.aatu_bow;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Converts ampersand color/format codes into styled text instead of displaying the codes. */
public final class LegacyText {
    private LegacyText() {
    }

    public static Component parse(String text) {
        MutableComponent result = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder segment = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '&' && index + 1 < text.length()) {
                ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(index + 1));
                if (formatting != null) {
                    if (!segment.isEmpty()) {
                        result.append(Component.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }
                    // Legacy color codes reset preceding bold/italic flags; &l adds bold again.
                    style = style.applyLegacyFormat(formatting);
                    index++;
                    continue;
                }
            }
            segment.append(current);
        }
        if (!segment.isEmpty()) {
            result.append(Component.literal(segment.toString()).setStyle(style));
        }
        return result;
    }
}
