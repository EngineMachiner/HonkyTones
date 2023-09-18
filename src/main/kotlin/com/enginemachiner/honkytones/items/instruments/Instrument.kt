package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import com.enginemachiner.honkytones.MusicTheory.completeSet
import com.enginemachiner.honkytones.MusicTheory.instrumentFiles
import com.enginemachiner.honkytones.MusicTheory.index
import com.enginemachiner.honkytones.MusicTheory.noteCount
import com.enginemachiner.honkytones.MusicTheory.sharpsToFlats
import com.enginemachiner.honkytones.MusicTheory.shift
import com.enginemachiner.honkytones.NBT.networkNBT
import com.enginemachiner.honkytones.NBT.trackHand
import com.enginemachiner.honkytones.NBT.trackSlot
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.sound.InstrumentSound
import com.enginemachiner.honkytones.sound.Sound
import com.enginemachiner.honkytones.sound.Sound.modSound
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.registry.FuelRegistry
import net.minecraft.block.AirBlock
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.client.world.ClientWorld
import net.minecraft.enchantment.Enchantment
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
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.StackReference
import net.minecraft.item.ItemStack
import net.minecraft.item.ToolMaterial
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.screen.slot.Slot
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.tag.BlockTags
import net.minecraft.util.ActionResult
import net.minecraft.util.ClickType
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import org.lwjgl.glfw.GLFW
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val particles = Instrument.Companion.ActionParticles

