package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.items.instruments.Instrument
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.render.*
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.*
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.Matrix3f
import net.minecraft.util.math.Matrix4f
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3f
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import java.util.*
import kotlin.concurrent.schedule

class Projectiles : ClientModInitializer {

    override fun onInitializeClient() {
        NoteProjectileEntity.clientRegister()
    }

    companion object {
        fun networking() { NoteProjectileEntity.networking() }
    }

}

class NoteProjectileEntity : PersistentProjectileEntity, FlyingItemEntity {

    constructor(entityType: EntityType<out PersistentProjectileEntity>, world: World)
            : super(entityType, world)

    constructor(world: World, x: Double, y: Double, z: Double)
            : super(Companion.type, x, y, z, world)

    constructor(world: World, entity: LivingEntity)
            : super(Companion.type, entity, world)

    constructor( stack: ItemStack, world: World ) : this( world, stack.holder as LivingEntity ) {

        this.stack = stack
        val living = stack.holder!!

        val rot = living.rotationVecClient.multiply(1.25)
        setVelocityClient(rot.x, rot.y, rot.z)

        if ( !world.isClient ) return

        owner as LivingEntity

        val inst = stack.item as Instrument
        val sounds = inst.getSounds(stack, "notes")
        val list = sounds.filterNotNull();      val sound = list.random()

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        nbt.putString( "projectileSound", sound.id.toString() )
        Network.sendNbtToServer(nbt)

    }

    private var stack: ItemStack? = null
    private val color: Vec3f = getRandomColor()
    private var tickCount = 0
    private val tickLimit = 100
    private val randomTexture = (0..1).random() == 0

    // initSpeed, rate
    private var speedData = mutableListOf( 0.0, 0.1 )

    private val randomPattern = (0..4).random()
    private val patterns =
        mutableListOf( this::patternOne, this::patternTwo, this::patternThree,
        this::patternFour, this::patternFive )

    init {
        if ( (0..1).random() == 1 ) speedData[1] = - speedData[1]
    }

    override fun tick() {
        tickCount++;    if ( tickCount > tickLimit ) discard()
        for ( i in 0..4 ) { if ( randomPattern == i ) patterns[i]() }
        super.tick()
    }

    override fun onEntityHit( entityHitResult: EntityHitResult? ) {

        val collideEntity = entityHitResult!!.entity
        if ( this.owner == collideEntity ) return

        val stack = stack ?: return
        if ( stack.item !is Instrument ) return

        val inst = stack.item as Instrument
        val entity = entityHitResult.entity

        if ( entity is LivingEntity ) {

            Instrument.spawnHitParticles( entity, Particles.NOTE_IMPACT )

            var num = 30 - inst.material.enchantability;        num = (num * 0.5f).toInt()
            if ( (0..num).random() == 0 ) {
                entity.addVelocity(0.0, 0.625 * 0.5f, 0.0)
            }

        }

        damage = 2.0
        super.onEntityHit(entityHitResult)

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)

        val pitch = (5..15).random() * 0.1f
        var volume = nbt.getFloat("Volume")
        if (volume == 0f) volume = 0.25f

        val players = world.players
        val id = Identifier( Base.MOD_NAME, "projectile_sound" )
        val buf = PacketByteBufs.create()

        buf.writeString( nbt.getString("projectileSound") )
        buf.writeFloat( volume );   buf.writeFloat( pitch )
        buf.writeBlockPos(blockPos)

