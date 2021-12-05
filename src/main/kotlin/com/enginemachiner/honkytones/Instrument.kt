package com.enginemachiner.honkytones

import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttribute
import net.minecraft.entity.attribute.EntityAttributeModifier
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.damage.DamageSource
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.*
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleEffect
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.tag.BlockTags
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.world.World
import kotlin.math.abs
import kotlin.math.pow

fun formatNote(s: String, i: Int): String {
    val flat = if ( s.length == 2 ) s[1] else ""
    return s[0] + i.toString() + flat
}

private val identifier = Identifier(Base.MOD_ID, "itemgroup")
val honkyTonesGroup: ItemGroup = FabricItemGroupBuilder.create(identifier)!!
    .icon { ItemStack(Blocks.NOTE_BLOCK) }
    .build()

private val setting = Item.Settings()
    .group( honkyTonesGroup )
    .maxCount( 1 )

// TODO: CREATE BLOCK PLAYER THAT PLAYS INSTRUMENTS AND RADIO ITEM SYNCED (should link to players instruments or the block)
// TODO: MAGIC BOOK THAT YOU NEED FOR THE BLOCK TO PLAY THE INSTRUMENTS
// TODO: To do above I need to do a better sequencer that handles ticks first and it can be more complex :( am shook

private fun musicalHitParticles(entity: LivingEntity, particleType: ParticleEffect) {

    val d = - MathHelper.sin(entity.yaw * (Math.PI.toFloat() / 180)).toDouble()
    val num = 5
    for ( i in 1..num ) {

        var e = MathHelper.cos(entity.yaw * (Math.PI.toFloat() / 180)).toDouble()
        if ( i == 5 ) { e *= 2 }

        if (entity.world is ServerWorld) {
            (entity.world as ServerWorld).spawnParticles(
                particleType, entity.x + ( i - num * 0.5 ) * 0.1,
                entity.getBodyY(0.5) + (-75..75).random() * 0.01, entity.z - ( i - num * 0.5 ) * 0.1,
                0, d, (-100..100).random() * 0.01, e, 0.0
            )
        }

    }

}

fun stopSounds(sound: HonkyTonesSoundInstance, id: String) {
    sound.stop()
    val buf = PacketByteBufs.create()
    buf.writeString(id)
    ClientPlayNetworking.send( Identifier(netID + "soundevent-stop"), buf )
}

fun playNetwork(sound: HonkyTonesSoundInstance, entity: LivingEntity, add: String) {

    sound.entity = entity
    MinecraftClient.getInstance().soundManager.play(sound)

    val buf = PacketByteBufs.create()
    // 1st substring is the sound id
    var s = "${sound.id} Entity: ${entity.uuidAsString}";   s += add
    buf.writeString(s)
    buf.writeFloat(sound.pitch)
    ClientPlayNetworking.send( Identifier(netID + "soundevent"), buf )

}

