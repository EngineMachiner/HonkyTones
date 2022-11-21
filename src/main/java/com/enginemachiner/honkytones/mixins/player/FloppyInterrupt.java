package com.enginemachiner.honkytones.mixins.player;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.items.floppy.FloppyDisk;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Timer;
import java.util.TimerTask;

@Mixin( ScreenHandler.class )
public class FloppyInterrupt {

    /** If a floppy disk has a stream and the stack is dropped,
     * this method can keep track, so it updates the name on next click  */
    @Inject( at = @At("HEAD"), method = "onSlotClick" )
    private void honkyTonesKeepTrackOfFloppyQuery( int slotIndex, int button, SlotActionType actionType,
                                         PlayerEntity player, CallbackInfo info ) {

        if ( slotIndex < 0 || player.world.isClient ) return;

        ScreenHandler handler = (ScreenHandler) (Object) this;
        DefaultedList<Slot> slots = handler.slots;
        ItemStack stack = slots.get(slotIndex).getStack();
        final NbtCompound[] nbt = { stack.getOrCreateNbt() };
        Item item = stack.getItem();

        if ( item instanceof FloppyDisk && nbt[0].contains(Base.MOD_NAME) ) {

            new Timer().schedule( new TimerTask() {

                @Override
                public void run() {

                    for ( Slot slot : slots ) {

                        ItemStack stack = slot.getStack();
                        NbtCompound foundNbt = stack.getOrCreateNbt();
                        if ( foundNbt.getInt("id") == nbt[0].getInt("id") ) {

                            foundNbt = foundNbt.getCompound(Base.MOD_NAME);
                            boolean b = !player.getInventory().contains(stack);
                            b = b && !foundNbt.contains("queryInterrupted");

                            if ( foundNbt.contains("onQuery") && b ) {
                                //System.out.println( "interrupted " + stack );
                                foundNbt.putBoolean("queryInterrupted", true);
                                break;
                            }

                        }

                    }

                }

            }, 50);

        }

    }

}
