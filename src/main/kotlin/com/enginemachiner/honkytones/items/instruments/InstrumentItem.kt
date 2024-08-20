package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.NBT.trackHand
import com.enginemachiner.harmony.NBT.trackSlot
import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreenHandler
import com.enginemachiner.honkytones.items.instruments.InstrumentItem.Companion.ActionParticles
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.minecraft.block.AirBlock
import net.minecraft.block.BlockState
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.AbstractHorseEntity
import net.minecraft.entity.passive.MerchantEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.ToolMaterial
import net.minecraft.nbt.NbtCompound
import net.minecraft.particle.DefaultParticleType
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.registry.tag.BlockTags
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.UseAction
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import kotlin.math.abs
import kotlin.random.Random

// TODO: Ring of notes as shield.

private typealias AttributesBuilder = ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier>
private typealias AttributeMap = Multimap<EntityAttribute, EntityAttributeModifier>
private typealias Attributes = ImmutableMultimap<EntityAttribute, EntityAttributeModifier>?

open class InstrumentItem(

    val damage: Float,      val useSpeed: Float,        material: ToolMaterial

) : ToolItem( material, settings(material) ), Silencer {


    private var attributes: Attributes = null

    private fun init() { setAttributes() };     init { init() }

    //** Remember to call it from both sides. */
    @BasedOn("Based on item cooldown behaviour.")
    protected fun cooldown(stack: ItemStack): Float {

        val player = player(stack)

        return player.getAttackCooldownProgress(0.5f)

    }

    override fun trackTick( stack: ItemStack, slot: Int ) { trackHand(stack);    trackSlot(stack, slot) }

    override fun getUseAction(stack: ItemStack): UseAction { return UseAction.BOW }

    override fun getMaxUseTime(stack: ItemStack): Int { return 200 }

    override fun getAttributeModifiers( slot: EquipmentSlot ): AttributeMap {

        val onMain = slot == EquipmentSlot.MAINHAND

        return if (onMain) attributes!! else super.getAttributeModifiers(slot)

    }

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();                    val center = stack.item !is DrumSet

        nbt.putString( "Sequence", "" );            nbt.putString( "lastSequence", "" )
        nbt.putString( "Action", "Melee" );         nbt.putInt( "MIDI Channel", 1 )
        nbt.putFloat( "Volume", 1f );               nbt.putBoolean( "Center Notes", center )
        nbt.putInt( "ID", stack.hashCode() )

        return nbt

    }

    private fun playTickCheck( stack: ItemStack, player: PlayerEntity ) {

        // Stop playing on screen or when switching stacks.

        val screen = player.currentScreenHandler

        val isOnScreen = screen !is PlayerScreenHandler

        val onConsole = screen is DigitalConsoleScreenHandler


        val nbt = nbt(stack)

        val onHand = nbt.getInt("Hand") != -1

        val isActive = player.activeItem == stack


        var stop = !onHand && !isActive;        stop = stop || isOnScreen


        if ( stop && !onConsole ) stopSounds(stack)

    }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        if ( world.isClient || entity !is PlayerEntity ) return

        playTickCheck(stack, entity)

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);      trackHolder(stack, user)

        val canUse = canUse(stack, hand);           val action = TypedActionResult.pass(stack)


        if ( !canUse ) return action;        rangedAttack(stack)

        sendAction( stack, "play" );        return action

    }

    override fun useOnEntity( stack: ItemStack, player: PlayerEntity, entity: LivingEntity, hand: Hand ): ActionResult {

        val result = ActionResult.PASS;         val action = action(stack);         val world = player.world


        val isRanged = action == "Ranged";          val isForced = isForced(player, entity)

        val isMuting = !isRanged && mute( player, entity, PlayerEntity::class )

        if ( isMuting || isRanged || !isForced ) return result

        use( world, player, hand );         trackHolder(stack, player)

        when( action ) {

            "Melee" -> attack( stack, entity );         "Push" -> push( stack, entity )

        }


        return result

    }

    // Triggers once even if you have 2 instruments on hands.
    // Stop the off hand stack instrument too if there are 2 stacks on hands.

    override fun onStoppedUsing( stack: ItemStack, world: World, user: LivingEntity, remainingUseTicks: Int ) {

        if ( world.isClient || user !is PlayerEntity ) return


        val mainStack = user.mainHandStack;         val offStack = user.offHandStack

        if ( stack == mainStack && offStack.item is InstrumentItem ) stopSounds(offStack)

        stopSounds(stack)

    }

    fun action( stack: ItemStack ): String { return Companion.action(stack) }

    companion object : ModID {

        private const val MIN_PUSH_SPEED = 4.5f

        val hitSounds = mutableListOf<SoundEvent>()

        val enchantments = mutableListOf(

            Enchantments.FIRE_ASPECT,   Enchantments.KNOCKBACK,
            Enchantments.LOOTING,       Enchantments.SMITE,
            Enchantments.MENDING

        )

        val classes = listOf(

            DrumSet::class,

            AcousticGuitar::class,      ElectricGuitar::class,      ElectricGuitarClean::class,

            Harp::class,                Viola::class,               Violin::class,
            Banjo::class,               Cello::class,               Koto::class,

            Trombone::class,            Recorder::class,            Oboe::class,
            Accordion::class,           MutedTrumpet::class,        Trumpet::class,
            Sax::class,

            Kalimba::class,             Marimba::class,             MusicBox::class,
            SFX::class,                 Xylophone::class,

            Organ::class,               Keyboard::class,               Harpsichord::class,
            ElectricPiano::class,       Rhodes::class,

            BassSynth::class,           BassLeadSynth::class,       Bass2Synth::class,
            CelesteSynth::class,        DocSynth::class,            MetalPadSynth::class,
            PolySynth::class,           SawSynth::class,            SineSynth::class,
            SquareSynth::class,         StringsSynth::class

        )

        private val interactiveMobs = listOf( AbstractHorseEntity::class,     MerchantEntity::class )

        override fun className(): String { return "instrument" }

        fun stopSounds(stack: ItemStack) { sendAction(stack, "stop") }

        private fun sendAction( stack: ItemStack, id: String ) {

            val player = stack.holder ?: return;          val world = player.world

            if ( world.isClient ) return;           player as PlayerEntity


            val nbt = nbt(stack);         val slot = nbt.getInt("Slot")


            val id = netID(id);          val sender = Sender(id) { it.write(slot) }

            sender.toClient(player)

        }

        fun networking() { ActionParticles.networking() }

        private fun action( stack: ItemStack ): String { return nbt(stack).getString("Action") }

        private fun settings( material: ToolMaterial ): Settings {

            return modItemSettings().maxDamage( material.durability )

        }

        /** Returns if the attack is forced. */
        fun isForced( player: PlayerEntity, entity: LivingEntity ): Boolean {

            val isInteractive = interactiveMobs.find { it.isInstance(entity) } != null

            val isSneaking = player.isSneaking;         return isInteractive && isSneaking || !isInteractive

        }

        fun mobPlay( mob: LivingEntity ) {

            val min = Particles.MIN_DISTANCE;       val id = mob.id


            val netID = netID("mob_play")

            val sender = Sender(netID) { it.write(id) }

            sender.toClients( mob.world ) { it, _ ->

                it.blockPos.isWithinDistance( mob.pos, min )

            }


            ActionParticles.spawn( mob.server!!, id, "simple" )

        }

        fun registerHitSounds() {

            for ( i in 1..9 ) {

                val sound = Register.sound("hit$i");        hitSounds.add(sound)

            }

        }

        class PushData( stack: ItemStack ) {

            private val instrument = stack.item as InstrumentItem

            val speed = MIN_PUSH_SPEED + instrument.useSpeed

            fun strength( cooldown: Float, b: Float = 2f ): Double {

                var strength = cooldown / speed;       strength *= b

                return strength.toDouble()

            }

        }

        object ActionParticles {

            private fun canSpawn( entity: Entity ): Boolean {

                val config = Config.server()
                val player = entity.isPlayer && config.playerParticles
                val mob = entity is MobEntity && config.mobParticles

                return player || mob

            }

            private fun offset(): Double {

                return Random.nextInt( - 100, 100 ) * 0.01

            }

            //** Spawn hit particles. */
            fun hit( entity: Entity, particle: ParticleEffect, n: Int = 4 ) {

                val world = entity.world;       if ( world.isClient ) return;       world as ServerWorld


                val box = entity.boundingBox

                val x = box.xLength * 0.75f;     val y = box.yLength * 0.5f;    val z = box.zLength * 0.75f


                for ( i in 1..n ) {

                    val x = x * offset();       val z = z * offset()

                    var y = y;      y *= 1 + offset()


                    val offset = Vec3d(x, y, z);       val pos = entity.pos.add(offset)

                    Particles.spawn( world, particle, pos )

                }

            }

            /** Sends and spawns particles to the clients. */
            fun spawn( server: MinecraftServer, id: Int, particleName: String ) {

                val world = server.overworld;       val entity = world.getEntityById(id) ?: return


                val netID = netID("particle");      val canSpawn = canSpawn(entity)

                val sender = Sender(netID) { it.write(id).write( particleName ) }


                val min = Particles.MIN_DISTANCE;       val pos = entity.pos

                sender.toClients(world) { it, _ ->

                    it.blockPos.isWithinDistance(pos, min) && canSpawn

                }

            }

            fun networking() {

                val id = netID("particle")

                Receiver(id).register { server, _, buf ->

                    val id = buf.readInt();      val type = buf.readString()

                    serverSend(server) { spawn(server, id, type) }

                }

            }

        }

    }

    fun canUse( stack: ItemStack, hand: Hand ): Boolean {

        val user = player(stack);       val mainStack = user.mainHandStack

        val isActive = user.activeItem == stack


        // Only set the hand when the player has one instrument.

        var single = hand == Hand.OFF_HAND && mainStack.item !is InstrumentItem

        single = single || hand == Hand.MAIN_HAND


        // Using setCurrentHand() can control the sound length.

        if (single) user.setCurrentHand(hand)


        if ( isActive ) return false;     return true

    }

    private fun attack( stack: ItemStack, attacked: LivingEntity ) {

        val cooldown = cooldown(stack)

        val player = player(stack);         player.attack(attacked)

        val world = player.world;           if ( world.isClient ) return


        // Launch chance hit.

        var chance = 30 - material.enchantability;          chance = ( 0 .. chance ).random()

        if ( chance == 0 ) attacked.addVelocity( 0.0, 0.6, 0.0 )


        // Spawn particles.

        val nbt = nbt(stack);           val hand = nbt.getInt("Hand")

        val particle = ModParticles.hand[hand];         ActionParticles.hit( attacked, particle )


        playHitSound(attacked)


        // Apply damage.

        var damage = damage + material.attackDamage;      damage *= cooldown

        val source = DamageSource.player(player);       attacked.damage(source, damage)


        damage(stack)

    }

    private fun push( stack: ItemStack, pushed: LivingEntity ) {

        val player = player(stack);             val cooldown = cooldown(stack)


        val canPush = Config.server().allowPushingPlayers

        if ( pushed.isPlayer && !canPush ) return


        // Pushing is tied to the cooldown and mining speed.

        val data = PushData(stack);         val speed = data.speed

        val strength = data.strength(cooldown)

        val direction = player.rotationVector.normalize()

        var y = abs( direction.y ) + 1 / speed;     y *= cooldown * 0.5f

        var delta = direction.multiply(strength)

        delta = Vec3d( delta.x, y, delta.z )

        addVelocity( pushed, delta )


        player.resetLastAttackedTicks()

    }

    private fun spawnProjectile( stack: ItemStack, world: World ) {

        val projectile = NoteEntity( stack, world )

        world.spawnEntity(projectile)

    }

    //** Spawn from 1 to multiple note projectiles. */
    private fun rangedAttack(stack: ItemStack) {

        val player = player(stack);         val world = player.world

        val isRanged = action(stack) == "Ranged";           if ( !isRanged ) return


        var damage = 1

        if ( !player.isSneaking ) spawnProjectile(stack, world) else {

            val n = 6;      var ticks = 0;      damage = 3

            for ( i in 1 .. n ) {

                Timer(ticks) { spawnProjectile(stack, world) };     ticks += 6

            }

        }


        // Chance to break.

        val chance = ( 0..1 ).random()

        if ( chance != 0 || world.isClient ) return

        damage(stack, damage)

    }


    private fun setAttributes() {

        val builder: AttributesBuilder = ImmutableMultimap.builder()


        // Attack speed attribute modifier.

        val attribute = object {

            val id = ATTACK_SPEED_MODIFIER_ID

            val name = "Weapon modifier";       val value = useSpeed.toDouble()

            val operation = EntityAttributeModifier.Operation.ADDITION

        }

        val modifier = EntityAttributeModifier( attribute.id, attribute.name, attribute.value, attribute.operation )

        val key = EntityAttributes.GENERIC_ATTACK_SPEED

        builder.put( key, modifier )


        attributes = builder.build()

    }

    protected fun playSoundEffect( entity: LivingEntity, sound: SoundEvent, pitch: Float ) {

        val world = entity.world;       val category = SoundCategory.PLAYERS;       val volume = 0.5f

        world.playSoundFromEntity( null, entity, sound, category, volume, pitch )

    }

    private fun playHitSound(entity: LivingEntity) {

        val sound = hitSounds.random();         val index = hitSounds.indexOf(sound)


        val gap = Random.nextInt(1, 5)

        val pitch = ( 75..125 ).random() * 0.01f

        var delay = 0;      var times = 1

        when (index) {

            5 -> times = Random.nextInt(1, 5)

            in 6..8 -> times = Random.nextInt(4)

        }


        for ( i in 1..times ) {

            Timer(delay) { playSoundEffect(entity, sound, pitch) }

            delay += gap

        }

    }

}

