package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.GenericReceiver
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.modPrint
import com.enginemachiner.honkytones.sound.InstrumentSound
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack

@Environment(EnvType.CLIENT)
class MusicPlayerReceiver( private val blockEntity: MusicPlayerBlockEntity ) : GenericReceiver() {

    override fun close() { modPrint("$entity: Device has been closed.") }

    override fun setData() {

        entity = blockEntity.entity!!

        val instruments = mutableListOf<ItemStack>()

        for ( i in 0..15 ) instruments.add( blockEntity.getStack(i) )

        this.instruments = instruments

    }

    override fun canPlay( stack: ItemStack, channel: Int ): Boolean {

        val instrument = stack.item;    val index = instruments.indexOf(stack)

        return instrument is Instrument && index == channel

    }

    override fun onPlay( sound: InstrumentSound, stack: ItemStack, entity: Entity ) {

        val instrument = stack.item as Instrument

        if ( isMuted(entity) ) {

            instrument.stopDeviceSounds(stack);     setData()

            sound.setData(stack);   sound.playOnClients()

        } else super.onPlay( sound, stack, entity )

    }

}
