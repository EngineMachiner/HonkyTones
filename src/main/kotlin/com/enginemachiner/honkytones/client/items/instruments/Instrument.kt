package com.enginemachiner.honkytones.client.items.instruments

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.HarmonyItem.Companion.trackHolder
import com.enginemachiner.harmony.NBT
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.Message
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.client.Particles
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.Sender
import com.enginemachiner.honkytones.ModParticles
import com.enginemachiner.honkytones.MusicTheory.completeSet
import com.enginemachiner.honkytones.MusicTheory.index
import com.enginemachiner.honkytones.MusicTheory.noteCount
import com.enginemachiner.honkytones.MusicTheory.noteMap
import com.enginemachiner.honkytones.MusicTheory.sharpsToFlats
import com.enginemachiner.honkytones.MusicTheory.shift
import com.enginemachiner.honkytones.client.Config
import com.enginemachiner.honkytones.client.sound.InstrumentSound
import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import net.minecraft.client.util.InputUtil
import net.minecraft.entity.Entity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.item.ItemStack
import net.minecraft.particle.ParticleEffect
import net.minecraft.util.math.Vec3d
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private typealias InstrumentSounds = MutableList<InstrumentSound?>

object Instrument : ModID {

    val netStacks = NetStacks()

    fun stop(stack: ItemStack) {

        if ( stack.item !is InstrumentItem ) return

        soundsCopy(stack).common.stop()

    }

    fun stopMidi(stack: ItemStack) { soundsCopy(stack).device.stop() }

    private fun playRandom(stack: ItemStack) {

        val copy = soundsCopy(stack);           val sound = copy.common.random()

        copy.play( sound, stack );           if ( stack.holder is MobEntity ) sound.fadeOut()

    }

    private fun play(stack: ItemStack) {

        val nbt = nbt(stack);       val holder = stack.holder ?: return


        val isRanged = nbt.getString("Action") == "Ranged"

        if ( !isRanged ) ActionParticles.spawn( holder, "simple" )


        val parser = SequenceParser(stack);     val read = parser.read()

        if ( !read ) playRandom(stack) else send(nbt)

    }

    fun soundsCopy( stack: ItemStack ): Sounds.Copy { return Sounds.copy(stack) }

    private class SequenceParser( val stack: ItemStack ) {

        private val nbt = nbt(stack);       private var current = input()

        private fun input(): String { return nbt.getString("Sequence") }

        private fun put(next: String ) { nbt.putString( "Sequence", next ) }

        private fun isEmpty(): Boolean { return current.isEmpty() }

        private fun clear() { put("") }

        /** Returns if it was possible to read. */
        fun read(): Boolean {

            if ( isEmpty() || !isValid() ) return false


            val next = next()

            val notes = next.split(",").toMutableList()

            notes.forEach { sharps(it, notes) }

            notes.forEach { val play = play(it);        if ( !play ) return false }


            current = current.substringAfter(next);          onLast()


            val remove = current.isNotEmpty() && current.first() == '-'

            if (remove) current = current.substring(1)


            put(current);         return true

        }

        private fun onLast() {

            val warning = Translations.sequenceEnd

            if ( current.isNotEmpty() ) return

            Message(warning).send(true)

        }

        private fun play( it: String ): Boolean {

            val copy = soundsCopy(stack);             val sounds = copy.common()

            var index = completeSet.indexOf(it);            index = copy.pos(index)

            val translation = Translations.noteError.replace( "X", it )

            try {

                val sound = sounds[index]!!;       sound.play(stack);       return true

            } catch ( _: Exception ) { sendMessage(translation);    return false }

        }

        /** Get the next section to play. */
        private fun next(): String {

            val match = Regex("^\\D*\\d*[^-]*").find( current )

            return match?.value ?: current

        }

        private fun isValid(): Boolean {

            val first = "${ current.first() }";       val last = "${ current.last() }"

            val regex = Regex("[-,]")
            val repeats = Regex("[-,][-,]").containsMatchIn(current)
            val isValid = !regex.matches(first) && !repeats && !regex.matches(last)

            if ( !isValid ) { sendMessage("error.invalid_sequence");       clear() }

            return isValid

        }

        private fun sharps( it: String, section: MutableList<String> ) {

            val hasSharps = it.contains("#");       if ( !hasSharps ) return


            val regex = Regex("-?\\d")

            val index = section.indexOf(it);      val range = regex.find(it)

            val sharp = it.replace( regex, "" )

            val flat = sharpsToFlats[sharp]

            val translation = Translations.sharpError.replace( "X", it )


            if ( flat == null || range == null ) sendMessage(translation) else {

                section[index] = flat[0] + range.value + flat[1]

            }

        }

    }

    object ActionParticles {

        private val spawn = mapOf( "simple" to ::spawnSimpleNote,      "device" to ::spawnDeviceNote )

        private fun spawnDeviceNote(entity: Entity) {

            val slices = 12
            val radius = Random.nextInt(750) * 0.001
            val angle = Random.nextInt(slices) * 360.0 / slices
            val height =  Random.nextInt( 50,175 ) * 0.01

            val data = Vec3d( radius, angle, height )

            spawnNote( ModParticles.DEVICE_NOTE, entity, data )

        }

