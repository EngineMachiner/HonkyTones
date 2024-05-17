package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.sendNBT
import com.enginemachiner.harmony.NBT.trackHand
import com.enginemachiner.harmony.NBT.trackSlot
import com.enginemachiner.honkytones.CanBeMuted
import com.enginemachiner.honkytones.Config
import com.enginemachiner.honkytones.ModParticles
import com.enginemachiner.honkytones.MusicTheory.completeSet
import com.enginemachiner.honkytones.MusicTheory.index
import com.enginemachiner.honkytones.MusicTheory.instrumentFiles
import com.enginemachiner.honkytones.MusicTheory.noteCount
import com.enginemachiner.honkytones.MusicTheory.sharpsToFlats
import com.enginemachiner.honkytones.MusicTheory.shift
import com.enginemachiner.honkytones.NoteProjectileEntity
import com.enginemachiner.honkytones.items.console.DigitalConsoleScreen
import com.enginemachiner.honkytones.sound.InstrumentSound
import com.enginemachiner.honkytones.sound.Sound.modSound
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.block.AirBlock
import net.minecraft.block.BlockState
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.client.world.ClientWorld
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
import net.minecraft.inventory.StackReference
import net.minecraft.item.ItemStack
import net.minecraft.item.ToolMaterial
import net.minecraft.nbt.NbtCompound
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.screen.slot.Slot
import net.minecraft.server.MinecraftServer
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.tag.BlockTags
import net.minecraft.util.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import net.minecraft.world.World
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val particles = Instrument.Companion.ActionParticles