/** Instruments that don't fade out and play until the end. */
interface PlayCompletely

/** Instruments that stop immediately. */
interface NoFading

open class Keyboard : InstrumentItem( 5f, -2.4f, MusicalQuartz() )
class Organ : InstrumentItem( 5f, -3.5f, MusicalIron() )

open class DrumSet : InstrumentItem( 3.5f, -3f, MusicalIron() ), PlayCompletely
open class AcousticGuitar : InstrumentItem( 3f, -2.4f, MusicalString() )

open class ElectricGuitar : InstrumentItem( 4f, -2.4f, MusicalRedstone() ) {

    private val miningSpeed = MusicalRedstone().miningSpeedMultiplier

    private val effectiveBlocks = BlockTags.AXE_MINEABLE

    private fun isEffective(state: BlockState): Boolean { return state.isIn(effectiveBlocks) }

    override fun getMiningSpeedMultiplier( stack: ItemStack, state: BlockState ): Float {

        val isEffective = isEffective(state);       return if (isEffective) miningSpeed else 1.0f

    }

    override fun canMine( state: BlockState, world: World, pos: BlockPos, miner: PlayerEntity ): Boolean { return true }

    override fun postMine( stack: ItemStack, world: World, state: BlockState, pos: BlockPos, miner: LivingEntity ): Boolean {

        val isEffective = isEffective(state)

        val postMine = super.postMine( stack, world, state, pos, miner ) // What does this bool do?

        if ( !isEffective ) return postMine


        damage( stack, 1, miner );      return postMine

    }

}

