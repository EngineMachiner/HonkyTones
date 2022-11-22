package com.enginemachiner.honkytones;

import net.minecraft.client.sound.AudioStream;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.util.Identifier;

import java.util.concurrent.CompletableFuture;

public abstract class AudioStreamSoundInstance extends CustomSoundInstance {

    private static final String id = Base.MOD_NAME + ":audio_stream";

    public AudioStreamSoundInstance() { super(id); }

    public CompletableFuture<AudioStream> getAudioStream( SoundLoader loader,
                                                          Identifier id, boolean shouldLoop ) {
        return super.getAudioStream(loader, id, shouldLoop);
    }

}