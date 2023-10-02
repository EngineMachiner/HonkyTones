package com.enginemachiner.honkytones.mixin.player;

import com.enginemachiner.honkytones.items.floppy.FloppyDisk;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT) @Mixin( ScreenHandler.class )
public class FloppyOnScreen {

    /** If a floppy disk queries the title and the stack changes slots.
     * This can track it to request the query again. */
    @Inject( at = @At("HEAD"), method = "onSlotClick" )
    private void honkyTonesFloppySlotClick(
            int slotIndex, int button, SlotActionType actionType,
            PlayerEntity player, CallbackInfo info
    ) {

        if ( slotIndex < 0 ) return;

        ScreenHandler handler = (ScreenHandler) (Object) this;
        DefaultedList<Slot> slots = handler.slots;
        ItemStack slotStack = slots.get(slotIndex).getStack();
        Item slotItem = slotStack.getItem();

        boolean isFloppy = slotItem instanceof FloppyDisk;     if ( !isFloppy ) return;

        FloppyDisk.Companion.interrupt(slotStack);

    }

}
