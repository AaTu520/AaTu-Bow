package cn.blockforge.aatu_bow.mixin.compat;

import cn.blockforge.aatu_bow.compat.SpellEngineCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.container.SpellContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.spell_engine.api.spell.container.SpellContainerHelper", remap = false)
public abstract class SpellEngineContainerMixin {
    @ModifyReturnValue(method = "containerFromItemStack", at = @At("RETURN"), require = 1, remap = false)
    private static SpellContainer aatuBow$includeFusedSkill(SpellContainer original, ItemStack stack) {
        return SpellEngineCompat.includeFusedSkill(original, stack);
    }
}