open class Instrument(
    private val dmg: Float,
    private val speed: Float,
    t: ToolMaterial
) : ToolItem(t, setting.maxDamage(t.durability) ) {

    override fun getAttributeModifiers(slot: EquipmentSlot?): Multimap<EntityAttribute, EntityAttributeModifier> {
        return if (slot == EquipmentSlot.MAINHAND) { attributeModifiers!! }
        else { super.getAttributeModifiers(slot) }
    }

    override fun canMine(state: BlockState?, world: World?, pos: BlockPos?, miner: PlayerEntity?): Boolean {
        return !miner!!.isCreative
    }
    override fun canRepair(stack: ItemStack?, ingredient: ItemStack?): Boolean { return true }

    val instrumentName = map[this::class]

    private val dataSet = data[instrumentName]!!
    private var soundPathHint = "honkytones:$instrumentName-"

    var noteSequence = "";         var sequenceSub = ""
    private var selectedNote = "";         private var selectedRange = 3

    var state = "Attack"

    private val builder: ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> = ImmutableMultimap.builder()
    private var attributeModifiers: ImmutableMultimap<EntityAttribute, EntityAttributeModifier>?

    init {

        builder.put(
            EntityAttributes.GENERIC_ATTACK_SPEED,
            EntityAttributeModifier(
                ATTACK_SPEED_MODIFIER_ID,
                "Weapon modifier",
                speed.toDouble(),
                EntityAttributeModifier.Operation.ADDITION
            )
        )
        attributeModifiers = builder.build()

        registerNetworking()

        if ( FabricLoader.getInstance().environmentType == EnvType.CLIENT ) {
            val tick = ClientTickEvents.EndTick { client: MinecraftClient ->
                if (client.player != null) {
                    updateData(client.player!!.mainHandStack.item)
                    inputScreen(client)
                }
            }
            ClientTickEvents.END_CLIENT_TICK.register(tick)
        }

    }

    open var incomingSounds = mutableMapOf< String, MutableList<HonkyTonesSoundInstance?> >()

    private fun registerNetworking() {

        serverToClients( netID + "soundevent", netID + "soundevent-clients", 25f ) {
                buf: PacketByteBuf -> val newbuf = PacketByteBufs.create()
            newbuf.writeString(buf.readString());       newbuf.writeFloat(buf.readFloat())
            newbuf
        }

        if ( FabricLoader.getInstance().environmentType == EnvType.CLIENT ) {

            val clientNet =
                ClientPlayNetworking.PlayChannelHandler {

                        client: MinecraftClient, _: ClientPlayNetworkHandler,
                        packet: PacketByteBuf,
                        _: PacketSender ->

                    var s = packet.readString()
                    val pitch = packet.readFloat()

                    client.execute {

                        val soundPath = s.substringBefore(" Entity: ")
                        s = s.substringAfter(" Entity: ")
                        val entID = s.substringBefore(" ID: ")
                        s = s.substringAfter(" ID: ")
                        val id = s // last
                        val instance = getSoundInstance(soundPath)
                        instance.pitch = pitch
                        for (ent in client.world!!.entities) {
                            if (ent.uuidAsString == entID && ent.isLiving) {
                                instance.entity = ent as LivingEntity
                                break
                            }
                        }

                        if (incomingSounds[id] == null) {
                            incomingSounds[id] = mutableListOf()
                        }
                        val list = incomingSounds[id]!!
                        list.add(instance)
                        client.soundManager.play(instance)

                    }

                }
            ClientPlayNetworking.registerGlobalReceiver(Identifier(netID + "soundevent-clients"), clientNet)

        }

        serverToClients( netID + "soundevent-stop", netID + "soundevent-stop-clients", 25f ) {
                buf: PacketByteBuf ->
            val newbuf = PacketByteBufs.create()
            newbuf.writeString(buf.readString())
            newbuf
        }

        if ( FabricLoader.getInstance().environmentType == EnvType.CLIENT ) {

            val clientNet =
                ClientPlayNetworking.PlayChannelHandler {
                    client: MinecraftClient, _: ClientPlayNetworkHandler, buf: PacketByteBuf,
                    _: PacketSender ->
                val id = buf.readString()
                client.execute {
                    for (instance in incomingSounds[id]!!) { instance!!.stop() }
                    incomingSounds[id] = mutableListOf()
                }
            }

            ClientPlayNetworking.registerGlobalReceiver( Identifier(netID + "soundevent-stop-clients"), clientNet )

        }

        // Networking screen data
        val net =
            ServerPlayNetworking.PlayChannelHandler {
                    server: MinecraftServer, player: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf,
                    _: PacketSender ->

                val data = buf.readString()
                server.execute {
                    val inst = player.mainHandStack.item as Instrument
                    inst.noteSequence = data.substringBefore(" Action: ")
                    inst.state = data.substringAfter(" Action: ")
                    inst.sequenceSub = ""
                }

        }

        ServerPlayNetworking.registerGlobalReceiver( Identifier(netID + "client-screen"), net )

    }

    val useKeyID = "keyUse"
    private var ranOnce = true
    override fun use(world: World?, user: PlayerEntity, hand: Hand?): TypedActionResult<ItemStack>? {

        if (world!!.isClient && ranOnce) { inputLogic(useKeyID); ranOnce = false }
        val itemStack = user.getStackInHand(hand)
        user.setCurrentHand(hand)
        if (user.abilities.creativeMode) {
            return TypedActionResult.pass(itemStack)
        }
        return TypedActionResult.fail(itemStack)

    }

    // Tick logic
    private fun customPostHit(target: LivingEntity?) {

        val i = (1..9).random()
        val sound = getSoundInstance("${Base.MOD_ID}:hit0$i")
        sound.pitch = (75..125).random() * 0.1f
        sound.volume = 0.625f

        playNetwork(sound, target!!, " ID: $useKeyID-hit")

    }
    override fun useOnEntity(stack: ItemStack?, user: PlayerEntity?, entity: LivingEntity?, hand: Hand?): ActionResult {

        use(user!!.world, user, hand)

        val cd = user.getAttackCooldownProgress(0.5f)

        if (state == "Attack") {

            user.attack(entity)
            if ( user.world.isClient ) {

                customPostHit(entity)

            } else {

                // Random chance
                val num = 30 - material.enchantability
                if ((0..num).random() == 0) {
                    entity!!.addVelocity(0.0, 0.625, 0.0)
                }

                musicalHitParticles(entity!!, ParticleTypes.FIREWORK)

                var dmg = dmg + material.attackDamage
                dmg *= cd
                entity.damage(DamageSource.player(user), dmg)

                stack!!.damage(1, user) { e: LivingEntity? ->
                    e!!.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND)
                }

            }

        }

        if (state == "Push" && !entity!!.isPlayer) {
            user.resetLastAttackedTicks()
            if ( !user.world.isClient ) {
                var value = speed + 4.5 // 3.5 (lowest) + 1
                value = 3 / value.pow(2)
                val dir = user.rotationVector.normalize().multiply(value)
                entity.addVelocity(dir.x, abs(dir.y), dir.z)

                stack!!.damage(1, user) { e: LivingEntity? ->
                    e!!.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND)
                }
            }
        }

        if (state == "Play") { return ActionResult.PASS }
        return ActionResult.CONSUME

    }

    override fun getMaxUseTime(stack: ItemStack?): Int { return 150 }

    override fun onStoppedUsing(stack: ItemStack?, world: World?, user: LivingEntity?, remainingUseTicks: Int) {

        if (world!!.isClient()) {
            ranOnce = true
            val list = soundMap[useKeyID]!!
            for (i in 0 until list.size) { stopSounds( list[i]!!, useKeyID) }
            soundMap[useKeyID] = mutableListOf()
        }

    }

    // Each keybinding has a sound and SOUND state assigned
    // First string should be the keybinding translation key or some id
    private val soundMap = mutableMapOf< String, MutableList<HonkyTonesSoundInstance?> >()

    // Play and stop
    private fun inputLogic(id: String) {

        val player = MinecraftClient.getInstance().player!!

        if (soundMap[id] == null) { soundMap[id] = mutableListOf() }

        // Group of notes to play at the same time
        val noteGroup = mutableListOf<HonkyTonesSoundInstance?>()

        if ( id == useKeyID ) {

            // Sequencer logic
            if ( noteSequence.isNotEmpty() && state == "Play" ) {


                if ( sequenceSub.isEmpty() ) { sequenceSub = noteSequence }

                when {

                    sequenceSub.contains(',') -> {
                        val sub = sequenceSub.substringBefore("-")
                        for (note in sub.split(",")) {
                            noteGroup.add( getNote(this, note) )
                        }
                    }

                    sequenceSub.contains('-') -> {
                        val note = sequenceSub.substringBefore("-")
                        noteGroup.add( getNote(this, note) )
                    }

                    else -> { noteGroup.add( getNote(this, sequenceSub) ) }

                }
                sequenceSub = sequenceSub.substringAfter("-")

            } else { noteGroup.add( getRandomKey(this) ) }

        }

        for ( soundPath in noteGroup ) {

            if (soundPath!!.id.path.isNotEmpty()) {

                val list = soundMap[id]!!
                list.add(soundPath)
                val last = list[list.size - 1]!!

                playNetwork(last, player, " ID: $useKeyID")

            }

        }

    }

    // Sequencer and action menu
    private fun inputScreen(client: MinecraftClient) {

        val invBind = sequenceBind!!
        val player = client.player!!

        val item = player.mainHandStack.item
        if (invBind.wasPressed() && item.group == group) {
            if ( client.world!!.isClient ) {
                client.soundManager.stopAll()
            }
            client.setScreen( Menu() )
        }

    }

    private var minRange = 0;       private var maxRange = 0
    private fun updateData(item: Item) {

        // Search ranges for each instrument
        if ( item.group == group ) {

            val instrument = item as Instrument
            val name = instrument.instrumentName
            val data = data[name]!!
            val first = data.elementAt(0)
            val last = data.elementAt(data.size - 1)
            minRange = first.substringBefore("-").filter { it.isDigit() }.toInt() - 1
            maxRange = last.substringAfter("-").filter { it.isDigit() }.toInt() + 1

            while ( restartBind!!.wasPressed() ) { item.sequenceSub = item.noteSequence }

        }

        when {
            selectedRange < minRange -> selectedRange = minRange
            selectedRange > maxRange -> selectedRange = maxRange
        }

    }

    // Notes logic
    private fun getRandomKey(inst: Instrument): HonkyTonesSoundInstance {
        var key = octave.random()
        val random = (3..4).random().toString()
        key = if ( key.length == 1 ) key + random else key[0] + random + key[1]
        if ( getNote(inst, key).id.path.isNotEmpty() ) { return getNote(inst, key) }
        return getNote(inst, "")
    }

    fun getNote(inst: Instrument, s: String): HonkyTonesSoundInstance {

        val debug = false
        fun path(s: Any): String { return inst.soundPathHint.plus(s).lowercase() }

        if ( s.length > 3 || s.filter { it.isDigit() } == "" ) {
            println(" [HONKYTONES]: ERROR: $s has wrong format!")
            return getSoundInstance("")
        }

        var hint = s
        val range = hint.filter { it.isDigit() } .toInt()

        // Sharp notation
        for ( sharp in reference.keys ) {
            if (sharp == hint.filter { !it.isDigit() }) {
                val note = reference[sharp]!!
                hint = "${note[0]}$range${note[1]}"
                break
            }
        }

        val noteHint = hint[0]
        val dataSet = inst.dataSet
        val flat = if (hint.length > 2) hint[2] else ""

        var pitch = 1f
        var result = ""

        // Get skipping intervals
        val borderNotes = mapOf< String, MutableSet<String> >(
            "down" to mutableSetOf(), "up" to mutableSetOf()
        )
        val lowerBorder = borderNotes["down"]!!
        val higherBorder = borderNotes["up"]!!

        var i = 0

        lowerBorder.add(dataSet.elementAt(0))
        for ( note in dataSet ) {

            if ( i + 1 > dataSet.size - 1 ) { break }

            val one = dataSet.elementAt(i)[1].toString().toInt()
            val two = dataSet.elementAt(i + 1)[1].toString().toInt()

            if ( two > one + 1 ) {
                higherBorder.add( dataSet.elementAt(i) )
                lowerBorder.add( dataSet.elementAt(i + 1) )
                break
            }
            i++

        }
        higherBorder.add(dataSet.elementAt(dataSet.size - 1))

        for ( note in dataSet ) {

            // 1. case -> Literal
            if (note == hint) { result = path(note); break }

            // 2. case -> Pitch

            // Note and range reference
            var noteRangeI = note.substringBefore("-")
            var noteRangeE = note.substringAfter("-")

            // First note range found, no pitch affected
            if (noteRangeI == hint) { result = path(note); break }

            // Indexes indicate relative positions
            var index = 0;      var index2 = 0;        var index3 = 0

            // Notes in range of a double octave (Non-border notes)
            if (noteRangeI != noteRangeE) {

                // Start
                for (noteDO in doubleOctave) {
                    val b = noteRangeI.filter { !it.isDigit() };    index++
                    if (noteDO == b) { break }
                }

                // End
                for (noteDO in doubleOctave) {
                    val b = noteRangeE.filter { !it.isDigit() };    index3++
                    if (noteDO == b && index3 >= index) { break }
                }

                // Max pitch case
                if (noteRangeE == hint) { pitch = 2f.pow( ( index3 - index ).toFloat() / 12f )
                    result = path(note); break
                }

                // Between
                for (noteDO in doubleOctave) { index2++
                    if (noteDO == "$noteHint$flat" && index3 >= index2 && index2 >= index) { break }
                }

                val dist = abs( index2 - index )
                if ( index2 <= index3 && range == noteRangeI.filter { it.isDigit() } .toInt() ) {

                    if (debug) {
                        println("//// Between pitching ////")
                        println("Indexes: $index $index2 $index3")
                        println("Dist: $dist")
                        println("Note: $hint")
                        println("Intervals: $noteRangeI, $noteRangeE")
                        println("$doubleOctave")
                    }

                    pitch = 2f.pow(( dist ).toFloat() / 12f )
                    result = path(note)
                    break

                }

            }

            // Border note pitching

            // Range
            index = noteRangeI.filter { it.isDigit() } .toInt()
            index3 = noteRangeE.filter { it.isDigit() } .toInt()
            index2 = range

            // Note
            noteRangeI = noteRangeI.filter { !it.isDigit() }
            noteRangeE = noteRangeE.filter { !it.isDigit() }

            // When the note is lower than the border note
            var check = false
            var borderNote = dataSet.elementAt(0)
            if (lowerBorder.size > 1) {
                for ( bNote in lowerBorder ) {
                    check = note == bNote && bNote[1].toString().toInt() - 1 == range
                    if ( check ) { borderNote = bNote;     break }
                }
            }

            if ( index2 < index || check ) {
                if ( note == dataSet.elementAt(0) || check ) {

                    var b = 0
                    for (noteDO in doubleOctave) { b++
                        if (noteDO == noteRangeI) { break }
                    }

                    var a = 0
                    for (noteDO in doubleOctave) { a++
                        if (noteDO == "$noteHint$flat") { break }
                    }

                    val dist = ( index2 - index )
                    pitch = 2f.pow( (a - b + 12 * dist).toFloat() / 12f )

                    if (debug) {
                        println("//// Border pitching down ////")
                        println("Index: $index2 $index")
                        println("Note: $noteHint$flat")
                        println("EndRange: $noteRangeE")
                        println("Dist: $b, $a");   println("Range Dist: $dist")
                        println("Pitch: $pitch")
                        println("$borderNote $range")
                        println(doubleOctave)
                    }

                    result = path(borderNote); break

                }
            }

            // When the note is higher than the border note
            check = false
            borderNote = dataSet.elementAt(dataSet.size - 1)
            if (higherBorder.size > 1) {
                for ( bNote in higherBorder ) {
                    check = note == bNote && bNote[1].toString().toInt() + 1 == range
                    if ( check ) { borderNote = bNote;     break }
                }
            }

            if ( index3 < index2 || check ) {
                if ( note == dataSet.elementAt(dataSet.size - 1) || check ) {

                    var b = 0
                    for (noteDO in doubleOctave) { b++
                        if ( noteDO == "$noteHint$flat" ) { break }
                    }

                    var a = 0
                    for (noteDO in doubleOctave) { a++
                        if (noteDO == noteRangeI) { break }
                    }

                    val dist = ( index2 - index )
                    pitch = 2f.pow((b - a + 12 * dist).toFloat() / 12f )

                    if (debug) {
                        println("//// Border pitching up ////")
                        println(s)
                        println("Index: $index2 $index")
                        println("Note: $noteHint$flat")
                        println("EndRange: $noteRangeI")
                        println("Dist: $b, $a");   println("Range Dist: $dist")
                        println("$pitch")
                        println("$borderNote $range")
                        println(doubleOctave)
                    }

                    result = path(borderNote); break

                }
            }

        }

        if ( result.isNotEmpty() && (0.5f..2f).contains(pitch) ) {
            val sound = getSoundInstance(result)
            sound.pitch = pitch
            return sound
        }

        // Warn the user about the limits
        println(" [HONKYTONES]: ERROR: $s note is out of range or not found!")

        return getSoundInstance("")

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
        return if (effectiveBlocks.contains(state!!.block)) miningSpeed else 1.0f
    }

}

class ElectricGuitarClean : ElectricGuitar() {

    override fun useOnEntity(stack: ItemStack?, user: PlayerEntity?, entity: LivingEntity?, hand: Hand?): ActionResult {

        return if ( user!!.isInSneakingPose ) {

            if (!user.world.isClient) {

                musicalHitParticles(entity!!, ParticleTypes.LANDING_OBSIDIAN_TEAR)
                entity.extinguish()

                stack!!.damage((material.durability * 0.1f).toInt(), user) { e: LivingEntity? ->
                    e!!.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND)
                }

            } else {

                val sound = getSoundInstance("${Base.MOD_ID}:magic")
                sound.pitch = (75..125).random() * 0.01f

                playNetwork(sound, entity!!, " ID: $useKeyID-ability")

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