class ElectricGuitarClean : ElectricGuitar() {

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);          val action = TypedActionResult.pass(stack)

        val canUse = user.isOnFire && user.isSneaking && canUse(stack, hand)

        if ( canUse ) extinguish(stack, user) else super.use( world, user, hand )

        return action

    }

    override fun useOnEntity( stack: ItemStack, player: PlayerEntity, entity: LivingEntity, hand: Hand ): ActionResult {

        val canHelp = !isForced( player, entity ) && entity.wasOnFire && entity !is HostileEntity

        val action = ActionResult.CONSUME


        if (canHelp) { extinguish(stack, entity);   return action }

        return super.useOnEntity( stack, player, entity, hand )

    }

    private fun extinguish( stack: ItemStack, entity: LivingEntity ) {

        val player = player(stack);         val world = player.world

        if ( world.isClient ) return


        ActionParticles.hit( entity, particleType );   entity.extinguish()


        val damage = ( material.durability * 0.1f ).toInt();        damage( stack, damage )


        val sound = modSound("magic.c3-e3_")!!

        val pitch = ( 75..125 ).random() * 0.01f

        playSoundEffect(player, sound, pitch)

    }

    private companion object {

        val particleType: DefaultParticleType = ParticleTypes.LANDING_OBSIDIAN_TEAR

    }

}

