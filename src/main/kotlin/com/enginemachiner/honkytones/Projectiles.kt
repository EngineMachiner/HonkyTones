package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.sound.NoteProjectileSound
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.client.render.*
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.*
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.*
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object Projectiles {

    fun register() { NoteProjectileEntity.register() }

}

class NoteProjectileEntity : PersistentProjectileEntity {

    constructor( entityType: EntityType< out PersistentProjectileEntity >, world: World ) : super( entityType, world )

    constructor( world: World, entity: LivingEntity ) : super( Companion.type, entity, world )

    constructor( stack: ItemStack, world: World ) : this( world, stack.holder as LivingEntity ) {

        this.stack = stack;         val holder = stack.holder!!

        // Set projectile aim.
        val rotation = holder.rotationVecClient.multiply(1.25)
        setVelocity( rotation.x, rotation.y, rotation.z )

        onOffHand();        direction.normalize()

    }

    // @Environment(EnvType.CLIENT)
    val textureKey = ( 1..2 ).random()

    private var stack: ItemStack? = null;       private val color = randomColor()

    private var tickCount = 0;      private val tickLimit = 50

    private val direction = Vec3f( 1f, 0f, 1f )
    private val patternIndex = ( 0..4 ).random()
    private val patterns = mutableListOf(

        fun() { movement( Direction.EAST.unitVector ) },

        fun() { movement( Direction.UP.unitVector, 3f ); movement( direction, 3f ) },

        fun() { movement( Direction.SOUTH.unitVector ) },

        fun() { movement(direction) },

        fun() { movement( Direction.UP.unitVector, 3f ); movement( direction, 3f ) }

    )

    override fun tick() {

        tickCount++;    if ( tickCount > tickLimit && !isRemoved ) discard()

        for ( i in 0..4 ) if ( patternIndex == i ) { patterns[i](); break }

        super.tick()

    }

    override fun onEntityHit(entityHitResult: EntityHitResult) {

        val stack = stack ?: return;    val entity = entityHitResult.entity

        if ( this.owner == entity || entity !is LivingEntity ) return

        val instrument = stack.item as Instrument

        Instrument.Companion.ActionParticles.hit( entity, ModParticles.NOTE_IMPACT3, 2 )

        chanceHit( instrument, entity );        damage = instrument.damage.toDouble()

        super.onEntityHit(entityHitResult);     entity.stuckArrowCount = 0

        playHitSound(entity)

    }

    override fun onBlockHit(blockHitResult: BlockHitResult) { discard() }

    override fun getSoundCategory(): SoundCategory { return SoundCategory.PLAYERS }

    override fun getHitSound(): SoundEvent {

        val hitSound = Instrument.hitSounds.random()

        return Registry.SOUND_EVENT.get( hitSound.id )!!

    }

    override fun asItemStack(): ItemStack { return ItemStack.EMPTY }

    private fun onOffHand() {

        val holder = stack!!.holder!! as LivingEntity

        if ( holder.offHandStack != stack ) return

        var yaw = holder.yaw.toDouble();     yaw = rad(yaw)

        val offset = Vec3d( cos(yaw), 0.0, sin(yaw) ).multiply(1.5)
        setPosition( pos.add(offset) )

    }

    private fun playHitSound( hitEntity: Entity ) {

        val netID = netID("hit_sound");     val id = hitEntity.id

        val sender = Sender(netID) { it.write(stack).write(id) }

        sender.toClients(world)

    }

    private fun chanceHit( instrument: Instrument, entity: LivingEntity ) {

        var max = 30 - instrument.material.enchantability

        max = ( max * 0.5f ).toInt();       if ( ( 0..max ).random() > 0 ) return

        entity.addVelocity( 0.0, 0.3, 0.0 )

    }

    private var sum = 0f
    private var rate = Random.nextInt( 15, 40 ) * 0.001f

    private fun movement( direction: Vec3f ) { movement( direction, 0.125f ) }
    private fun movement( direction: Vec3f, limit: Float ) {

        val direction = Vec3d( direction.x.toDouble(), direction.y.toDouble(), direction.z.toDouble() )

        if ( sum > limit || sum < - limit ) rate = - rate * 1.125f

        velocity = velocity.add( direction.multiply( rate.toDouble() ) )

        if ( ( 0..1 ).random() == 1 ) sum += rate

    }

