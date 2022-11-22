package com.enginemachiner.honkytones.mixins.sound;

import com.enginemachiner.honkytones.FabricSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;

// My own (temporal?) implementation to play audio streams online, all credits go to Fabric.
// https://github.com/FabricMC/fabric/pull/2558

@Mixin( SoundInstance.class )
public interface SoundInstanceMixin extends FabricSoundInstance {}