open class Instrument(
    val damage: Float, val useSpeed: Float, material: ToolMaterial
) : ToolItem( material, createSettings(material) ), CanBeMuted {

    // Instruments sounds to be copied for each new stack.

    // @Environment(EnvType.CLIENT)
    private var soundsTemplate: SoundsTemplate? = null

    // @Environment(EnvType.CLIENT)
    private val stacksSounds = mutableMapOf<Int, Sounds>()

    private var attributes: ImmutableMultimap<EntityAttribute, EntityAttributeModifier>? = null

    init { init() }

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();              val shouldCenter = stack.item !is DrumSet

        nbt.putString( "Sequence", "" );      nbt.putString( "lastSequence", "" )
        nbt.putString( "Action", "Melee" );   nbt.putInt( "MIDI Channel", 1 )
        nbt.putFloat( "Volume", 1f );         nbt.putBoolean( "Center Notes", shouldCenter )
        nbt.putInt( "ID", stack.hashCode() )

        return nbt

    }

    override fun trackTick( stack: ItemStack, slot: Int ) { trackHand(stack);    trackSlot( stack, slot ) }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        val nbt = NBT.get(stack)

        if ( !world.isClient || entity !is PlayerEntity ) return

        val isOnConsole = currentScreen() is DigitalConsoleScreen
        val shouldStop = nbt.getInt("Hand") == -1 && entity.activeItem != stack

        // Stop playing on screen or when switching stacks.
        if ( ( shouldStop || isOnScreen() ) && !isOnConsole ) {
            onStoppedUsing( stack, world, entity, 0 )
        }

        Tick.onKey()

    }

    override fun onClicked(
        stack: ItemStack, otherStack: ItemStack, slot: Slot, clickType: ClickType,
        player: PlayerEntity, cursorStackReference: StackReference
    ): Boolean {

        val world = player.world

        if ( world.isClient ) { stopSounds(stack); stopDeviceSounds(stack) }

        return super.onClicked( stack, otherStack, slot, clickType, player, cursorStackReference )

    }

    override fun getAttributeModifiers(slot: EquipmentSlot): Multimap<EntityAttribute, EntityAttributeModifier> {

        val onMain = slot == EquipmentSlot.MAINHAND

        return if (onMain) attributes!! else super.getAttributeModifiers(slot)

    }

    override fun getUseAction(stack: ItemStack): UseAction { return UseAction.BOW }

    override fun use( world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);      val nbt = NBT.get(stack)

        val action = TypedActionResult.pass(stack)

        trackHolder(stack, user) // Fixes issue when you spam use the item and the tick doesn't get there.

        if ( !shouldUse( user, stack, hand ) ) return action

        rangedAttack( stack, user );    if ( !world.isClient ) return action

        val isRanged = nbt.getString("Action") == "Ranged"
        if ( !isRanged ) particles.spawn( user, "simple" )

        if ( !loadSequence(stack) ) stackSounds(stack).randomNote().play(stack)

        sendNBT(nbt);        return action

    }

    // TODO: Check screen interaction mobs force attack particles spam.
    override fun useOnEntity(
        stack: ItemStack, player: PlayerEntity, entity: LivingEntity, hand: Hand
    ): ActionResult {

        val nbt = NBT.get(stack);     val action = nbt.getString("Action")
        val result = ActionResult.PASS

        // Mute a player.
        val isRanged = action == "Ranged"

        val isForced = isForced( player, entity )

        val willMute = !isRanged && mute( player, entity, PlayerEntity::class )
        if ( willMute || isRanged || !isForced ) return result

        use( player.world, player, hand )

        val cooldown = player.getAttackCooldownProgress(0.5f)
        if ( action == "Melee" ) attack( stack, player, entity, cooldown )
        if ( action == "Push" ) push( stack, player, entity, cooldown )

        return result

    }

    override fun getMaxUseTime(stack: ItemStack): Int { return 200 }

    override fun onStoppedUsing(
        stack: ItemStack, world: World, user: LivingEntity, remainingUseTicks: Int
    ) {

        if ( !world.isClient || NBT.get(stack).getBoolean("onKey") ) return

        // Stop the off hand stack instrument if there are 2 stacks on hands.
        val mainStack = user.mainHandStack;       val offStack = user.offHandStack

        if ( stack == mainStack && offStack.item is Instrument ) {
            offStack.onStoppedUsing( world, user, remainingUseTicks )
        }

        stopSounds(stack)

    }

    companion object : ModID {

        val enchantments = mutableListOf(
            Enchantments.FIRE_ASPECT,   Enchantments.KNOCKBACK,
            Enchantments.LOOTING,       Enchantments.SMITE,
            Enchantments.MENDING
        )

        private val stacks = mutableListOf<ItemStack>()

        val classes = mutableListOf(

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

        val hitSounds = mutableListOf<SoundEvent>()

        /** Find networked instrument stacks. */
        fun find(netStack: ItemStack): ItemStack {

            if ( netStack.isEmpty ) return netStack

            var stack = stacks.find { NBT.id(it) == NBT.id(netStack) }

            if ( stack == null ) { stacks.add(netStack); stack = netStack }

            return stack

        }

        // @Environment(EnvType.CLIENT)
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

        // @Environment(EnvType.CLIENT)
        open class Sounds( val instrument: Instrument ) {

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

            private val spawn = mapOf(
                "simple" to ::spawnSimpleNote,      "device" to ::spawnDeviceNote
            )

            // TODO: Ring of notes as shield.

            // @Environment(EnvType.CLIENT)
            private fun spawnDeviceNote(entity: Entity) {

                val slices = 12
                val radius = Random.nextInt(750) * 0.001 + 1
                val angle = Random.nextInt(slices) * 360.0 / slices
                val height =  Random.nextInt( 50,175 ) * 0.01

                val data = Vec3d( radius, angle, height )

                spawnNote( ModParticles.DEVICE_NOTE, entity, data )

            }

            private var onMainHand = false

            private const val ANGLE_BETWEEN_HANDS = 15

            // @Environment(EnvType.CLIENT)
            private fun spawnSimpleNote(entity: Entity) {

                var data = Vec3d( 1.5, 0.0, 1.5 );      var n = 0

                entity.handItems.forEach { if ( it.item is Instrument ) n++; }

                if ( n != 2 ) onMainHand = false else {

                    val angle = ANGLE_BETWEEN_HANDS

                    var y = data.y + angle;     if (onMainHand) y = data.y - angle


                    data = Vec3d( data.x, y, data.z );      onMainHand = !onMainHand

                }

                spawnNote( ModParticles.SIMPLE_NOTE, entity, data )

            }

            // @Environment(EnvType.CLIENT)
            private fun spawnNote(particle: ParticleEffect, entity: Entity, data: Vec3d ) {

                val world = entity.world;       if ( world !is ClientWorld ) return

                val radius = data.x;     val angleOffset = data.y;      val height = data.z

                var yaw = entity.bodyYaw + 90.0 + angleOffset;      yaw = rad(yaw)

                val angle = Vec3d( cos(yaw), 0.0, sin(yaw) ).multiply(radius)

                val pos = entity.pos.add(angle).add( Vec3d( 0.0, height, 0.0 ) )

                Particles.spawnOne( particle, pos )

            }

            //** Spawn 4 hit particles. */
            fun hit( entity: Entity, particleType: ParticleEffect ) { hit( entity, particleType, 4 ) }

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

            private fun canSpawn(entity: Entity): Boolean {

                val config = Config.server()
                var playerParticles = config.playerParticles
                var mobParticles = config.mobParticles


                if ( isClient() ) {

                    val config = Config.client()
                    playerParticles = config.playerParticles
                    mobParticles = config.mobParticles

                }


                var canSpawn = entity.isPlayer && playerParticles
                canSpawn = canSpawn || entity is MobEntity && mobParticles

                return canSpawn

            }

            //** Spawn particles on client to be networked. */
            // @Environment(EnvType.CLIENT)
            fun spawn( entity: Entity, particleName: String ) {

                val netID = netID("particle");      val id = entity.id

                val sender = Sender(netID) { it.write(id).write( particleName ) }

                sender.toServer()

            }

            fun spawn(server: MinecraftServer, id: Int, particleName: String ) {

                val world = server.overworld;       val entity = world.getEntityById(id) ?: return


                val netID = netID("particle")

                val sender = Sender(netID) { it.write(id).write( particleName ) }


                val min = Particles.MIN_DISTANCE;       val pos = entity.pos

                sender.toClients(world) { it, _ ->

                    it.blockPos.isWithinDistance(pos, min) && canSpawn(entity)

                }

            }

            fun networking() {


                val id = netID("particle")

                Receiver(id).register { server, _, buf ->

                    val id = buf.readInt();      val type = buf.readString()

                    serverSend(server) { spawn( server, id, type ) }

                }


                if ( !isClient() ) return


                Receiver(id).register { buf ->

                    val id = buf.readInt();      val type = buf.readString()

                    client().send {

                        val entity = entity(id) ?: return@send

                        val canSpawn = canSpawn(entity)

                        if ( !canSpawn ) return@send

                        spawn[type]!!(entity)

                    }

                }


            }

        }

        // @Environment(EnvType.CLIENT)
        private object Tick {

            private fun play(stack: ItemStack) {

                val player = player();       val world = player.world

                val instrument = stack.item as Instrument

                val play = KeyBindings.play!!;      val nbt = NBT.get(stack)

                val isPressed = play.isPressed;     val onKey = nbt.getBoolean("onKey")

                if ( isPressed && !onKey ) {

                    // TODO: FIX THIS SHIT
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

                val nbt = NBT.get(stack);                   val sequence = nbt.getString("lastSequence")

                nbt.putString( "Sequence", sequence );  sendNBT(nbt)

                if ( !isLast ) return;      wasPressed = true

                warnUser("message.resetSequences")

            }

            /** Won't open if player has two instruments. */
            private fun menu( stack: ItemStack, canOpen: Boolean ) {

                val client = client()

                val menu = KeyBindings.menu!!;      if ( !menu.isPressed ) return

                if (canOpen) client.setScreen( InstrumentsScreen(stack) )

            }

            fun onKey() {

                val handItems = player().handItems.toSet();      val size = handItems.size

                val instruments = handItems.filter { it.item is Instrument }.toSet()

                if ( instruments.isEmpty() ) return

                for ( stack in instruments ) {

                    play(stack);        reset( stack, stack == instruments.last() )

                    menu( stack, instruments.size < size )

                }

            }

        }

        private val interactiveMobs = listOf(
            AbstractHorseEntity::class,     MerchantEntity::class
        )

        private fun isForced( player: PlayerEntity, entity: LivingEntity ): Boolean {

            val isInteractive = interactiveMobs.find { it.isInstance(entity) } != null
            val isSneaking = player.isSneaking

            return isInteractive && isSneaking || !isInteractive

        }

        fun shouldUse( user: PlayerEntity, stack: ItemStack, hand: Hand ): Boolean {

            val mainStack = user.mainHandStack;     val isActive = user.activeItem == stack

            // Only set the hand when the player has one instrument.
            var hasOne = hand == Hand.OFF_HAND && mainStack.item !is Instrument
            hasOne = hasOne || hand == Hand.MAIN_HAND


            // Using setCurrentHand() can control the sound length.
            if (hasOne) user.setCurrentHand(hand)

            if (isActive) return false;     return true

        }

        fun mobPlay( mob: LivingEntity ) {

            val netID = netID("mob_play");     val id = mob.id

            val sender = Sender(netID) { it.write(id) }

            val min = Particles.MIN_DISTANCE


            sender.toClients( mob.world ) { it, _ ->

                it.blockPos.isWithinDistance( mob.pos, min )

            }

            particles.spawn( mob.server!!, id, "simple" )

        }

        // @Environment(EnvType.CLIENT)
        object KeyBindings {

            var play: KeyBinding? = null;       var menu: KeyBinding? = null;       var reset: KeyBinding? = null

            fun register() {

                val key = "key.$MOD_NAME";      val category = "category.$MOD_NAME.instrument"

                var keyBind = KeyBinding( "$key.play", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
                play = KeyBindingHelper.registerKeyBinding(keyBind)

                keyBind = KeyBinding( "$key.menu", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, category )
                menu = KeyBindingHelper.registerKeyBinding(keyBind)

                keyBind = KeyBinding( "$key.reset", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category )
                reset = KeyBindingHelper.registerKeyBinding(keyBind)

            }

        }

        private fun createSettings(material: ToolMaterial): Settings {
            return modItemSettings().maxDamage( material.durability )
        }

        fun networking() {

            ActionParticles.networking();       if ( !isClient() ) return

            val id = netID("mob_play")

            Receiver(id).register { buf ->

                val id = buf.readInt()

                client().send {

                    val mob = world()!!.getEntityById(id) ?: return@send

                    mob as MobEntity;       val stack = mob.mainHandStack

                    val instrument = stack.item as Instrument

                    stack.holder = mob

                    val sounds = instrument.stackSounds(stack)
                    val sound = sounds.randomNote()

                    sound.play(stack);      instrument.stopSounds(stack)

                }

            }

        }

    }

    private fun init() {

        setAttributes();        if ( !isClient() ) return

        soundsTemplate = SoundsTemplate(this)

    }

    // @Environment(EnvType.CLIENT)
    private fun keyUse(stack: ItemStack) {

        particles.spawn( player(), "simple" )

        if ( !loadSequence(stack) ) stackSounds(stack).randomNote().play(stack)


    }

    // @Environment(EnvType.CLIENT)
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

    // @Environment(EnvType.CLIENT)
    private fun loadSequence(stack: ItemStack): Boolean {

        val nbt = NBT.get(stack)
        var input = nbt.getString("Sequence")

        if ( input.isEmpty() ) return false

        val sounds = stackSounds(stack).notes

        val first = "${ input.first() }";       val last = "${ input.last() }"

        val regex = Regex("[-,]")
        val repeats = Regex("[-,][-,]").containsMatchIn(input)
        var invalid = regex.matches(first) || regex.matches(last)
        invalid = invalid || repeats

        if (invalid) {

            warnUser("error.invalid_sequence")

            nbt.putString( "Sequence", "" )

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

            } else {

                val translation = Translation.get("error.sharp").replace( "X", it )

                warnUser(translation)

            }

        }

        notes.forEach {

            var index = completeSet.indexOf(it)
            index = soundIndex( stack, index )

            if ( index == -1 || sounds[index] == null ) {

                val translation = Translation.get("error.note").replace( "X", it )

                warnUser(translation)

            } else sounds[index]!!.play(stack)

        }

        input = input.substringAfter(next)

        val endMessage = "Sequence has ended!"

        if ( input.isEmpty() ) warnPlayer( endMessage, true )
        else if ( input.first() == '-' ) input = input.substring(1)

        nbt.putString( "Sequence", input )

        return true

    }

    private fun attack( stack: ItemStack, player: PlayerEntity, entity: LivingEntity, cooldown: Float ) {

        player.attack(entity);      val world = player.world;       if ( world.isClient ) return

        val nbt = NBT.get(stack)

        // Random chance hit.
        val n = 30 - material.enchantability
        if ( ( 0..n ).random() == 0 ) entity.addVelocity( 0.0, 0.6, 0.0 )

        // Spawn particles.
        val particle = ModParticles.hand[ nbt.getInt("Hand") ]

        particles.hit( entity, particle );     playHitSound(entity)

        // Set the attack damage
        var damage = damage + material.attackDamage;      damage *= cooldown
        entity.damage( DamageSource.player(player), damage )

        stack.damage( 1, player ) { breakEquipment( it, stack ) }

    }

    private fun push( stack: ItemStack, player: PlayerEntity, entity: LivingEntity, cooldown: Float ) {

        // Pushing is tied to the cooldown and mining speed.

        player.resetLastAttackedTicks();        if ( player.world.isClient ) return


        val canPushPlayers = Config.server().allowPushingPlayers

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

            val n = 6;      var ticks = 0

            for ( i in 1 .. n ) {

                Timer(ticks) {

                    val projectile = NoteProjectileEntity( stack, world )

                    world.spawnEntity(projectile)

                }

                ticks += Random.nextInt( 2, 6 )

            }

            damage = 3

        }

        // Chance to break.
        if ( ( 0..1 ).random() == 0 && !world.isClient ) {
            stack.damage( damage, user ) { breakEquipment( it, stack ) }
        }

    }

    // @Environment(EnvType.CLIENT)
    private fun createSounds(instrument: Instrument): Sounds { return Sounds(instrument) }

    // @Environment(EnvType.CLIENT)
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

        var delay = 0;      var times = 1;     val index = sounds.indexOf(sound)

        if ( index == 5 ) times = Random.nextInt(1, 5)
        else if ( index in 6..8 ) times = Random.nextInt(4)

        val gap = Random.nextInt( 1, 5 )

        for ( i in 1..times ) {

            Timer(delay) { world.playSoundFromEntity( null, entity, sound, SoundCategory.PLAYERS, 0.5f, pitch ) }

            delay += gap

        }

    }

    // @Environment(EnvType.CLIENT)
    fun stopSounds( stack: ItemStack ) { stopSounds( stackSounds(stack).notes ) }

    // @Environment(EnvType.CLIENT)
    fun stopDeviceSounds( stack: ItemStack ) { stopSounds( stackSounds(stack).deviceNotes ) }

    // @Environment(EnvType.CLIENT)
    private fun stopSounds( notes: List<InstrumentSound?> ) {

        val notes = notes.filterNotNull().filter { it.isPlaying() && !it.isStopping() }

        notes.forEach { it.fadeOut() }

    }

}