    companion object : ModID {

        private lateinit var type: EntityType<NoteProjectileEntity>

        private fun networking() {


            if ( !isClient() ) return


            val id = netID("hit_sound")

            Receiver(id).register { buf ->

                val stack = buf.readItemStack();        val id = buf.readInt()

                client().send {

                    val entity = entity(id) ?: return@send;         val instrument = stack.item as Instrument


                    val instrumentSound = instrument.stackSounds(stack).randomNote()

                    val sound = NoteProjectileSound( instrumentSound, entity.pos )


                    sound.play(stack);      Timer( Random.nextInt(10) ) { sound.fadeOut() }

                }

            }


        }

        fun register() {

            type = FabricEntityTypeBuilder.create( SpawnGroup.MISC, ::NoteProjectileEntity )
                .dimensions( EntityDimensions.fixed( 0.5f, 0.5f ) )
                .build()

            Registry.register( Registry.ENTITY_TYPE, classID(), type )

            clientRegister();       networking()

        }

        // @Environment(EnvType.CLIENT)
        private fun clientRegister() {

            if ( !isClient() ) return

            EntityRendererRegistry.register(type) { Renderer(it) }

        }

        // @Environment(EnvType.CLIENT)
        class Renderer( context: EntityRendererFactory.Context ) : EntityRenderer<NoteProjectileEntity>(context) {

            override fun getTexture( entity: NoteProjectileEntity ): Identifier {
                return textureID("particle/note/projectile.png")
            }

            override fun render(
                entity: NoteProjectileEntity, yaw: Float, tickDelta: Float,
                matrices: MatrixStack, vertexConsumers: VertexConsumerProvider,
                light: Int
            ) {

                matrices.push()

                val light = WorldRenderer.getLightmapCoordinates( entity.world, entity.blockPos )

                val entry = matrices.peek()
                val posMatrix: Matrix4f = entry.positionMatrix
                val normalMatrix: Matrix3f = entry.normalMatrix

                val layer = RenderLayer.getEntityTranslucent( getTexture(entity) )
                val consumer = vertexConsumers.getBuffer(layer)
                val rotation = dispatcher.rotation

                matrices.scale( SCALE, SCALE, SCALE )
                matrices.multiply(rotation)
                matrices.multiply( Vec3f.POSITIVE_X.getDegreesQuaternion(180f) )
                matrices.translate( -1.0, 0.0, -1.0 )

                if ( entity.textureKey == 2 ) {

                    matrices.multiply( Vec3f.POSITIVE_Y.getDegreesQuaternion(180f) )
                    matrices.translate( -1.0, 0.0, 0.0 )

                }

                vertex( posMatrix, normalMatrix, consumer, 0f, 1f, 0f, 0f, 1f, 0, 0, 1, light, entity.color )
                vertex( posMatrix, normalMatrix, consumer, 1f, 1f, 0f, 1f, 1f, 0, 0, 1, light, entity.color )
                vertex( posMatrix, normalMatrix, consumer, 1f, 0f, 0f, 1f, 0f, 0, 0, 1, light, entity.color )
                vertex( posMatrix, normalMatrix, consumer, 0f, 0f, 0f, 0.0f, 0f, 0, 0, 1, light, entity.color )

                matrices.pop()

            }

            companion object {

                const val SCALE = 1.25f

                private fun vertex(

                    model: Matrix4f, normal: Matrix3f, vertexConsumer: VertexConsumer,

                    x: Float, y: Float, z: Float,       u: Float, v: Float,

                    normalX: Int, normalY: Int, normalZ: Int,       light: Int, color: Vec3f

                ) {

                    vertexConsumer.vertex( model, x, y, z )
                        .color( color.x.toInt(), color.y.toInt(), color.z.toInt(), 255 )
                        .texture( u, v ).overlay( OverlayTexture.DEFAULT_UV ).light(light)
                        .normal( normal, normalX.toFloat(), normalY.toFloat(), normalZ.toFloat() )
                        .next()

                }

            }

        }

    }

}
