package com.enginemachiner.honkytones.sound

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.Instrument
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

        if ( stack!!.item is DrumSet ) return;      super.fadeOut()

    }

    override fun playOnClients() {

        val netId = InstrumentSoundNetworking.netID("play")

        val buf = PacketByteBufs.create()

        buf.writeString(path);          buf.writeItemStack(stack)
        buf.writeFloat(maxVolume);      buf.writeInt(semitones)
        buf.writeInt( entity!!.id )

        ClientPlayNetworking.send( netId, buf )

    }

    override fun fadeOutOnClients() {

        val netId = InstrumentSoundNetworking.netID("stop")

        val buf = PacketByteBufs.create()

        buf.writeString(path);          buf.writeItemStack(stack)
        buf.writeInt(semitones)

        ClientPlayNetworking.send( netId, buf )

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

        var stack = stacks.find { getID(it) == getID(netStack) }

        if ( stack == null ) { stacks.add(netStack); stack = netStack }

        return stack

    }

    private fun getID(stack: ItemStack): Int { return NBT.get(stack).getInt("ID") }

    fun networking() {

        registerSpecialServerReceiver(

            netID("play"),

            {

                sentBuf: PacketByteBuf, nextBuf: PacketByteBuf ->

                nextBuf.writeString( sentBuf.readString() );       nextBuf.writeItemStack( sentBuf.readItemStack() )
                nextBuf.writeFloat( sentBuf.readFloat() );         nextBuf.writeInt( sentBuf.readInt() )
                nextBuf.writeInt( sentBuf.readInt() )

            },

            ::playFilter

        )

        registerSpecialServerReceiver(

            netID("stop"),

            {

                sentBuf: PacketByteBuf, nextBuf: PacketByteBuf ->

                nextBuf.writeString( sentBuf.readString() );       nextBuf.writeItemStack( sentBuf.readItemStack() )
                nextBuf.writeInt( sentBuf.readInt() )

            },

            ::fadeOutFilter

        )

        if ( !isClient() ) return

        var id = netID("play")
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

                val stack = findStack(netStack)

                val instrument = stack.item as Instrument

                val notes = instrument.stackSounds(stack).notes
                val device = instrument.stackSounds(stack).deviceNotes

                var sound = findSound( notes, path, semitones )

                if ( sound.isPlaying() ) sound = findSound( device, path, semitones )

                val holder = entity(id)!!;    stack.holder = holder

                if ( isMuted(holder) ) return@send

                sound.shouldNetwork = false;        sound.maxVolume = maxVolume

                sound.play(stack)

            }

        }

        id = netID("stop")
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