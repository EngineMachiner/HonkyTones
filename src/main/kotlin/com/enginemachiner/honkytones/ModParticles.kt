package com.enginemachiner.honkytones

import com.enginemachiner.harmony.Particles
import com.enginemachiner.harmony.SimpleParticle
import com.enginemachiner.harmony.isClient
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.client.particle.ParticleFactory
import net.minecraft.client.particle.SpriteBillboardParticle
import net.minecraft.client.particle.SpriteProvider
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.particle.DefaultParticleType
import net.minecraft.util.math.Vec3d
import kotlin.random.Random
import kotlin.reflect.KFunction1

open class MuteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : FollowingParticle(clientWorld, x, y, z) {

    override fun init() { scale = 0.5f }

    companion object {

        class Factory( provider: SpriteProvider ) : FollowingParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return MuteParticle(world, x, y, z)
            }

        }

    }

}

class DeviceNoteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleNoteParticle(clientWorld, x, y, z) {

    override fun init() {

        super.init();    velocityY *= ( 7..12 ).random() * 0.1f;     scale *= Random.nextInt(125) * 0.01f

        if ( ( 0..1 ).random() == 1 ) velocityY *= -0.5;        if ( ( 0..1 ).random() == 1 ) scale *= -1

    }

    companion object {

        // @Environment(EnvType.CLIENT)
        class Factory( provider: SpriteProvider ) : SimpleNoteParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return DeviceNoteParticle(world, x, y, z)
            }

        }

    }

}

open class SimpleNoteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleParticle(clientWorld, x, y, z) {

    override fun init() {

        setRandomColor();       maxAge = 10;        scale *= ( 8..14 ).random() * 0.1f

        velocityY *= 0.01;      velocityY += 0.12;      velocityY *= ( 7..12 ).random() * 0.1

        collidesWithWorld = false

    }

    fun addVelocityY(delta: Double) { velocityY += delta }

    companion object {

        // @Environment(EnvType.CLIENT)
        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return SimpleNoteParticle(world, x, y, z)
            }

        }

    }

}

class WaveParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleParticle(clientWorld, x, y, z) {

    override fun init() {

        maxAge = 25;        scale *= ( 10..20 ).random() * 0.1f

        collidesWithWorld = false

    }

    fun flip() { scale( -1f ) }

    companion object {

        // @Environment(EnvType.CLIENT)
        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return WaveParticle(world, x, y, z)
            }

        }

    }

}

open class FollowingParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleParticle(clientWorld, x, y, z) {

    var entity: Entity? = null;     var offset: Vec3d = Vec3d.ZERO

    private fun followEntity() {

        val entity = entity!!;      val height = entity.boundingBox.yLength

        val add = Vec3d( 0.0, height + scale * 3.5, 0.0 )

        val pos = entity.pos.add(offset).add(add)

        setPos( pos.x, pos.y, pos.z );      age = 0

    }

    override fun tick() {

        val entity = entity ?: return

        if ( entity.isRemoved ) { markDead(); return };     followEntity()

        super.tick()

    }

    companion object {

        // @Environment(EnvType.CLIENT)
        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return FollowingParticle(world, x, y, z)
            }

        }

    }

}

open class TemplateParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleParticle(clientWorld, x, y, z) {

    companion object {

        // @Environment(EnvType.CLIENT)
        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return TemplateParticle(world, x, y, z)
            }

        }

    }

}

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

        clientRegister()

    }

    // @Environment(EnvType.CLIENT)
    private fun clientRegister() {

        if ( !isClient() ) return

        val factoryInstance = ParticleFactoryRegistry.getInstance()

        fun register( type: DefaultParticleType, factory: KFunction1< SpriteProvider, ParticleFactory<DefaultParticleType> > ) {

            factoryInstance.register(type, factory)

        }

        register( MUTE, MuteParticle.Companion::Factory )

        register( NOTE_IMPACT1, TemplateParticle.Companion::Factory )
        register( NOTE_IMPACT2, TemplateParticle.Companion::Factory )
        register( NOTE_IMPACT3, TemplateParticle.Companion::Factory )

        register( SIMPLE_NOTE, SimpleNoteParticle.Companion::Factory )
        register( DEVICE_NOTE, DeviceNoteParticle.Companion::Factory )

        register( WAVE1, WaveParticle.Companion::Factory )
        register( WAVE2, WaveParticle.Companion::Factory )
        register( WAVE3, WaveParticle.Companion::Factory )
        register( WAVE4, WaveParticle.Companion::Factory )

    }

    val MUTE: DefaultParticleType = FabricParticleTypes.simple()

    private val NOTE_IMPACT1: DefaultParticleType = FabricParticleTypes.simple()
    private val NOTE_IMPACT2: DefaultParticleType = FabricParticleTypes.simple()
    val NOTE_IMPACT3: DefaultParticleType = FabricParticleTypes.simple()

    val SIMPLE_NOTE: DefaultParticleType = FabricParticleTypes.simple()
    val DEVICE_NOTE: DefaultParticleType = FabricParticleTypes.simple()

    val WAVE1: DefaultParticleType = FabricParticleTypes.simple()
    val WAVE2: DefaultParticleType = FabricParticleTypes.simple()
    val WAVE3: DefaultParticleType = FabricParticleTypes.simple()
    val WAVE4: DefaultParticleType = FabricParticleTypes.simple()

    val hand = listOf( NOTE_IMPACT1, NOTE_IMPACT2 )

}