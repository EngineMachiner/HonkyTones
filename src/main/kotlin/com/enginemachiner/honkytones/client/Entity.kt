package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.client.Particles
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.client.entity
import com.enginemachiner.honkytones.ModParticles.MUTE
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.Vec3d

/** Can mute entities. */
object Silencer : ModID {

    private val BLOCK_OFFSET = Vec3d( 0.0, -1.0, 0.0 )

    private fun offset(entity: Entity): Vec3d {

        if ( entity is MusicPlayerEntity ) return BLOCK_OFFSET

        return Vec3d.ZERO

    }

    private fun particle( offset: Vec3d ): MuteParticle {

        return Particles.spawnOne( MUTE, offset ) as MuteParticle

    }

    private fun add(entity: Entity) {

        val offset = offset(entity);        val particle = particle(offset)

        particle.entity = entity


        blacklist[entity] = particle

    }

    private fun remove(entity: Entity) {

        val particle = blacklist[entity]!!;         particle.markDead()

        blacklist.remove(entity)

    }

    private fun mute(entity: Entity) {

        val isMuted = isMuted(entity)

        if ( !isMuted ) add(entity) else remove(entity)

    }

    private val blacklist = mutableMapOf<Entity, MuteParticle>()

    fun isMuted(entity: Entity): Boolean { return blacklist.contains(entity) }

    fun networking() {

        val id = netID("mute")

        Receiver(id).register {

            val id = it.readInt()


            client().send {

                val entity = entity(id) ?: return@send

                mute(entity)

            }

        }

    }



}