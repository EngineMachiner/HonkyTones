package com.enginemachiner.honkytones.mixins;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.ChestLidAnimator;
import net.minecraft.block.entity.ChestStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin( ChestBlockEntity.class )
public interface ChestBlockEntityAccessor {

    @Accessor
    ChestStateManager getStateManager();

    @Accessor
    ChestLidAnimator getLidAnimator();

}