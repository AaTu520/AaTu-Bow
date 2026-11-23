package cn.blockforge.aatu_bow.mixin.compat;

import cn.blockforge.aatu_bow.compat.ModifiersExclusion;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Run before Modifiers-Neo's createResult injection, which otherwise consumes its book.
@Mixin(value = AnvilMenu.class, priority = 2000)
public abstract class ModifiersAnvilMixin extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;
    @Shadow public int repairItemCountCost;

    protected ModifiersAnvilMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true, require = 1)
    private void aatuBow$rejectModifierBook(CallbackInfo ci) {
        if (ModifiersExclusion.excludes(this.inputSlots.getItem(0))
                && ModifiersExclusion.isBook(this.inputSlots.getItem(1))) {
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.cost.set(0);
            this.repairItemCountCost = 0;
            this.broadcastChanges();
            ci.cancel();
        }
    }
}
