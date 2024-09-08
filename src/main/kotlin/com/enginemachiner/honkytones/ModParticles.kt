package com.enginemachiner.honkytones

import com.enginemachiner.harmony.Particles
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.particle.DefaultParticleType

object ModParticles {

    private fun register( id: String, type: DefaultParticleType ) { Particles.register(id, type) }

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

    val MUTE: DefaultParticleType = FabricParticleTypes.simple()

    val NOTE_IMPACT1: DefaultParticleType = FabricParticleTypes.simple()
    val NOTE_IMPACT2: DefaultParticleType = FabricParticleTypes.simple()

    val NOTE_IMPACT3: DefaultParticleType = FabricParticleTypes.simple()

    val SIMPLE_NOTE: DefaultParticleType = FabricParticleTypes.simple()
    val DEVICE_NOTE: DefaultParticleType = FabricParticleTypes.simple()

    val WAVE1: DefaultParticleType = FabricParticleTypes.simple()
    val WAVE2: DefaultParticleType = FabricParticleTypes.simple()
    val WAVE3: DefaultParticleType = FabricParticleTypes.simple()
    val WAVE4: DefaultParticleType = FabricParticleTypes.simple()

    val hand = listOf( NOTE_IMPACT1, NOTE_IMPACT2 )

}