package com.enginemachiner.honkytones.mixin.player;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// I learnt that to point to the right method you have to use the bytecode reader.

@Mixin( ClientPlayerEntity.class )
public class ClientPlayerMixin {

    /** Stop the sounds when the instrument is dropped. */
    @Inject( at = @At("HEAD"), method = "dropSelectedItem" )
    private void honkyTonesStopInstrumentSoundsOnDrop( boolean entireStack, CallbackInfoReturnable<Boolean> callback ) {

        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        ItemStack stack = player.getMainHandStack();
        World world = player.world;

        if ( stack.getItem() instanceof Instrument instrument ) {
            instrument.onStoppedUsing( stack, world, player, 0 );
        }

    }

}
