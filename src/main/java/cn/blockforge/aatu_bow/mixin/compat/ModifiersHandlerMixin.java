package cn.blockforge.aatu_bow.mixin.compat;

import cn.blockforge.aatu_bow.compat.ModifiersExclusion;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "cursedflames.modifiers.ModifierHandler", remap = false)
public abstract class ModifiersHandlerMixin {
    @Inject(method = "canHaveModifiers", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void aatuBow$excludeRandomModifiers(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ModifiersExclusion.excludes(stack)) {
            cir.setReturnValue(false);
        }
    }
}
