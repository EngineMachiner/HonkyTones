package com.enginemachiner.honkytones.mixins;

import com.enginemachiner.honkytones.Instrument;
import com.enginemachiner.honkytones.MobLogicMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobInstrumentAttack {

    MobLogicMixin logic = new MobLogicMixin();

    @Inject(at = @At("HEAD"), method = "tryAttack")
    private void tryAttack(Entity target, CallbackInfoReturnable<Boolean> info) {

        World world = MinecraftClient.getInstance().world;
        MobEntity mob = ((MobEntity) (Object) this);
        Item item = mob.getMainHandStack().getItem();

        if (item instanceof Instrument inst && logic.isInGame()) {

            int keyChance1 = (int) (Math.random() + 0.1);
            int keyChance2 = (int) (Math.random() + 0.1);

            // Play minimum 1 note
            for (int i = 1; (i < 2 + keyChance1 + keyChance2); i++) inst.playRandomSound(mob);
            inst.stopAllNotes(world);

        }

    }

}