/** Instruments that don't fade out and play until the end. */
interface PlayCompletely

/** Instruments that stop immediately. */
interface NoFading

open class Keyboard : Instrument( 5f, -2.4f, MusicalQuartz() )
class Organ : Instrument( 5f, -3.5f, MusicalIron() )

open class DrumSet : Instrument( 3.5f, -3f, MusicalIron() ), PlayCompletely
open class AcousticGuitar : Instrument( 3f, -2.4f, MusicalString() )

open class ElectricGuitar : Instrument( 4f, -2.4f, MusicalRedstone() ) {

    private val miningSpeed = MusicalRedstone().miningSpeedMultiplier
    private val effectiveBlocks = BlockTags.AXE_MINEABLE

    override fun getMiningSpeedMultiplier( stack: ItemStack, state: BlockState ): Float {

        return if ( state.isIn(effectiveBlocks) ) miningSpeed else 1.0f

    }

    override fun canMine( state: BlockState, world: World, pos: BlockPos, miner: PlayerEntity ): Boolean { return true }

    override fun postMine(
        stack: ItemStack, world: World, state: BlockState, pos: BlockPos, miner: LivingEntity
    ): Boolean {

        if ( state.isIn(effectiveBlocks) ) {

            stack.damage( 1, miner ) { breakEquipment( miner, stack ) }

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

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);      val action = TypedActionResult.pass(stack)

        if ( user.isOnFire && user.isSneaking ) {

            if ( !shouldUse( user, stack, hand ) ) return action

            ability( stack, user, user )

        } else super.use( world, user, hand )

        return action

    }

