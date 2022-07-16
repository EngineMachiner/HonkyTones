package com.enginemachiner.honkytones

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.*
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.tag.BlockTags
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import javax.sound.midi.MidiSystem
import kotlin.math.abs
import kotlin.math.pow

// ItemGroup
object HTGroupIcon: Item( Settings() )

private val iconID = Identifier(Base.MOD_NAME, "itemgroup")
val instrumentsGroup: ItemGroup = FabricItemGroupBuilder.create(iconID)!!
    .icon { HTGroupIcon.defaultStack }
    .build()

// Item Settings
private val settings = Item.Settings()
    .group( instrumentsGroup )
    .maxCount( 1 )

object HitSounds {
    val list = mutableListOf<HTSound?>()
    private const val PATH = "${Base.MOD_NAME}:hit0"
    init { for ( i in 1..9 ) {
        val sound = HTSound( PATH + i );    sound.volume = 0.5f
        list.add( sound )
    } }
}

object MagicSound {
    val sound = HTSound("${Base.MOD_NAME}:magic-c3-e3_")
    init { sound.volume = 0.5f }
}

open class Instrument(
    private val dmg: Float,     private val speed: Float,       mat: ToolMaterial
) : ToolItem( mat, settings.maxDamage( mat.durability ) ) {

    // Data
    val name = classesMap[this::class];     private val filesSet = soundsMap[name]!!
    var subsequence = ""

    val sounds = mutableMapOf<String, MutableList<HTSound?>>(
        "notes" to MutableList(127) { null },
        "hits" to HitSounds.list,
        "abilities" to mutableListOf(MagicSound.sound)
    )
    var onUse = false

    private val enchants = mutableMapOf<Enchantment, Int>()

    private var attributeBuilder
            : ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> = ImmutableMultimap.builder()

    private lateinit var attributeModifiers
            : ImmutableMultimap<EntityAttribute, EntityAttributeModifier>

    init {
        loadSounds(); setAttributes()
        setEnchantments(); networking(); tick()
    }

    // Vanilla methods

    // Stacks on creation can help me put custom NBTs
    override fun appendStacks(group: ItemGroup?, stacks: DefaultedList<ItemStack>?) {
        if (isIn(group)) {
            val stack = ItemStack(this);    loadNbtData(stack.orCreateNbt)
            stacks!!.add(stack)
        }
    }

    override fun getAttributeModifiers(slot: EquipmentSlot?)
            : Multimap<EntityAttribute, EntityAttributeModifier> {
        return if (slot == EquipmentSlot.MAINHAND) {
            attributeModifiers
        } else super.getAttributeModifiers(slot)
    }

    // This method wasn't letting me enchant, and I should have read better
    override fun canRepair(stack: ItemStack?, ingredient: ItemStack?): Boolean {
        return super.canRepair(stack, ingredient)
    }

    override fun use(world: World?, user: PlayerEntity, hand: Hand?)
            : TypedActionResult<ItemStack>? {

        // I need to set the current hand to the given hand
        // so the item and player is capable of holding input
        user.setCurrentHand(hand)

        val itemStack = user.getStackInHand(hand)
        if (world!!.isClient && !onUse) {

            onUse = true
            val sounds = sounds["notes"]!!

            val hasBadFormat = Regex("[-,][-,]").containsMatchIn(subsequence)
            val badText = "Subsequence has bad formatting!"
            if (hasBadFormat) {
                user.sendMessage(Text.of(badText), true)
                subsequence = ""
            }

            if (subsequence.isNotEmpty() && !hasBadFormat) {

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
                        } else user.sendMessage(userText(note, 0), false)

                    }
                }

                for (note in notes) {
                    var index = wholeNoteSet.indexOf(note)
                    index = getIndexIfCenter(itemStack.nbt!!, index)
                    if (index != -1 && sounds[index] != null) playSound(sounds[index]!!, user)
                    else user.sendMessage(userText(note, 1), false)
                }

                subsequence = subsequence.substringAfter(tempSeq)
                if (subsequence.isEmpty()) user.sendMessage(userText("", 2), true)

            } else if (!hasBadFormat) playRandomSound(user)
        }

        if (user.abilities.creativeMode) {
            return TypedActionResult.pass(itemStack)
        }
        return TypedActionResult.fail(itemStack)

    }

    override fun useOnEntity(
        stack: ItemStack?, user: PlayerEntity?,
        entity: LivingEntity?, hand: Hand? ): ActionResult {

        use(user!!.world, user, hand)

        val action = stack!!.nbt!!.getString("Action")
        val cd = user.getAttackCooldownProgress(0.5f)

        if (action == "Attack") {

            // Attack
            user.attack(entity)
            if (user.world.isClient) { playHitSound(entity!!) }
            else {

                // Random chance velocity
                val num = 30 - material.enchantability
                if ( (0..num).random() == 0 ) {
                    entity!!.addVelocity(0.0, 0.625, 0.0)
                }

                // Spawn particles
                spawnHitParticles(entity!!, ParticleTypes.FIREWORK)

                // Set the attack damage
                var dmg = dmg + material.attackDamage;      dmg *= cd
                entity.damage(DamageSource.player(user), dmg)

                stack.damage(1, user) { e: LivingEntity? ->
                    e!!.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND)
                }

            }

        }

        if (action == "Push" && !entity!!.isPlayer) {
            user.resetLastAttackedTicks()
            if (!user.world.isClient) {

                val spd = speed + 4.5 // 3.5 (lowest speed) + 1
                var value = cd / spd;       value = (1 + value) * 0.75f
                val dir = user.rotationVector.normalize()
                val y = cd * (abs(dir.y) + 1 / spd) * 0.625f

                entity.addVelocity(dir.x * value, y, dir.z * value)

                stack.damage(1, user) { e: LivingEntity? ->
                    e!!.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND)
                }

            }
        }

        if (action == "Play") { return ActionResult.PASS }
        return ActionResult.CONSUME

    }

    override fun getMaxUseTime(stack: ItemStack?): Int { return 2f.pow(1024f).toInt() }

    override fun onStoppedUsing(stack: ItemStack?, world: World?,
        user: LivingEntity?, remainingUseTicks: Int
    ) { stopAllNotes(world); onUse = false }

    // Logic and Data methods

    fun playRandomSound(ent: LivingEntity) {
        playSound(sounds["notes"]!!.filter {it != null && !it.isPlaying} .random()!!, ent)
    }

    private fun userText(element: String, i: Int): Text {
        val messages = mutableSetOf(
            " is not a sharp note!",    " note does not exist!",
            "HonkyTones sequence has ended!"
        )
        return Text.of(element + messages.elementAt(i))
    }

    // Get the note relative position (indexes) or distance
    // Needed to get the proper pitch
    private fun getNoteIndex(s: String): Int {
        for (note in twoOctaves) {
            // Find index by note range
            val find = s.replace(Regex("-?\\d"), "")
            if (find == note) return twoOctaves.indexOf(note)
        }
        return -1
    }

    // Move a semitone distance up an octave (12 semitones)
    private fun up(a: Int, b: Int): Int {
        if (a < b) return a + 12
        return a
    }

    fun getIndexIfCenter(nbt: NbtCompound, index: Int): Int {
        if (index == -1) return -1
        val sounds = sounds["notes"]!!;     var newIndex = index
        if ( nbt.getBoolean("Center Notes") ) {
            val filter = sounds.filterNotNull()
            val first = filter.first();     val last = filter.last()
            if (index < sounds.indexOf(first)) {
                while ( sounds[newIndex] == null ) newIndex += 12
            } else if (index > sounds.indexOf(last)) {
                while ( sounds[newIndex] == null ) newIndex -= 12
            }
            if (sounds[newIndex] != null) return newIndex
        }
        return index
    }

    private fun loadSounds() {

        val list = sounds["notes"]!!
        val firstPair = filesSet.first()
        val hasRangedPitch = firstPair.contains(Regex("-[A-Z]"))

        for ( pair in filesSet ) {
            val path = Base.MOD_NAME + ':' + name + '-' + pair.lowercase()
            if (!hasRangedPitch) {
                // No pitch alterations, each sound file is a note
                // The pair is just a note
                val index = wholeNoteSet.indexOf(pair)
                list[index] = HTSound(path)
            } else {

                // If you're setting up wrong formats this will throw exceptions

                // The pair has a range of notes
                val first = mutableMapOf<String, Any>(
                    "note" to Regex("^[A-Z]-?\\d_?").find(pair)!!.value
                )

                val last = mutableMapOf<String, Any>(
                   "note" to Regex("[A-Z]-?\\d_?$").find(pair)!!.value
                )

                // Semitone distance
                first["index"] = getNoteIndex(first["note"] as String)
                last["index"] = getNoteIndex(last["note"] as String)

                last["index"] = up(last["index"] as Int, first["index"] as Int)
                val length = last["index"] as Int - first["index"] as Int

                // Range
                first["range"] = Regex("-?\\d").find(first["note"] as String)!!.value.toInt()
                last["range"] = Regex("-?\\d").find(last["note"] as String)!!.value.toInt()

                for ( i in 0..length ) {

                    val sound = HTSound(path);  sound.toPitch = i
                    val index = wholeNoteSet.indexOf(first["note"]) + i
                    list[index] = sound

                }
            }
        }

        if (name == "drumset") return

        // Border pitch
        val gaps = mutableMapOf< String, MutableList<Int> >(
            "back" to mutableListOf(),      "forward" to mutableListOf()
        )

        for ( sound in list.filterNotNull() ) {
            // For border pitch to be added to the list, the former and last sound must have natural pitch
            if (sound.toPitch == 0 || sound.toPitch == null) {
                val index = list.indexOf(sound)
                if (list[index + 1] != null && list[index - 1] == null) gaps["back"]!!.add(index)
                if (list[index - 1] != null && list[index + 1] == null) gaps["forward"]!!.add(index)
            }
        }

        fun addBorderPitch(s: String, i2: Int) {
            for ( index in gaps[s]!! ) {
                val id = list[index]!!.id
                val path = id.namespace + ":" + id.path
                for (i in 1..12) {
                    val i3 = i * i2
                    if (list[index + i3] == null) {
                        val sound = HTSound(path)
                        sound.toPitch = i3
                        list[index + i3] = sound
                    }
                }
            }
        }

        addBorderPitch("back", -1)
        addBorderPitch("forward", 1)

    }

    private fun loadNbtData(nbt: NbtCompound) {
        val shouldCenter = name != "drumset"
        nbt.putString("Sequence", "")
        nbt.putString("Action", "Attack")
        nbt.putInt("MIDI Channel", 1)
        nbt.putFloat("Volume", 1f)
        nbt.putBoolean("Center Notes", shouldCenter)
        nbt.putString( "MIDI Device", MidiSystem.getMidiDeviceInfo()[0].name )
    }

    private fun setAttributes() {
        attributeBuilder.put(
            EntityAttributes.GENERIC_ATTACK_SPEED,
            EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID, "Weapon modifier",
                speed.toDouble(), EntityAttributeModifier.Operation.ADDITION
            )
        )
        attributeModifiers = attributeBuilder.build()
    }

    private fun setEnchantments() {

        for (i in (1..5)) {

            when {

                (i < 3) -> {
                    enchants[Enchantments.FIRE_ASPECT] = i
                    enchants[Enchantments.KNOCKBACK] = i
                }

                (i < 4) -> enchants[Enchantments.LOOTING] = i

                else -> {
                    enchants[Enchantments.SMITE] = i
                }

            }

        }
        enchants[Enchantments.MENDING] = 1

    }

    protected fun spawnHitParticles(entity: LivingEntity, particleType: ParticleEffect) {

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

    // Sounds
    private fun playHitSound(target: LivingEntity) {

        val sound = sounds["hits"]!!.random()!!;        sound.key = "hits"
        sound.pitch = (75..125).random() * 0.1f

        playSound(sound, target)

    }

    fun stopAllNotes(world: World?) {
        if ( world!!.isClient ) {
            val soundList = sounds["notes"]!!
            val soundsPlaying = soundList.filter {
                (it != null) && it.isPlaying && !it.isStopping()
            }
            for (note in soundsPlaying) { stopSound(note!!, soundList) }
        }
    }

    // Tick Logic
    private fun tick() {
        // Register the tick on client-side
        if ( FabricLoader.getInstance().environmentType == EnvType.CLIENT ) {
            val tick = ClientTickEvents.EndTick { client: MinecraftClient ->

                val player = client.player
                if (player != null) {

                    val ents = client.world!!.entities

                    bindLogic(client)

                    // Silence all instruments with no player
                    for ( itemEnt in ents ) {
                        for ( livingEnt in ents ) {
                            if ( itemEnt is ItemEntity && livingEnt is LivingEntity ) {

                                val stack = itemEnt.stack;      val item = stack.item

                                if (item is Instrument) {

                                    var shouldStop = livingEnt.mainHandStack != stack
                                    if (livingEnt is PlayerEntity) {
                                        val b1 = shouldStop && stack.nbt!!.getString("Action") != "Play"
                                        shouldStop = !livingEnt.inventory.contains(stack) || b1
                                    }

                                    if (shouldStop) item.stopAllNotes(client.world)

                                }

                            }
                        }
                    }

                }

            }
            ClientTickEvents.END_CLIENT_TICK.register(tick)
        }
    }

    // Bindings Logic
    private fun bindLogic(client: MinecraftClient) {

        val player = client.player!!
        val stack = player.mainHandStack
        val item = stack.item
        if (item.group == group) {

            // Screen open
            if ( sequenceMenuBind!!.wasPressed() ) {
                client.setScreen(Menu(stack))
            }

            // Reset sequence
            if (sequenceResetBind!!.wasPressed()) {
                subsequence = stack.nbt!!.getString("Sequence")
            }

        }

    }

    private fun networking() {

        // Sender

        // All read order and write order must be the same
        val hint = Base.MOD_NAME
        serverToClients("$hint-playsound", 25f) { buf: PacketByteBuf ->
            val newbuf = PacketByteBufs.create()

            // Instrument sound category, path, entity UUID, sound volume and pitch
            newbuf.writeString(buf.readString())
            newbuf.writeString(buf.readString());   newbuf.writeString(buf.readString())
            newbuf.writeFloat(buf.readFloat());     newbuf.writeFloat(buf.readFloat())

            newbuf
        }

        serverToClients("$hint-stopsound", 25f) { buf: PacketByteBuf ->
            val newbuf = PacketByteBufs.create()

            // Instrument sound category and path
            newbuf.writeString(buf.readString());   newbuf.writeString(buf.readString())

            newbuf
        }

        // Receiver
        val env = FabricLoader.getInstance().environmentType
        if ( env == EnvType.CLIENT ) {

            fun findEntity(client: MinecraftClient, uuid: String): LivingEntity {
                val entities = client.world!!.entities
                return entities.find {
                    it.uuidAsString == uuid && it.isLiving
                } as LivingEntity
            }

            var net = ClientPlayNetworking.PlayChannelHandler {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    packet: PacketByteBuf, _: PacketSender ->

                val category = packet.readString()
                val path = packet.readString();      val uuid = packet.readString()
                val volume = packet.readFloat();      val pitch = packet.readFloat()

                client.execute {

                    // Find entity
                    val entity = findEntity(client, uuid)

                    val sound = sounds[category]!!.find { it != null && it.id.path == path }!!

                    client.soundManager.play(sound)
                    sound.isPlaying = true
                    sound.volume = volume;      sound.pitch = pitch
                    sound.entity = entity

                }

            }
            ClientPlayNetworking.registerGlobalReceiver( Identifier("$hint-playsound"), net )

            net = ClientPlayNetworking.PlayChannelHandler {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val category = buf.readString();    val path = buf.readString()
                client.execute {
                    val list = sounds[category]!!
                    val sound = list.find { it != null && it.id.path == path }!!
                    val index = list.indexOf(sound)
                    val p = sound.id.namespace + ":" + sound.id.path
                    sound.stop();       list[index] = HTSound(p)
                }

            }
            ClientPlayNetworking.registerGlobalReceiver(Identifier("$hint-stopsound"), net)

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
        return if (state!!.isIn(effectiveBlocks)) miningSpeed else 1.0f
    }

}

class ElectricGuitarClean : ElectricGuitar() {

    override fun useOnEntity(stack: ItemStack?, user: PlayerEntity?, entity: LivingEntity?,
                             hand: Hand?): ActionResult {

        return if ( user!!.isInSneakingPose ) {

            if (!user.world.isClient) {

                spawnHitParticles(entity!!, ParticleTypes.LANDING_OBSIDIAN_TEAR)
                entity.extinguish()

                stack!!.damage((material.durability * 0.1f).toInt(), user) { e: LivingEntity? ->
                    e!!.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND)
                }

            } else {

                val sound = sounds["abilities"]!!.random()!!;       sound.key = "abilities"
                sound.pitch = (75..125).random() * 0.01f

                playSound(sound, entity!!)

            }
            ActionResult.CONSUME

        } else { super.useOnEntity(stack, user, entity, hand) }

    }

}

class Harp : Instrument( 2f, -1f, MusicalString() )
class Viola : Instrument( 3.5f, -2f, MusicalString() )
class Violin : Instrument( 3.75f, -2f, MusicalRedstone() )
class Flute : Instrument( 1.25f, -1.5f, MusicalString() )
class Oboe : Instrument( 3.25f, -1f, MusicalIron() )
class Trombone : Instrument( 5f, -3f, MusicalRedstone() )
