package cn.blockforge.aatu_bow.mixin.compat;

import cn.blockforge.aatu_bow.compat.ModifiersExclusion;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ModifiersComponentMixin {
    @Inject(method = "set", at = @At("HEAD"), cancellable = true, require = 1)
    private void aatuBow$rejectModifierComponent(DataComponentType<?> type, Object value,
                                               CallbackInfoReturnable<Object> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (value != null && ModifiersExclusion.excludes(stack) && ModifiersExclusion.isModifier(type)) {
            cir.setReturnValue(stack.getComponents().get(type));
        }
    }
}
