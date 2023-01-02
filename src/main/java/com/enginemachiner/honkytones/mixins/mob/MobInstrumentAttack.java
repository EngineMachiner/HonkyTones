package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.items.instruments.Instrument;
import kotlin.random.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobInstrumentAttack {

    /** Make mobs play instruments if they have one on hand when attacking */
    @Inject(at = @At("HEAD"), method = "tryAttack")
    private void honkyTonesPlayInstrumentOnAttack( Entity target,
                                                   CallbackInfoReturnable<Boolean> callback ) {

        MobEntity mob = (MobEntity) (Object) this;
        ItemStack stack = mob.getMainHandStack();
        Item item = stack.getItem();

        if ( item instanceof Instrument ) {

            int random1 = Random.Default.nextInt(2);
            int random2 = Random.Default.nextInt(2);

            // Play minimum 1 note
            for ( int i = 1; ( i < 2 + random1 + random2 ); i++ ) {
                stack.setHolder(mob);
                Instrument.Companion.mobAction( mob, "play" );
            }
            Instrument.Companion.mobAction( mob, "stop" );

        }

    }

}
