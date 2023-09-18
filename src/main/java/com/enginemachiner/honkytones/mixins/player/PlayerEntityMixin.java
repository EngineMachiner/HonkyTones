package com.enginemachiner.honkytones.mixins.player;

import com.enginemachiner.honkytones.items.floppy.FloppyDisk;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( PlayerEntity.class )
public class PlayerEntityMixin {

    private static final String method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;";

    /** If a floppy disk queries the title and is dropped.
     * This can track it to request the query again. */
    @Inject( at = @At("HEAD"), method = method )
    private void honkyTonesFloppyDrop(
            ItemStack stack, boolean throwRandomly, boolean retainOwnership,
            CallbackInfoReturnable<ItemEntity> callback
    ) {

        boolean isFloppy = stack.getItem() instanceof FloppyDisk;

        if ( !isFloppy ) return;        FloppyDisk.Companion.interrupt(stack);

    }

}
