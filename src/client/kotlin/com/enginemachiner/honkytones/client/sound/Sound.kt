package com.enginemachiner.honkytones.client.sound

import com.enginemachiner.harmony.client.Network.hasHandler
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.modID
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.sound.SoundManager
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.random.Random

// TODO: Fix spamming sound cut off bug. It's probably a sound engine issue. It works if the sound manager reloads the sounds.
// Still using the temporal fix where I count the times the sound is stopped and played too quick.

open class FadingSound( open val path: String ) : MovingSoundInstance( soundEvent(path), category, random() ) {

    var entity: Entity? = null;         var maxVolume = 1f;         var canSend = true

    private var stopCount = 0

    private var volumeRate = maxVolume;         private var canReplay = true

    private var fadeIn = false;                 private var fadeOut = false

    private var isPlaying = false;              protected var position: Vec3d = Vec3d.ZERO

    override fun shouldAlwaysPlay(): Boolean { return canReplay }

    override fun tick() {

        if ( !isPlaying ) return;       fadingTick()

        if ( entity != null ) position = entity!!.pos;       setPos(position)

    }

    private fun fadingTick() { fadeInTick(); fadeOutTick() }

    private fun fadeInTick() {

        if ( !fadeIn || fadeOut ) return


        val next = volume + rate()

        if ( next < maxVolume ) volume = next else fadeIn = false

    }

    private fun fadeOutTick() {

        if ( !fadeOut ) return


        val stopRate = stopCount * 1.25f + 1

        val rate = rate() / stopRate;           val next = volume - rate

        if ( next > 0 ) volume = next else stop()

    }


    fun setPitch(f: Float) { pitch = f }

    private fun setPos(pos: Vec3d) { x = pos.x; y = pos.y; z = pos.z }

    private fun rate(): Float { return 0.125f * volumeRate }

    private fun canSend(): Boolean { return hasHandler() && canSend }

    fun isPlaying(): Boolean { return isPlaying }

    fun isStopping(): Boolean { return fadeOut }


    fun play() {

        if ( maxVolume == 0f ) return;         if ( !fadeIn ) volume = maxVolume


        if ( isPlaying() ) { stopCount++; stop() }


        isPlaying = true;       manager().play(this)


        if ( !canSend() ) return;           sendPlay()

    }

    protected open fun stop() {

        isPlaying = false;      fadeOut = false;        volume = maxVolume

        manager().stop(this);           if ( !canReplay ) setDone()

    }


    open fun fadeIn() { fadeIn = true;   volume = 0f }

    open fun fadeOut() {

        fadeOut = true;     volumeRate = volume

        if ( !canSend() ) return;       sendStop()

    }


    open fun sendPlay() {};         open fun sendStop() {}

    companion object {

        private val category = SoundCategory.PLAYERS

        private fun random(): Random { return SoundInstance.createRandom() }

        private fun soundEvent(path: String): SoundEvent { val id = modID(path);    return SoundEvent.of(id) }

        fun manager(): SoundManager { return client().soundManager!! }

    }

}

abstract class StackSound(path: String) : FadingSound(path) {

    var stack: ItemStack? = null

    protected open fun setData(stack: ItemStack) {

        this.stack = stack;       entity = stack.holder

    }

}
