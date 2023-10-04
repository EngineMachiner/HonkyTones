package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerEntity
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.BlockWithEntity
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.particle.Particle
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.reflect.KClass

/** Searches a client world entity by its id. Mostly used on networking. */
@Environment(EnvType.CLIENT)
fun entity(id: Int): Entity? { return world()!!.getEntityById(id) }

@Environment(EnvType.CLIENT)
fun player(): ClientPlayerEntity? { return client().player }

@Environment(EnvType.CLIENT)
fun world(): ClientWorld? { return client().world }

fun addVelocity( entity: Entity, delta: Vec3d ) { entity.addVelocity( delta.x, delta.y, delta.z ) }

/** Entities that can be muted. */
interface CanBeMuted {

    /** Tries to mute the entity. Returns a boolean if the entity can be muted. */
    fun mute( player: PlayerEntity, entity: Entity ): Boolean {

        return mute( player, entity, Vec3d( 0.0, -1.0, 0.0 ) )

    }

    /** Tries to mute the entity and sets the offset of the mute particle.
     * Returns a boolean if the entity can be muted. */
    fun mute( player: PlayerEntity, entity: Entity, offset: Vec3d ): Boolean {

        val b = mute( player, entity, entity::class );      val particle = blacklist[entity]

        if ( !b || particle == null ) return b;             particle as MuteParticle

        particle.offset = particle.offset.add(offset);      return true

    }

    /** Mutes the entity if the class is the same as the entity's class.
     * Returns a boolean if the entity can be muted. */
    fun mute( player: PlayerEntity, entity: Entity, kClass: KClass<out Entity> ): Boolean {

        val b = player.isInSneakingPose && kClass.isInstance(entity)

        if ( !b || !player.world.isClient ) return b

        // Spawn or remove the particle.
        if ( !isMuted(entity) ) {

            blacklist[entity] = spawnMuteParticle(entity)

        } else {

            blacklist[entity]!!.markDead();     blacklist.remove(entity)

        }

        return true

    }

    @Environment(EnvType.CLIENT)
    private fun spawnMuteParticle(entity: Entity): Particle {

        val particle = Particles.spawnOne( Particles.MUTE, Vec3d.ZERO ) as MuteParticle

        particle.entity = entity;       return particle

    }

    companion object {

        val blacklist = mutableMapOf<Entity, Particle>()

        fun isMuted(entity: Entity): Boolean {

            if ( entity is MusicPlayerEntity ) {

                val musicPlayer = world()!!.getBlockEntity( entity.blockPos )

                if ( musicPlayer !is MusicPlayerBlockEntity ) return true

                return !musicPlayer.isListening

            }

            return blacklist.contains(entity)

        }

    }

}

abstract class BlockWithEntity(settings: Settings) : BlockWithEntity(settings), ModID