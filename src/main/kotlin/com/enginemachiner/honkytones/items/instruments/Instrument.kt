package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.NoteData.sharpsMap
import com.enginemachiner.honkytones.NoteData.soundsMap
import com.enginemachiner.honkytones.NoteData.twoOctaves
import com.enginemachiner.honkytones.NoteData.wholeNoteSet
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.impl.content.registry.FuelRegistryImpl
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.inventory.StackReference
import net.minecraft.item.ItemStack
import net.minecraft.item.ToolItem
import net.minecraft.item.ToolMaterial
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.screen.slot.Slot
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.tag.BlockTags
import net.minecraft.text.Text
import net.minecraft.util.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import org.lwjgl.glfw.GLFW
import java.util.*
import javax.sound.midi.MidiSystem
import kotlin.math.abs
import kotlin.math.pow

@Environment(EnvType.CLIENT)
class HitSounds {

    val list = mutableListOf<CustomSoundInstance?>()
    private val path = Base.MOD_NAME + ":hit0"

    init { for ( i in 1..9 ) {
        val sound = CustomSoundInstance( path + i );    sound.volume = 0.5f
        sound.index = list.size
        sound.key = "hits";     list.add( sound )
    } }

}

@Environment(EnvType.CLIENT)
class MagicSounds {

    val list = mutableListOf<CustomSoundInstance?>()

    init {
        val sound = CustomSoundInstance( Base.MOD_NAME + ":magic-c3-e3_" )
        sound.key = "abilities";        sound.index = list.size
        sound.volume = 0.5f;        list.add( sound )
    }

}

@Environment(EnvType.CLIENT)
class SoundsTemplate {

    var map = mutableMapOf< String, MutableList<CustomSoundInstance?> >(
        "notes" to MutableList(127) { null },
        "hits" to HitSounds().list
    )

}