class Harp : InstrumentItem( 2f, -1f, MusicalString() )
class Recorder : InstrumentItem( 1.25f, -1.5f, MusicalString() )
class Oboe : InstrumentItem( 3.25f, -1f, MusicalIron() )

open class Viola : InstrumentItem( 3.5f, -2f, MusicalString() )
open class Violin : InstrumentItem( 3.75f, -2f, MusicalRedstone() )

open class Trombone : InstrumentItem( 5f, -3f, MusicalRedstone() ) {

    private fun thrust( stack: ItemStack ) {

        val player = player(stack);         val data = Companion.PushData(stack)

        val cooldown = cooldown(stack);     val strength = data.strength(cooldown)

        val y = Random.nextDouble( 0.875, 1.125 )

        val direction = player.rotationVecClient
            .normalize().multiply( - strength )
            .multiply(2.0, y, 2.0 )

        addVelocity( player, direction );           player.resetLastAttackedTicks()

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val result = super.use( world, user, hand );        val stack = user.getStackInHand(hand)

        val action = action(stack)


        val rotation = user.rotationVector.multiply(4.0);       val eyePos = user.eyePos

        val shape = RaycastContext.ShapeType.OUTLINE;       val fluid = RaycastContext.FluidHandling.NONE

        val context = RaycastContext( eyePos, eyePos.add(rotation), shape, fluid, user )

        val raycast = world.raycast(context)


        val pos = raycast.blockPos;         val isAir = world.getBlockState(pos).block is AirBlock


        val thrust = action == "Thrust" && user.isOnGround && !isAir

        if ( !thrust ) return result;           thrust(stack)

        damage(stack);          return result

    }

}

