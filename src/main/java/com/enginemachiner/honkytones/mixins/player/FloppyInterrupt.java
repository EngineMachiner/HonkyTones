package com.enginemachiner.honkytones.mixins.player;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.items.floppy.FloppyDisk;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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

@Environment(EnvType.CLIENT)
@Mixin( ScreenHandler.class )
public class FloppyInterrupt {

    /** If a floppy disk is processing the query and the stack are moved,
     * this method can keep track, so it updates the name soon as possible  */
    @Inject( at = @At("HEAD"), method = "onSlotClick" )
    private void honkyTonesKeepTrackOfFloppyQuery(
            int slotIndex, int button, SlotActionType actionType, PlayerEntity player,
            CallbackInfo info
    ) {

        if ( slotIndex < 0 || player.world.isClient ) return;

        ScreenHandler handler = (ScreenHandler) (Object) this;
        DefaultedList<Slot> slots = handler.slots;
        ItemStack stack = slots.get(slotIndex).getStack();
        final NbtCompound[] nbtInit = { stack.getOrCreateNbt() };
        Item item = stack.getItem();

        boolean hasNbt = nbtInit[0].contains(Base.MOD_NAME);
        if ( item instanceof FloppyDisk && hasNbt ) {

            NbtCompound nbt = nbtInit[0];
            new Timer().schedule( new TimerTask() {

                @Override
                public void run() {

                    for ( Slot slot : slots ) {

                        ItemStack stack = slot.getStack();
                        NbtCompound foundNbt = stack.getOrCreateNbt();
                        if ( foundNbt.getInt("id") == nbt.getInt("id") ) {

                            foundNbt = foundNbt.getCompound(Base.MOD_NAME);
                            boolean b = !player.getInventory().contains(stack);
                            b = b && !foundNbt.contains("queryInterrupted");

                            if ( foundNbt.contains("onQuery") && b ) {
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
