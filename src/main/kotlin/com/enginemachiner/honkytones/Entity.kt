package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerCompanion
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.particle.Particle
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Vec3d
import kotlin.reflect.KClass

object Entity

@Environment(EnvType.CLIENT)
fun findByUuid(client: MinecraftClient, uuid: String): Entity? {

    var entity: Entity? = null
    val list = mutableListOf( client.world!!.entities, MusicPlayerCompanion.entities )
    for ( entities in list ) {
        entity = entities.find { it.uuidAsString == uuid }
        if (entity != null) break
    }

    return entity

}

/** Entities that can be muted */
interface CanBeMuted {

    // Default offset
    fun shouldBlacklist( player: PlayerEntity, entity: Entity ): Boolean {
        return shouldBlacklist( player, entity, Vec3d(0.5, -1.0, 0.5) )
    }

    fun shouldBlacklist( player: PlayerEntity, entity: Entity, offset: Vec3d ): Boolean {

        val b = shouldBlacklist( player, entity, entity::class )

        var particle = blacklist[entity]

        if ( !b || particle == null ) return b

        particle = particle as MuteParticle
        particle.followPosOffset = particle.followPosOffset.add(offset)

        return true

    }

    fun shouldBlacklist( player: PlayerEntity, entity: Entity,
                         kClass: KClass<out Entity> ): Boolean {

        val b = player.isInSneakingPose
        if ( b && kClass.isInstance(entity) ) {

            if (player.world.isClient) {
                if ( !blacklist.keys.contains(entity) ) {
                    blacklist[entity] = spawnMuteNote(entity)
                } else {
                    blacklist[entity]!!.markDead()
                    blacklist.remove(entity)
                }
            }

            return true

        }

        return false

    }

    private fun spawnMuteNote(entity: Entity): Particle {

        val client = MinecraftClient.getInstance()
        val manager = client.particleManager

        val particle = manager.addParticle(
            Particles.MUTE, entity.x, entity.y, entity.z,
            0.0, 0.0, 0.0
        ) as MuteParticle
        particle.entity = entity

        return particle

    }

    companion object {
        val blacklist = mutableMapOf<Entity, Particle>()
    }

}