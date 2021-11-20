package com.enginemachiner.honkytones

import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos

fun getSoundInstance(id: String?): HonkyTonesSoundInstance {
    val sound = SoundEvent(Identifier(id))
    return HonkyTonesSoundInstance(sound, SoundCategory.PLAYERS)
}

class HonkyTonesSoundInstance(sE: SoundEvent, sC: SoundCategory): MovingSoundInstance(sE, sC) {

    // Moving pos
    lateinit var player: PlayerEntity
    var repeat = true
    var movePos: BlockPos? = null;      var lim = 0.125f

    private var finished = false
    private var fadeout = false

    private fun setPos(newPos: BlockPos?) {
        val pos = newPos!!
        x = pos.x.toDouble(); y = pos.y.toDouble()
        z = pos.z.toDouble()
    }
    fun stop() { fadeout = true }

    override fun tick() {

        if ( !finished ) {

            if (fadeout) {
                if (volume <= 0) { setDone();   finished = true
                } else { volume -= lim }
            }

            // Moving pos
            if (player.pos != null) {
                val pos = player.pos
                x = pos.x; y = pos.y; z = pos.z
            }
            if (movePos != null) { setPos(movePos) }

        }

    }

    fun setPitch(f: Float) { pitch = f }

}