package com.enginemachiner.honkytones

import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.entity.LivingEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

fun getSoundInstance(id: String?): HTSoundInstance {
    val newID = id!!
    val sound = SoundEvent(Identifier(newID))
    val instance = HTSoundInstance(sound, SoundCategory.PLAYERS)
    if ( id.contains("hits/hit") ) { instance.volume = 0.5f }
    return instance
}

class HTSoundInstance(sE: SoundEvent, sC: SoundCategory): MovingSoundInstance(sE, sC) {

    var entity: LivingEntity? = null
    var lim = 0f

    init { lim = 0.075f }

    private var finished = false
    private var fadeout = false

    fun setPitch(f: Float) { pitch = f }
    fun setVolume(f: Float) { volume = f }

    fun stop() { fadeout = true }

    override fun tick() {

        if ( !finished ) {

            if (fadeout) {
                if (volume <= 0) { setDone();   finished = true
                } else { volume -= lim }
            }

            // Moving pos
            if (entity != null && volume > 0) {
                val pos = entity!!.pos
                x = pos.x; y = pos.y; z = pos.z
            }

        }

    }

}