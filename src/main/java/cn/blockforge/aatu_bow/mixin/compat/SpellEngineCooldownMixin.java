package cn.blockforge.aatu_bow.mixin.compat;

import java.util.Set;

import cn.blockforge.aatu_bow.FusionService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.container.SpellContainerSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Keep an engine weapon skill from locking the weapon's independent native use. */
@Pseudo
@Mixin(targets = "net.spell_engine.internals.cost.SpellCooldowns", remap = false)
public abstract class SpellEngineCooldownMixin {
    @Unique
    private static final Set<String> EXTRA_WEAPON_SKILLS = Set.of(
            "rpg_series:swift_strikes", "rpg_series:flurry",
            "rpg_series:swipe", "rpg_series:smash");

    @ModifyExpressionValue(method = "imposeCooldown",
            at = @At(value = "FIELD",
                    target = "Lnet/spell_engine/api/spell/Spell$Cost$Cooldown;hosting_item:Z"),
            require = 1, remap = false)
    private static boolean aatuBow$preserveNativeRightClick(boolean hostingItem, Player player,
            SpellContainerSource.SourcedContainer source, Holder<Spell> spell, float progress) {
        if (!hostingItem || source == null) {
            return hostingItem;
        }
        ItemStack host = source.itemStack();
        if (host == null || !(FusionService.hasSwordSkill(host))) {
            return hostingItem;
        }
        // SpellCooldownManager has already received the full skill cooldown. Suppress
        // only this additional item lock; never clear the weapon's own cooldowns.
        return spell.unwrapKey()
                .map(key -> !EXTRA_WEAPON_SKILLS.contains(key.location().toString()))
                .orElse(hostingItem);
    }
}

