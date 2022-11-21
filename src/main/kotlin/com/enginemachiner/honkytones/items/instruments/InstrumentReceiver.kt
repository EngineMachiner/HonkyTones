package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.Base
import com.enginemachiner.honkytones.clientConfig
import com.enginemachiner.honkytones.honkyTonesGroup
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.MusicalStorageInventory
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import javax.sound.midi.MidiMessage
import javax.sound.midi.Receiver
import javax.sound.midi.ShortMessage

class InstrumentReceiver( private val id: String ) : Receiver {

    private fun checkInventory( inv: DefaultedList<ItemStack>,
                                list: MutableList<ItemStack> ) {

        for ( stack in inv ) {

            val item = stack.item
            if ( item.group == honkyTonesGroup && !list.contains(stack) ) {

                val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)
                val hasTag = nbt.getString("MIDI Device") == id
                if ( hasTag ) list.add(stack)

                if ( item is MusicalStorage ) {
                    val inv = MusicalStorageInventory(stack).getItems()
                    checkInventory( inv, list )
                }

            }

        }

    }

    // Get instrument stacks
    private fun getStacks( player: ClientPlayerEntity ): MutableList<ItemStack> {

        val list = mutableListOf<ItemStack>()
        checkInventory( player.inventory.main, list )
        checkInventory( player.inventory.offHand, list )

        return list

    }

    override fun close() { println( Base.DEBUG_NAME + "$id device has been closed." ) }
    override fun send( msg: MidiMessage?, timeStamp: Long ) {

        val client = MinecraftClient.getInstance()

        client.send {

            val player = client.player ?: return@send
            val stacks = getStacks(player)

            val newMsg = msg as ShortMessage;       val channel = newMsg.channel
            val command = msg.command

            val screen = client.currentScreen
            if ( screen != null && screen.shouldPause() ) return@send

            for ( stack in stacks ) {

                val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
                val nbtChannel = nbt.getInt("MIDI Channel")
                val instrument = stack.item as Instrument
                val sounds = instrument.getSounds(stack, "notes")

                if ( channel + 1 == nbtChannel ) {

                    val index = instrument.getIndexIfCentered(stack, newMsg.data1)
                    val sound = sounds[index] ?: return@send
                    val volume = newMsg.data2 / 127f

                    if ( command == ShortMessage.NOTE_ON && stack.holder != player ) {
                        stack.holder = player
                    }

                    var b = command == ShortMessage.NOTE_OFF
                    b = b || ( command == ShortMessage.NOTE_ON && volume == 0f )
                    if ( volume > 0 && command == ShortMessage.NOTE_ON ) {

                        sound.volume = volume * nbt.getFloat("Volume")
                        sound.playSound(stack)

                        val b1 = clientConfig["playerParticles"] as Boolean
                        if ( (0..4).random() == 0 ) {
                            if (b1) Instrument.spawnDeviceNote(player.world, player)
                            val buf = PacketByteBufs.create().writeString(player.uuidAsString)
                            val id = Identifier( Base.MOD_NAME, "player_keybind_particle" )
                            ClientPlayNetworking.send(id, buf)
                        }

                    } else if ( instrument !is DrumSet && b ) sound.stopSound(stack)

                }

            }

        }

    }

}