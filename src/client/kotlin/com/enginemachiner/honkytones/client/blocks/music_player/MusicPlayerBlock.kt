package com.enginemachiner.honkytones.client.blocks.music_player

import MediaInfo
import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.Particles.MIN_DISTANCE
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.Message
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.client.Particles
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.Sender
import com.enginemachiner.honkytones.ModParticles
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayer.ActionParticles.waves
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity.Companion.INVENTORY_SIZE
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity.Companion.get
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerEntity
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerScreenHandler
import com.enginemachiner.honkytones.blocks.music_player.host
import com.enginemachiner.honkytones.client.*
import com.enginemachiner.honkytones.client.HonkyTones.Companion.directories
import com.enginemachiner.honkytones.client.Silencer.isMuted
import com.enginemachiner.honkytones.client.items.instruments.Instrument
import com.enginemachiner.honkytones.client.items.instruments.Instrument.soundsCopy
import com.enginemachiner.honkytones.client.items.music_player.Radio
import com.enginemachiner.honkytones.client.mixin.WorldRendererAccessor
import com.enginemachiner.honkytones.client.sound.CustomSound
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.particle.ParticleEffect
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import kotlin.io.path.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val coroutine = Coroutine("Music Player")

object MusicPlayerBlock : ModID {

    fun register() { MusicPlayerScreen.register() }

    private fun updateOnScreen( id: Int, set: (MusicPlayerScreen) -> Unit ) {

        val screen = currentScreen();       if ( screen !is MusicPlayerScreen ) return

        if ( screen.musicPlayer.id != id ) return;          set(screen)

    }

    fun networking() {

        MusicPlayer.networking()


        var id = netID("midi_tick")

        Receiver(id).register {

            val id = it.readInt()


            client().send { MusicPlayer.get(id).midiTick() }

        }


        id = netID("init")

        Receiver(id).register {

            val pos = it.readBlockPos();       val id = it.readInt()

            val stacks = MusicPlayer.stacksSent(it)

            client().send {  init( pos, id, stacks )  }

        }


        id = netID("remove")

        Receiver(id).register {

            val id = it.readInt()


            client().send {

                val list = MusicPlayer.list

                val musicPlayer = list.find { it.id == id } ?: return@send

                list.remove(musicPlayer)

            }

        }


        id = netID("set_playing")

        Receiver(id).register {

            val id = it.readInt();       val isPlaying = it.readBoolean()

            client().send {

                MusicPlayer.get(id).isPlaying = isPlaying

                updateOnScreen(id) { it.triggerButton?.updateMessage() }

            }

        }


        id = netID("set_repeat")

        Receiver(id).register {

            val id = it.readInt();       val onRepeat = it.readBoolean()

            client().send {

                MusicPlayer.get(id).onRepeat = onRepeat

                updateOnScreen(id) { it.repeatButton?.updateMessage() }

            }

        }


        id = netID("screen_listen_on")

        Receiver(id).register {

            client().send {

                val screen = client().currentScreen ?: return@send

                screen as MusicPlayerScreen;        screen.listen()

            }

        }

    }

    private fun init( pos: BlockPos, id: Int, stacks: MutableList<ItemStack> ) {

        val musicPlayer = MusicPlayer.get(id);          if ( musicPlayer.blockEntity != null ) return

        val blockEntity = get( world()!!, pos );          musicPlayer.pos = pos

        for ( i in 0 until stacks.size ) musicPlayer.items[i] = stacks[i]


        musicPlayer.update( blockEntity )

        musicPlayer.setMIDIReceiver();      musicPlayer.onSpawn()

    }

}

object MusicPlayerEntity : ModID {

    fun register() {

        val type = MusicPlayerEntity.type

        EntityRendererRegistry.register(type) { Renderer(it) }

    }

    class Renderer( context: EntityRendererFactory.Context ) : EntityRenderer<MusicPlayerEntity>(context) {

