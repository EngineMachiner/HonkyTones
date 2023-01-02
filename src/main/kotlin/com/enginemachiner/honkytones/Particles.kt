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
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry

class NoteProjectileParticle(clientWorld: ClientWorld, x: Double, y: Double, z: Double) : MuteParticle(clientWorld, x, y, z) {

    init {
        scale *= 0.75f
        followPosOffset = Vec3d.ZERO
    }

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template(world: ClientWorld, x: Double, y: Double, z: Double): SpriteBillboardParticle {

                val particle = NoteProjectileParticle(world, x, y, z)
                val color = getRandomColor()
                if ( (0..1).random() == 1 ) particle.scale(-2f)
                particle.setColor(color.x, color.y, color.z)

                return particle

            }

        }

    }

}

open class MuteParticle(clientWorld: ClientWorld, x: Double, y: Double, z: Double) : BaseParticle(clientWorld, x, y, z) {

    init { scale *= 2f;     followPosOffset = Vec3d(0.0, 2.6, 0.0) }

    override fun tick() {
        val entity = entity ?: return
        if (entity.isRemoved) markDead()
        followEntity(entity); super.tick()
    }

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template(world: ClientWorld, x: Double, y: Double, z: Double): SpriteBillboardParticle {
                return MuteParticle(world, x, y, z)
            }

        }

    }

}

@Verify("Particle movement")
class DeviceNoteParticle(clientWorld: ClientWorld, x: Double, y: Double, z: Double) : SimpleNoteParticle(clientWorld, x, y, z) {

    init {

        val random = mutableListOf<Float>()
        for ( i in 1..3 ) {
            var a = (5..15).random() * 0.1f
            if ( (0..1).random() == 1 ) a *= -1
            random.add(a)
        }

        velocityX *= random[0];     velocityY *= random[1]
        velocityZ *= random[2]

        if ( (0..1).random() == 1 ) scale *= -1

    }

    companion object {

        @Environment(EnvType.CLIENT)
        class Factory(spriteProvider: SpriteProvider) : SimpleNoteParticle.Companion.Factory(spriteProvider) {

            override fun template(world: ClientWorld, x: Double, y: Double, z: Double): SpriteBillboardParticle {
                val particle = DeviceNoteParticle(world, x, y, z)
                val color = getRandomColor()
                particle.setColor(color.x, color.y, color.z)
                return particle
            }

        }

    }

}

@Verify("Vanilla movement")
open class SimpleNoteParticle(clientWorld: ClientWorld, x: Double, y: Double, z: Double) : BaseParticle(clientWorld, x, y, z) {

    init {

        velocityX *= 0.009999999776482582
        velocityY *= 0.009999999776482582
        velocityZ *= 0.009999999776482582
        velocityY += 0.2
        scale *= 1.5f
        maxAge = 6

        velocityY *= (10..20).random() * 0.1
        scale *= (1..4).random() * 0.5f

    }

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template(world: ClientWorld, x: Double, y: Double, z: Double): SpriteBillboardParticle {

                val particle = SimpleNoteParticle(world, x, y, z)
                val color = getRandomColor()
                particle.setColor(color.x, color.y, color.z)

                return particle

            }

        }

    }

}

open class TemplateParticle(clientWorld: ClientWorld, x: Double, y: Double, z: Double) : BaseParticle(clientWorld, x, y, z) {

    companion object {

        @Environment(EnvType.CLIENT)
        open class Factory(spriteProvider: SpriteProvider) : BaseParticle.Companion.Factory(spriteProvider) {

            override fun template(world: ClientWorld, x: Double, y: Double, z: Double): SpriteBillboardParticle {
                return TemplateParticle(world, x, y, z)
            }

        }

    }

}

open class BaseParticle(clientWorld: ClientWorld, x: Double, y: Double, z: Double) : SpriteBillboardParticle(clientWorld, x, y, z) {

    var entity: Entity? = null
    var followPosOffset: Vec3d = Vec3d.ZERO

    open fun followEntity(entity: Entity?) {

        if (this.entity != entity) this.entity = entity

        if (entity != null) {
            val p = followPosOffset
            setPos( entity.x + p.x, entity.y + p.y, entity.z + p.z )
        } else markDead()

        age = 0

    }

    companion object {

        @Environment(EnvType.CLIENT)
        abstract class Factory(spriteProvider: SpriteProvider) : ParticleFactory<DefaultParticleType> {

            private var spriteProvider: SpriteProvider
            init { this.spriteProvider = spriteProvider }

            open fun template(world: ClientWorld, x: Double, y: Double, z: Double): SpriteBillboardParticle {
                return BaseParticle(world, x, y, z)
            }

            override fun createParticle(
                parameters: DefaultParticleType?, world: ClientWorld?,
                x: Double, y: Double, z: Double,
                velocityX: Double, velocityY: Double, velocityZ: Double
            ): Particle {

                val particle = template(world!!, x, y, z)
                particle.setSprite(spriteProvider)

                return particle

            }

        }

    }

    // Is this the default type?
    override fun getType(): ParticleTextureSheet {
        return ParticleTextureSheet.PARTICLE_SHEET_OPAQUE
    }

}

class Particles : ModInitializer, ClientModInitializer {

    companion object {

        const val minRadius = 40f

        val MUTE: DefaultParticleType = FabricParticleTypes.simple()
        val NOTE_PROJECTILE: DefaultParticleType = FabricParticleTypes.simple()
        val NOTE_PROJECTILE_2: DefaultParticleType = FabricParticleTypes.simple()
        val NOTE_IMPACT: DefaultParticleType = FabricParticleTypes.simple()
        val SIMPLE_NOTE: DefaultParticleType = FabricParticleTypes.simple()
        val DEVICE_NOTE: DefaultParticleType = FabricParticleTypes.simple()

    }

    private fun register(path: String, particle: DefaultParticleType) {
        val id = Identifier(Base.MOD_NAME, path)
        Registry.register(Registry.PARTICLE_TYPE, id, particle)
    }

    override fun onInitialize() {
        register("mute", MUTE)
        register("note-projectile", NOTE_PROJECTILE)
        register("note-projectile-2", NOTE_PROJECTILE_2)
        register("note-impact", NOTE_IMPACT)
        register("use-note", SIMPLE_NOTE)
        register("midi-note", DEVICE_NOTE)
    }

    override fun onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(
            MUTE, MuteParticle.Companion::Factory
        )

        ParticleFactoryRegistry.getInstance().register(
            NOTE_PROJECTILE, NoteProjectileParticle.Companion::Factory
        )

        ParticleFactoryRegistry.getInstance().register(
            NOTE_PROJECTILE_2, NoteProjectileParticle.Companion::Factory
        )

        ParticleFactoryRegistry.getInstance().register(
            NOTE_IMPACT, TemplateParticle.Companion::Factory
        )

        ParticleFactoryRegistry.getInstance().register(
            SIMPLE_NOTE, SimpleNoteParticle.Companion::Factory
        )

        ParticleFactoryRegistry.getInstance().register(
            DEVICE_NOTE, DeviceNoteParticle.Companion::Factory
        )
    }

}