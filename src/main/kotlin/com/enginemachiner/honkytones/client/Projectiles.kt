package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.client.entity
import com.enginemachiner.honkytones.client.items.instruments.Instrument.soundsCopy
import com.enginemachiner.honkytones.NoteEntity
import com.enginemachiner.honkytones.client.sound.NoteEntitySound
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.*
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3f
import java.awt.Color
import kotlin.random.Random

private typealias Context = EntityRendererFactory.Context

object NoteEntity : ModID {

    override fun className(): String { return NoteEntity.className() }

    private fun networking() {

        val id = netID("hit_sound")

        Receiver(id).register {

            val stack = it.readItemStack();        val id = it.readInt()


            client().send {

                val entity = entity(id) ?: return@send


                val instrumentSound = soundsCopy(stack).common.random()

                val sound = NoteEntitySound( instrumentSound, entity.pos )

                val time = Random.nextInt(10)


                sound.play(stack);      Timer(time) { sound.fadeOut() }

            }

        }

    }

    fun register() {

        networking();       val type = NoteEntity.type()

        EntityRendererRegistry.register(type) { Renderer(it) }

    }

    class Renderer(context: Context) : EntityRenderer<NoteEntity>(context) {

        private val texture = textureID("particle/note/projectile.png")

        override fun getTexture( entity: NoteEntity ): Identifier { return texture }

        override fun render(
            entity: NoteEntity, yaw: Float, tickDelta: Float,
            matrices: MatrixStack, provider: VertexConsumerProvider, light: Int
        ) {

            matrices.push();        val world = entity.world;       val pos = entity.blockPos

            val rgb = entity.color()
            val color = Color(rgb).getColorComponents(null)
            val light = WorldRenderer.getLightmapCoordinates(world, pos)

            val entry = matrices.peek()
            val posMatrix = entry.model
            val normal = entry.normal

            val layer = RenderLayer.getEntityTranslucent(texture)
            val consumer = provider.getBuffer(layer)

            val rotation = dispatcher.rotation
            val rotation2 = Vec3f.POSITIVE_X.getDegreesQuaternion(180f)
            val rotation3 = Vec3f.POSITIVE_Y.getDegreesQuaternion(180f)

            matrices.scale( SCALE, SCALE, SCALE )

            matrices.multiply(rotation);    matrices.multiply(rotation2)

            matrices.translate( -1.0, 0.0, -1.0 )


            if ( entity.textureIndex == 2 ) {

                matrices.multiply(rotation3);       matrices.translate( -1.0, 0.0, 0.0 )

            }

            fun vertex( x: Float, y: Float, z: Float, u: Float, v: Float ) {

                val defaultUV = OverlayTexture.DEFAULT_UV

                consumer.vertex( posMatrix, x, y, z )
                    .color( color[0], color[1], color[2], 1f )
                    .texture( u, v ).overlay(defaultUV).light(light)
                    .normal( normal, 0f, 0f, 1f )
                    .next()

            }

            vertex( 0f, 1f, 0f, 0f, 1f )
            vertex( 1f, 1f, 0f, 1f, 1f )
            vertex( 1f, 0f, 0f, 1f, 0f )
            vertex( 0f, 0f, 0f, 0f, 0f )

            matrices.pop()

        }

        private companion object { const val SCALE = 1.25f }

    }

}