        private var onMainHand = false

        private const val ANGLE_BETWEEN_HANDS = 15

        private fun spawnSimpleNote(entity: Entity) {

            var data = Vec3d( 1.5, 0.0, 1.5 );      var n = 0

            entity.itemsHand.forEach { if ( it.item is InstrumentItem) n++; }

            if ( n != 2 ) onMainHand = false else {

                val angle = ANGLE_BETWEEN_HANDS

                var y = data.y + angle;     if (onMainHand) y = data.y - angle


                data = Vec3d( data.x, y, data.z );      onMainHand = !onMainHand

            }

            spawnNote( ModParticles.SIMPLE_NOTE, entity, data )

        }

        private fun spawnNote( particle: ParticleEffect, entity: Entity, data: Vec3d ) {

            val radius = data.x;     val angleOffset = data.y;      val height = data.z

            var yaw = entity.yaw + 90.0 + angleOffset;          yaw = rad(yaw)

            val angle = Vec3d( cos(yaw), 0.0, sin(yaw) ).multiply(radius)

            val pos = entity.pos.add(angle).add( Vec3d( 0.0, height, 0.0 ) )

            Particles.spawnOne( particle, pos )

        }

        private fun canSpawn(entity: Entity): Boolean {

            val config = Config.client()
            val player = entity.isPlayer && config.playerParticles
            val mob = entity is MobEntity && config.mobParticles

            return player || mob

        }

        //** Spawn particles to the clients. */
        fun spawn( entity: Entity, particleName: String ) {

            val netID = netID("particle");      val id = entity.id

            val sender = Sender(netID) { it.write(id).write( particleName ) }

            sender.toServer()

        }

        fun networking() {

            val id = netID("particle")

            Receiver(id).register {

                val id = it.readInt();      val type = it.readString()


                client().send {

                    val entity = entity(id) ?: return@send

                    if ( !canSpawn(entity) ) return@send

                    val spawn = spawn[type]!!;          spawn(entity)

                }

            }

        }

    }

    object Sounds {

        val models = mutableMapOf<InstrumentItem, Model>()

        private val sounds = mutableMapOf<Int, Copy>() // The Int is the stack ID.

        fun copy( stack: ItemStack ): Copy {

            val nbt = nbt(stack);           val id = nbt.getInt("ID")

            if ( sounds[id] == null ) sounds[id] = Copy(stack)

            return sounds[id]!!

        }

        open class SoundsList {

            fun copy( soundList: SoundsList ) {

                soundList.all.forEachIndexed { i, sound ->

                    val sound = sound ?: return@forEachIndexed

                    val path = sound.path;          val semitones = sound.semitones()

                    all[i] = InstrumentSound( path, semitones )

                }

                init()

            }

            var all: InstrumentSounds = MutableList( noteCount() ) { null }

            var filtered = listOf<InstrumentSound>()

            fun random(): InstrumentSound { return filtered.random() }

            fun init() { filtered = all.filterNotNull() }

            /** Stops all the instrument sounds. */
            fun stop() {

                val list = filtered.filter { it.isPlaying() && !it.isStopping() }

                list.forEach { it.fadeOut() }

            }

        }

        abstract class Builder {

            open val common = SoundsList()

            protected abstract fun add()

            fun common(): List<InstrumentSound?> { return common.all }

        }

        /** It builds the sound data model based on an instrument. */
        class Model( private val instrument: InstrumentItem ) : Builder() {

            private val sounds = common.all

            init { add();     common.init() }

            /** Add the instrument sounds. */
            override fun add() {

                val files = noteMap[ instrument::class ]!!

                val isRanged = files.first().contains( Regex("-[A-Z]") )

                val className = instrument.className()


                // 1. Assign each sound.

                for ( fileName in files ) {

                    val path = className + '.' + fileName.lowercase()

                    if ( !isRanged ) {

                        // Each file is a sound. No pitch tweaking.

                        val index = completeSet.indexOf(fileName)

                        sounds[index] = InstrumentSound(path)

                    } else {

                        // There is a range of notes. There is pitch tweaking.

                        val pair1 = Regex("^[A-Z]-?\\d_?").find(fileName)!!.value
                        val pair2 = Regex("[A-Z]-?\\d_?$").find(fileName)!!.value


                        // Semitones distance.

                        val index1 = index(pair1);      var index2 = index(pair2)

                        index2 = shift( index2, index1 );       val length = index2 - index1


                        for ( pitch in 0..length ) {

                            val sound = InstrumentSound( path, pitch )

                            val index = completeSet.indexOf(pair1) + pitch

                            sounds[index] = sound

                        }

                    }

                }


                if ( instrument is DrumSet ) return


                // 2. Add border pitch sounds.

                val lowIndexes = mutableListOf<Int>();       val highIndexes = mutableListOf<Int>()

                sounds.filterNotNull().forEach {

                    // Only pick sounds with no pitch tweaking and assign their indexes.

                    if ( it.semitones() != 0 ) return@forEach

                    val i = sounds.indexOf(it);          val back = sounds[ i - 1 ];          val front = sounds[ i + 1 ]

                    if ( back == null && front != null ) lowIndexes.add(i)
                    else if ( back != null && front == null ) highIndexes.add(i)

                }


                // 3. Do the borders.

                border( lowIndexes, -1 );       border( highIndexes, 1 )

            }