open class Instrument( private val damage: Float, val speed: Float, material: ToolMaterial )
    : ToolItem( material, createDefaultItemSettings().maxDamage( material.durability ) ),
    CanBeMuted {

    val name = classes[this::class]

    @Environment(EnvType.CLIENT)
    private val soundPaths = soundsMap[name]!!

    // Default sounds map copied for each stack
    @Environment(EnvType.CLIENT)
    var soundsTemplate = mutableMapOf< String, MutableList<CustomSoundInstance?> >()

    @Environment(EnvType.CLIENT)
    private val stackSounds = mutableMapOf<Int, SoundsTemplate>()

    private lateinit var attributes
            : ImmutableMultimap<EntityAttribute, EntityAttributeModifier>

    init {

        setAttributes()

        if ( FabricLoaderImpl.INSTANCE.environmentType == EnvType.CLIENT ) {
            soundsTemplate = SoundsTemplate().map;      loadSounds()
        }

    }

    override fun onItemEntityDestroyed(entity: ItemEntity?) {
        stacks.remove(entity!!.stack)
    }

    override fun onClicked( stack: ItemStack?, otherStack: ItemStack?, slot: Slot?,
                            clickType: ClickType?, player: PlayerEntity?,
                            cursorStackReference: StackReference? ): Boolean {
        stack!!.holder = player!!
        stopAllNotes(stack, player.world)
        return super.onClicked(stack, otherStack, slot, clickType, player, cursorStackReference)
    }

    override fun inventoryTick( stack: ItemStack?, world: World?, entity: Entity?,
                                slot: Int, selected: Boolean ) {

        if ( !world!!.isClient ) {

            var nbt = stack!!.nbt!!

            if ( !nbt.contains(Base.MOD_NAME) ) loadNbtData(stack)

            nbt = nbt.getCompound(Base.MOD_NAME)

            if ( stacks.elementAtOrNull( nbt.getInt("Index") ) == null ) {
                nbt.putInt( "Index", stacks.size );     stacks.add(stack)
            }

            trackHandOnNbt(stack, entity!!)

        } else {

            val nbt = stack!!.orCreateNbt.getCompound(Base.MOD_NAME)
            val b = MinecraftClient.getInstance().currentScreen != null
            if ( b && nbt.getBoolean("isOnUse") ) {
                onStoppedUsing(stack, world, entity as LivingEntity, 0)
            }

        }

    }

    override fun getAttributeModifiers(slot: EquipmentSlot?)
    : Multimap<EntityAttribute, EntityAttributeModifier> {
        return if (slot == EquipmentSlot.MAINHAND) attributes
        else super.getAttributeModifiers(slot)
    }

    override fun use( world: World?, user: PlayerEntity, hand: Hand? )
    : TypedActionResult<ItemStack>? {

        // I need to set the current hand to the hand given
        // so the item and player is capable of holding input each frame
        user.setCurrentHand(hand)

        val stack = user.getStackInHand(hand)
        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)

        stack.holder = user

        if ( !nbt.getBoolean("isOnUse") ) {

            nbt.putBoolean("isOnUse", true)
            if ( world!!.isClient ) {

                var b = loadSequence(stack)
                if ( !b ) playRandomSound(stack)
                Network.sendNbtToServer(nbt)

                b = !nbt.getBoolean("isOnKeyBindUse")
                b = b && nbt.getString("Action") != "Ranged"
                if ( b ) spawnNoteParticle(user, "simple")

            }

        }

        rangedAttack(stack, user)

        return TypedActionResult.pass(stack)

    }

    override fun useOnEntity( stack: ItemStack?, player: PlayerEntity?, entity: LivingEntity?,
                              hand: Hand? ): ActionResult {

        val nbt = stack!!.nbt!!.getCompound(Base.MOD_NAME)
        val action = nbt.getString("Action")
        val player = player!!;      val entity = entity!!

        // Player mute
        val isRanged = action == "Ranged"
        if ( !isRanged && shouldBlacklist( player, entity, PlayerEntity::class ) ) {
            return ActionResult.PASS
        }

        val cooldown = player.getAttackCooldownProgress(0.5f)

        if (isRanged) return ActionResult.PASS

        use(player.world, player, hand)
        stack.holder = player

        if (action == "Melee") attack(stack, player, entity, cooldown)
        if (action == "Push") push(stack, player, entity, cooldown)

        return ActionResult.PASS

    }

    override fun getMaxUseTime( stack: ItemStack? ): Int { return 200 }

    override fun onStoppedUsing( stack: ItemStack?, world: World?, user: LivingEntity?,
                                 remainingUseTicks: Int ) {

        val nbt = stack!!.nbt!!.getCompound(Base.MOD_NAME)
        stack.holder = user

        if ( nbt.getBoolean("isOnUseKeyBind") ) return

        nbt.putBoolean("isOnUse", false)

        val b = stack.item is DrumSet;      if (b) return
        stopAllNotes(stack, world)

    }

    companion object {

        val stacks = mutableListOf<ItemStack>()

        @Environment(EnvType.CLIENT)
        private lateinit var playKeyBind: KeyBinding

        @Environment(EnvType.CLIENT)
        private lateinit var menuKeyBind: KeyBinding

        @Environment(EnvType.CLIENT)
        private lateinit var replayKeyBind: KeyBinding

        val classes = mapOf(
            DrumSet::class to "drumset",
            Keyboard::class to "keyboard",       Organ::class to "organ",
            AcousticGuitar::class to "acousticguitar",
            ElectricGuitar::class to "electricguitar",
            ElectricGuitarClean::class to "electricguitar-clean",
            Harp::class to "harp",       Viola::class to "viola",
            Violin::class to "violin",      Trombone::class to "trombone",
            Flute::class to "flute",       Oboe::class to "oboe",
        )

        @Environment(EnvType.CLIENT)
        private val instrumentsMessage = Text.of(
            menuMessage.replace("%item%", "instruments")
        )

        fun mobAction(mob: MobEntity) { mobAction(mob, "") }

        fun mobAction( mob: MobEntity, action: String ) {

            val world = mob.world
            var list: List<PlayerEntity> = world.players
            list = list.filter { it.squaredDistanceTo(mob) < Sound.minRadius.pow(2) }
            val id = Identifier( Base.MOD_NAME, "mob_instrument_action" )

            for ( player in list ) {
                val player = player as ServerPlayerEntity
                val buf = PacketByteBufs.create()
                buf.writeString(action);    buf.writeInt(mob.id)
                ServerPlayNetworking.send(player, id, buf)
            }

        }

        @Environment(EnvType.CLIENT)
        fun registerKeyBindings() {

            var category = Base.MOD_NAME + ".instrument"
            category = "category.$category"

            val key = "key." + Base.MOD_NAME

            playKeyBind = KeyBindingHelper.registerKeyBinding(
                KeyBinding("$key.play",      InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,  category )
            )

            menuKeyBind = KeyBindingHelper.registerKeyBinding(
                KeyBinding("$key.menu",      InputUtil.Type.MOUSE,
                    GLFW.GLFW_MOUSE_BUTTON_MIDDLE,        category )
            )

            replayKeyBind = KeyBindingHelper.registerKeyBinding(
                KeyBinding("$key.replay",      InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,        category )
            )

        }

        private fun keyBindLogic() {

            val client = MinecraftClient.getInstance()
            val player = client.player!!;       val world = client.world

            val stacks = player.handItems.toSet();      val size = stacks.size
            val instStack = stacks.filter { it.item is Instrument }.toSet()

            if ( instStack.isNotEmpty() ) {

                for ( stack in instStack ) {

                    val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)

                    if ( menuKeyBind.wasPressed() ) {
                        if (instStack.size < size) client.setScreen( InstrumentsScreen(stack) )
                        else player.sendMessage(instrumentsMessage, true)
                    }

                    if ( replayKeyBind.wasPressed() ) {
                        val seq = nbt.getString("Sequence")
                        nbt.putString("TempSequence", seq)
                        Network.sendNbtToServer(nbt)
                    }

                    val b = nbt.getBoolean("isOnUseKeyBind")
                    val inst = stack.item as Instrument

                    if ( playKeyBind.isPressed && !b ) {

                        nbt.putBoolean("isOnUseKeyBind", true)
                        val index = stacks.indexOf(stack)
                        inst.use(world, player, hands[index])

                        spawnNoteParticle(player, "simple", true)

                    }

                    if ( !playKeyBind.isPressed && b ) {
                        stack.holder = player
                        inst.stopAllNotes(stack, world)
                        nbt.putBoolean("isOnUseKeyBind", false)
                        nbt.putBoolean("isOnUse", false)
                    }

                }

            }


        }

        @Environment(EnvType.CLIENT)
        fun tick() { keyBindLogic() }

        lateinit var enchants : Multimap<Enchantment, Int>
        private fun setEnchantments() {

            val builder = ImmutableMultimap.builder<Enchantment, Int>()

            for (i in 1..4) {
                if (i < 3) {
                    builder.put(Enchantments.FIRE_ASPECT, i)
                    builder.put(Enchantments.KNOCKBACK, i)
                } else if (i < 4) builder.put(Enchantments.LOOTING, i)
                else builder.put(Enchantments.SMITE, i)
            }

            builder.put(Enchantments.MENDING, 1);       builder.put(RangedEnchantment(), 1)

            enchants = builder.build()

        }

        fun registerFuel() {

            val registry = FuelRegistryImpl.INSTANCE

            var time = 300 * 3 + 100 * 3
            registry.add( getRegisteredItem("acousticguitar"), time + 100 )
            registry.add( getRegisteredItem("harp"), time - 100 )
            registry.add( getRegisteredItem("viola"), time )

            // + a blaze rod
            time += 2400;       time += (time * 0.25f).toInt()
            registry.add( getRegisteredItem("violin" ), time )
        }

        fun networking() {

            if ( serverConfig.isNotEmpty() ) {

                // Entities UUIDs being read
                Network.registerServerToClientsHandler(

                    "entity_spawn_particle",
                    Particles.minRadius, Sound.ticksAhead,
                    serverConfig["playerParticles"] as Boolean

                ) {
                    val newBuf = PacketByteBufs.create()
                    newBuf.writeInt(it.readInt())
                    newBuf.writeString(it.readString())
                    newBuf.writeString(it.readString())
                    newBuf

                }

            }

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            var id = Identifier( Base.MOD_NAME, "entity_spawn_particle" )
            ClientPlayNetworking.registerGlobalReceiver(id) {

                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val id = buf.readInt();     val particleType = buf.readString()
                val key = buf.readString()

                client.send {
                    val b = clientConfig[key] as Boolean
                    val player = client.world!!.getEntityById(id)
                    if ( !b || player == null ) return@send
                    if ( particleType.isEmpty() ) spawnNoteParticle(player, "simple", false)
                    else if ( particleType == "device" ) spawnNoteParticle(player, "device", false)
                }

            }

            id = Identifier( Base.MOD_NAME, "mob_instrument_action" )
            ClientPlayNetworking.registerGlobalReceiver(id) {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val action = buf.readString()
                val id = buf.readInt();    val world = client.world!!
                client.send {

                    val mob = client.world!!.getEntityById(id) ?: return@send
                    mob as MobEntity

                    val stack = mob.mainHandStack
                    val instrument = mob.mainHandStack.item as Instrument
                    stack.holder = mob
                    if ( action == "play" || action.isEmpty() ) instrument.playRandomSound(stack)
                    if ( action == "stop" || action.isEmpty() ) instrument.stopAllNotes(stack, world)
                    if ( action.isEmpty() ) spawnNoteParticle(mob, "simple", false)

                }

            }

        }

        private fun spawnDeviceNote(entity: Entity) {
            val world = entity.world
            world.addParticle(
                Particles.DEVICE_NOTE,
                entity.x, entity.y + 3, entity.z,
                0.0, 0.0, 0.0
            )
        }

        private fun spawnSimpleNote(entity: Entity) {
            val world = entity.world;       val len = 1.5f
            world.addParticle(
                Particles.SIMPLE_NOTE,
                entity.x + entity.rotationVecClient.x * len, entity.y + 1.75f,
                entity.z + entity.rotationVecClient.z * len,
                0.0, 0.0, 1.5
            )
        }

        fun spawnNoteParticle( entity: Entity, type: String ) {
            spawnNoteParticle( entity, type, true )
        }

        fun spawnNoteParticle( entity: Entity, type: String,
                               shouldNetwork: Boolean ) {

            var key = "mobsParticles"
            if (entity is PlayerEntity) key = "playerParticles"

            if ( clientConfig[key] as Boolean ) {
                if ( type.isEmpty() || type == "simple" ) spawnSimpleNote(entity)
                else if ( type == "device" ) spawnDeviceNote(entity)
            }

            if ( !shouldNetwork ) return

            val buf = PacketByteBufs.create()
            buf.writeInt(entity.id)
            buf.writeString(type);      buf.writeString(key)
            val id = Identifier( Base.MOD_NAME, "entity_spawn_particle" )
            ClientPlayNetworking.send( id, buf )

        }

        fun spawnHitParticles( entity: Entity, particleType: ParticleEffect ) {

            val num = 5;        val world = entity.world

            val toDeg = MathHelper.RADIANS_PER_DEGREE
            val yawDeg = entity.yaw * toDeg
            val delta = mutableMapOf('x' to - MathHelper.sin( yawDeg ))

            for ( i in 1..num ) {

                if (world is ServerWorld) {

                    delta['y'] = (-100..100).random() * 0.01f
                    delta['z'] = MathHelper.cos( yawDeg )

                    if ( i == 5 ) { delta['z'] = delta['z']!! * 2 }

                    val pos = mutableMapOf(
                        'x' to entity.x + ( i - num * 0.5 ) * 0.1,
                        'y' to entity.getBodyY(0.5),
                        'z' to entity.z - ( i - num * 0.5 ) * 0.1
                    )

                    pos['y'] = pos['y']!! + (-75..75).random() * 0.01

                    world.spawnParticles(

                        particleType,

                        pos['x']!!, pos['y']!!, pos['z']!!,

                        0,

                        delta['x']!!.toDouble(),
                        delta['y']!!.toDouble(),
                        delta['z']!!.toDouble(),

                        0.0

                    )
                }

            }

        }

        init { setEnchantments() }

    }

    private fun rangedAttack( stack: ItemStack, user: PlayerEntity ) {

        val world = user.world

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        if (nbt.getString("Action") != "Ranged") return

        if ( !user.isSneaking ) world.spawnEntity( NoteProjectileEntity(stack, world) )
        else {

            val num = 6
            for ( i in 1 .. num ) {
                val dir = ( 360f / num ) * i
                val projectile = NoteProjectileEntity(stack, world)
                projectile.changeLookDirection( dir.toDouble(), 0.0 )
                world.spawnEntity( projectile )
            }

            if ( !world.isClient ) {
                stack.damage(6, user) { sendStatus(it, stack) }
            }

        }

        if ( (0..1).random() == 0 && !world.isClient ) {
            stack.damage(1, user ) { sendStatus(it, stack) }
        }

    }

    // Used for the sequence string
    @Environment(EnvType.CLIENT)
    fun getIndexIfCentered(stack: ItemStack, index: Int): Int {

        if (index == -1) return -1

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        val sounds = getSounds(stack, "notes");     var newIndex = index

        if ( nbt.getBoolean("Center Notes") ) {

            val filter = sounds.filterNotNull()
            val first = filter.first();     val last = filter.last()

            if (index < sounds.indexOf(first)) {
                while ( sounds[newIndex] === null ) newIndex += 12
            } else if (index > sounds.indexOf(last)) {
                while ( sounds[newIndex] === null ) newIndex -= 12
            }

            if (sounds[newIndex] !== null) return newIndex

        }

        return index

    }

    @Environment(EnvType.CLIENT)
    private fun loadSequence(stack: ItemStack): Boolean {

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        var subsequence = nbt.getString("TempSequence")

        if ( subsequence.isEmpty() ) return false

        val sounds = getSounds(stack, "notes")
        val user = stack.holder as PlayerEntity
        val hasBadFormat = Regex("[-,][-,]")
            .containsMatchIn(subsequence)

        if ( hasBadFormat ) {
            val badText = "Subsequence has bad formatting!"
            user.sendMessage(Text.of(badText), true)
            nbt.putString("TempSequence","")
            return false
        }

        val chars = mutableSetOf('-', ',')
        val first = subsequence.first()
        val last = subsequence.last()
        for (char in chars) {
            if (first == char) subsequence = subsequence.substring(1)
            if (last == char) subsequence = subsequence.substringBeforeLast(char)
        }

        val match = Regex("^\\D*\\d*[^-]*").find(subsequence)
        var tempSeq = subsequence
        if (match != null) tempSeq = match.value
        val notes = tempSeq.split(",").toMutableList()

        // Convert sharps
        for (note in notes) {
            if (note.contains("#")) {

                val index = notes.indexOf(note)
                val sharpNote = note.replace(Regex("-?\\d"), "")
                val range = Regex("-?\\d").find(note)
                val flatNote = sharpsMap[sharpNote]

                if (flatNote != null && range != null) {
                    notes[index] = flatNote[0] + range.value + flatNote[1]
                } else user.sendMessage(getSequenceText(note, 0), false)

            }
        }

        for (note in notes) {
            var index = wholeNoteSet.indexOf(note)
            index = getIndexIfCentered(stack, index)
            if (index != -1 && sounds[index] !== null) {
                sounds[index]!!.playSound(stack)
            } else user.sendMessage(getSequenceText(note, 1), false)
        }

        subsequence = subsequence.substringAfter(tempSeq)
        if (subsequence.isEmpty()) {
            user.sendMessage(getSequenceText("", 2), true)
        }

        nbt.putString("TempSequence", subsequence)
        return true

    }

    private fun attack( stack: ItemStack, ply: PlayerEntity, entity: LivingEntity,
                        cooldown: Float ) {

        // Attack
        ply.attack(entity)
        if (ply.world.isClient) playHitSound(stack)
        else {

            // Random chance velocity
            val num = 30 - material.enchantability
            if ( (0..num).random() == 0 ) {
                entity.addVelocity(0.0, 0.625, 0.0)
            }

            // Spawn particles
            spawnHitParticles(entity, Particles.NOTE_IMPACT)

            // Set the attack damage
            var dmg = damage + material.attackDamage;      dmg *= cooldown
            entity.damage(DamageSource.player(ply), dmg)

            stack.damage(1, ply) { sendStatus(it, stack) }

        }

    }

    @Verify("?")
    private fun push( stack: ItemStack, ply: PlayerEntity, entity: LivingEntity,
                      cooldown: Float ) {

        ply.resetLastAttackedTicks()
        if (!ply.world.isClient) {

            val pushPlayers = serverConfig["allowPushingPlayers"] as Boolean
            if ( entity.isPlayer && !pushPlayers ) return

            val spd = speed + 4.5 // 3.5 (lowest speed) + 1
            var value = cooldown / spd;       value = (1 + value) * 0.75f
            val dir = ply.rotationVector.normalize()
            val y = cooldown * (abs(dir.y) + 1 / spd) * 0.625f

            entity.addVelocity(dir.x * value, y, dir.z * value)

            stack.damage(1, ply) { sendStatus(it, stack) }

        }

    }

    @Environment(EnvType.CLIENT)
    fun getSounds(stack: ItemStack, key: String) : List<CustomSoundInstance?> {

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        val hash = nbt.getInt("hashID")

        if (stackSounds[hash] == null) {
            val template = SoundsTemplate()
            template.map = soundsTemplate.toMutableMap()
            stackSounds[hash] = template
        }

        return stackSounds[hash]!!.map[key]!!

    }

    @Environment(EnvType.CLIENT)
    fun playRandomSound(stack: ItemStack) {
        val sounds = getSounds(stack, "notes")
        val list = sounds.filterNotNull()
        list.random().playSound(stack)
    }

    /** Return Text related to the sequence playing */
    @Environment(EnvType.CLIENT)
    private fun getSequenceText( element: String, i: Int ): Text {

        val messages = mutableSetOf(
            " is not a sharp note!",    " note does not exist!",
            "HonkyTones sequence has ended!"
        )

        return Text.of( element + messages.elementAt(i) )

    }

    /** Get the note relative position (indexes) or distance
        Needed to get the proper pitch
    */
    @Environment(EnvType.CLIENT)
    private fun getNoteIndex(s: String): Int {

        for (note in twoOctaves) {

            // Find index by note range
            val find = s.replace(Regex("-?\\d"), "")

            if (find == note) return twoOctaves.indexOf(note)

        }

        return -1

    }

    /** Move a semitone distance up an octave (12 semitones) */
    private fun up( a: Int, b: Int ): Int { if (a < b) return a + 12;   return a }

    /** Add the sounds to the default sound template */
    @Environment(EnvType.CLIENT)
    private fun loadSounds() {

        val list = soundsTemplate["notes"]!!
        val firstPair = soundPaths.first()
        val isRanged = firstPair.contains( Regex("-[A-Z]") )

        for ( pair in soundPaths ) {

            val path = Base.MOD_NAME + ':' + name + '-' + pair.lowercase()

            if ( !isRanged ) {

                // No pitch alterations, each sound file is a note
                // The pair is just a note
                val index = wholeNoteSet.indexOf(pair)
                val sound = CustomSoundInstance(path)

                sound.index = index
                list[index] = sound

            } else {

                // The pair has a range of notes
                val first = mutableMapOf<String, Any>( "note" to Regex("^[A-Z]-?\\d_?").find(pair)!!.value )
                val last = mutableMapOf<String, Any>( "note" to Regex("[A-Z]-?\\d_?$").find(pair)!!.value )

                // Semitones distance
                first["index"] = getNoteIndex( first["note"] as String )
                last["index"] = getNoteIndex( last["note"] as String )

                last["index"] = up( last["index"] as Int, first["index"] as Int )
                val length = last["index"] as Int - first["index"] as Int

                // Range
                first["range"] = Regex("-?\\d")
                    .find( first["note"] as String )!!.value.toInt()

                last["range"] = Regex("-?\\d")
                    .find( last["note"] as String )!!.value.toInt()

                for ( i in 0..length ) {
                    val sound = CustomSoundInstance(path);  sound.toPitch = i
                    val index = wholeNoteSet.indexOf(first["note"]) + i
                    sound.index = index
                    list[index] = sound
                }

            }

        }

        if (name == "drumset") return

        // Border pitch data
        val gaps = mutableMapOf< String, MutableList<Int> >(
            "back" to mutableListOf(),      "forward" to mutableListOf()
        )

        for ( sound in list.filterNotNull() ) {

            // For border pitch to be added to the list, the former and
            // last sound must have natural pitch

            if ( sound.toPitch == 0 || sound.toPitch == null ) {

                val index = list.indexOf(sound)

                if ( list[index + 1] !== null && list[index - 1] === null ) {
                    gaps["back"]!!.add(index)
                }

                if ( list[index - 1] !== null && list[index + 1] === null ) {
                    gaps["forward"]!!.add(index)
                }

            }

        }

        fun addBorderPitch( key: String, multiplier: Int ) {

            for ( index in gaps[key]!! ) {

                val id = list[index]!!.id
                val path = id.namespace + ":" + id.path

                for ( i in 1..12 ) {

                    val newIndex = i * multiplier

                    if ( list[index + newIndex] === null ) {
                        val sound = CustomSoundInstance(path)
                        sound.toPitch = newIndex;     sound.index = index + newIndex
                        list[index + newIndex] = sound
                    }

                }

            }

        }

        addBorderPitch("back", -1)
        addBorderPitch("forward", 1)

    }

    fun loadNbtData(stack: ItemStack) {

        val nbt = NbtCompound()
        val inst = stack.item as Instrument
        val shouldCenter = name != "drumset"

        nbt.putString("Sequence", "");      nbt.putString("TempSequence", "")
        nbt.putString("Action", "Melee");  nbt.putInt("MIDI Channel", 1)
        nbt.putFloat("Volume", 1f);         nbt.putBoolean("Center Notes", shouldCenter)
        nbt.putString( "MIDI Device", MidiSystem.getMidiDeviceInfo()[0].name )
        nbt.putBoolean( "isOnUse", false );     nbt.putInt("Index", -1)
        nbt.putString( "itemID", classes[inst::class] )
        nbt.putString( "itemClass", "Instruments" )

        stack.nbt!!.put(Base.MOD_NAME, nbt)

    }

    private fun setAttributes() {

        val attributeBuilder
                : ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier>
                = ImmutableMultimap.builder()

        attributeBuilder.put(
            EntityAttributes.GENERIC_ATTACK_SPEED,
            EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier",
                speed.toDouble(), EntityAttributeModifier.Operation.ADDITION
            )
        )

        attributes = attributeBuilder.build()

    }

    @Environment(EnvType.CLIENT)
    private fun playHitSound(stack: ItemStack) {

        val sound = getSounds(stack, "hits").random()!!;    sound.key = "hits"
        sound.pitch = (75..125).random() * 0.1f

        sound.playSound(stack)

    }

    fun stopAllNotes(stack: ItemStack, world: World?) {

        if ( world != null && world.isClient ) {

            val sounds = getSounds(stack, "notes")
            val playingSounds = sounds.filterNotNull()
                .filter { it.isPlaying && !it.isStopping() }

            for (note in playingSounds) { note.stopSound(stack) }

        }

    }

}

