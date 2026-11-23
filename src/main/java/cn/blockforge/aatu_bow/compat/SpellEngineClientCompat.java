package cn.blockforge.aatu_bow.compat;

import java.util.ArrayList;
import java.util.List;

import cn.blockforge.aatu_bow.AatuBow;
import cn.blockforge.aatu_bow.FusionService;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.spell_engine.client.input.SpellHotbar;
import net.spell_engine.internals.casting.SpellCast;

public final class SpellEngineClientCompat {
    private static final ClassValue<Boolean> CUSTOM_ITEM_USE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> itemClass) {
            try {
                // Instant weapon abilities often keep UseAnim.NONE. Check their use
                // implementation once per item class instead of changing actual animations.
                return itemClass.getMethod("use", Level.class, Player.class, InteractionHand.class)
                        .getDeclaringClass() != Item.class;
            } catch (NoSuchMethodException exception) {
                return false;
            }
        }
    };

    private SpellEngineClientCompat() {
    }

    public static boolean reservesNativeUse(ItemStack stack) {
        if (stack.is(AatuBow.AATU_SWORD.get())) {
            return true;
        }
        return FusionService.hasFusedSwordSkill(stack)
                && (stack.getUseAnimation() != UseAnim.NONE || CUSTOM_ITEM_USE.get(stack.getItem().getClass()));
    }

    public static List<SpellCast.Option> orderFusedSkill(List<SpellCast.Option> options, Player player) {
        if (!FusionService.hasFusedSwordSkill(player.getMainHandItem())) {
            return options;
        }
        int fusedIndex = -1;
        for (int index = 0; index < options.size(); index++) {
            if (SpellEngineCompat.FUSED_SWORD_SPELL.equals(options.get(index).id())) {
                fusedIndex = index;
                break;
            }
        }
        if (fusedIndex < 0) {
            return options;
        }
        int destination = SpellHotbar.expectedUseStack(player) == null ? 0 : options.size() - 1;
        if (fusedIndex == destination) {
            return options;
        }
        List<SpellCast.Option> ordered = new ArrayList<>(options);
        SpellCast.Option fusedSkill = ordered.remove(fusedIndex);
        ordered.add(destination, fusedSkill);
        return ordered;
    }

    public static boolean usesFusedSkillOnRightClick(Player player) {
        return FusionService.hasFusedSwordSkill(player.getMainHandItem())
                && SpellHotbar.expectedUseStack(player) == null;
    }
}
