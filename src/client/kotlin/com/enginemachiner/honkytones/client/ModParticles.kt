package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.client.SimpleParticle
import com.enginemachiner.honkytones.ModParticles.DEVICE_NOTE
import com.enginemachiner.honkytones.ModParticles.MUTE
import com.enginemachiner.honkytones.ModParticles.NOTE_IMPACT1
import com.enginemachiner.honkytones.ModParticles.NOTE_IMPACT2
import com.enginemachiner.honkytones.ModParticles.NOTE_IMPACT3
import com.enginemachiner.honkytones.ModParticles.SIMPLE_NOTE
import com.enginemachiner.honkytones.ModParticles.WAVE1
import com.enginemachiner.honkytones.ModParticles.WAVE2
import com.enginemachiner.honkytones.ModParticles.WAVE3
import com.enginemachiner.honkytones.ModParticles.WAVE4
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.minecraft.client.particle.ParticleFactory
import net.minecraft.client.particle.SpriteBillboardParticle
import net.minecraft.client.particle.SpriteProvider
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.particle.DefaultParticleType
import net.minecraft.util.math.Vec3d
import kotlin.random.Random
import kotlin.reflect.KFunction1

open class MuteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : FollowingParticle( clientWorld, x, y, z ) {

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

        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return WaveParticle(world, x, y, z)
            }

        }

    }

}

open class FollowingParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleParticle(clientWorld, x, y, z) {

    var entity: Entity? = null;         var offset: Vec3d = Vec3d.ZERO

    // fun offset( offBy: Vec3d ) { offset.add(offBy) }

    private fun followEntity() {

        val entity = entity!!;      val height = entity.boundingBox.lengthY

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

        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return FollowingParticle(world, x, y, z)
            }

        }

    }

}

open class TemplateParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleParticle(clientWorld, x, y, z) {

    companion object {

        open class Factory( provider: SpriteProvider ) : SimpleParticle.Companion.Factory(provider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return TemplateParticle(world, x, y, z)
            }

        }

    }

}

object ModParticles {

    private val factoryInstance: ParticleFactoryRegistry = ParticleFactoryRegistry.getInstance()

    private fun register( type: DefaultParticleType, factory: KFunction1< SpriteProvider, ParticleFactory<DefaultParticleType> > ) {

        factoryInstance.register(type, factory)

    }

    fun register() {

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

}