// TODO: Fix stop sound on death.
open class Instrument(
    val damage: Float, val useSpeed: Float, material: ToolMaterial
) : ToolItem( material, createSettings(material) ), CanBeMuted {

    // Instruments sounds to be copied for each new stack.

    @Environment(EnvType.CLIENT)
    private var soundsTemplate: SoundsTemplate? = null

    @Environment(EnvType.CLIENT)
    private val stacksSounds = mutableMapOf<Int, Sounds>()

    private var attributes: ImmutableMultimap<EntityAttribute, EntityAttributeModifier>? = null

    init { init() }

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();              val shouldCenter = stack.item !is DrumSet

        nbt.putString( "Sequence", "" );      nbt.putString( "SequenceInput", "" )
        nbt.putString( "Action", "Melee" );   nbt.putInt( "MIDI Channel", 1 )
        nbt.putFloat( "Volume", 1f );         nbt.putBoolean( "Center Notes", shouldCenter )
        nbt.putInt( "ID", stack.hashCode() )

        return nbt

    }

    override fun trackTick(stack: ItemStack, slot: Int ) {

        trackHand(stack);       trackSlot( stack, slot )

    }

    override fun inventoryTick( stack: ItemStack?, world: World?, entity: Entity?, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        val nbt = NBT.get(stack!!)

        if ( !world!!.isClient || entity !is PlayerEntity ) return

        val isOnConsole = currentScreen() is DigitalConsoleScreen
        val shouldStop = nbt.getInt("Hand") == -1 && entity.activeItem != stack

        // Stop playing on screen or when switching stacks.
        if ( ( shouldStop || isOnScreen() ) && !isOnConsole ) {
            onStoppedUsing( stack, world, entity, 0 )
        }

        Tick.onKey()

    }

    override fun onClicked(
        stack: ItemStack?, otherStack: ItemStack?, slot: Slot?, clickType: ClickType?,
        player: PlayerEntity?, cursorStackReference: StackReference?
    ): Boolean {

        val world = player!!.world

        if ( world.isClient ) { stopSounds(stack!!); stopDeviceSounds(stack) }

        return super.onClicked( stack, otherStack, slot, clickType, player, cursorStackReference )

    }

    override fun getAttributeModifiers( slot: EquipmentSlot? ): Multimap<EntityAttribute, EntityAttributeModifier> {

        val onMain = slot == EquipmentSlot.MAINHAND

        return if (onMain) attributes!! else super.getAttributeModifiers(slot)

    }

    override fun use( world: World?, user: PlayerEntity, hand: Hand? ): TypedActionResult<ItemStack>? {

        val stack = user.getStackInHand(hand);      val nbt = NBT.get(stack)

        val mainStack = user.mainHandStack;         val action = TypedActionResult.pass(stack)

        val wasActive = user.activeItem == stack

        // Only set the hand when the player has one instrument.
        var hasOne = hand == Hand.OFF_HAND && mainStack.item !is Instrument
        hasOne = hasOne || hand == Hand.MAIN_HAND

        // Using setCurrentHand() can control the sound length.
        if (hasOne) user.setCurrentHand(hand)

        if (wasActive) return action;       rangedAttack( stack, user )

        if ( !world!!.isClient ) return action

        val isRanged = nbt.getString("Action") == "Ranged"
        if ( !isRanged ) particles.clientSpawn( user, "simple" )

        if ( !loadSequence(stack) ) stackSounds(stack).randomNote().play(stack)

        networkNBT(nbt);        return action

    }

    // TODO: interactive mobs make this trigger a lot idk why.
    override fun useOnEntity(
        stack: ItemStack?, player: PlayerEntity?, entity: LivingEntity?, hand: Hand?
    ): ActionResult {

        val player = player!!;          val entity = entity!!
        val nbt = NBT.get(stack!!);     val action = nbt.getString("Action")
        val result = ActionResult.PASS

        // Mute a player.
        val isRanged = action == "Ranged"

        val willMute = !isRanged && mute( player, entity, PlayerEntity::class )
        if ( willMute || isRanged ) return result

        use( player.world, player, hand )

        val cooldown = player.getAttackCooldownProgress(0.5f)
        if ( action == "Melee" ) attack( stack, player, entity, cooldown )
        if ( action == "Push" ) push( stack, player, entity, cooldown )

        return result

    }

    // TODO: Verify how long.
    override fun getMaxUseTime( stack: ItemStack? ): Int { return 200 }

    override fun onStoppedUsing(
        stack: ItemStack?, world: World?, user: LivingEntity?, remainingUseTicks: Int 
    ) {

        val user = user!!;      val world = world!!

        if ( !world.isClient || NBT.get(stack!!).getBoolean("onKey") ) return

        // Stop the off hand stack instrument if there are 2 stacks on hands.
        val mainStack = user.mainHandStack;       val offStack = user.offHandStack
        if ( stack == mainStack && offStack.item is Instrument ) {
            offStack.onStoppedUsing( world, user, remainingUseTicks )
        }

        stopSounds(stack)

    }

    companion object : ModID {

        init { setEnchantments() }

        lateinit var enchants : Multimap<Enchantment, Int>

        val stacks = mutableListOf<ItemStack>()

        val classes = mutableListOf(
            DrumSet::class,             Keyboard::class,            Organ::class,
            AcousticGuitar::class,      ElectricGuitar::class,      ElectricGuitarClean::class,
            Harp::class,                Viola::class,               Violin::class,
            Trombone::class,            Recorder::class,               Oboe::class,
        )

        val hitSounds = mutableListOf<SoundEvent>()

        @Environment(EnvType.CLIENT)
        open class SoundsTemplate( private val instrument: Instrument ) {

            val notes = MutableList<InstrumentSound?>( noteCount() ) { null }

            init { loadNotes() }

            /** Add the instrument sounds. */
            private fun loadNotes() {

                val files = instrumentFiles[ instrument::class ]!!

                val isRanged = files.first().contains( Regex("-[A-Z]") )

                val className = ( instrument as ModID ).className()

                // 1. Assign each sound.

                for ( fileName in files ) {

                    val path = className + '.' + fileName.lowercase()

                    if ( !isRanged ) {

                        // Each file is a sound. No pitch tweaking.

                        val index = completeSet.indexOf(fileName)

                        notes[index] = InstrumentSound(path)

                    } else {

                        // There is a range of notes. There is pitch tweaking.

                        val pair1 = Regex("^[A-Z]-?\\d_?").find(fileName)!!.value
                        val pair2 = Regex("[A-Z]-?\\d_?$").find(fileName)!!.value

                        // Semitones distance
                        val index1 = index(pair1);    var index2 = index(pair2)

                        index2 = shift( index2, index1 );   val length = index2 - index1

                        for ( i in 0..length ) {

                            val sound = InstrumentSound( path, i )
                            val index = completeSet.indexOf(pair1) + i
                            notes[index] = sound

                        }

                    }

                }

                if ( instrument is DrumSet ) return

                // 2. Add border pitch sounds.

                val lowIndexes = mutableListOf<Int>();       val highIndexes = mutableListOf<Int>()

                notes.filterNotNull().forEach {

                    // Only pick sounds with no pitch tweaking and assign their indexes.

                    if ( it.semitones() != 0 ) return@forEach

                    val i = notes.indexOf(it)

                    val back = notes[ i - 1 ];           val front = notes[ i + 1 ]

                    if ( back == null && front != null ) lowIndexes.add(i)
                    else if ( back != null && front == null ) highIndexes.add(i)

                }

                border( lowIndexes, -1 );       border( highIndexes, 1 )

            }

            private fun border( indexes: MutableList<Int>, direction: Int ) {

                for ( i in indexes ) {

                    val path = notes[i]!!.path

                    // 12 semitones max.
                    ( 1..12 ).forEach {

                        val direction = it * direction
                        val index = i + direction

                        if ( notes[index] != null ) return@forEach

                        val sound = InstrumentSound( path, direction )

                        notes[index] = sound

                    }

                }

            }

        }

        @Environment(EnvType.CLIENT)
        open class Sounds(instrument: Instrument) {

            private val template = instrument.soundsTemplate!!

            val notes = MutableList<InstrumentSound?>( noteCount() ) { null }
            val deviceNotes = notes.toMutableList()

            init { loadNotes() }

            private fun loadNotes() {

                val former = template.notes

                former.forEach {

                    it ?: return@forEach

                    val i = former.indexOf(it)
                    val path = it.path;     val semitones = it.semitones()

                    notes[i] = InstrumentSound( path, semitones )
                    deviceNotes[i] = InstrumentSound( path, semitones )

                }

            }

            fun randomNote(): InstrumentSound { return notes.filterNotNull().random() }

        }

        object ActionParticles {

            private val functions = mapOf(
                "simple" to ::spawnSimpleNote,      "device" to ::spawnDeviceNote
            )

            // TODO: Ring of notes as shield.

            @Environment(EnvType.CLIENT)
            private fun spawnDeviceNote(entity: Entity) {

                val slices = 12
                val radius = Random.nextInt(750) * 0.001 + 1
                val angle = Random.nextInt(slices) * 360.0 / slices
                val height =  Random.nextInt( 50,175 ) * 0.01

                val data = Vec3d( radius, angle, height )

                spawnNote( Particles.DEVICE_NOTE, entity, data )

            }

            private var isMain = false
            private const val HANDS_ANGLE = 15

            @Environment(EnvType.CLIENT)
            private fun spawnSimpleNote(entity: Entity) {

                var data = Vec3d( 1.5, 0.0, 1.5 )

                var n = 0
                entity.handItems.forEach { if ( it.item is Instrument ) n++; }

                if ( n == 2 ) {

                    data = if ( !isMain ) Vec3d( data.x, data.y + HANDS_ANGLE, data.z )
                    else Vec3d( data.x, data.y - HANDS_ANGLE, data.z )

                    isMain = !isMain

                } else isMain = false

                spawnNote( Particles.SIMPLE_NOTE, entity, data )

            }

            @Environment(EnvType.CLIENT)
            fun spawnNote(particle: ParticleEffect, entity: Entity, data: Vec3d ) {

                val world = entity.world

                if ( world !is ClientWorld ) return

                val radius = data.x;     val angleOffset = data.y;      val height = data.z

                var yaw = entity.bodyYaw + 90.0 + angleOffset;      yaw = degreeToRadians(yaw)

                val angle = Vec3d( cos(yaw), 0.0, sin(yaw) ).multiply(radius)

                val pos = entity.pos.add(angle).add( Vec3d( 0.0, height, 0.0 ) )

                Particles.spawnOne( particle, pos )

            }

            //** Spawn 4 hit particles. */
            fun hit( entity: Entity, particleType: ParticleEffect ) {
                hit( entity, particleType, 4 )
            }

            //** Spawn hit particles. */
            fun hit( entity: Entity, particleType: ParticleEffect, n: Int ) {

                val world = entity.world;       if ( world.isClient ) return

                val box = entity.boundingBox

                val x = box.xLength * 0.75f;     val y = box.yLength * 0.5f
                val z = box.zLength * 0.75f

                for ( i in 1..n ) {

                    val x = x * Random.nextInt( - 100, 100 ) * 0.01
                    val y = y * ( 1 + Random.nextInt( - 100, 100 ) * 0.01 )
                    val z = z * Random.nextInt( - 100, 100 ) * 0.01

                    val pos = entity.pos.add( Vec3d( x, y, z ) )

                    Particles.spawnOne( world as ServerWorld, particleType, pos )

                }

            }

            private fun canSpawn( config: Map<String, Any>, entity: Entity ): Boolean {

                val playerParticles = config["player_particles"] as Boolean
                val mobParticles = config["mob_particles"] as Boolean

                var canSpawn = entity.isPlayer && playerParticles
                canSpawn = canSpawn || entity is MobEntity && mobParticles

                return canSpawn

            }

            //** Spawn particles on client to be networked. */
            @Environment(EnvType.CLIENT)
            fun clientSpawn( entity: Entity, particleName: String ) {

                val id = netID("particle")

                val buf = PacketByteBufs.create()
                buf.writeInt( entity.id );          buf.writeString(particleName)

                ClientPlayNetworking.send( id, buf )

            }

            fun serverSpawn( server: MinecraftServer, entityID: Int, particleName: String ) {

                val id = netID("particle")

                val world = server.overworld

                val entity = world.getEntityById(entityID) ?: return

                val buf = PacketByteBufs.create()
                buf.writeInt(entityID);       buf.writeString(particleName)

                val players = world.players

                players.forEach {

                    val distance = Particles.MIN_DISTANCE

                    var canSpawn = it.blockPos.isWithinDistance( entity.pos, distance )
                    canSpawn = canSpawn && canSpawn( serverConfig, entity )

                    if ( !canSpawn ) return@forEach

                    ServerPlayNetworking.send( it, id, buf )

                }

            }

            fun networking() {

                val id = netID("particle")
                ServerPlayNetworking.registerGlobalReceiver(id) {

                    server: MinecraftServer, _: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                    val id = buf.readInt();      val type = buf.readString()

                    server.send( ServerTask( server.ticks ) { serverSpawn( server, id, type ) } )

                }

                if ( !isClient() ) return

                ClientPlayNetworking.registerGlobalReceiver(id) {

                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                    val id = buf.readInt();      val type = buf.readString()

                    client.send {

                        val entity = entity(id) ?: return@send

                        val canSpawn = canSpawn( clientConfig, entity )

                        if ( !canSpawn ) return@send

                        functions[type]!!(entity)

                    }

                }

            }

        }

        @Environment(EnvType.CLIENT)
        private object Tick {

            private fun play(stack: ItemStack) {

                val player = player()!!;       val world = player.world

                val instrument = stack.item as Instrument

                val play = KeyBindings.play!!;      val nbt = NBT.get(stack)

                val isPressed = play.isPressed;     val onKey = nbt.getBoolean("onKey")

                if ( isPressed && !onKey ) {

                    nbt.putBoolean( "onKey", true )

                    instrument.keyUse(stack)

                } else if ( !isPressed && onKey ) {

                    nbt.putBoolean( "onKey", false )

                    instrument.onStoppedUsing( stack, world, player, 0 )

                }

            }

            private var wasPressed = false
            private fun reset( stack: ItemStack, isLast: Boolean ) {

                val reset = KeyBindings.reset!!;      val isPressed = reset.isPressed

                if ( !isPressed ) wasPressed = false;       if ( wasPressed || !isPressed ) return

                val nbt = NBT.get(stack);                   val sequence = nbt.getString("Sequence")

                nbt.putString( "SequenceInput", sequence );  networkNBT(nbt)

                if ( !isLast ) return

                wasPressed = true

                warnUser( Translation.get("message.resetSequences") )

            }

            /** Won't open if player has two instruments. */
            private fun menu( stack: ItemStack, canOpen: Boolean ) {

                val client = client()

                val menu = KeyBindings.menu!!;      if ( !menu.isPressed ) return

                if (canOpen) client.setScreen( InstrumentsScreen(stack) )

            }

            fun onKey() {

                val player = player()!!

                val handItems = player.handItems.toSet();      val size = handItems.size

                val instruments = handItems.filter { it.item is Instrument }.toSet()

                if ( instruments.isEmpty() ) return

                for ( stack in instruments ) {

                    play(stack);        reset( stack, stack == instruments.last() )

                    menu( stack, instruments.size < size )

                }

            }

        }

        fun mobPlay(mob: MobEntity) {

            val world = mob.world

            val players = world.players.filter {
                it.blockPos.isWithinDistance( mob.pos, Sound.MIN_DISTANCE )
            }

            val id = netID("mob_play")

            val buf = PacketByteBufs.create();      buf.writeInt( mob.id )

            players.forEach { ServerPlayNetworking.send( it as ServerPlayerEntity, id, buf ) }

            particles.serverSpawn( mob.server!!, mob.id, "simple" )

        }

        @Environment(EnvType.CLIENT)
        object KeyBindings {

            var play: KeyBinding? = null;       var menu: KeyBinding? = null;       var reset: KeyBinding? = null

            fun register() {

                val key = "key.$MOD_NAME";      val category = "category.$MOD_NAME.instrument"

                var keybind = KeyBinding( "$key.play", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
                play = KeyBindingHelper.registerKeyBinding(keybind)

                keybind = KeyBinding( "$key.menu", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, category )
                menu = KeyBindingHelper.registerKeyBinding(keybind)

                keybind = KeyBinding( "$key.reset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
                reset = KeyBindingHelper.registerKeyBinding(keybind)

            }

        }

        private fun createSettings(material: ToolMaterial): Settings {
            return defaultSettings().maxDamage( material.durability )
        }

        // Works using the enchanting mixins.
        private fun setEnchantments() {

            val builder = ImmutableMultimap.builder<Enchantment, Int>()

            for ( i in 1..4 ) {

                if ( i < 3 ) {

                    builder.put( Enchantments.FIRE_ASPECT, i )
                    builder.put( Enchantments.KNOCKBACK, i )

                }

                if ( i == 3 ) builder.put( Enchantments.LOOTING, i )
                else builder.put( Enchantments.SMITE, i )

            }

            builder.put( Enchantments.MENDING, 1 );       builder.put( RangedEnchantment(), 1 )

            enchants = builder.build()

        }

        fun registerFuel() {

            val registry = FuelRegistry.INSTANCE

            var time = 300 * 3 + 100 * 3
            registry.add( modItem( AcousticGuitar::class ), time + 100 )
            registry.add( modItem( Harp::class ), time - 100 )
            registry.add( modItem( Viola::class ), time )


            time += 2400       // A blaze rod.
            time += ( time * 0.25f ).toInt()
            registry.add( modItem( Violin::class ), time )

        }

        fun networking() {

            ActionParticles.networking()

            if ( !isClient() ) return

            val id = netID("mob_play")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val id = buf.readInt()
                val world = client.world!!

                client.send {

                    val mob = world.getEntityById(id) ?: return@send
                    mob as MobEntity

                    val stack = mob.mainHandStack
                    val instrument = stack.item as Instrument

                    stack.holder = mob

                    val sounds = instrument.stackSounds(stack)
                    val sound = sounds.randomNote()
                    sound.play(stack)

                    instrument.stopSounds(stack)

                }

            }

        }

    }

    private fun init() {

        setAttributes();        if ( !isClient() ) return

        soundsTemplate = SoundsTemplate(this)

    }

    @Environment(EnvType.CLIENT)
    private fun keyUse(stack: ItemStack) {

        val player = player()!!

        particles.clientSpawn( player, "simple" )

        if ( !loadSequence(stack) ) stackSounds(stack).randomNote().play(stack)


    }

    @Environment(EnvType.CLIENT)
    fun soundIndex(stack: ItemStack, index: Int ): Int {

        if ( index == -1 ) return index

        val nbt = NBT.get(stack);       val sounds = stackSounds(stack).notes

        if ( !nbt.getBoolean("Center Notes") ) return index

        val filter = sounds.filterNotNull();        var next = index

        if ( filter.size < 24 ) return index

        val first = filter.first();     val last = filter.last()

        while ( next > sounds.size - 1 ) next -= 12

        try {

            if ( index < sounds.indexOf(first) ) {

                while ( sounds[next] == null ) next += 12

            } else if ( index > sounds.indexOf(last) ) {

                while ( sounds[next] == null ) next -= 12

            }

        } catch( _: Exception ) {}

        return next

    }

    @Environment(EnvType.CLIENT)
    private fun loadSequence(stack: ItemStack): Boolean {

        val nbt = NBT.get(stack)
        var input = nbt.getString("SequenceInput")

        if ( input.isEmpty() ) return false

        val sounds = stackSounds(stack).notes

        val first = "${ input.first() }";       val last = "${ input.last() }"

        val regex = Regex("[-,]")
        val repeats = Regex("[-,][-,]").containsMatchIn(input)
        var invalid = regex.matches(first) || regex.matches(last)
        invalid = invalid || repeats

        if (invalid) {

            warnUser( Translation.get("error.invalid_sequence") )

            nbt.putString( "SequenceInput", "" )

            return false

        }

        var next = input

        val match = Regex("^\\D*\\d*[^-]*").find(input)
        if ( match != null ) next = match.value

        val notes = next.split(",").toMutableList()

        // Parse sharps.
        notes.forEach {

            val hasSharps = it.contains("#")
            if ( !hasSharps ) return@forEach

            val regex = Regex("-?\\d")

            val index = notes.indexOf(it);      val range = regex.find(it)

            val sharpNote = it.replace( regex, "" )
            val flatNote = sharpsToFlats[sharpNote]

            if ( flatNote != null && range != null ) {
                notes[index] = flatNote[0] + range.value + flatNote[1]
            } else warnUser("$it is not a sharp note!")

        }

        notes.forEach {

            var index = completeSet.indexOf(it)
            index = soundIndex( stack, index )

            if ( index == -1 || sounds[index] == null ) {
                warnUser("$it note does not exist!")
            } else sounds[index]!!.play(stack)

        }

        input = input.substringAfter(next)

        val endMessage = "Sequence has ended!"

        if ( input.isEmpty() ) warnPlayer( endMessage, true )
        else if ( input.first() == '-' ) input = input.substring(1)

        nbt.putString( "SequenceInput", input )

        return true

    }

    private fun attack( stack: ItemStack, player: PlayerEntity, entity: LivingEntity, cooldown: Float ) {

        player.attack(entity);      val world = player.world;       if ( world.isClient ) return

        val nbt = NBT.get(stack)

        // Random chance hit.
        val n = 30 - material.enchantability
        if ( ( 0..n ).random() == 0 ) entity.addVelocity( 0.0, 0.6, 0.0 )

        // Spawn particles.
        val particle = Particles.hand[ nbt.getInt("Hand") ]

        particles.hit( entity, particle );     playHitSound(entity)

        // Set the attack damage
        var damage = damage + material.attackDamage;      damage *= cooldown
        entity.damage( DamageSource.player(player), damage )

        stack.damage( 1, player ) { breakEquipment( it, stack ) }

    }

    private fun push( stack: ItemStack, player: PlayerEntity, entity: LivingEntity, cooldown: Float ) {

        // Pushing is tied to the cooldown and mining speed.

        player.resetLastAttackedTicks()

        if ( player.world.isClient ) return

        val canPushPlayers = serverConfig["allow_pushing_players"] as Boolean
        if ( entity.isPlayer && !canPushPlayers ) return

        val minSpeed = 4.5;     val speed = minSpeed + useSpeed

        var length = cooldown / speed;       length = ( 1 + length ) * 0.875f

        val direction = player.rotationVector.normalize()

        val y = cooldown * ( abs( direction.y ) + 1 / speed ) * 0.625f

        var delta = direction.multiply(length);     delta = Vec3d( delta.x, 0.0, delta.z )
        delta = delta.add( 0.0, y, 0.0 )

        addVelocity( entity, delta )

        stack.damage( 1, player ) { breakEquipment( it, stack ) }

    }

    //** Spawn from 1 to multiple note projectiles. */
    private fun rangedAttack( stack: ItemStack, user: PlayerEntity ) {

        val world = user.world;     val nbt = NBT.get(stack)

        val isRanged = nbt.getString("Action") == "Ranged"

        if ( !isRanged ) return;        var damage = 1

        if ( !user.isSneaking ) {

            val projectile = NoteProjectileEntity( stack, world )

            world.spawnEntity(projectile)

        } else {

            val n = 6;      var time = 0L

            for ( i in 1 .. n ) {

                Timer().schedule(time) {

                    val projectile = NoteProjectileEntity( stack, world )

                    val deltaX = 360f * i / n
                    projectile.changeLookDirection( deltaX.toDouble(), 0.0 )

                    world.spawnEntity( projectile )

                }

                time += 150L

            }

            damage = 3

        }

        // Chance to break.
        if ( ( 0..1 ).random() == 0 && !world.isClient ) {
            stack.damage( damage, user ) { breakEquipment( it, stack ) }
        }

    }

    @Environment(EnvType.CLIENT)
    fun createSounds(instrument: Instrument): Sounds { return Sounds(instrument) }

    @Environment(EnvType.CLIENT)
    fun stackSounds(stack: ItemStack): Sounds {

        val instrument = stack.item as Instrument
        val nbt = NBT.get(stack);     val i = nbt.getInt("ID")

        if ( stacksSounds[i] == null ) stacksSounds[i] = createSounds(instrument)

        return stacksSounds[i]!!

    }

    private fun setAttributes() {

        val attributeBuilder: ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> = ImmutableMultimap.builder()

        attributeBuilder.put(

            EntityAttributes.GENERIC_ATTACK_SPEED,

            EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier",
                useSpeed.toDouble(), EntityAttributeModifier.Operation.ADDITION
            )

        )

        attributes = attributeBuilder.build()

    }

    private fun playHitSound(entity: LivingEntity) {

        val world = entity.world;               val sounds = hitSounds

        val sound = sounds.random();            val pitch = ( 75..125 ).random() * 0.1f

        var delay = 0L;      var times = 1;     val index = sounds.indexOf(sound)

        if ( index == 5 ) times = Random.nextInt(1, 5)
        else if ( index in 6..8 ) times = Random.nextInt(4)

        val gap = Random.nextInt( 50, 200 )
        for ( i in 1..times ) {

            Timer().schedule(delay) { world.playSoundFromEntity( null, entity, sound, SoundCategory.PLAYERS, 0.5f, pitch ) }
            delay += gap

        }

    }

    @Environment(EnvType.CLIENT)
    fun stopSounds( stack: ItemStack ) { stopSounds( stackSounds(stack).notes ) }

    @Environment(EnvType.CLIENT)
    fun stopDeviceSounds( stack: ItemStack ) { stopSounds( stackSounds(stack).deviceNotes ) }

    @Environment(EnvType.CLIENT)
    fun stopSounds( notes: List<InstrumentSound?> ) {

        val notes = notes.filterNotNull().filter { it.isPlaying() && !it.isStopping() }

        notes.forEach { it.fadeOut() }

    }

}

