package com.enginemachiner.honkytones.mixin.chest;

import com.enginemachiner.honkytones.LidAnimatorBehaviour;
import net.minecraft.block.entity.ChestLidAnimator;
import org.spongepowered.asm.mixin.Mixin;

import static com.enginemachiner.honkytones.UtilityKt.client;

@Mixin(ChestLidAnimator.class)
public class LidAnimatorMixin implements LidAnimatorBehaviour {

    @Override
    public void renderStep() {

        LidAnimatorAccessor lid = (LidAnimatorAccessor) this;

        float progress = lid.getProgress();     boolean open = lid.getOpen();
        float rate = client().getLastFrameDuration() * 0.1f;

        lid.setLastProgress(progress);

        if ( !open && progress > 0.0F ) {
            lid.setProgress( Math.max( progress - rate, 0.0F ) );
        } else if ( open && progress < 1.0F ) {
            lid.setProgress( Math.min( progress + rate, 1.0F ) );
        }

    }
}