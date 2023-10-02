package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.MusicalStorageInventory
import com.enginemachiner.honkytones.sound.InstrumentSound
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.collection.DefaultedList

private val particles = Instrument.Companion.ActionParticles

@Environment(EnvType.CLIENT)
class InstrumentReceiver( private val deviceID: String ) : GenericReceiver() {

    //** Adds the stacks that should be linked to the receiver. */
    private fun filter( stacks: DefaultedList<ItemStack>, list: MutableList<ItemStack> ) {

        stacks.forEach {

            val item = it.item

            if ( item.group != itemGroup || list.contains(it) ) return@forEach

            val nbt = NBT.get(it);          val hasTag = nbt.getString("MIDI Device") == deviceID

            if (hasTag) list.add(it);       if ( item !is MusicalStorage ) return@forEach

            val stacks2 = MusicalStorageInventory(it).items()

            filter( stacks2, list )

        }

    }

    /** Read and filter the player's stacks. */
    private fun read(player: ClientPlayerEntity): MutableList<ItemStack> {

        val list = mutableListOf<ItemStack>();      val inventory = player.inventory

        filter( inventory.main, list );             filter( inventory.offHand, list )

        return list

    }

    override fun close() { modPrint("$deviceID device has been closed.") }

    override fun setData() {

        val player = player()!!;         entity = player;       instruments = read(player)

    }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {

        return channel + 1 == NBT.get(stack).getInt("MIDI Channel")

    }

    override fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        spawnParticle( entity as PlayerEntity );    super.onPlay( sound, stack, entity )

    }

    companion object {

        fun spawnParticle( player: PlayerEntity ) {

            if ( ( 0..4 ).random() != 0 ) return

            particles.clientSpawn( player, "device" )

        }

    }

}