        override fun getTexture( entity: MusicPlayerEntity ): Identifier { return Identifier("") }

    }

}

/** Handles all the music playback interactions. */
class MusicPlayer( val id: Int ) {

    /*
        Local files are linked to host (last owner) of the floppy disk.
        Online files are linked by the listening button.
     */

    var pos: BlockPos? = null;          var isPlaying = false;              var onRepeat = false

    var items: DefaultedList<ItemStack> = DefaultedList.ofSize( INVENTORY_SIZE, ItemStack.EMPTY )

    var blockEntity: MusicPlayerBlockEntity? = null

    fun update( handler: MusicPlayerScreenHandler ) {

        pos = handler.pos;      isPlaying = handler.isPlaying

        onRepeat = handler.onRepeat


        for ( i in 0 until items.size ) items[i] = handler.slots[i].stack

    }

    fun update( blockEntity: MusicPlayerBlockEntity? ) {

        blockEntity ?: return;          blockEntity.entity = MusicPlayerEntity(blockEntity)

        isPlaying = blockEntity.isPlaying();            onRepeat = blockEntity.onRepeat

        items = blockEntity.items();            this.blockEntity = blockEntity

    }


    /** Reads requests on world load. */
    var worldLoaded = false

    /** Linked to the user listening state. It's linked in the screen listening button. */
    var isListening = false


    private var currentJob: Job? = null;        var radio: ItemStack? = null

    private var sequencer: Sequencer? = null;       var path = ""

    private var pauseTick: Long = 0;                var spawnParticles = false

    private var onQuery = false;                    private var isInputStream = false

    var sound: CustomSound? = null;                 init { list.add(this) }

    private val actions = mapOf( "play" to ::play, "pause" to ::pause )


    override fun toString(): String { return "Music Player: $pos" }


    private fun file(): ModFile { return ModFile(path) }

    private fun url(): URL { return URI(path).toURL() }

    fun entity(): Entity? {

        if ( radio != null ) return player()

        return blockEntity?.entity

    }

    fun floppy(): ItemStack { return items[0] }

    private fun hasSequencer(): Boolean { return sequencer != null }

    private fun hasSound(): Boolean { return sound != null }

    private fun statusPrint( status: String ) {

        if (onRepeat) return;           modPrint("$this: $status \"$path\"")

    }

    private fun isHost(): Boolean {

        val host = host( world()!!, floppy() ) ?: return false

        return player().uuid == host.uuid

    }

    private fun updateEntities( isPlaying: Boolean ) {

        blockEntity ?: return

        val renderer = client().worldRenderer as WorldRendererAccessor

        renderer.invokeUpdateEntitiesForSong( world()!!, pos, isPlaying )

    }

    fun removeRadio() {

        Radio.removeUse(radio);      radio = null

    }

    fun inputStream(): InputStream {

        val isURL = isValidUrl(path)

        return if (isURL) url().openStream() else file().inputStream()

    }

    private fun sendParticles() {

        if ( !isHost() ) return;    val netID = netID("particles")

        val sender = Sender(netID) { it.write(pos!!).write(id) }

        sender.toServer()

    }

    private fun setPlayingState( isPlaying: Boolean ) {

        updateEntities(isPlaying);          if ( !isHost() ) return

        this.isPlaying = isPlaying;         sendState( "set_playing", isPlaying )

    }


    fun play() { coroutine.launch {

        if ( !hasInput() ) return@launch


        val isPlaying = playMidi() || playSound()

        setPlayingState(isPlaying)


        if ( !isPlaying ) return@launch


        statusPrint("Playing...");        sendParticles()

    } }


    fun pause() { pause(onRepeat) }

    private fun pause(stop: Boolean) {

        fun pause() {

            spawnParticles = false;         if ( !isPlaying ) return


            setPlayingState(false)

            if ( hasSound() ) sound!!.fadeOut() else pauseMidi(stop)

            statusPrint("Stopping...")

        }


        val deferred = coroutine.async { pause() }

        runBlocking { deferred.await() }

    }


