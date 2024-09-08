package com.enginemachiner.honkytones.client.mixin.chest;

import com.enginemachiner.honkytones.LidAnimatorBehaviour;
import net.minecraft.block.entity.ChestLidAnimator;
import org.spongepowered.asm.mixin.Mixin;

import static com.enginemachiner.harmony.client.ClientKt.client;

@Mixin(ChestLidAnimator.class)
public abstract class LidAnimatorMixin implements LidAnimatorAccessor, LidAnimatorBehaviour {

    @Override
    public void honkyTones$renderStep() {

        float progress = getProgress();     boolean open = getOpen();

        float rate = client().getLastFrameDuration() * 0.1f;


        setLastProgress(progress);

        if ( !open && progress > 0.0F ) {
            setProgress( Math.max( progress - rate, 0.0F ) );
        } else if ( open && progress < 1.0F ) {
            setProgress( Math.min( progress + rate, 1.0F ) );
        }

    }

}