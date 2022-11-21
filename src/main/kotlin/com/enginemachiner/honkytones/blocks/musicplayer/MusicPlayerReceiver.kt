package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.Base
import com.enginemachiner.honkytones.CanBeMuted
import com.enginemachiner.honkytones.Network
import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.Instrument
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import javax.sound.midi.MidiMessage
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage

class MusicPlayerReceiver( private val musicPlayer: MusicPlayerEntity ) : Receiver {

    override fun close() {}

    override fun send( msg: MidiMessage?, timeStamp: Long ) {

        val client = MinecraftClient.getInstance()

        client.send {

            val newMsg = msg as ShortMessage;       val channel = newMsg.channel
            val command = msg.command

            val sequencer = musicPlayer.sequencer
            var b = musicPlayer.isPlaying
            if ( sequencer.tickPosition == sequencer.tickLength && b ) {
                musicPlayer.clientPause(true);      return@send
            }

            val screen = client.currentScreen
            b = screen != null && screen.isPauseScreen
            if ( b && !Network.isOnline() ) {
                musicPlayer.clientPause();      return@send
            }

            for ( i in 0..15 ) {

                val instrumentStack = musicPlayer.getStack(i)
                val nbt = instrumentStack.orCreateNbt.getCompound( Base.MOD_NAME )
                val instrument = instrumentStack.item

                if ( instrument is Instrument && i == channel ) {

                    val sounds = instrument.getSounds(instrumentStack, "notes")
                    val index = instrument.getIndexIfCentered(instrumentStack, newMsg.data1)
                    val sound = sounds[index] ?: return@send
                    val volume = newMsg.data2 / 127f
                    val companion = musicPlayer.companion

                    if ( instrumentStack.holder != companion ) {
                        instrumentStack.holder = companion
                    }

                    var b = command == ShortMessage.NOTE_OFF
                    b = b || ( command == ShortMessage.NOTE_ON && volume == 0f )
                    if ( volume > 0 && command == ShortMessage.NOTE_ON ) {

                        sound.volume = volume * nbt.getFloat("Volume")

                        var skipClient = false
                        if ( CanBeMuted.blacklist.keys.contains( companion as Entity ) ) {
                            instrument.stopAllNotes(instrumentStack, client.world)
                            skipClient = true
                        }

                        sound.playSound(instrumentStack, skipClient, true)

                    } else if ( instrument !is DrumSet && b ) {
                        sound.stopSound(instrumentStack)
                    }


                }

            }

        }

    }

}