package cn.blockforge.aatu_bow.mixin.compat;

import java.util.List;

import cn.blockforge.aatu_bow.compat.SpellEngineClientCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.casting.SpellCast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.spell_engine.client.input.SpellHotbar", remap = false)
public abstract class SpellEngineHotbarMixin {
    @Inject(method = "expectedUseStack", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void aatuBow$reserveOriginalSkill(Player player,
            CallbackInfoReturnable<SpellHotbar.ItemUseExpectation> callback) {
        ItemStack mainHand = player.getMainHandItem();
        if (SpellEngineClientCompat.reservesNativeUse(mainHand)) {
            callback.setReturnValue(new SpellHotbar.ItemUseExpectation(InteractionHand.MAIN_HAND, mainHand));
        }
    }

    @ModifyExpressionValue(method = "update",
            at = @At(value = "INVOKE", target = "Lnet/spell_engine/client/casting/ClientCastController;"
                    + "displayOptions()Ljava/util/List;"), require = 1, remap = false)
    private List<SpellCast.Option> aatuBow$orderFusedSkill(List<SpellCast.Option> options,
                                                        LocalPlayer player, Options settings) {
        return SpellEngineClientCompat.orderFusedSkill(options, player);
    }

    @ModifyExpressionValue(method = "update",
            at = @At(value = "FIELD", target = "Lnet/spell_engine/config/ClientConfig;spellHotbarUseKey:Z"),
            require = 1, remap = false)
    private boolean aatuBow$useRightClickWhenAvailable(boolean configured,
                                                      LocalPlayer player, Options settings) {
        return configured || SpellEngineClientCompat.usesFusedSkillOnRightClick(player);
    }
}
