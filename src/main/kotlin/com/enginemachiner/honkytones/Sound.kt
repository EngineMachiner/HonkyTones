package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerCompanion
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerEntity
import com.enginemachiner.honkytones.items.instruments.Instrument
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.sound.AudioStream
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.OggAudioStream
import net.minecraft.client.sound.Sound
import net.minecraft.client.sound.SoundLoader
import net.minecraft.entity.Entity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.io.BufferedInputStream
import java.io.File
import java.util.concurrent.CompletableFuture
import kotlin.math.pow

// Sounds played by stacks
open class CustomSoundInstance( val s: String )
    : MovingSoundInstance( SoundEvent( Identifier(s) ), SoundCategory.PLAYERS ) {

    var index = -1
    var entity: Entity? = null;   private var timesForcedStopped = 0
    private var doFadeOut = false;      var isPlaying = false
    var key = "notes";                  var toPitch: Int? = null
    var playOnce = false

    fun playSound(stack: ItemStack) {
        playSound(stack, false, true)
    }

    fun playSound(stack: ItemStack, skipClient: Boolean, shouldNetwork: Boolean) {

        val holder = stack.holder ?: return

        val nbt = stack.tag!!.getCompound(Base.MOD_NAME)

        val isOnUse = nbt.getBoolean("isOnUse")

        val client = MinecraftClient.getInstance()
        val manager = client.soundManager

        if ( manager.isPlaying(this) ) { resetOrDone(); timesForcedStopped++ }
        if ( !skipClient ) manager.play(this)
        else {
            sound = Sound( "", 1f, 1f, 0,
                Sound.RegistrationType.FILE, false, false, 0 )
        }

        if (isOnUse) volume = nbt.getFloat("Volume")

        // toPitch is a number of semitones to pitch
        if (toPitch != null) pitch = 2f.pow( (toPitch as Int / 12f) )

        entity = holder;      setPlayState()

        // Mobs are networked
        if ( holder is MobEntity || !Network.isOnline() || !shouldNetwork ) return

        var netName = "playsound"
        if ( holder is MusicPlayerCompanion ) netName = "play_midi"
        val id = Identifier( Base.MOD_NAME, netName )

        val buf = PacketByteBufs.create()
        buf.writeString( holder.uuidAsString )
        buf.writeString( nbt.getString("itemClass") )
        buf.writeString(key);       buf.writeInt(index)
        buf.writeInt( nbt.getInt("Index") )
        buf.writeFloat(volume);   buf.writeFloat(pitch)
        buf.writeString( nbt.getString("itemID") )
        ClientPlayNetworking.send( id, buf )

    }

    fun stopSound(stack: ItemStack) { stopSound(stack, true) }

    fun stopSound( stack: ItemStack, shouldNetwork: Boolean ) {

        val holder = stack.holder ?: return

        setStopState()

        if ( holder is MobEntity || !Network.isOnline() || !shouldNetwork ) return

        val nbt = stack.tag!!.getCompound(Base.MOD_NAME)

        var netName = "stopsound"
        if ( holder is MusicPlayerCompanion ) netName = "stop_midi"
        val id = Identifier( Base.MOD_NAME, netName )

        val buf = PacketByteBufs.create()
        buf.writeString( nbt.getString("itemClass") )
        buf.writeString(key);     buf.writeInt(index)
        buf.writeInt( nbt.getInt("Index") )

        ClientPlayNetworking.send(id, buf)

    }

    fun resetOrDone() {

        if (playOnce) { setDone();  return }

        val manager = MinecraftClient.getInstance().soundManager
        manager.stop(this)

    }

    fun setPlayState() { doFadeOut = false;  isPlaying = true }
    fun setPitch(f: Float) { pitch = f }
    fun setVolume(f: Float) { volume = f }
    fun isStopping(): Boolean { return doFadeOut }

    fun setStopState() { setStopState(false) }
    fun setStopState( beDone: Boolean ) {
        doFadeOut = true;   playOnce = beDone
    }

    fun setSound(s: Sound) { sound = s }
    fun setPos(pos: Vec3d) { x = pos.x; y = pos.y; z = pos.z }
    fun setPos(pos: BlockPos) { x = pos.x.toDouble(); y = pos.y.toDouble(); z = pos.z.toDouble() }

    override fun shouldAlwaysPlay(): Boolean { return true }

    override fun tick() {

        if ( isPlaying ) {

            if ( doFadeOut ) {

                // Counting each time the sound is force-stopped immediately
                // Helps to do a proper fade out without cutting it
                // I have no clue what causes this.

                val rate = 0.1f / ( timesForcedStopped + 1 )
                if (volume - rate > 0) volume -= rate
                else { isPlaying = false; doFadeOut = false; volume = 0f; resetOrDone() }

            }

            // Moving pos
            if (entity != null && volume > 0) setPos(entity!!.pos)

        }

    }

}

