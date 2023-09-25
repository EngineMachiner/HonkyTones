package com.enginemachiner.honkytones.mixins.chest;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChestLidAnimator;
import net.minecraft.block.entity.ViewerCountManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin( ChestBlockEntity.class )
public interface ChestBlockEntityAccessor {

    @Accessor
    ViewerCountManager getStateManager();

    @Accessor
    ChestLidAnimator getLidAnimator();

}