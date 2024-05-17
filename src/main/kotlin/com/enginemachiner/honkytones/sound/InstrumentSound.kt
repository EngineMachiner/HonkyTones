package com.enginemachiner.honkytones.sound

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.items.instruments.NoFading
import com.enginemachiner.honkytones.items.instruments.PlayCompletely
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

object InstrumentSoundNetworking : ModID {

    override fun className(): String {

        return super.className().replace( "_networking", "" )

    }

    private fun inRange( addDistance: Double, current: PlayerEntity, sender: PlayerEntity ): Boolean {

        return current.blockPos.isWithinDistance( sender.pos, Sound.MIN_DISTANCE + addDistance )
                && isNotSender(current, sender)

    }

    private fun canPlay( current: PlayerEntity, sender: PlayerEntity? ): Boolean {
        return inRange( 0.0, current, sender!! )
    }

    private fun canFadeOut( current: PlayerEntity, sender: PlayerEntity? ): Boolean {
        return inRange( 1.0, current, sender!! )
    }

    private fun findSound( list: List<InstrumentSound?>, path: String, semitones: Int ): InstrumentSound {

        return list.filterNotNull().find { it.path == path && it.semitones() == semitones }!!

    }

    private fun sound( netStack: ItemStack, path: String, semitones: Int ): InstrumentSound {

        val instrument = netStack.item as Instrument


        val sounds = instrument.stackSounds(netStack)

        val notes = sounds.notes


        return findSound( notes, path, semitones )

    }

    fun networking() {

        val play = Receiver( netID("play") ) { sent, send ->

            send.write( sent.readString() ).write( sent.readItemStack() )
                .write( sent.readFloat() ).write( sent.readInt() )
                .write( sent.readInt() )

        }

        play.registerBroadcast( ::canPlay )


        val stop = Receiver( netID("stop") ) { sent, send ->

            send.write( sent.readString() ).write( sent.readItemStack() )
                .write( sent.readInt() )

        }

        stop.registerBroadcast( ::canFadeOut )


        if ( !isClient() ) return

        /*

            Using the item stack sent to directly play the sounds is wrong because each time
            there is a new stack that would try to get and create stack sounds.

            It wastes resources.

            To avoid that I'll store them and search them by an NBT ID, so they can be reused.

        */


        play.register { buf ->

            val path = buf.readString();        val stackSent = buf.readItemStack()
            val maxVolume = buf.readFloat();    val semitones = buf.readInt()
            val id = buf.readInt()

            client().send {

                val holder = entity(id) ?: return@send

                if ( isMuted(holder) ) return@send


                val stack = Instrument.find(stackSent)

                val sound = sound( stack, path, semitones )


                stack.holder = holder;              sound.shouldNetwork = false

                sound.maxVolume = maxVolume;        sound.play(stack)

            }

        }


        stop.register { buf ->

            val path = buf.readString();        val sentStack = buf.readItemStack()
            val semitones = buf.readInt()

            client().send {

                val stack = Instrument.find(sentStack)

                val sound = sound( stack, path, semitones )


                if ( !sound.isPlaying() ) return@send;      sound.fadeOut()

            }

        }

    }

}

// @Environment(EnvType.CLIENT)
open class InstrumentSound(path: String) : StackSound(path), ModID {

    constructor( path: String, semitones: Int ) : this(path) { this.semitones = semitones }

    private var semitones = 0;      var isManual = false

    override fun setData(stack: ItemStack) {

        super.setData(stack);       val nbt = NBT.get(stack)

        if ( !isManual ) maxVolume = nbt.getFloat("Volume")

        if ( semitones != 0 ) pitch = 2f.pow( semitones / 12f )

    }

    override fun fadeOut() {

        val item = stack!!.item;        if ( item is NoFading ) { stop(); return }

        if ( item is PlayCompletely ) return;      super.fadeOut()

    }

    override fun sendPlay() {

        val netID = netID("play");     val id = entity!!.id

        val sender = Sender(netID) {

            it.write(path).write(stack).write( maxVolume ).write( semitones )
                .write(id)

        }

        sender.toServer()

    }

    override fun sendFadeOut() {

        val id = netID("stop")

        val sender = Sender(id) { it.write(path).write(stack).write(semitones) }

        sender.toServer()

    }

    fun semitones(): Int { return semitones }

    fun play(stack: ItemStack) { setData(stack);     super.play() }

}

// @Environment(EnvType.CLIENT)
class NoteProjectileSound( sound: InstrumentSound, pos: Vec3d ) : InstrumentSound( sound.path ) {

    init {

        val semitones = sound.semitones();      this.pos = pos

        if ( semitones != 0 ) pitch = 2f.pow( semitones / 12f )

        shouldNetwork = false

    }

}