class SpecialSoundInstance( var file: File,
                            private val musicPlayer: MusicPlayerEntity
) : AudioStreamSoundInstance() {

    private val client = MinecraftClient.getInstance()
    private val player = client.player!!
    private val companion = musicPlayer.companion
    private var buffStream = BufferedInputStream( file.inputStream() )
    private val oggStream = OggAudioStream( buffStream )

    init { key = "";    //val buf = oggStream.buffer
        //buf.position( ( buf.limit() * 0.5 ).toInt() )
    }

    override fun tick() {

        super.tick()

        // Had to implement my own attenuation
        if ( !isStopping() ) {

            var nbt = musicPlayer.getStack(16).tag!!
            nbt = nbt.getCompound(Base.MOD_NAME)
            var vol = nbt.getFloat("Volume")
            val minDistance = minDistance * vol
            val dist = player.squaredDistanceTo(companion) * 0.01
            if ( dist > minDistance ) {
                vol = ( minDistance * 0.25f - dist * 0.03f ).toFloat()
            }

            if (vol < 0) vol = 0f;     if (vol > 1) vol = 1f

            volume = vol

        }

        if ( volume == 0f && isStopping() ) {
            setDone();  buffStream.close()
        }

    }

    override fun getAudioStream( loader: SoundLoader?, id: Identifier?, shouldLoop: Boolean )
    : CompletableFuture<AudioStream> {
        return CompletableFuture.completedFuture( oggStream )
    }

    companion object {
        private const val minDistance = 3f
    }

}

object Sound {

    private fun playSoundWriteBuf( it: PacketByteBuf ): PacketByteBuf {

        val newbuf = PacketByteBufs.create()

        newbuf.writeString(it.readString()); newbuf.writeString(it.readString())
        newbuf.writeString(it.readString()); newbuf.writeInt(it.readInt())
        newbuf.writeInt(it.readInt()); newbuf.writeFloat(it.readFloat())
        newbuf.writeFloat(it.readFloat()); newbuf.writeString(it.readString())

        return newbuf

    }

    private fun stopSoundWriteBuf( it: PacketByteBuf ): PacketByteBuf {

        val newbuf = PacketByteBufs.create()

        newbuf.writeString(it.readString()); newbuf.writeString(it.readString())
        newbuf.writeInt(it.readInt()); newbuf.writeInt(it.readInt())

        return newbuf

    }

    fun networking() {

        // Play and stop sounds through the net

        // All read order and write order must be the same

        Network.registerServerToClientsHandler("playsound",
            25f, 18) { playSoundWriteBuf(it) }

        Network.registerServerToClientsHandler("play_midi",
            "playsound", 25f, 18) { playSoundWriteBuf(it) }

        Network.registerServerToClientsHandler("stopsound",
            25f, 18) { stopSoundWriteBuf(it) }

        Network.registerServerToClientsHandler("stop_midi",
            "stopsound", 25f, 18) { stopSoundWriteBuf(it) }

        if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

        var id = Identifier( Base.MOD_NAME, "playsound" )
        ClientPlayNetworking.registerGlobalReceiver(id) {
                client: MinecraftClient, _: ClientPlayNetworkHandler,
                packet: PacketByteBuf, _: PacketSender ->

            val uuid = packet.readString()
            val listName = packet.readString()
            val category = packet.readString();     val soundIndex = packet.readInt()
            val stackIndex = packet.readInt();        val volume = packet.readFloat()
            val pitch = packet.readFloat();       val itemID = packet.readString()

            client.send {

                // Find entity
                val entity = findByUUID(client, uuid) ?: return@send

                // Get instrument
                val stacks = stackLists[listName]!!
                createStackIfMissing(stacks, itemID, stackIndex)
                val stack = stacks[stackIndex]
                val inst = stack.item as Instrument

                // Temp storing hash to get the sounds
                val sounds = inst.getSounds(stack, category)
                val sound = sounds.filterNotNull().find { it.index == soundIndex }!!

                var b = CanBeMuted.blacklist.keys.contains(entity)
                b = b && sound.key == "notes"
                if (b) return@send

                sound.volume = volume;      sound.pitch = pitch
                sound.entity = entity;      stack.holder = entity

                sound.playSound(stack, false, false)

            }

        }

        id = Identifier( Base.MOD_NAME, "stopsound" )
        ClientPlayNetworking.registerGlobalReceiver(id) {
                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

            val listName = buf.readString();    val category = buf.readString()
            val soundIndex = buf.readInt();    val stackIndex = buf.readInt()

            client.send {

                // Get instrument
                val stacks = stackLists[listName]!!
                val stack = stacks[stackIndex]
                val inst = stack.item as Instrument

                val sounds = inst.getSounds(stack, category)
                val sound = sounds.filterNotNull().find { it.index == soundIndex }!!

                sound.stopSound(stack, false)

            }

        }

    }

}