class Keyboard : Instrument( 5f, -2.4f, MusicalQuartz() )
class Organ : Instrument( 5f, -3.5f, MusicalIron() )
class DrumSet : Instrument( 3.5f, -3f, MusicalIron() )
class AcousticGuitar : Instrument( 3f, -2.4f, MusicalString() )

open class ElectricGuitar : Instrument( 4f, -2.4f, MusicalRedstone() ) {

    private val miningSpeed = MusicalRedstone().miningSpeedMultiplier
    private val effectiveBlocks = BlockTags.AXE_MINEABLE

    override fun getMiningSpeedMultiplier( stack: ItemStack?, state: BlockState? ): Float {

        return if ( state!!.isIn(effectiveBlocks) ) miningSpeed else 1.0f

    }

    override fun canMine( state: BlockState?, world: World?, pos: BlockPos?, miner: PlayerEntity? ): Boolean { return true }

    override fun postMine(
        stack: ItemStack?, world: World?, state: BlockState?, pos: BlockPos?, miner: LivingEntity?
    ): Boolean {

        if ( state!!.isIn(effectiveBlocks) ) {

            stack!!.damage( 1, miner ) { breakEquipment( miner, stack ) }

        }

        // What does this bool do?
        return super.postMine( stack, world, state, pos, miner )

    }

}

