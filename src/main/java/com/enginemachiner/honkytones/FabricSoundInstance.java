package com.enginemachiner.honkytones;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public interface FabricSoundInstance {
    default CompletableFuture<AudioStream> getAudioStream(SoundLoader loader, Identifier id,
                                                          boolean repeatInstantly) {
        return loader.loadStreamed(id, repeatInstantly);
    }
}