    fun stopSequencer() { sequencer!!.stop() }

    private fun isMidi(): Boolean { return path.endsWith(".mid") }

    private fun warnInvalidMidi(exception: Exception) {

        Message( "$path /@error.invalid_midi", exception ).console()

    }

    private fun playMidi(): Boolean {

        if ( !isMidi() || !hasSequencer() ) return false


        val sequencer = sequencer!!

        try {

            val next = MidiSystem.getSequence( inputStream() )

            sequencer.sequence = next;          sound = null

        } catch (exception: Exception) {

            warnInvalidMidi(exception);     return false

        }


        sequencer.start();      sequencer.tickPosition = pauseTick

        return true

    }

    private fun pauseMidi(stop: Boolean) {

        if ( !hasSequencer() ) return


        val sequencer = sequencer!!

        pauseTick = sequencer.tickPosition;     if (stop) pauseTick = 0


        for ( i in 0 until items.size ) {

            val stack = items[i];    val item = stack.item

            if ( item is InstrumentItem ) soundsCopy(stack).device.stop()

        }


        sequencer.stop()

    }


    private fun warnMissingFile() { Message("$path /@error.missing_file").console() }

    private fun sound(): CustomSound? {

        return try { CustomSound(this) } catch (exception: Exception) {

            Message( "error.file_access", exception ).console();    null

        }

    }

    private fun playSound(): Boolean {

        if (onQuery) { sendMessage("message.file_on_query");    return false }


        val sound = sound() ?: return false;        if ( !sound.isValid() ) return false


        this.sound = sound;     sound.play();       return true

    }


    fun tryReading() {

        val floppy = floppy();          if ( floppy.isEmpty ) return

        val path = nbt(floppy).getString("Path")

        if ( this.path == path ) return;            this.path = path


        currentJob?.cancel()

        currentJob = coroutine.launch { read();         onQuery = false }

    }

    /** Reads requests asynchronously. Download media using yt-dlp + ffmpeg. */
    private fun read() {

        onQuery = true;     isInputStream = false


        val isValid = isValidUrl(path);       if ( !isValid ) { sendFile(); return }


        // Application / octet-stream.

        modPrint("$this: Reading...")

        val connection = url().openConnection();        val type = connection.contentType ?: return


        isInputStream = type.contains("audio")

        if ( isInputStream ) statusPrint("URL Content Type: $type:")


        val ytdlp = YTDLP(path);        val info = ytdlp.info ?: return

        val output = ytdlp.output("ogg") ?: return


        // Use yt-dlp and ffmpeg to convert wav to ogg directly.

        val isWav = type.endsWith("wav")

        if ( isInputStream && !isWav ) return

        if ( isStored(info) || isLong(info) ) return


        try {


            sendMessage("message.downloading");         sendMessage( info.title )

            if ( !ytdlp.requestAudio() ) throw Exception("Failed to get audio from yt-dlp request!")


            val keepVideos = Config.client().keepVideos

            if ( keepVideos ) coroutine.launch { ytdlp.requestVideo() }


            path = output;    sendMessage("message.done")


            val nbt = nbt( floppy() );          nbt.putString( "Path", path );          send(nbt)

        } catch (exception: Exception) {

            val message = "error.exec_ytdlp@: @error.check_console"

            Message( message, exception ).send();           ModFile(output).delete()

        }

    }

    private fun sendFile() {

        val canSend = isMidi() || path.endsWith(".mp3") || path.endsWith(".ogg")

        if ( !canSend || !isHost() ) return


        val file = file();          if ( !file.exists() ) { warnMissingFile(); return }

        sendMessage( "message.reading/@ " + file.name )


        val bytes = file.readBytes();       val size = bytes.size;          val indices = bytes.indices

        for ( i in indices step MAX_BYTES ) {

            var nextSize = size - i

            if ( nextSize > MAX_BYTES ) nextSize = MAX_BYTES


            val id = netID("share_file")

            val sender = Sender(id) {

                it.write( pos!! ).write(nextSize)

                it.buf.writeBytes( bytes, i, nextSize )

                it.write( i == indices.first )

            }

            sender.toServer()

        }

    }

