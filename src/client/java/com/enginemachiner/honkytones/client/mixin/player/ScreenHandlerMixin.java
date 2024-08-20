package com.enginemachiner.honkytones.client.mixin.player;

import com.enginemachiner.honkytones.client.items.instruments.Instrument;
import com.enginemachiner.honkytones.client.items.music_player.Radio;
import com.enginemachiner.honkytones.items.FloppyDisk;
import com.enginemachiner.honkytones.items.instruments.InstrumentItem;
import com.enginemachiner.honkytones.items.music_player.RadioItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.enginemachiner.harmony.NBT.nbt;
import static com.enginemachiner.harmony.client.NBT.send;

@SuppressWarnings("UnreachableCode")
@Mixin( ScreenHandler.class )
public class ScreenHandlerMixin {

    @Unique
    private ItemStack stack( int slotIndex ) {

        ScreenHandler handler = (ScreenHandler) (Object) this;

        return handler.getStacks().get(slotIndex);

    }

    /** If a floppy disk queries the title and the stack changes slots.
     * This can track it to request the query again. */
    @Inject( at = @At("HEAD"), method = "onSlotClick" )
    private void honkyTonesFloppySlotClick(
            int slotIndex, int button, SlotActionType actionType,
            PlayerEntity player, CallbackInfo info
    ) {

        boolean isClient = player.world.isClient;

        if ( slotIndex < 0 || !isClient ) return;


        ItemStack stack = stack(slotIndex);         Item item = stack.getItem();

        boolean isFloppy = item instanceof FloppyDisk;     if ( !isFloppy ) return;


        NbtCompound nbt = stack.getNbt();

        FloppyDisk.Companion.interrupt(stack);      send(nbt);

    }

    @Inject( at = @At("HEAD"), method = "onSlotClick" )
    private void honkyTonesInstrumentSlotClick(
            int slotIndex, int button, SlotActionType actionType,
            PlayerEntity player, CallbackInfo info
    ) {

        boolean isClient = player.world.isClient;

        if ( slotIndex < 0 || !isClient ) return;


        ItemStack stack = stack(slotIndex);         Item item = stack.getItem();

        boolean isInstrument = item instanceof InstrumentItem;     if ( !isInstrument ) return;


        Instrument instrument = Instrument.INSTANCE;

        instrument.stop(stack);         instrument.stopMidi(stack);

    }

    @Inject( at = @At("HEAD"), method = "onSlotClick" )
    private void honkyTonesRadioSlotClick(
            int slotIndex, int button, SlotActionType actionType,
            PlayerEntity player, CallbackInfo info
    ) {

        boolean isClient = player.world.isClient;

        if ( slotIndex < 0 || !isClient ) return;


        ItemStack stack = stack(slotIndex);         Item item = stack.getItem();

        boolean isRadio = item instanceof RadioItem;     if ( !isRadio ) return;


        NbtCompound nbt = nbt(stack);       int id = nbt.getInt("PlayerID");

        Radio.INSTANCE.unlink(id);

    }

}
