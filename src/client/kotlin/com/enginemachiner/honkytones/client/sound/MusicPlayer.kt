package com.enginemachiner.honkytones.client.sound

import MarkErrorInputStream
import com.enginemachiner.harmony.client.Message
import com.enginemachiner.harmony.client.player
import com.enginemachiner.harmony.client.sendMessage
import com.enginemachiner.honkytones.SOUND_MIN_DISTANCE
import com.enginemachiner.honkytones.client.Silencer.isMuted
import com.enginemachiner.honkytones.client.blocks.music_player.MusicPlayer
import com.enginemachiner.honkytones.client.isValidUrl
import com.enginemachiner.honkytones.items.FloppyDisk
import net.minecraft.client.sound.AudioStream
import net.minecraft.client.sound.OggAudioStream
import net.minecraft.client.sound.SoundLoader
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.Identifier
import okhttp3.OkHttpClient
import okhttp3.Request
import org.lwjgl.BufferUtils
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Sound that can stream or play certain external audio files. */
class CustomSound( private val musicPlayer: MusicPlayer ) : FadingSound("audio_stream") {

    override val path = musicPlayer.path

    private var audio: AudioStream? = null;         init { init() }


    private fun init() {

        entity = musicPlayer.entity()

        try { audio = audio() } catch (exception: Exception) {

            sendMessage( "ERROR: /@message.check_console", exception )

        }

    }


    fun inputStream(): InputStream { return musicPlayer.inputStream() }

    fun isValid(): Boolean { return audio != null }


    fun publicStop() { stop() }

    private fun floppy(): ItemStack { return musicPlayer.floppy() }

    private fun settings(): NbtCompound {

        return FloppyDisk.settings( floppy(), player() )

    }

    private fun volume(): Double {

        if ( entity != null && isMuted(entity!!) ) return 0.0

        return settings().getDouble("Volume")

    }

    private fun audio(): AudioStream {

        val isOgg = path.endsWith(".ogg");      val isURL = isValidUrl(path)

        return if (isOgg) OggAudio(this) else {

            if (isURL) HttpAudio(this) else LocalAudio(this)

        }

    }


    override fun fadeOut() {

        val onRepeat = musicPlayer.onRepeat

        if ( !onRepeat ) super.fadeOut() else stop()

    }

    override fun tick() {

        super.tick();       if ( isStopping() || !musicPlayer.hasInput() ) return


        val pos1 = musicPlayer.soundPos() ?: return;            val pos2 = player().pos

        val factor = SOUND_MIN_DISTANCE.pow(2) * 0.5f


        val length1 = pos1.getSquaredDistance(pos2) * 0.03


        var length2 = factor - length1

        length2 /= factor;      length2 += 0.05f;       length2 *= volume()


        val min = min( 1.0, length2 );          volume = max( 0.0, min ).toFloat()

    }

    override fun stop() {

        if ( !isPlaying() ) return;         super.stop();       musicPlayer.pause()

    }

    override fun getAudioStream( loader: SoundLoader, id: Identifier, shouldLoop: Boolean ): CompletableFuture<AudioStream> {

        audio ?: return super.getAudioStream( loader, id, shouldLoop )

        return CompletableFuture.completedFuture(audio)

    }

}

private class OggAudio( private val sound: CustomSound ) : OggAudioStream( sound.inputStream() ) {

    override fun close() { sound.publicStop();    super.close() }

}

private class HttpAudio(sound: CustomSound) : LocalAudio(sound) {

    private val url = sound.path;        private val client = OkHttpClient()

    private val request = Request.Builder().url(url).build()

    private val response = client.newCall(request).execute()

    private val body = response.body


    override val inputStream = body.byteStream()


    init {

        try { AudioSystem.getAudioInputStream(inputStream) } catch( _: Exception ) { body.close() }

        if ( !response.isSuccessful ) close()

    }


    override fun close() { body.close();    super.close() }

}

private open class LocalAudio( private val sound: CustomSound ) : AudioStream {

    protected open val inputStream = sound.inputStream()

    private val lazyStream = lazy { inputStream }.value

    private val nextStream = MarkErrorInputStream( BufferedInputStream(lazyStream) )

    private val audioStream = AudioSystem.getAudioInputStream(nextStream)

    private val format = AudioFormat( AudioFormat.Encoding.PCM_SIGNED, 44100F, 16, 2, 4, 44100F, false )

    private val finalStream: AudioInputStream = AudioSystem.getAudioInputStream(format, audioStream)

    private val zeroBuffer = BufferUtils.createByteBuffer(0)


    override fun close() { finalStream.close();     sound.publicStop() }

    override fun getFormat(): AudioFormat { return format }

    override fun getBuffer(size: Int): ByteBuffer {

        try {

            val array = ByteArray(size)

            var read = finalStream.read( array, 0, size )

            if ( read <= 0 ) return zeroBuffer

            var buffer = BufferUtils.createByteBuffer(size)
                .order( ByteOrder.LITTLE_ENDIAN )
                .put( array, 0, read )

            for ( i in 1..10 ) {

                read = finalStream.read( array, 0, size )

                if ( read == -1 ) break

                buffer = buffer.put( array, 0, read )

            }

            return buffer.flip()

        } catch (exception: Exception) {

            Message( "error.parse", exception ).send()

        }

        return zeroBuffer

    }

}