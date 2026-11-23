package cn.blockforge.aatu_bow.mixin.compat;

import cn.blockforge.aatu_bow.GradientText;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Colors Modifiers-Neo's completed default name, without touching renamed items or lore. */
// Tooltip names are also finalized in ClientEvents after all getHoverName modifications.
@Mixin(value = ItemStack.class, priority = 900)
public abstract class ModifiersNameMixin {
    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"), require = 1)
    private Component aatuBow$gradientWithModifierPrefix(Component original) {
        return GradientText.defaultWeaponName((ItemStack) (Object) this, original);
    }
}