class Keyboard : Instrument( 5f, -2.4f, MusicalQuartz() )
class Organ : Instrument( 5f, -3.5f, MusicalIron() )
class DrumSet : Instrument( 3.5f, -3f, MusicalIron() )
class AcousticGuitar : Instrument( 3f, -2.4f, MusicalString() )

open class ElectricGuitar : Instrument( 4f, -2.4f, MusicalRedstone() ) {

    private val miningSpeed = MusicalRedstone().miningSpeedMultiplier
    private val effectiveBlocks = BlockTags.AXE_MINEABLE
    override fun getMiningSpeedMultiplier(stack: ItemStack?, state: BlockState?): Float {
        return if ( state!!.isIn(effectiveBlocks) ) miningSpeed else 1.0f
    }

    override fun canMine(state: BlockState?, world: World?, pos: BlockPos?, miner: PlayerEntity?): Boolean { return true }

    override fun postMine(
        stack: ItemStack?, world: World?,
        state: BlockState?, pos: BlockPos?, miner: LivingEntity?
    ): Boolean {

        if ( state!!.isIn(effectiveBlocks) ) {
            stack!!.damage(1, miner) { sendStatus(miner, stack) }
        }

        // What does the return bool do?
        return super.postMine(stack, world, state, pos, miner)

    }

}

