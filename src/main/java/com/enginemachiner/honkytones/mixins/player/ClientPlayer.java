package com.enginemachiner.honkytones.mixins.player;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.Network;
import com.enginemachiner.honkytones.items.instruments.Instrument;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Learnt that to point the right method you have to use the bytecode reader

@Environment(EnvType.CLIENT)
@Mixin( ClientPlayerEntity.class )
public class ClientPlayer {

    /** Stop all instrument sounds when the stack is dropped */
    @Inject( at = @At("HEAD"), method = "dropSelectedItem" )
    private void honkyTonesStopInstrumentSoundsOnDrop( boolean entireStack,
                                                       CallbackInfoReturnable<Boolean> callback ) {

        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        ItemStack stack = player.getMainHandStack();
        World world = player.world;
        if ( stack.getItem() instanceof Instrument instrument ) {
            instrument.onStoppedUsing(stack, world, player, 0);
            Network.INSTANCE.sendNbtToServer( stack.getNbt().getCompound( Base.MOD_NAME ) );
        }

    }

}