class ElectricGuitarClean : ElectricGuitar() {

    private fun ability( stack: ItemStack, player: PlayerEntity, entity: LivingEntity ) {

        val world = player.world;       if ( world.isClient ) return

        particles.hit( entity, ParticleTypes.LANDING_OBSIDIAN_TEAR );   entity.extinguish()

        val damage = material.durability * 0.1f

        stack.damage( damage.toInt(), player ) { breakEquipment( it, stack ) }

        val sound = modSound("magic.c3-e3_");           val pitch = ( 75..125 ).random() * 0.01f

        world.playSoundFromEntity( null, player, sound, SoundCategory.PLAYERS, 0.5f, pitch )

    }

    override fun use( world: World?, user: PlayerEntity, hand: Hand? ): TypedActionResult<ItemStack>? {

        val stack = user.getStackInHand(hand)

        if ( user.isOnFire && user.isSneaking ) ability(stack, user, user)
        else super.use( world, user, hand )

        return TypedActionResult.pass(stack)

    }

    override fun useOnEntity(
        stack: ItemStack?, player: PlayerEntity?, entity: LivingEntity?, hand: Hand?
    ): ActionResult {

        val canHelp = entity!!.wasOnFire && entity !is HostileEntity

        return if (canHelp) {

            ability( stack!!, player!!, entity );   ActionResult.CONSUME

        } else super.useOnEntity( stack, player, entity, hand )

    }

}

