package com.enginemachiner.honkytones.sound

import com.enginemachiner.honkytones.canNetwork
import com.enginemachiner.honkytones.client
import com.enginemachiner.honkytones.modID
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.sound.SoundManager
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry

@Environment(EnvType.CLIENT)
open class FadingSound( val path: String ) : MovingSoundInstance(
    SoundEvent( modID(path) ), SoundCategory.PLAYERS
) {

    // No clue why.
    // Sounds get cutoff or something when the sound overlaps playing.
    // I'm trying a workaround using timesStopped to fix that.

    var entity: Entity? = null;         var maxVolume = 1f
    private var fadeIn = false;         private var fadeOut = false
    var shouldNetwork = true;           var canReplay = true
    private var timesStopped = 0;       private var volumeRate = maxVolume

    private var isPlaying = false;      protected var pos: Vec3d = Vec3d.ZERO

    override fun shouldAlwaysPlay(): Boolean { return canReplay }

    override fun tick() {

        if ( !isPlaying ) return;   fadeInTick();       fadeOutTick()

        if ( entity != null ) pos = entity!!.pos;       setPosition(pos)

    }

    private fun setPosition(pos: Vec3d) { x = pos.x; y = pos.y; z = pos.z }

    private fun getRate(): Float {

        return 0.125f * volumeRate / ( timesStopped + 1 )

    }

    private fun fadeInTick() {

        if ( !fadeIn || fadeOut ) return

        val nextVolume = volume + getRate()
        if ( nextVolume < maxVolume ) volume = nextVolume else fadeIn = false

    }

    private fun fadeOutTick() {

        if ( !fadeOut ) return

        val nextVolume = volume - getRate()
        if ( nextVolume > 0 ) volume = nextVolume else stop()

    }

    open fun playOnClients() {};    open fun fadeOutOnClients() {}

    fun play() {

        if ( volume == 0f ) return;     if ( !fadeIn ) volume = maxVolume

        if ( isPlaying() ) { stop();    timesStopped++ }

        isPlaying = true;       getManager().play(this)

        if ( !canNetwork() || !shouldNetwork ) return

        playOnClients()

    }

    protected open fun stop() {

        isPlaying = false;      fadeOut = false

        getManager().stop(this);        volume = maxVolume

        if ( !canReplay ) setDone()

    }

    open fun fadeIn() { fadeIn = true;   volume = 0f }

    open fun fadeOut() {

        fadeOut = true;     volumeRate = volume

        if ( !canNetwork() || !shouldNetwork ) return

        fadeOutOnClients()

    }

    fun setPitch(f: Float) { pitch = f }

    fun isPlaying(): Boolean { return isPlaying }
    fun isStopping(): Boolean { return fadeOut }

    fun addTimesStopped() { timesStopped++ }

    companion object {

        fun getManager(): SoundManager { return client().soundManager!! }

    }

}

@Environment(EnvType.CLIENT)
abstract class StackSound(path: String) : FadingSound(path) {

    var stack: ItemStack? = null

    protected open fun setData(stack: ItemStack) {

        this.stack = stack;       entity = stack.holder

    }

}

object Sound {

    const val MIN_DISTANCE = 15.75

    fun networking() { InstrumentSoundNetworking.networking() }

    fun modSound(path: String): SoundEvent? { return Registry.SOUND_EVENT.get( modID(path) ) }

}
