package com.enginemachiner.honkytones.client.sound

import com.enginemachiner.harmony.HarmonyItem.Companion.trackHolder
import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.Sender
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.client.entity
import com.enginemachiner.honkytones.client.Silencer.isMuted
import com.enginemachiner.honkytones.client.items.instruments.Instrument.netStacks
import com.enginemachiner.honkytones.client.items.instruments.Instrument.soundsCopy
import com.enginemachiner.honkytones.items.instruments.InstrumentSound.netID
import com.enginemachiner.honkytones.items.instruments.NoFading
import com.enginemachiner.honkytones.items.instruments.PlayCompletely
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

open class InstrumentSound(path: String) : StackSound(path), ModID {

    constructor( path: String, semitones: Int ) : this(path) { this.semitones = semitones }

    /** State to set some sound data manually or implemented by its own class,
     * like setting the maximum volume on MIDI receivers. */
    var isManual = false;           private var semitones = 0

    override fun setData(stack: ItemStack) {

        super.setData(stack);       val nbt = nbt(stack)

        if ( !isManual ) maxVolume = nbt.getFloat("Volume")

        if ( semitones != 0 ) pitch = 2f.pow( semitones / 12f )

    }

    override fun fadeOut() {

        val stack = stack ?: return;           val item = stack.item

        when (item) {

            is NoFading -> { stop(); return };          is PlayCompletely -> return

        }

        super.fadeOut()

    }

    override fun sendPlay() {

        val netID = netID("play");     val id = entity!!.id

        val sender = Sender(netID) {

            it.write(path).write(stack).write( maxVolume ).write( semitones ).write(id)

        }

        sender.toServer()

    }

    override fun sendStop() {

        val id = netID("stop")

        val sender = Sender(id) { it.write(path).write(stack).write(semitones) }

        sender.toServer()

    }


    fun semitones(): Int { return semitones }

    fun play(stack: ItemStack) { setData(stack);     super.play() }


    companion object {

        private fun sound( list: List<InstrumentSound?>, path: String, semitones: Int ): InstrumentSound {

            return list.filterNotNull().find { it.path == path && it.semitones() == semitones }!!

        }

        private fun sound( netStack: ItemStack, path: String, semitones: Int ): InstrumentSound {

            val sounds = soundsCopy(netStack).common()

            return sound( sounds, path, semitones )

        }

        fun networking() {

            /*

                Using the item stack sent to directly play the sounds is wrong because each time
                there is a new stack that would try to get and create stack sounds.

                It wastes resources.

                To avoid that, I'll store them and search them by an NBT ID, so they can be reused.

            */

            var id = netID("play")

            Receiver(id).register {

                val path = it.readString();        val stack = it.readItemStack()
                val maxVolume = it.readFloat();    val semitones = it.readInt()
                val id = it.readInt()

                client().send {

                    val holder = entity(id) ?: return@send

                    if ( isMuted(holder) ) return@send


                    val stack = netStacks.find(stack)

                    val sound = sound( stack, path, semitones )


                    trackHolder(stack, holder)

                    sound.canSend = false;          sound.isManual = true

                    sound.maxVolume = maxVolume;        sound.play(stack)

                }

            }


            id = netID("stop")

            Receiver(id).register {

                val path = it.readString();        val stackSent = it.readItemStack()
                val semitones = it.readInt()

                client().send {

                    val stack = netStacks.find(stackSent)

                    val sound = sound( stack, path, semitones )


                    if ( !sound.isPlaying() ) return@send;      sound.fadeOut()

                }

            }

        }

    }

}

class NoteEntitySound( sound: InstrumentSound, pos: Vec3d ) : InstrumentSound( sound.path ) {

    init {

        val semitones = sound.semitones();      this.position = pos

        if ( semitones != 0 ) pitch = 2f.pow( semitones / 12f )

        canSend = false

    }

}