class Harp : Instrument( 2f, -1f, MusicalString() )
class Viola : Instrument( 3.5f, -2f, MusicalString() )
class Violin : Instrument( 3.75f, -2f, MusicalRedstone() )
class Recorder : Instrument( 1.25f, -1.5f, MusicalString() )
class Oboe : Instrument( 3.25f, -1f, MusicalIron() )

class Trombone : Instrument( 5f, -3f, MusicalRedstone() ) {

    override fun use( world: World?, user: PlayerEntity, hand: Hand? ): TypedActionResult<ItemStack>? {

        val result = super.use( world, user, hand );    val stack = user.getStackInHand(hand)

        val nbt = NBT.get(stack);   val action = nbt.getString("Action")

        val rotation = user.rotationVector.multiply(4.0);       val pos = user.eyePos

        val raycast = world!!.raycast( RaycastContext( pos, pos.add(rotation), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user ) )

        var canThrust = action != "Thrust" || !user.isOnGround
        canThrust = canThrust || world.getBlockState( raycast.blockPos ).block is AirBlock

        if (canThrust) return result

        if ( world.isClient ) {

            // Similar to ranged attack.
            val minSpeed = 4.5;         val speed = useSpeed + minSpeed
            var value = 1 / speed;      value = ( 1 + value ) * 0.75f
            var direction = user.rotationVecClient.normalize().multiply( - value )
            direction = direction.multiply(2.0, 1.25, 2.0 )

            addVelocity( user, direction )

        } else stack.damage( 1, user ) { breakEquipment( it, stack ) }

        return result

    }

}