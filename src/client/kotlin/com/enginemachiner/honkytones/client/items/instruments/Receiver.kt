package com.enginemachiner.honkytones.client.items.instruments

import com.enginemachiner.harmony.ModItemGroup
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.client.inventory
import com.enginemachiner.harmony.client.player
import com.enginemachiner.harmony.inventoryList
import com.enginemachiner.harmony.modPrint
import com.enginemachiner.honkytones.client.AbstractReceiver
import com.enginemachiner.honkytones.client.sound.InstrumentSound
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.MusicalStorageInventory
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack

private val particles = Instrument.ActionParticles

/** Direct MIDI receiver to play directly using your MIDI controller. */
class InstrumentReceiver( private val deviceID: String ) : AbstractReceiver() {

    //** Add the instruments that should be linked to the receiver. */
    private fun add( inventory: Collection<ItemStack>, current: MutableList<ItemStack> ) {

        inventory.forEach {

            val item = it.item;         val itemGroup = item.group != ModItemGroup.itemGroup

            if ( itemGroup || current.contains(it) || !it.hasNbt() ) return@forEach


            val nbt = nbt(it);          val hasDevice = nbt.getString("MIDI Device") == deviceID

            if ( hasDevice ) current.add(it);          if ( item !is MusicalStorage ) return@forEach


            val deep = MusicalStorageInventory(it).items();      add( deep, current )

        }

    }

    /** Read and filter the player's stacks. */
    private fun instruments(): MutableList<ItemStack> {

        val list = mutableListOf<ItemStack>();      val inventory = inventoryList( inventory() )

        add( inventory, list );         return list

    }

    override fun close() { modPrint("$deviceID device has been closed.") }

    override fun setData() { entity = player();       instruments = instruments() }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {

        return channel + 1 == nbt(stack).getInt("MIDI Channel")

    }

    override fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        spawnParticle( entity as PlayerEntity );    super.onPlay( sound, stack, entity )

    }

    companion object {

        fun spawnParticle( player: PlayerEntity ) {

            val i = ( 0..4 ).random();      if ( i != 0 ) return

            particles.spawn( player, "device" )

        }

    }

}