class Accordion : InstrumentItem( 2.25f, -2.5f, MusicalIron() )
class Kalimba : InstrumentItem( 1f, -0.5f, MusicalIron() ), PlayCompletely
class Koto : InstrumentItem( 3.5f, -2f, MusicalIron() )

class Marimba : InstrumentItem( 2.5f, -1.25f, MusicalString() ), PlayCompletely
class Xylophone : InstrumentItem( 2.5f, -1.25f, MusicalString() )

class ElectricPiano : Keyboard();       class Harpsichord : Keyboard()
class Rhodes : Keyboard()

abstract class Synth : Keyboard()

class BassSynth : Synth();              class BassLeadSynth : Synth()
class Bass2Synth : Synth();             class CelesteSynth : Synth()
class DocSynth : Synth();               class MetalPadSynth : Synth()
class PolySynth : Synth();              class SawSynth : Synth()
class SineSynth : Synth();              class SquareSynth : Synth()
class StringsSynth : Synth()

class Banjo : AcousticGuitar();         class Cello : InstrumentItem( 2f, -2f, MusicalString() )

class MutedTrumpet : Trombone();        class Trumpet : Trombone()
class Sax : Trombone()

class MusicBox : Viola()

class SFX : InstrumentItem( 3.5f, -3f, MusicalIron() ), ColorItem {

    override fun getSetupNBT( stack: ItemStack ): NbtCompound {

        val nbt = super.getSetupNBT(stack);         return setColor(nbt)

    }

    companion object { lateinit var registeredItem: Item }

}