            private fun border( indexes: MutableList<Int>, direction: Int ) {

                for ( i in indexes ) {

                    val path = sounds[i]!!.path
                    
                    ( 1..12 ).forEach { // 12 semitones max.

                        val pitch = it * direction;         val index = i + pitch

                        if ( sounds[index] != null ) return@forEach

                        val sound = InstrumentSound( path, pitch )

                        sounds[index] = sound

                    }

                }

            }

        }

        /** Creates a copy of the model based on the stack's instrument. */
        class Copy( private val stack: ItemStack ): Builder() {

            val device = SoundsList()

            fun device(): InstrumentSounds { return device.all }


            private val instrument = stack.item as InstrumentItem

            init { add();     common.init() }

            // Stack as parameter because stacks all the time are not the same.
            fun play( sound: InstrumentSound, stack: ItemStack ) { sound.play(stack) }

            /** Gets the sound position. */
            fun pos( index: Int ): Int {

                if ( index == -1 ) return index


                val nbt = nbt(stack)

                val isCenter = nbt.getBoolean("Center Notes")

                if ( !isCenter ) return index


                val filtered = common.filtered

                if ( filtered.size < 24 ) return index


                var next = index;       val first = filtered.first()

                val last = filtered.last()


                val sounds = common()

                while ( next > sounds.size - 1 ) next -= 12


                if ( index < sounds.indexOf(first) ) {

                    while ( sounds[next] == null ) next += 12

                } else if ( index > sounds.indexOf(last) ) {

                    while ( sounds[next] == null ) next -= 12

                }


                return next

            }

            override fun add() {

                val model = models[instrument]!!

                val modelNotes = model.common()

                modelNotes.forEach {

                    it ?: return@forEach;           val i = modelNotes.indexOf(it)

                    val path = it.path;             val semitones = it.semitones()

                    common.all[i] = InstrumentSound( path, semitones )

                }

                common.init();      device.copy(common)

            }

        }

    }

    object KeyBindings {

        private const val CATEGORY = "instrument"

        val play = ModKey( "play", CATEGORY );           val menu = ModKey( "reset", CATEGORY )

        val reset = ModKey( "menu", CATEGORY, InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE )

        fun register() { play.register();   menu.register();    reset.register() }

    }

    object Tick {

        private var isPlaying = false

        private fun keyPlay(stack: ItemStack) {

            val play = KeyBindings.play.bind();       val isPressed = play.isPressed

            if ( isPressed && !isPlaying ) {

                isPlaying = true;       play(stack)

            } else if ( !isPressed && isPlaying ) {

                isPlaying = false;      stop(stack)

            }

        }

        /** Resets the instrument sequence. */
        private fun reset( stack: ItemStack, isLast: Boolean ) {

            val reset = KeyBindings.reset.bind();         val isPressed = reset.isPressed

            if ( !isPressed ) return


            val nbt = nbt(stack);       val sequence = nbt.getString("lastSequence")

            nbt.putString( "Sequence", sequence );      send(nbt)


            if ( !isLast ) return;      reset.isPressed = false

            Message("message.reload_sequences").send()

        }


        /** Tries to open the instrument screen. Won't open if player has two instruments in both hands. */
        private fun screen( stack: ItemStack, canOpen: Boolean ) {

            val menu = KeyBindings.menu.bind();           if ( !menu.isPressed || !canOpen ) return

            menu.isPressed = false;         client().setScreen( InstrumentsScreen(stack) )

        }


        fun onKey() {

            val handItems = player().itemsHand.toSet();      val size = handItems.size

            val instruments = handItems.filter { it.item is InstrumentItem }.toSet()

            if ( instruments.isEmpty() ) return


            for ( stack in instruments ) {

                keyPlay(stack);        reset( stack, stack == instruments.last() )

                screen( stack, instruments.size < size )

            }

        }


    }

    object Translations {

        val sharpError = Translation.get("error.sharp")
        val noteError = Translation.get("error.note")
        val sequenceEnd = Translation.get("message.sequence_end")

    }

    fun networking() {

        ActionParticles.networking()

        fun common( id: String, onClient: (ItemStack) -> Unit ) {

            val id = netID(id)

            Receiver(id).register {

                val slot = it.readInt()


                client().send {

                    val stack = stack(slot);        if ( !NBT.has(stack) ) return@send

                    onClient(stack)

                }

            }

        }

        common("play") { play(it) }

        common("stop") { stop(it) }

        common("stop_midi") { stopMidi(it) }


        val id = netID("mob_play")

        Receiver(id).register {

            val id = it.readInt()


            client().send {

                val mob = entity(id) ?: return@send

                mob as MobEntity;       val stack = mob.mainHandStack

                trackHolder(stack, mob);        playRandom(stack)

            }

        }

    }

}