    override fun useOnEntity(
        stack: ItemStack, player: PlayerEntity, entity: LivingEntity, hand: Hand
    ): ActionResult {

        val canHelp = entity.wasOnFire && entity !is HostileEntity

        return if (canHelp) {

            ability( stack, player, entity );   ActionResult.CONSUME

        } else super.useOnEntity( stack, player, entity, hand )

    }

}

class Harp : Instrument( 2f, -1f, MusicalString() )
class Recorder : Instrument( 1.25f, -1.5f, MusicalString() )
class Oboe : Instrument( 3.25f, -1f, MusicalIron() )

open class Viola : Instrument( 3.5f, -2f, MusicalString() )
open class Violin : Instrument( 3.75f, -2f, MusicalRedstone() )

open class Trombone : Instrument( 5f, -3f, MusicalRedstone() ) {

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val result = super.use( world, user, hand );    val stack = user.getStackInHand(hand)

        val nbt = NBT.get(stack);   val action = nbt.getString("Action")

        val rotation = user.rotationVector.multiply(4.0);       val pos = user.eyePos

        val raycast = world.raycast( RaycastContext( pos, pos.add(rotation), RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, user ) )

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

class Accordion : Instrument( 2.25f, -2.5f, MusicalIron() )
class Kalimba : Instrument( 1f, -0.5f, MusicalIron() ), PlayCompletely
class Koto : Instrument( 3.5f, -2f, MusicalIron() )

class Marimba : Instrument( 2.5f, -1.25f, MusicalString() ), PlayCompletely
class Xylophone : Instrument( 2.5f, -1.25f, MusicalString() )

class ElectricPiano : Keyboard();       class Harpsichord : Keyboard()
class Rhodes : Keyboard()

abstract class Synth : Keyboard()

class BassSynth : Synth();              class BassLeadSynth : Synth()
class Bass2Synth : Synth();             class CelesteSynth : Synth()
class DocSynth : Synth();               class MetalPadSynth : Synth()
class PolySynth : Synth();              class SawSynth : Synth()
class SineSynth : Synth();              class SquareSynth : Synth()
class StringsSynth : Synth()

class Banjo : AcousticGuitar();         class Cello : Instrument( 2f, -2f, MusicalString() )

class MutedTrumpet : Trombone();        class Trumpet : Trombone()
class Sax : Trombone()

class MusicBox : Viola()

class SFX : Instrument( 3.5f, -3f, MusicalIron() )
