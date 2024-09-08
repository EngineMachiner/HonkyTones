package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.SOUND_MIN_DISTANCE
import net.minecraft.entity.player.PlayerEntity

object InstrumentSound : ModID {

    private fun inRange( addDistance: Double, current: PlayerEntity, sender: PlayerEntity ): Boolean {

        val distance = SOUND_MIN_DISTANCE + addDistance;            val pos = sender.pos

        return current.blockPos.isWithinDistance(pos, distance) && isNotSender(current, sender)

    }

    private fun canPlay( current: PlayerEntity, sender: PlayerEntity? ): Boolean {
        return inRange( 0.0, current, sender!! )
    }

    private fun canStop( current: PlayerEntity, sender: PlayerEntity? ): Boolean {
        return inRange( 1.0, current, sender!! )
    }

    fun networking() {

        var id = netID("play")

        val play = Receiver(id) { sent, send ->

            send.write( sent.readString() ).write( sent.readItemStack() )
                .write( sent.readFloat() ).write( sent.readInt() )
                .write( sent.readInt() )

        }

        play.registerBroadcast(::canPlay)


        id = netID("stop")

        val stop = Receiver(id) { sent, send ->

            send.write( sent.readString() ).write( sent.readItemStack() )
                .write( sent.readInt() )

        }

        stop.registerBroadcast(::canStop)

    }

}