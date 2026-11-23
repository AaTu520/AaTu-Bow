package cn.blockforge.aatu_bow.compat;

import java.util.List;

import cn.blockforge.aatu_bow.AatuBow;
import cn.blockforge.aatu_bow.AatuConfig;
import cn.blockforge.aatu_bow.FusionService;
import cn.blockforge.aatu_bow.SkillService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.container.SpellContainers;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.casting.SpellCaster;

/** Loaded only when Spell Engine is installed; item fusion data remains independent. */
public final class SpellEngineCompat {
    public static final ResourceLocation FUSED_SWORD_SPELL = ResourceLocation.fromNamespaceAndPath(
            AatuBow.MOD_ID, "fused_polar_night_radiance");
    private static final List<String> FUSED_SPELL_IDS = List.of(FUSED_SWORD_SPELL.toString());

    private SpellEngineCompat() {
    }

    public static void initialize() {
        SpellHandlers.registerCustomDelivery(FUSED_SWORD_SPELL,
                (level, spell, caster, targets, context, location) -> {
                    if (!(caster instanceof ServerPlayer player)
                            || player.isSpectator() || !player.isAlive()
                            || !FusionService.hasFusedSwordSkill(player.getMainHandItem())) {
                        return false;
                    }
                    if (!SkillService.tryActivate(player, player.getMainHandItem(), SkillService.AATU_SWORD)) {
                        return false;
                    }
                    // Mirror the authoritative skill cooldown without locking the host item.
                    ((SpellCaster.Player) player).getCooldownManager().set(spell,
                            AatuConfig.AATU_SWORD_COOLDOWN_TICKS.get());
                    return true;
                });
    }

    public static SpellContainer includeFusedSkill(SpellContainer original, ItemStack stack) {
        if (!FusionService.hasFusedSwordSkill(stack)) {
            return original;
        }
        SpellContainer container = original == null ? SpellContainers.forMagicWeapon() : original;
        if (container.access() == SpellContainer.ContentType.NONE) {
            container = container.withContentType(SpellContainer.ContentType.MAGIC);
        }
        if (container.contains(FUSED_SWORD_SPELL)) {
            return container;
        }
        // withSpell replaces the host's spell list; this method must preserve it.
        return container.withAdditionalSpell(FUSED_SPELL_IDS);
    }
}
