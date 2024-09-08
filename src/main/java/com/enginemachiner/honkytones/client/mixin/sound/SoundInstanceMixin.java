package com.enginemachiner.honkytones.client.mixin.sound;

import com.enginemachiner.honkytones.client.FabricSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;

/** Implementation to play audio streams taken from Fabric.
 * <a href="https://github.com/FabricMC/fabric/pull/2558">...</a>
 */
@Mixin( SoundInstance.class )
public interface SoundInstanceMixin extends FabricSoundInstance {}