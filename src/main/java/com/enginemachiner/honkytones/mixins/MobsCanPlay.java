package com.enginemachiner.honkytones.mixins;

import com.enginemachiner.honkytones.Base;
import com.enginemachiner.honkytones.MobLogicMixin;
import net.minecraft.entity.mob.*;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.LocalDifficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

import static com.enginemachiner.honkytones.BaseKt.getClassesMap;

// DrownedEntity.class and PillagerEntity.class are ranged big sad
@Mixin( {
        MobEntity.class, AbstractSkeletonEntity.class,
        PiglinEntity.class, PiglinBruteEntity.class,
        VindicatorEntity.class, ZombifiedPiglinEntity.class,
        WitherSkeletonEntity.class
} )
public class MobsCanPlay {

    MobLogicMixin logic = new MobLogicMixin();

    @Inject(at = @At("TAIL"), method = "initEquipment")
    private void initEquipment(LocalDifficulty diff, CallbackInfo info) {

        MobEntity mob = ((MobEntity) (Object) this);

        boolean chance = new Random().nextInt(8) + 1 == 8;

        if ( chance && logic.canPlay( (Class<MobEntity>) mob.getClass() ) ) {

            Object[] names = getClassesMap().values().toArray();
            int index = new Random().nextInt(names.length);
            String name = (String) names[index];

            Identifier id = new Identifier(Base.MOD_NAME + ":" + name);
            Item inst = Registry.ITEM.get(id);

            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(inst));

        }

    }

}

