package com.enginemachiner.honkytones.client.mixin;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin( WorldRenderer.class ) // @Environment(EnvType.CLIENT)
public interface WorldRendererAccessor {

    @Invoker("updateEntitiesForSong")
    void invokeUpdateEntitiesForSong( World world, BlockPos pos, boolean playing );

}
