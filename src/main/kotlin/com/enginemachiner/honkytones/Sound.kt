package com.enginemachiner.honkytones

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.entity.LivingEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import kotlin.math.pow

fun playSound(sound: HTSound, entity: LivingEntity) {

    MinecraftClient.getInstance().soundManager.play(sound)

    // toPitch is a number of semitones to pitch
    if (sound.toPitch is Int) {
        sound.pitch = 2f.pow( ( sound.toPitch as Int / 12f ) )
    }

    val stack = entity.mainHandStack;       val item = stack.item
    if (item is Instrument && item.onUse) {
        sound.volume = stack.tag!!.getFloat("Volume")
    }

    if (sound.volume > 0) {

        sound.isPlaying = true
        sound.entity = entity

        val debugString = Base.MOD_NAME + "-playsound"

        val buf = PacketByteBufs.create()
        buf.writeString(sound.key)
        buf.writeString(sound.id.path); buf.writeString(entity.uuidAsString)
        buf.writeFloat(sound.volume);   buf.writeFloat(sound.pitch)
        ClientPlayNetworking.send( Identifier(debugString), buf )      // Sender

    } else { sound.volume = 0f;  sound.stop() }

}

fun stopSound(sound: HTSound, list: MutableList<HTSound?>) {
    val index = list.indexOf(sound)
    if (sound.isPlaying && index >= 0) {

        val debugString = Base.MOD_NAME + "-stopsound"

        val buf = PacketByteBufs.create();      buf.writeString(sound.key)
        buf.writeString(sound.id.path)
        val id = Identifier(debugString)

        sound.stop();       ClientPlayNetworking.send(id, buf)      // Sender

        // Create a new sound because fading might overlap while playing
        val path = sound.id.namespace + ":" + sound.id.path
        val newSound = HTSound(path)
        if (sound.toPitch != null) newSound.toPitch = sound.toPitch
        list[index] = newSound

    }
}

class HTSound(s: String)
    : MovingSoundInstance( SoundEvent( Identifier(s) ), SoundCategory.PLAYERS ) {

    var entity: LivingEntity? = null

    private var doFadeOut = false
    var isPlaying = false
    var key = "notes"
    var toPitch: Int? = null

    fun setPitch(f: Float) { pitch = f }
    fun setVolume(f: Float) { volume = f }
    fun isStopping(): Boolean { return doFadeOut }
    fun stop() { doFadeOut = true }

    override fun shouldAlwaysPlay(): Boolean { return true }
    override fun tick() {

        if ( isPlaying ) {

            if (doFadeOut) {
                if (volume <= 0) { isPlaying = false; doFadeOut = false;   volume = 0f
                } else { volume -= 0.1f }
            }

            // Moving pos
            if (entity != null && volume > 0) {
                val pos = entity!!.pos
                x = pos.x; y = pos.y; z = pos.z
            }

        }

    }

}