    private fun receiveFile( bytes: ByteArray, isFirst: Boolean ) {

        val file = file();      val exists = file.exists();       val path = Path(path)

        if ( exists && isFirst ) Files.delete(path)

        file.appendBytes(bytes)

    }

    /** Check if requested media is stored. */
    private fun isStored( info: MediaInfo ): Boolean {

        val directory = directories["streams"]!!

        val name = info.id + " - " + info.title


        directory.listFiles()!!.forEach {

            val name2 = it.name;        val ext = it.extension

            if ( path == it.path ) return true

            if ( !name2.contains(name) || ext != "ogg" ) return@forEach


            path = it.path;    sendMessage("$name2 /@message.file_found")


            return true

        }


        return false

    }

    fun hasInput(): Boolean {

        val isEmpty = floppy().isEmpty;         val isBlank = path.isBlank()

        val isValid = isValidUrl(path) || file().exists()

        return !isEmpty && !isBlank && isValid

    }

    fun setMIDIReceiver() {

        if ( hasSequencer() || !MIDI.hasSystemSequencer() ) return


        sequencer = MidiSystem.getSequencer();      val sequencer = sequencer!!

        if ( !sequencer.isOpen ) sequencer.open()


        val transmitter = sequencer.transmitter
        val transmitters = sequencer.transmitters

        for ( transmitter in transmitters ) transmitter.receiver = MusicPlayerReceiver(this)

        transmitter.receiver = MusicPlayerReceiver(this)

    }

    fun spawnParticles( wave: ParticleEffect ) {

        val canSpawn = Config.client().musicParticles

        if ( !spawnParticles || !canSpawn ) return


        val l1 = Random.nextInt(10)
        val l2 = Random.nextInt( 10, 15 )
        val l3 = Random.nextInt( 10, 15 )
        val l4 = Random.nextInt( 5, 15 )

        if (onRepeat) spawnParticles = false

        Timer(l4) { spawnParticles(wave) }


        val entity = blockEntity?.entity ?: return

        val distance = MIN_DISTANCE;        val playerPos = player().blockPos

        val isNear = playerPos.isWithinDistance( entity.pos, distance )

        if ( isMuted(entity) || !isNear ) return


        Timer(l1) { ActionParticles.spawnNote(entity) }

        Timer(l2) { ActionParticles.spawnWave( entity, wave, false ) }

        Timer(l3) { ActionParticles.spawnWave( entity, wave, true ) }

    }

    fun midiTick() {

        if ( hasSound() || !hasSequencer() ) return


        val sequencer = sequencer!!

        if ( floppy().isEmpty ) {

            if ( sequencer.isRunning ) pause(true); return

        }


        val sequence = sequencer.sequence ?: return

        val isDone = sequencer.tickPosition == sequence.tickLength

        if ( isPlaying && isDone ) pause(true)

    }

    fun sendState( id: String, state: Boolean ) {

        val netID = netID(id)

        val sender = Sender(netID) { it.write(pos!!).write(state) }

        sender.toServer()

    }


    private fun spawnRead() {

        if ( !isListening ) return;         tryReading()

    }

    fun onSpawn() {

        if ( worldLoaded ) return;      worldLoaded = true

        onListenAll();      spawnRead() // Because needs a bit of time to network the host.

    }

    private fun onListenAll() {

        val listenAll = Config.client().listenAll

        if ( !listenAll || isListening ) return

        isListening = true;     sendState( "set_user_state", true )

    }

    fun soundPos(): BlockPos? {

        entity() ?: return pos;             return entity()!!.blockPos

    }

