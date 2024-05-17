package com.enginemachiner.honkytones.mixin.mob;

import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.passive.ParrotEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin( ParrotEntity.class )
public class ParrotEntityMixin {

    @Redirect( method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z" ) )
    private boolean allowMusicPlayer( BlockState blockState, Block block ) {

        Block musicPlayerBlock = MusicPlayerBlock.registryBlock;

        return blockState.isOf(block) || blockState.isOf(musicPlayerBlock);

    }

}