class ElectricGuitarClean : ElectricGuitar() {

    private fun ability( stack: ItemStack, ply: PlayerEntity, entity: LivingEntity ) {

        if ( !ply.world.isClient ) {

            spawnHitParticles(entity, ParticleTypes.LANDING_OBSIDIAN_TEAR)
            entity.extinguish()

            val damage = ( material.durability * 0.1f ).toInt()
            stack.damage( damage, ply ) { sendStatus( it, stack ) }

        } else {

            stack.holder = ply
            val sound = getSounds(stack, "abilities").random()!!
            sound.pitch = (75..125).random() * 0.01f

            sound.playSound(stack)

        }

    }

    init {

        if ( FabricLoaderImpl.INSTANCE.environmentType == EnvType.CLIENT ) {
            soundsTemplate["abilities"] = MagicSounds().list
        }

    }

    override fun use(world: World?, user: PlayerEntity, hand: Hand?): TypedActionResult<ItemStack>? {

        return if (user.isOnFire) {

            val stack = user.getStackInHand(hand)
            ability(stack, user, user)

            TypedActionResult.pass(stack)

        } else super.use(world, user, hand)

    }

    override fun useOnEntity(
        stack: ItemStack?, player: PlayerEntity?, entity: LivingEntity?, hand: Hand?
    ): ActionResult {

        return if ( player!!.isInSneakingPose ) {

            ability(stack!!, player, entity!!)

            ActionResult.CONSUME

        } else super.useOnEntity(stack, player, entity, hand)

    }

}

