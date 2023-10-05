package com.enginemachiner.honkytones.sound
import MarkErrorInputStream
import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayer
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.sound.AudioStream
import net.minecraft.client.sound.OggAudioStream
import net.minecraft.client.sound.SoundLoader
import net.minecraft.util.Identifier
import org.lwjgl.BufferUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CompletableFuture
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Environment(EnvType.CLIENT)
private class CustomOggAudioStream( private val sound: ExternalSound, inputStream: InputStream ) : OggAudioStream(inputStream) {

    override fun close() { sound.stop(); super.close() }

}

@Environment(EnvType.CLIENT)
private class DirectAudioStream( private val sound: ExternalSound ) : AudioStream {

    private val url = sound.sourceInput;        private val client = OkHttpClient()

    private val request = Request.Builder().url(url).build()

    private val response = client.newCall(request).execute();       private val body = response.body()

    private val inputStream = MarkErrorInputStream( BufferedInputStream( body.byteStream() ) )

    private val audioInputStream = AudioSystem.getAudioInputStream(inputStream)

    private val newFormat = AudioFormat( AudioFormat.Encoding.PCM_SIGNED, 44100F, 16, 2, 4, 44100F, false )

    private val newInputStream: AudioInputStream = AudioSystem.getAudioInputStream( newFormat, audioInputStream )

    private val zeroBuffer = BufferUtils.createByteBuffer(0)

    init { if ( !response.isSuccessful ) close() }

    override fun close() { newInputStream.close();    body.close();     sound.stop() }

    override fun getFormat(): AudioFormat { return newFormat }

    override fun getBuffer(size: Int): ByteBuffer {

        try {

            val array = ByteArray(size)
            var read = newInputStream.read( array, 0, size )

            if ( read > 0 ) {

                var buffer = BufferUtils.createByteBuffer(size)
                    .order( ByteOrder.LITTLE_ENDIAN )
                    .put( array, 0, read )

                for ( i in 1..10 ) {

                    read = newInputStream.read( array, 0, size )
                    buffer = buffer.put( array, 0, read )

                }

                return buffer.flip()

            }

        } catch ( e: Exception ) {

            warnUser( Translation.get("error.parse") )
            warnUser( Translation.get("message.check_console") )

            e.printStackTrace()

        }

        return zeroBuffer

    }

}

/** Sound that can stream or play certain external audio files. */
@Environment(EnvType.CLIENT)
class ExternalSound( private val musicPlayer: MusicPlayer ) : FadingSound("audio_stream") {

    val sourceInput = musicPlayer.path;     init { init() }

    private var nbtVolume = 1f;     private var audioStream: AudioStream? = null

    private var blockEntity: MusicPlayerBlockEntity? = null

    private fun init() {

        blockEntity = musicPlayer.blockEntity

        entity = blockEntity!!.entity

        try { audioStream = getAudioStream() } catch (e: Exception) {

            warnUser( "ERROR: " + Translation.get("message.check_console") )

            e.printStackTrace()

        }

    }

    fun isValid(): Boolean { return audioStream != null }

    private fun getAudioStream(): AudioStream {

        return if ( sourceInput.endsWith(".ogg") ) {

            val file = File(sourceInput)

            val inputStream = if ( file.exists() ) file.inputStream() else URL(sourceInput).openStream()

            CustomOggAudioStream( this, inputStream )

        } else DirectAudioStream(this)

    }

    private fun setNBTVolume() {

        val floppy = musicPlayer.items[16]

        if ( floppy.isEmpty ) return;       val nbt = NBT.get(floppy)

        if ( isMuted(entity!!) ) { nbtVolume = 0f; return };    val volume = nbt.getFloat("Volume")

        if ( nbtVolume == volume ) return;      nbtVolume = nbt.getFloat("Volume")

    }

    override fun fadeOut() { if ( !blockEntity!!.repeatOnPlay ) super.fadeOut() else stop() }
    
    override fun tick() {

        super.tick();       if ( isStopping() ) return;         setNBTVolume()

        val factor = Sound.MIN_DISTANCE.pow(2) * 0.5f

        val distance1 = musicPlayer.pos().getSquaredDistance( player()!!.blockPos ) * 0.03

        var distance2 = factor - distance1;     distance2 /= factor

        distance2 += 0.05f;        distance2 *= nbtVolume

        volume = max( 0f, min( 1f, distance2.toFloat() ) )

    }

    public override fun stop() {

        if ( !isPlaying() ) return;     super.stop();       musicPlayer.pause()

    }

    override fun getAudioStream( loader: SoundLoader, id: Identifier, shouldLoop: Boolean ): CompletableFuture<AudioStream> {

        if ( audioStream == null ) return super.getAudioStream( loader, id, shouldLoop )

        return CompletableFuture.completedFuture(audioStream)

    }

}