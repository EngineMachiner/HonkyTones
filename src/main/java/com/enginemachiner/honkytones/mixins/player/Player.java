package com.enginemachiner.honkytones.mixins.player;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.items.floppy.FloppyDisk;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Learnt that to point the right method you have to use the bytecode reader

@Mixin( PlayerEntity.class )
public class Player {

    /** If a floppy disk has a stream and the stack is dropped,
     * this method can keep track, so it updates the name on next pick up  */
    @Inject( at = @At("HEAD"), method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;" )
    private void honkyTonesKeepTrackOfFloppyQuery( ItemStack stack, boolean throwRandomly,
                                                   boolean retainOwnership,
                                                   CallbackInfoReturnable<ItemEntity> callback ) {

        NbtCompound nbt = stack.getOrCreateNbt().getCompound( Base.MOD_NAME );
        if ( stack.getItem() instanceof FloppyDisk && nbt.contains("onQuery") ) {
            nbt.putBoolean("queryInterrupted", true);
        }

    }

}