class Harp : Instrument( 2f, -1f, MusicalString() )
class Viola : Instrument( 3.5f, -2f, MusicalString() )
class Violin : Instrument( 3.75f, -2f, MusicalRedstone() )
class Flute : Instrument( 1.25f, -1.5f, MusicalString() )
class Oboe : Instrument( 3.25f, -1f, MusicalIron() )

class Trombone : Instrument( 5f, -3f, MusicalRedstone() ) {

    override fun use(world: World?, user: PlayerEntity, hand: Hand?): TypedActionResult<ItemStack>? {

        val actionResult = super.use(world, user, hand)
        val stack = user.getStackInHand(hand)
        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        val isOnUse = nbt.getBoolean("isOnUse")

        if (isOnUse) {

            val action = nbt.getString("Action")
            if ( action == "Thrust" && user.isOnGround ) {

                if ( world!!.isClient ) {

                    val spd = speed + 4.5 // 3.5 (lowest speed) + 1
                    var value = 1 / spd;    value = (1 + value) * 0.75f
                    val dir = user.rotationVecClient.normalize().multiply(-value)

                    user.addVelocity(dir.x * 2, dir.y * 1.25, dir.z * 2)

                } else stack.damage(1, user) { sendStatus(it, stack) }

            }

        }

        return actionResult

    }

}
