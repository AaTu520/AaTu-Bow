package cn.blockforge.aatu_bow.mixin.compat;

import cn.blockforge.aatu_bow.AatuBow;
import cn.blockforge.aatu_bow.compat.ConfiguredCompat;
import com.electronwill.nightconfig.core.concurrent.SynchronizedConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional client-side fix which leaves Configured's save call available to other mods. */
@Pseudo
@Mixin(targets = "com.mrcrayfish.configured.impl.neoforge.NeoForgeConfig", remap = false)
public abstract class ConfiguredNeoForgeConfigMixin {
    @Shadow
    @Final
    protected ModConfig config;

    @ModifyExpressionValue(
            method = "update",
            at = @At(value = "NEW", target = "com/electronwill/nightconfig/core/concurrent/SynchronizedConfig"),
            require = 1,
            remap = false)
    private SynchronizedConfig aatuBow$seedCompleteUpdate(SynchronizedConfig pending) {
        if (AatuBow.MOD_ID.equals(this.config.getModId())) {
            var loaded = this.config.getLoadedConfig();
            if (loaded != null) {
                // Deep-copy before Configured writes edited paths into this batch. A shallow copy
                // would let edits mutate the original categories before validation and saving.
                ConfiguredCompat.copyValues(pending, loaded.config());
            }
        }
        // Other mods receive the original, empty batch and keep their existing save behavior.
        return pending;
    }

    @Inject(
            method = "update",
            at = @At(value = "INVOKE", target = "Lcom/mrcrayfish/configured/util/NeoForgeConfigHelper;"
                    + "correctConfig(Lnet/neoforged/fml/config/ModConfig;"
                    + "Lcom/electronwill/nightconfig/core/CommentedConfig;)V", shift = At.Shift.AFTER),
            require = 1,
            remap = false)
    private void aatuBow$refreshEditedValues(CallbackInfoReturnable<?> cir) {
        if (AatuBow.MOD_ID.equals(this.config.getModId())
                && this.config.getSpec() instanceof ModConfigSpec spec) {
            // The remote-server branch does not perform Configured's local cache reset.
            // Refresh only after validation so the UI reads corrected, updated values.
            spec.afterReload();
        }
    }
}
