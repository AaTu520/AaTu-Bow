package cn.blockforge.aatu_bow;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;

/** Stores and reads the skill granted to an item by the anvil fusion recipe. */
public final class FusionService {
    public static final String BOW_SKILL = "bow";
    public static final String SWORD_SKILL = "sword";
    public static final int MAX_SWORD_HITS = 3;
    public static final TagKey<Item> FUSABLE_MELEE_WEAPONS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AatuBow.MOD_ID, "fusable_melee_weapons"));

    private static final String SKILL_TYPE_KEY = "aatu_bow_skill_type";
    private static final String SWORD_HITS_KEY = "aatu_bow_sword_skill_hits";

    private FusionService() {
    }

    public static boolean canFuseBow(ItemStack target) {
        return isEligibleTarget(target)
                && target.getItem() instanceof BowItem;
    }

    public static boolean canFuseSword(ItemStack target) {
        return isEligibleTarget(target)
                && (target.getItem() instanceof SwordItem
                || target.getItem() instanceof AxeItem
                || target.getItem() instanceof MaceItem
                || target.getItem() instanceof TridentItem
                || target.is(FUSABLE_MELEE_WEAPONS));
    }

    public static boolean hasBowSkill(ItemStack stack) {
        return stack.is(AatuBow.AATU_BOW.get()) || hasFusedBowSkill(stack);
    }

    public static boolean hasSwordSkill(ItemStack stack) {
        return stack.is(AatuBow.AATU_SWORD.get()) || hasFusedSwordSkill(stack);
    }

    public static boolean hasFusedBowSkill(ItemStack stack) {
        return BOW_SKILL.equals(fusedSkillType(stack));
    }

    public static boolean hasFusedSwordSkill(ItemStack stack) {
        return SWORD_SKILL.equals(fusedSkillType(stack));
    }

    public static ItemStack createFusedOutput(ItemStack target, String skillType) {
        ItemStack output = target.copyWithCount(1);
        CustomData.update(DataComponents.CUSTOM_DATA, output, tag -> {
            tag.putString(SKILL_TYPE_KEY, skillType);
            tag.remove(SWORD_HITS_KEY);
        });
        return output;
    }

    public static int swordHits(ItemStack stack) {
        if (!hasFusedSwordSkill(stack)) {
            return 0;
        }
        int hits = fusedData(stack).getInt(SWORD_HITS_KEY);
        return Math.min(MAX_SWORD_HITS, Math.max(0, hits));
    }

    public static void setSwordHits(ItemStack stack, int hits) {
        if (!hasFusedSwordSkill(stack)) {
            return;
        }
        int clampedHits = Math.min(MAX_SWORD_HITS, Math.max(0, hits));
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(SWORD_HITS_KEY, clampedHits));
    }

    private static boolean isEligibleTarget(ItemStack target) {
        return !target.isEmpty()
                && target.getCount() == 1
                && !target.is(AatuBow.AATU_BOW.get())
                && !target.is(AatuBow.AATU_SWORD.get())
                && fusedSkillType(target).isEmpty();
    }

    private static String fusedSkillType(ItemStack stack) {
        return fusedData(stack).getString(SKILL_TYPE_KEY);
    }

    private static net.minecraft.nbt.CompoundTag fusedData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
