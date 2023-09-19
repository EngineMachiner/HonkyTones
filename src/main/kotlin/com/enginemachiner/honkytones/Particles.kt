package com.enginemachiner.honkytones

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.client.particle.*
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.particle.DefaultParticleType
import net.minecraft.particle.ParticleEffect
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import kotlin.random.Random

open class MuteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : FollowingParticle( clientWorld, x, y, z ) {

    override fun setting() { scale = 0.5f }

    companion object {

        class Factory(spriteProvider: SpriteProvider) : FollowingParticle.Companion.Factory(spriteProvider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return MuteParticle( world, x, y, z )
            }

        }

    }

}

class DeviceNoteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SimpleNoteParticle( clientWorld, x, y, z ) {

    override fun setting() {

        super.setting();    velocityY *= ( 7..12 ).random() * 0.1f;     scale *= Random.nextInt(125) * 0.01f

        if ( ( 0..1 ).random() == 1 ) velocityY *= -0.5;        if ( ( 0..1 ).random() == 1 ) scale *= -1

    }

    companion object {

        @Environment(EnvType.CLIENT)
        class Factory(spriteProvider: SpriteProvider) : SimpleNoteParticle.Companion.Factory(spriteProvider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return DeviceNoteParticle( world, x, y, z )
            }

        }

    }

}

open class SimpleNoteParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : BaseParticle( clientWorld, x, y, z ) {

    override fun setting() {

        setRandomColor();       maxAge = 10;        scale *= ( 8..14 ).random() * 0.1f

        velocityY *= 0.01;      velocityY += 0.12;      velocityY *= ( 7..12 ).random() * 0.1

        collidesWithWorld = false

    }

    fun addVelocityY(delta: Double) { velocityY += delta }

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return SimpleNoteParticle( world, x, y, z )
            }

        }

    }

}

class WaveParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : BaseParticle( clientWorld, x, y, z ) {

    override fun setting() { maxAge = 25;   scale *= ( 10..20 ).random() * 0.1f;    collidesWithWorld = false }

    fun flip() { scale( -1f ) }

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return WaveParticle( world, x, y, z )
            }

        }

    }

}

open class FollowingParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : BaseParticle( clientWorld, x, y, z ) {

    var entity: Entity? = null;     var offset: Vec3d = Vec3d.ZERO

    private fun followEntity() {

        val entity = entity!!;      val height = entity.boundingBox.yLength

        val pos = entity.pos.add(offset).add( Vec3d( 0.0, height + scale * 3.5, 0.0 ) )

        setPos( pos.x, pos.y, pos.z );      age = 0

    }

    override fun tick() {

        entity ?: return;       val entity = entity!!

        if ( entity.isRemoved ) { markDead(); return };     followEntity()

        super.tick()

    }

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return FollowingParticle( world, x, y, z )
            }

        }

    }

}

/** This particle is registered replacing BaseParticle. */
open class TemplateParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : BaseParticle( clientWorld, x, y, z ) {

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return TemplateParticle( world, x, y, z )
            }

        }

    }

}

open class BaseParticle( clientWorld: ClientWorld, x: Double, y: Double, z: Double ) : SpriteBillboardParticle( clientWorld, x, y, z ) {

    init { init() };     private fun init() { setting() };      open fun setting() {}

    fun setRandomColor() { val color = randomColor();   setColor( color.x, color.y, color.z ) }

    companion object {

        @Environment(EnvType.CLIENT)
        abstract class Factory(spriteProvider: SpriteProvider) : ParticleFactory<DefaultParticleType> {

            private var spriteProvider: SpriteProvider;     init { this.spriteProvider = spriteProvider }

            open fun template( world: ClientWorld, x: Double, y: Double, z: Double ): SpriteBillboardParticle {
                return BaseParticle( world, x, y, z )
            }

            override fun createParticle(
                parameters: DefaultParticleType?, world: ClientWorld?,
                x: Double, y: Double, z: Double,
                velocityX: Double, velocityY: Double, velocityZ: Double
            ): Particle {

                val particle = template( world!!, x, y, z );        particle.setSprite(spriteProvider)

                return particle

            }

        }

    }

    override fun getType(): ParticleTextureSheet { return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE }

}

class Particles : ModInitializer, ClientModInitializer {

    private fun register( path: String, particle: DefaultParticleType ) {
        Registry.register( Registry.PARTICLE_TYPE, modID(path), particle )
    }

    override fun onInitialize() {

        register( "mute", MUTE )

        register( "note_impact1", NOTE_IMPACT1 )
        register( "note_impact2", NOTE_IMPACT2 )
        register( "note_impact3", NOTE_IMPACT3 )

        register( "simple_note", SIMPLE_NOTE )
        register( "device_note", DEVICE_NOTE )

        register( "wave1", WAVE1 );     register( "wave2", WAVE2 )
        register( "wave3", WAVE3 );     register( "wave4", WAVE4 )

    }

    override fun onInitializeClient() {

        val registry = ParticleFactoryRegistry.getInstance()

        registry.register( MUTE, MuteParticle.Companion::Factory )

        registry.register( NOTE_IMPACT1, TemplateParticle.Companion::Factory )
        registry.register( NOTE_IMPACT2, TemplateParticle.Companion::Factory )
        registry.register( NOTE_IMPACT3, TemplateParticle.Companion::Factory )

        registry.register( SIMPLE_NOTE, SimpleNoteParticle.Companion::Factory )
        registry.register( DEVICE_NOTE, DeviceNoteParticle.Companion::Factory )

        registry.register( WAVE1, WaveParticle.Companion::Factory )
        registry.register( WAVE2, WaveParticle.Companion::Factory )
        registry.register( WAVE3, WaveParticle.Companion::Factory )
        registry.register( WAVE4, WaveParticle.Companion::Factory )

    }

    companion object {

        const val MIN_DISTANCE = 36.0

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

        private fun spawn( world: ServerWorld, particle: ParticleEffect, count: Int, pos: Vec3d, delta: Vec3d, speed: Double ) {
            world.spawnParticles( particle, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed )
        }

        fun spawnOne( world: ServerWorld, particle: ParticleEffect, pos: Vec3d ) {
            spawn( world, particle, 1, pos, Vec3d.ZERO, 0.0 )
        }

        @Environment(EnvType.CLIENT)
        fun spawnOne( particle: ParticleEffect, pos: Vec3d ): Particle? {
            return spawnOne( particle, pos, Vec3d.ZERO )
        }

        @Environment(EnvType.CLIENT)
        fun spawnOne( particle: ParticleEffect, pos: Vec3d, delta: Vec3d ): Particle? {

            val manager = client().particleManager

            return manager.addParticle( particle, pos.x, pos.y, pos.z, delta.x, delta.y, delta.z )

        }

    }

}