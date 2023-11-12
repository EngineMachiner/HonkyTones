package com.enginemachiner.honkytones.mixin.chest;

import net.minecraft.block.entity.ChestLidAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChestLidAnimator.class)
public interface LidAnimatorAccessor {

    @Accessor
    boolean getOpen();

    @Accessor
    float getProgress();

    @Accessor("progress")
    void setProgress(float newProgress);

    @Accessor
    float getLastProgress();

    @Accessor("lastProgress")
    void setLastProgress(float newProgress);

}
