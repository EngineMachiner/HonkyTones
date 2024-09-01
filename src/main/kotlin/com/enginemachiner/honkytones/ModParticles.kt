package com.enginemachiner.honkytones

import com.enginemachiner.harmony.Particles
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.particle.SimpleParticleType

object ModParticles {

    private fun register( id: String, type: SimpleParticleType ) { Particles.register(id, type) }

    fun register() {

        register( "mute", MUTE )

        register( "note_impact1", NOTE_IMPACT1 )
        register( "note_impact2", NOTE_IMPACT2 )
        register( "note_impact3", NOTE_IMPACT3 )

        register( "simple_note", SIMPLE_NOTE )
        register( "device_note", DEVICE_NOTE )

        register( "wave1", WAVE1 );     register( "wave2", WAVE2 )
        register( "wave3", WAVE3 );     register( "wave4", WAVE4 )

    }

    val MUTE: SimpleParticleType = FabricParticleTypes.simple()

    val NOTE_IMPACT1: SimpleParticleType = FabricParticleTypes.simple()
    val NOTE_IMPACT2: SimpleParticleType = FabricParticleTypes.simple()

    val NOTE_IMPACT3: SimpleParticleType = FabricParticleTypes.simple()

    val SIMPLE_NOTE: SimpleParticleType = FabricParticleTypes.simple()
    val DEVICE_NOTE: SimpleParticleType = FabricParticleTypes.simple()

    val WAVE1: SimpleParticleType = FabricParticleTypes.simple()
    val WAVE2: SimpleParticleType = FabricParticleTypes.simple()
    val WAVE3: SimpleParticleType = FabricParticleTypes.simple()
    val WAVE4: SimpleParticleType = FabricParticleTypes.simple()

    val hand = listOf( NOTE_IMPACT1, NOTE_IMPACT2 )

}