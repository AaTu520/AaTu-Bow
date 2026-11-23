package cn.blockforge.aatu_bow.compat;

import cn.blockforge.aatu_bow.AatuBowItem;
import cn.blockforge.aatu_bow.AatuSwordItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Excludes only the two original AaTu weapons, without depending on Modifiers-Neo classes. */
public final class ModifiersExclusion {
    private static final ResourceLocation HAND = id("modifier_h");
    private static final ResourceLocation ARMOR = id("modifier_a");
    private static final ResourceLocation BOOK = id("modifier_book");

    private ModifiersExclusion() {
    }

    public static boolean excludes(ItemStack stack) {
        return stack.getItem() instanceof AatuBowItem || stack.getItem() instanceof AatuSwordItem;
    }

    public static boolean isModifier(DataComponentType<?> type) {
        ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return HAND.equals(key) || ARMOR.equals(key);
    }

    public static boolean isBook(ItemStack stack) {
        return BOOK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("modifiers", path);
    }
}
