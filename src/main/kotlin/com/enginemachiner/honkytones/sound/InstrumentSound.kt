package com.enginemachiner.honkytones.sound

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerEntity
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.NoFading
import com.enginemachiner.honkytones.items.instruments.PlayCompletely
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

@Environment(EnvType.CLIENT)
open class InstrumentSound(path: String) : StackSound(path) {

    constructor( path: String, semitones: Int ) : this(path) { this.semitones = semitones }

    private var semitones = 0

    public override fun setData(stack: ItemStack) {

        super.setData(stack);       val nbt = NBT.get(stack)

        maxVolume = nbt.getFloat("Volume")

        if ( semitones != 0 ) pitch = 2f.pow( semitones / 12f )

    }

    override fun fadeOut() {

        val item = stack!!.item;        if ( item is NoFading ) { stop(); return }

        if ( item is PlayCompletely ) return;      super.fadeOut()

    }

    override fun playOnClients() {

        var netID = InstrumentSoundNetworking.netID("play")

        if ( entity is MusicPlayerEntity ) netID = InstrumentSoundNetworking.netID("play_on_player")

        val buf = PacketByteBufs.create()

        buf.writeString(path);          buf.writeItemStack(stack)
        buf.writeFloat(maxVolume);      buf.writeInt(semitones)
        buf.writeInt( entity!!.id )

        ClientPlayNetworking.send( netID, buf )

    }

    override fun fadeOutOnClients() {

        var netID = InstrumentSoundNetworking.netID("stop")

        if ( entity is MusicPlayerEntity ) netID = InstrumentSoundNetworking.netID("stop_on_player")

        val buf = PacketByteBufs.create()

        buf.writeString(path);          buf.writeItemStack(stack)
        buf.writeInt(semitones)

        ClientPlayNetworking.send( netID, buf )

    }

    fun semitones(): Int { return semitones }

    fun play(stack: ItemStack) { setData(stack);     super.play() }

}

@Environment(EnvType.CLIENT)
class NoteProjectileSound( path: String, pos: Vec3d, semitones: Int ) : InstrumentSound(path) {

    init { this.pos = pos;     if ( semitones != 0 ) pitch = 2f.pow( semitones / 12f ) }

    override fun playOnClients() {};    override fun fadeOutOnClients() {}

}

object InstrumentSoundNetworking : ModID {

    private fun playerFilter( current: ServerPlayerEntity, sender: ServerPlayerEntity ): Boolean { return current != sender }

    private fun commonFilter( addDistance: Double, current: ServerPlayerEntity, sender: ServerPlayerEntity ): Boolean {
        return current.blockPos.isWithinDistance( sender.pos, Sound.MIN_DISTANCE + addDistance ) && current != sender
    }

    private fun playFilter( current: ServerPlayerEntity, sender: ServerPlayerEntity ): Boolean {
        return commonFilter( 0.0, current, sender )
    }

    private fun fadeOutFilter( current: ServerPlayerEntity, sender: ServerPlayerEntity ): Boolean {
        return commonFilter( 1.0, current, sender )
    }

    private fun findSound( list: MutableList<InstrumentSound?>, path: String, semitones: Int ): InstrumentSound {

        return list.filterNotNull().find { it.path == path && it.semitones() == semitones }!!

    }

    private fun findStack(netStack: ItemStack): ItemStack {

        val stacks = Instrument.stacks

        var stack = stacks.find { NBT.id(it) == NBT.id(netStack) }

        if ( stack == null ) { stacks.add(netStack); stack = netStack }

        return stack

    }

    fun networking() {

         fun writePlayBuf( sentBuf: PacketByteBuf, nextBuf: PacketByteBuf ) {

            nextBuf.writeString( sentBuf.readString() );       nextBuf.writeItemStack( sentBuf.readItemStack() )
            nextBuf.writeFloat( sentBuf.readFloat() );         nextBuf.writeInt( sentBuf.readInt() )
            nextBuf.writeInt( sentBuf.readInt() )

        }

        fun writeStopBuf( sentBuf: PacketByteBuf, nextBuf: PacketByteBuf ) {

            nextBuf.writeString( sentBuf.readString() );       nextBuf.writeItemStack( sentBuf.readItemStack() )
            nextBuf.writeInt( sentBuf.readInt() )

        }

        registerSpecialServerReceiver( netID("play"), ::writePlayBuf, ::playFilter )
        registerSpecialServerReceiver( netID("stop"), ::writeStopBuf, ::fadeOutFilter )

        registerSpecialServerReceiver( netID("play_on_player"), ::writePlayBuf, ::playerFilter )
        registerSpecialServerReceiver( netID("stop_on_player"), ::writeStopBuf, ::playerFilter )

        if ( !isClient() ) return

        for ( netName in listOf( "play", "play_on_player" ) ) {

            val id = netID(netName)

            ClientPlayNetworking.registerGlobalReceiver(id) {

                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val path = buf.readString();        val netStack = buf.readItemStack()
                val maxVolume = buf.readFloat();    val semitones = buf.readInt()
                val id = buf.readInt()

                /*

                 Using the netStack to play the sounds is wrong because each time
                 there is a new stack instance / object that would try to get
                 and create stack sounds, wasting resources. To avoid this I'll store
                 them and search them by an NBT ID, so they can be reused.

                 */

                client.send {

                    val holder = entity(id)

                    if ( holder == null || isMuted(holder) ) return@send

                    val stack = findStack(netStack)

                    val instrument = stack.item as Instrument

                    val notes = instrument.stackSounds(stack).notes
                    val device = instrument.stackSounds(stack).deviceNotes

                    var sound = findSound( notes, path, semitones )

                    if ( sound.isPlaying() ) sound = findSound( device, path, semitones )

                    stack.holder = holder;      sound.shouldNetwork = false

                    sound.maxVolume = maxVolume;        sound.play(stack)

                }

            }

        }

        for ( netName in listOf( "stop", "stop_on_player" ) ) {

            val id = netID(netName)
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val path = buf.readString();        val netStack = buf.readItemStack()
                val semitones = buf.readInt()

                client.send {

                    val stack = findStack(netStack)

                    val instrument = stack.item as Instrument

                    val notes = instrument.stackSounds(stack).notes
                    val device = instrument.stackSounds(stack).deviceNotes

                    var sound = findSound( notes, path, semitones )

                    if ( sound.isStopping() ) sound = findSound( device, path, semitones )

                    if ( !sound.isPlaying() ) return@send;      sound.fadeOut()

                }

            }

        }

    }

}