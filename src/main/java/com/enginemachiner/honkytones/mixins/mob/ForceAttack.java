package com.enginemachiner.honkytones.mixins.mob;

import com.enginemachiner.honkytones.HonkyTonesMixinLogic;
import net.minecraft.entity.LivingEntity;
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
public class ForceAttack {

    /** Skip opening a screen to allow attacks using Instruments */
    @Inject(at = @At("HEAD"), method = "interactMob", cancellable = true)
    private void honkyTonesSkipScreenToAttack(
            PlayerEntity ply, Hand hand, CallbackInfoReturnable<ActionResult> callback) {
        LivingEntity base = ( LivingEntity ) ( Object ) this;
        ActionResult action = ActionResult.PASS;
        if ( HonkyTonesMixinLogic.forceAttack(ply, hand, base) ) callback.setReturnValue(action);
    }

}
