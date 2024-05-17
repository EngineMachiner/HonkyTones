package com.enginemachiner.honkytones.mixin.mob;

import com.enginemachiner.honkytones.MixinLogic;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonHorseEntity;
import net.minecraft.entity.mob.ZombieHorseEntity;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin( {
        VillagerEntity.class, WanderingTraderEntity.class,
        AbstractDonkeyEntity.class, HorseEntity.class,
        SkeletonHorseEntity.class, ZombieHorseEntity.class
} )
public abstract class InteractionMixin extends MobEntity {

    protected InteractionMixin( EntityType<? extends MobEntity> entityType, World world ) {
        super( entityType, world );
    }

    @Inject( at = @At("HEAD"), method = "interactMob", cancellable = true )
    private void honkyTonesForceAttack(
            PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> callback
    ) {

        boolean forced = MixinLogic.tryForceAttack( player, this );

        if ( forced ) callback.setReturnValue( ActionResult.SUCCESS );

    }

}
