package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.MixinLogic;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( { AbstractDonkeyEntity.class, VillagerEntity.class, WanderingTraderEntity.class } )
public class MenuMob {

    /** Force attack while sneaking for mobs that open menus. */
    @Inject( at = @At("HEAD"), method = "interactMob", cancellable = true )
    private void honkyTonesForceAttackOnScreenOpening(
            PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> callback
    ) {

        MobEntity base = (MobEntity) (Object) this;
        ActionResult action = ActionResult.PASS;
        boolean canForce = MixinLogic.canForceAttack( player, base );

        if (canForce) callback.setReturnValue(action);

    }

}
