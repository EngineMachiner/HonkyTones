package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.ModParticles.NOTE_IMPACT3
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.entity.*
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.projectile.PersistentProjectileEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val particles = InstrumentItem.Companion.ActionParticles

object Projectiles {

    fun register() { NoteEntity.register() }

}

private typealias type = EntityType<out PersistentProjectileEntity>

class NoteEntity : PersistentProjectileEntity {

    constructor( entityType: type, world: World ) : super( entityType, world, ItemStack.EMPTY )

    constructor( world: World, stack: ItemStack ) : super( type(), holder(stack), world, stack )

    constructor( stack: ItemStack, world: World ) : this( world, stack ) {

        if ( !world.isClient ) setColor( randomColor().rgb )

        this.stack = stack;     aim();      onOffHand()

    }


    val tick = Tick()

    private var stack: ItemStack? = null

    private val pattern = ( 0..4 ).random()

    private val patterns = mutableListOf(

        fun() { move( Direction.EAST ) },

        fun() { move( Direction.UP, 3f );        move( direction, 3f ) },

        fun() { move( Direction.SOUTH ) },           fun() { move(direction) },

        fun() { move( Direction.UP, 3f );        move( direction, 3f ) }

    )

    val textureIndex = ( 1..2 ).random()

    override fun initDataTracker() {

        super.initDataTracker()

        dataTracker.startTracking( colorData, -1 )

    }

    override fun writeCustomDataToNbt( nbt: NbtCompound ) {

        super.writeCustomDataToNbt(nbt)

        nbt.putInt( "Color", color() )

    }

    override fun readCustomDataFromNbt( nbt: NbtCompound ) {

        super.readCustomDataFromNbt(nbt)

        setColor( nbt.getInt("Color") )

    }

    override fun tick() {

        tick.i++;    if ( tick.i > tick.limit && !isRemoved ) discard()

        patterns[pattern]();        super.tick()

    }

    override fun onEntityHit( result: EntityHitResult ) {

        val stack = stack ?: return;        val entity = result.entity

        if ( owner == entity || !entity.isAttackable || entity !is LivingEntity ) return


        val instrument = stack.item as InstrumentItem


        specialChance( instrument, entity )

        damage = instrument.damage.toDouble()

        super.onEntityHit(result)


        entity.stuckArrowCount = 0


        particles.hit( entity, NOTE_IMPACT3, 2 )

        playHitSound(entity)

    }

    override fun onBlockHit( blockHitResult: BlockHitResult ) { discard() }

    override fun getSoundCategory(): SoundCategory { return SoundCategory.PLAYERS }

    override fun getHitSound(): SoundEvent {

        val hitSound = InstrumentItem.hitSounds.random()

        return Registries.SOUND_EVENT.get( hitSound.id )!!

    }

    override fun asItemStack(): ItemStack { return ItemStack.EMPTY }


    fun color(): Int { return dataTracker.get(colorData) }

    private fun setColor( color: Int ) { dataTracker.set( colorData, color ) }

    private fun aim() {

        val holder = stack!!.holder!!

        velocity = holder.rotationVecClient.multiply(1.25)

        direction.normalize()

    }


    private fun onOffHand() {

        val holder = stack!!.holder!! as LivingEntity

        if ( holder.offHandStack != stack ) return


        var yaw = holder.yaw.toDouble();     yaw = rad(yaw)

        val offset = Vec3d( cos(yaw), 0.0, sin(yaw) ).multiply(1.5)

        setPosition( pos.add(offset) )

    }

    private fun playHitSound( entityHit: Entity ) {

        val netID = netID("hit_sound");     val id = entityHit.id

        val sender = Sender(netID) { it.write(stack).write(id) }

        sender.toClients(world)

    }

    private fun specialChance(instrument: InstrumentItem, entity: LivingEntity ) {

        var max = 30 - instrument.material.enchantability

        max = ( max * 0.5f ).toInt();       val chance = ( 0..max ).random()

        if ( chance > 0 ) return

        entity.addVelocity( 0.0, 0.3, 0.0 )

    }


    private var rateSum = 0.0

    private var rate = Random.nextInt( 15, 41 ) * 0.001


    private fun move( direction: Direction, limit: Float = 0.125f ) {

        move( direction.unitVector, limit )

    }

    private fun move( direction: Vector3f, limit: Float = 0.125f ) {

        val direction = vec3d(direction)


        if ( rateSum > limit || rateSum < - limit ) rate = - rate * 1.125f


        val add = direction.multiply(rate);         velocity = velocity.add(add)


        val chance = ( 0..1 ).random();         if ( chance == 1 ) rateSum += rate

    }

    companion object : ModID {

        override fun className(): String { return "note_projectile" }

        private val colorData = DataTracker.registerData( NoteEntity::class.java, TrackedDataHandlerRegistry.INTEGER )

        private val direction = Vector3f( 1f, 0f, 1f )

        private fun holder(stack: ItemStack): LivingEntity { return stack.holder as LivingEntity }


        private lateinit var type: EntityType<NoteEntity>

        fun type(): EntityType<NoteEntity> { return type }


        fun register() {

            type = FabricEntityTypeBuilder.create( SpawnGroup.MISC, ::NoteEntity )
                .dimensions( EntityDimensions.fixed( 0.5f, 0.5f ) )
                .build()

            Registry.register( Registries.ENTITY_TYPE, classID(), type )

        }

        class Tick { var i = 0;    val limit = 50 }

    }

}