    companion object : ModID {

        val list = mutableListOf<MusicPlayer>()

        private fun create(id: Int): MusicPlayer { return MusicPlayer(id) }

        // fun has(id: Int): Boolean { return list.find { it.id == id } != null }

        fun get(id: Int): MusicPlayer {

            val musicPlayer = list.find { it.id == id }

            if ( musicPlayer != null ) return musicPlayer

            return create(id)

        }

        fun onDisconnect() {

            list.forEach { it.pauseMidi(true);    it.worldLoaded = false }

        }

        fun isLong( info: MediaInfo ): Boolean {

            val maxDuration = Config.client().maxDuration

            if ( info.duration < maxDuration ) return false


            val warning = Translation.get("error.long_stream")
                .replace( "X", "${ maxDuration / 60f }" )

            sendMessage(warning);       return true

        }

        fun stacksSent( buf: PacketByteBuf ): MutableList<ItemStack> {

            val stacks = mutableListOf<ItemStack>()

            val size = INVENTORY_SIZE

            for ( i in 0 until size ) stacks.add( buf.readItemStack() )

            return stacks

        }

        fun networking() {

            var id = netID("particles")

            Receiver(id).register {

                val type = it.readInt();       val id = it.readInt()


                client().send {

                    val musicPlayer = get(id);          val type = waves[type]

                    musicPlayer.spawnParticles = true

                    musicPlayer.spawnParticles(type)

                }

            }


            id = netID("share_file")

            Receiver(id).register {

                val size = it.readInt()

                val array = ByteArray(size);        it.readBytes(array)

                val id = it.readInt();             val isFirst = it.readBoolean()

                val senderName = it.readString()


                client().send {

                    sendMessage( "$senderName /@message.player_shared" )

                    val musicPlayer = get(id);          sendMessage( musicPlayer.path )

                    musicPlayer.receiveFile( array, isFirst )

                }

            }


            id = netID("read")

            Receiver(id).register {

                val id = it.readInt();      val stacks = stacksSent(it)


                client().send {

                    val musicPlayer = get(id)

                    val blockEntity = musicPlayer.blockEntity!!


                    // The pause has to be done before the next stack is set.

                    val next = stacks[0];         val former = musicPlayer.floppy()

                    val pause = !NBT.equals( former, next ) && !former.isEmpty

                    if (pause) musicPlayer.pause(true)


                    stacks.forEachIndexed { i, stack ->

                        val next = Instrument.netStacks.find(stack)

                        blockEntity.setStack( i, next )

                    }

                    musicPlayer.items = blockEntity.items()


                    musicPlayer.tryReading()

                }

            }


            id = netID("action")

            Receiver(id).register {

                val actionName = it.readString();          val id = it.readInt()


                client().send {

                    val musicPlayer = get(id)

                    val actions = musicPlayer.actions

                    val action = actions[actionName]!!;       action()

                }

            }


        }

        object ActionParticles {

            fun spawnNote( entity: Entity ) {

                world() ?: return;          val simpleNote = ModParticles.SIMPLE_NOTE

                val pos = entity.pos.add( Vec3d( 0.0, 1.25, 0.0 ) )

                val particle = Particles.spawnOne( simpleNote, pos ) as SimpleNoteParticle

                particle.addVelocityY( - 0.06 )

            }

            fun spawnWave( entity: Entity, wave: ParticleEffect, flip: Boolean ) {

                world() ?: return

                var yaw = entity.yaw.toDouble();        yaw = rad(yaw)

                val pos = entity.pos.add( Vec3d( 0.0, 0.5, 0.0 ) )

                val particle = Particles.spawnOne( wave, pos ) as WaveParticle

                var velocity = Vec3d( cos(yaw), 0.0, sin(yaw) )

                velocity = velocity.multiply(0.05)

                if (flip) { velocity = velocity.multiply( - 1.0 );  particle.flip() }

                val y = Random.nextInt( -1, 5 ) * 0.01

                particle.setVelocity( velocity.x, y, velocity.z )

            }

        }

    }

}