        for (player in players) {
            player as ServerPlayerEntity
            ServerPlayNetworking.send(player, id, buf)
        }

    }

    override fun onBlockHit( blockHitResult: BlockHitResult? ) { discard() }

    override fun playSound(sound: SoundEvent?, volume: Float, pitch: Float) {
        super.playSound(sound, volume, pitch)
    }

    override fun getSoundCategory(): SoundCategory { return SoundCategory.PLAYERS }

    override fun getHitSound(): SoundEvent { return SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL }

    override fun asItemStack(): ItemStack { return ItemStack.EMPTY }

    override fun getStack(): ItemStack { return asItemStack() }

    private fun patternTemplate(x: Double, y: Double, z: Double) {
        patternTemplate(x, y, z, null, true)
    }

    private fun patternTemplate(x: Double, y: Double, z: Double, lim: Double?, b: Boolean ) {

        val lim = lim ?: 0.5
        if ( speedData[0] > lim || speedData[0] < - lim ) {
            speedData[1] = - speedData[1]
        }

        val rate = speedData[1]
        velocity = velocity.add(rate * x, rate * y, rate * z)

        if (b) speedData[0] += rate

    }

    private fun patternOne() { patternTemplate(1.0, 0.0, 0.0) }

    private fun patternTwo() {
        patternTemplate(0.0, 1.0, 0.0, 0.5, true)
        if (velocity.y < - 0.25) velocity = Vec3d( velocity.x, - 0.25, velocity.z )
    }

    private fun patternThree() { patternTemplate(0.0, 0.0, 1.0) }

    private fun patternFour() { patternTemplate(1.0, 0.0, 1.0) }

    private fun patternFive() {
        patternTemplate(0.0, 1.0, 0.0, 0.25, false)
        if (velocity.y < - 0.25) velocity = Vec3d( velocity.x, - 0.25, velocity.z )
        patternTemplate(1.0, 0.0, 1.0)
    }

    companion object {

        private lateinit var type: EntityType<NoteProjectileEntity>
        val id = Identifier( Base.MOD_NAME, "note_projectile" )

        fun networking() {

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            val id = Identifier( Base.MOD_NAME, "projectile_sound" )
            ClientPlayNetworking.registerGlobalReceiver(id) {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val id = buf.readString();      val volume = buf.readFloat()
                val pitch = buf.readFloat();    val pos = buf.readBlockPos()
                client.send {
                    val sound = CustomSoundInstance(id)
                    client.soundManager.play(sound)
                    sound.volume = volume * 0.5f;      sound.pitch = pitch
                    sound.setPos(pos);      sound.setPlayState()
                    Timer().schedule( (0..3).random() * 150L ) {
                        sound.setStopState(true)
                    }
                }

            }

        }

        fun register() {
            val typeBuilt = FabricEntityTypeBuilder.create(SpawnGroup.MISC, ::NoteProjectileEntity)
                .dimensions( EntityDimensions.fixed(0.25f, 0.25f) )
                .trackRangeBlocks(4).trackedUpdateRate(10)
                .build()
            type = Registry.register( Registry.ENTITY_TYPE, id, typeBuilt )
        }

        fun clientRegister() { EntityRendererRegistry.register(type) { Renderer(it) } }

        @Environment(EnvType.CLIENT)
        class Renderer( ctx: EntityRendererFactory.Context )
        : EntityRenderer<NoteProjectileEntity>(ctx) {

            override fun getTexture( entity: NoteProjectileEntity? ): Identifier {
                var s = ""
                if ( entity!!.randomTexture ) s = "-2"
                return Identifier(Base.MOD_NAME, "textures/particle/note/projectile$s.png")
            }

            override fun render(
                entity: NoteProjectileEntity?, yaw: Float, tickDelta: Float,
                matrices: MatrixStack?, vertexConsumers: VertexConsumerProvider?,
                light: Int
            ) {

                matrices!!.push()

                val light = WorldRenderer.getLightmapCoordinates( entity!!.world, entity.blockPos )

                val entry = matrices.peek()
                val mMat: Matrix4f = entry.model
                val nMat: Matrix3f = entry.normal

                val layer = RenderLayer.getEntityTranslucent( getTexture(entity) )
                val front = vertexConsumers!!.getBuffer( layer )
                val rot = dispatcher.rotation

                matrices.scale(scale, scale, scale)
                matrices.multiply( rot )
                matrices.multiply( Vec3f.POSITIVE_X.getDegreesQuaternion(180.0f) )
                matrices.translate( -1.0, 0.0, -1.0 )

                vertex(mMat, nMat, front, 0f, 1f, 0f, 0f, 1f, 0, 0, 1, light, entity.color)
                vertex(mMat, nMat, front, 1f, 1f, 0f, 1f, 1f, 0, 0, 1, light, entity.color)
                vertex(mMat, nMat, front, 1f, 0f, 0f, 1f, 0f, 0, 0, 1, light, entity.color)
                vertex(mMat, nMat, front, 0f, 0f, 0f, 0.0f, 0f, 0, 0, 1, light, entity.color)

                matrices.pop()

            }

            companion object {

                const val scale = 1.25f

                private fun vertex(
                    modelMatrix: Matrix4f, normalMatrix: Matrix3f, vertexConsumer: VertexConsumer,
                    x: Float, y: Float, z: Float, u: Float, v: Float,
                    normalX: Int, normalY: Int, normalZ: Int, light: Int,
                    color: Vec3f
                ) {
                    vertexConsumer.vertex(modelMatrix, x, y, z)
                        .color(color.x.toInt(), color.y.toInt(), color.z.toInt(), 255)
                        .texture(u, v)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(light)
                        .normal(normalMatrix, normalX.toFloat(), normalY.toFloat(), normalZ.toFloat())
                        .next()
                }

            }

        }

    }

}