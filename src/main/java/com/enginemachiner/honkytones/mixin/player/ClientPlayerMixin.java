package com.enginemachiner.honkytones.mixin.player;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.encryption.PlayerPublicKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( ClientPlayerEntity.class )
public abstract class ClientPlayerMixin extends PlayerEntity {

    public ClientPlayerMixin( World world, BlockPos pos, float yaw, GameProfile gameProfile, @Nullable PlayerPublicKey publicKey ) {
        super( world, pos, yaw, gameProfile, publicKey );
    }

    /** Stop the sounds when the instrument is dropped. */
    @Inject( at = @At("HEAD"), method = "dropSelectedItem" )
    private void honkyTonesStopInstrumentsOnDrop(boolean entireStack, CallbackInfoReturnable<Boolean> callback ) {

        ItemStack stack = getMainHandStack();   Item item = stack.getItem();

        boolean isInstrument = item instanceof Instrument;

        if ( !isInstrument ) return;

        item.onStoppedUsing( stack, world, this, 0 );

    }

}
