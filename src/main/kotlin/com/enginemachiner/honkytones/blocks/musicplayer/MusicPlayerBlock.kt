package com.enginemachiner.honkytones.blocks.musicplayer

import MediaInfo
import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.Timer
import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.Init.Companion.directories
import com.enginemachiner.honkytones.ModParticles.WAVE1
import com.enginemachiner.honkytones.ModParticles.WAVE2
import com.enginemachiner.honkytones.ModParticles.WAVE3
import com.enginemachiner.honkytones.ModParticles.WAVE4
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock.Companion.FACING
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock.Companion.PLAYING
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity.Companion.get
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.mixin.WorldRendererAccessor
import com.enginemachiner.honkytones.sound.CustomSound
import kotlinx.coroutines.*
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.block.Block
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.block.Material
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityTicker
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.Packet
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.particle.ParticleEffect
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.StateManager
import net.minecraft.state.property.BooleanProperty
import net.minecraft.state.property.DirectionProperty
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import net.minecraft.world.explosion.Explosion
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.util.*
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
    TODO: Consider saving data when breaking to be set in an unique stack.
     This might reduce the use of the musical storage.
*/

private val coroutine = CoroutineScope( Dispatchers.IO )

private fun setThreadName() { Thread.currentThread().name = "HonkyTones Resources thread"; }

private fun host( world: World, floppy: ItemStack ): PlayerEntity? {

    if ( !NBT.has(floppy) ) return null;    val nbt = NBT.get(floppy)

    if ( !nbt.containsUuid("Host") ) return null

    return world.getPlayerByUuid( nbt.getUuid("Host") )

}

class MusicPlayerBlock(settings: Settings) : BlockWithEntity(settings) {

    @Deprecated( "Deprecated in Java", ReplaceWith( "BlockRenderType.MODEL", "net.minecraft.block.BlockRenderType" ) )
    override fun getRenderType(state: BlockState): BlockRenderType { return BlockRenderType.MODEL }

    override fun createBlockEntity( pos: BlockPos, state: BlockState ): BlockEntity {
        return MusicPlayerBlockEntity( pos, state )
    }

    override fun appendProperties( builder: StateManager.Builder<Block, BlockState> ) {
        builder.add( *arrayOf( FACING, PLAYING ) )
    }

    override fun getPlacementState(context: ItemPlacementContext): BlockState {

        val direction = context.playerFacing.opposite

        return defaultState.with( FACING, direction ).with( PLAYING, false )

    }

    @Deprecated("Deprecated in Java")
    override fun onUse(
        state: BlockState, world: World, pos: BlockPos,
        player: PlayerEntity, hand: Hand, hit: BlockHitResult
    ): ActionResult {

        val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        player.openHandledScreen(blockEntity);      return ActionResult.CONSUME

    }

    override fun onBreak( world: World, pos: BlockPos, state: BlockState, player: PlayerEntity ) {

        onBreak( world, pos, player );      super.onBreak( world, pos, state, player )

    }

    private fun onBreak( world: World, pos: BlockPos, player: PlayerEntity ) {

        val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        val isPlaying = blockEntity.isPlaying()

        val entity = blockEntity.entity ?: return

        val drop = !isPlaying || player.isCreative


        if (drop) dropStacks( world, pos, blockEntity ) else explode(entity)

        entity.discard()

    }

    @Deprecated("Deprecated in Java")
    override fun neighborUpdate(
        state: BlockState, world: World, pos: BlockPos,
        block: Block, fromPos: BlockPos, notify: Boolean
    ) {

        // Check power from the block origin and beside the block.

        val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        var isTriggered = world.getReceivedStrongRedstonePower(pos) > 9
                || world.getReceivedRedstonePower(pos) > 9

        isTriggered = isTriggered && world.isReceivingRedstonePower(pos)

        if ( blockEntity.isTriggered == isTriggered ) return

        blockEntity.isTriggered = isTriggered;      if ( !isTriggered ) return

        if ( !blockEntity.isPlaying() ) blockEntity.play() else {

            blockEntity.pause();    if ( blockEntity.onRepeat ) blockEntity.play()

        }

    }

    override fun <T : BlockEntity> getTicker(
        world: World, state: BlockState, type: BlockEntityType<T>
    ): BlockEntityTicker<T> {

        val blockEntityType = MusicPlayerBlockEntity.type

        return checkType( type, blockEntityType ) {

            world, blockPos, _, _ ->

            MusicPlayerBlockEntity.tick( world, blockPos )

        }!!

    }

    private fun dropStacks(world: World, pos: BlockPos, blockEntity: MusicPlayerBlockEntity ) {

        for ( i in 0..16 ) dropStack( world, pos, blockEntity.getStack(i) )

    }

    private fun explode(entity: MusicPlayerEntity) {

        explode( entity, 0.75f, Explosion.DestructionType.DESTROY )

        explode( entity, 5f, Explosion.DestructionType.BREAK, true )

    }

    companion object : ModID {

        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING

        val PLAYING: BooleanProperty = BooleanProperty.of("playing")

        lateinit var registryBlock: MusicPlayerBlock

        /** Register the block, the block entity and the entity. */
        fun register() {

            val settings = FabricBlockSettings.of( Material.WOOD ).strength( 1.0f )

            val block = MusicPlayerBlock(settings);     registryBlock = block
            val registerBlock = Register.block( block, modItemSettings() )

            var id = MusicPlayerBlockEntity.classID()
            val builder1 = FabricBlockEntityTypeBuilder.create( ::MusicPlayerBlockEntity, registerBlock )

            /*
             * This might not be the right solution, but I did this
             * because of the tick in the block entity that checks new position and gets unsupported blocks.
             */

            val registry = Registry.BLOCK
            for ( i in 0 until registry.size() ) builder1.addBlock( registry[i] )

            MusicPlayerBlockEntity.type = Registry.register( Registry.BLOCK_ENTITY_TYPE, id, builder1.build() )

            id = MusicPlayerEntity.classID()
            val builder2 = FabricEntityTypeBuilder.create( SpawnGroup.MISC, ::MusicPlayerEntity ).build()

            MusicPlayerEntity.type = Registry.register( Registry.ENTITY_TYPE, id, builder2 )

            if ( !isClient() ) return

            EntityRendererRegistry.register( builder2 ) { MusicPlayerEntity.Companion.Renderer(it) }

        }

    }

}

class MusicPlayerBlockEntity( pos: BlockPos, state: BlockState ) : BlockEntity( type, pos, state ),
    ExtendedScreenHandlerFactory, CustomInventory {

    private val listeners = mutableMapOf<String, UUID>()

    var entity: MusicPlayerEntity? = null;      var id = this.hashCode()

    var isTriggered = false;      var onRepeat = false

    /** Linked to the user listening state. It's linked in the screen listening button. */
    /* @Environment(EnvType.CLIENT) */ var isListening = false

    private val items = DefaultedList.ofSize( INVENTORY_SIZE, ItemStack.EMPTY )

    override fun items(): DefaultedList<ItemStack> { return items }

    override fun toInitialChunkDataNbt(): NbtCompound { return createNbt() }

    override fun readNbt(nbt: NbtCompound) {

        super.readNbt(nbt);     Inventories.readNbt(nbt, items)

        id = nbt.getInt("ID");      readListeners(nbt)

        onRepeat = nbt.getBoolean("Repeat")


        val world = world ?: return;    if ( !world.isClient ) return

        val musicPlayer = MusicPlayer.get(id)

        setLastRendered( musicPlayer.blockEntity )

        musicPlayer.blockEntity = this

        musicPlayer.setMIDIReceiver();      onSpawn()

    }

    /** Set the last block entity rendered. */
    private fun setLastRendered( blockEntity: MusicPlayerBlockEntity? ) {

        if ( blockEntity == null ) return

        isListening = blockEntity.isListening;      entity = blockEntity.entity

    }

    override fun writeNbt(nbt: NbtCompound) {

        trySpawning()

        if ( !nbt.contains("ID") ) nbt.putInt( "ID", id )

        nbt.putBoolean( "Repeat", onRepeat );     writeListeners(nbt)

        Inventories.writeNbt( nbt, items );     super.writeNbt(nbt)

    }

    override fun markRemoved() {

        val world = world!!

        if ( !world.isClient ) { pause(); MusicPlayer.remove( world, id ) }

        super.markRemoved()

    }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener> { return BlockEntityUpdateS2CPacket.create(this) }

    // Thinking with hoppers.

    override fun canExtract( slot: Int, stack: ItemStack, direction: Direction ): Boolean {

        val item = stack.item;      val schedule = slot == 0 && item is FloppyDisk

        if (schedule) { pause(); scheduleRead() }

        return true

    }

    override fun canInsert( slot: Int, stack: ItemStack, direction: Direction? ): Boolean {

        val item = stack.item

        if ( slot > 0 && item !is Instrument ) return false

        if ( slot == 0 && item is FloppyDisk ) scheduleRead() else return false

        return true

    }

    override fun removeStack(slot: Int): ItemStack {

        read();     return super.removeStack(slot)

    }

    override fun setStack( slot: Int, stack: ItemStack ) {

        shouldListen = true;       super.setStack(slot, stack)

        read();     onEmptyFloppy(stack)

    }

    private fun onEmptyFloppy(stack: ItemStack) {

        val floppy = items.first();     val isNot = stack != floppy

        if ( !world!!.isClient || floppy.isEmpty || isNot  ) return


        val nbt = NBT.get(floppy);       val path = nbt.getString("Path")


        if ( path.isNotBlank() ) return;        warnUser("message.empty")

    }

    override fun createMenu( syncID: Int, playerInventory: PlayerInventory, player: PlayerEntity ): ScreenHandler {

        val inventory = this as Inventory;          val context = ScreenHandlerContext.create(world, pos)

        return MusicPlayerScreenHandler( syncID, playerInventory, inventory, context )

    }

    override fun getDisplayName(): Text {

        val title = Translation.block("music_player")

        return Text.of("§1$title")

    }

    override fun writeScreenOpeningData( player: ServerPlayerEntity, buf: PacketByteBuf ) { buf.writeBlockPos(pos) }

    companion object : ModID {

        var shouldListen = false

        const val INVENTORY_SIZE = 16 + 1;      lateinit var type: BlockEntityType<MusicPlayerBlockEntity>

        fun get( world: World, pos: BlockPos ): MusicPlayerBlockEntity? {

            val blockEntity = world.getBlockEntity(pos)

            if ( blockEntity !is MusicPlayerBlockEntity ) return null

            return blockEntity

        }

        fun tick( world: World, pos: BlockPos ) {

            val blockEntity = get( world, pos ) ?: return

            blockEntity.entityTick();   blockEntity.musicPlayerTick()

        }

        fun networking() {


            var id = netID("set_user_state")

            Receiver(id).register { server, sender, buf ->

                val world = server.overworld

                val pos = buf.readBlockPos();       val add = buf.readBoolean()


                serverSend(server) {

                    val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    val players = blockEntity.playersListening()


                    // About to have no listeners
                    // There could be listening button interaction.
                    if ( players.size == 1 ) blockEntity.pause()


                    val stored = blockEntity.listeners

                    val name = sender.name.string;          val uuid = sender.uuid


                    if (add) stored[name] = uuid else stored.remove(name)


                    blockEntity.setPlayerSettings(sender)

                }

            }


            id = netID("set_playing")

            Receiver(id).register { server, _, buf ->

                val world = server.overworld

                val pos = buf.readBlockPos();       val isPlaying = buf.readBoolean()


                serverSend(server) {

                    val blockEntity = get( world, pos ) ?: return@serverSend

                    blockEntity.setPlaying(isPlaying)

                }

            }


            id = netID("set_repeat")

            Receiver(id).register { server, sender, buf ->

                val world = server.overworld

                val pos = buf.readBlockPos();       val onRepeat = buf.readBoolean()


                serverSend(server) {

                    val blockEntity = get(world, pos) ?: return@serverSend

                    blockEntity.onRepeat = onRepeat


                    val id = netID("set_repeat")

                    val sender = Sender( id, sender ) { it.write(pos).write(onRepeat) }

                    sender.toClients(world)

                }

            }


            MusicPlayer.networking();       if ( !isClient() ) return


            id = netID("set_user_state")

            Receiver(id).register { buf ->

                val pos = buf.readBlockPos();       val onRepeat = buf.readBoolean()


                client().send {

                    val blockEntity = get( world()!!, pos ) ?: return@send

                    blockEntity.onRepeat = onRepeat

                }

            }


        }

    }

    private fun setPlayerSettings( player: PlayerEntity ) {

        val floppy = getStack(0);       if ( floppy.isEmpty ) return

        FloppyDisk.settings( floppy, player )

    }

    /** Set the map, reading the stored listeners. */
    private fun readListeners(nbt: NbtCompound) {

        if ( world != null && world!!.isClient ) return

        val listeners = nbt.get("Listeners") as NbtCompound

        listeners.keys.forEach { this.listeners[it] = listeners.getUuid(it) }

    }

    private fun writeListeners(nbt: NbtCompound) {

        val data = NbtCompound()

        for ( ( name, uuid ) in listeners ) { data.putUuid( name, uuid ) }

        nbt.put( "Listeners", data )

    }

    // @Environment(EnvType.CLIENT)
    fun updateState( id: String, b: Boolean ) {

        val netID = netID(id)

        val sender = Sender(netID) { it.write(pos).write(b) }

        sender.toServer()

    }

    // @Environment(EnvType.CLIENT)
    private fun spawnRead() {

        if ( !isListening ) return;     MusicPlayer.get(id).tryReading()

    }

    // @Environment(EnvType.CLIENT)
    private fun onSpawn() {

        val musicPlayer = MusicPlayer.get(id)

        if ( musicPlayer.worldLoaded ) return;      musicPlayer.worldLoaded = true

        onListenAll();      Timer(5) { spawnRead() } // Because needs a bit of time to network the host.

    }

    // @Environment(EnvType.CLIENT)
    private fun onListenAll() {

        val listenAll = Config.client().listenAll

        if ( !listenAll || isListening ) return

        isListening = true;     updateState( "set_user_state", true )

    }

    /** Tries to spawn the entity in the server. */
    private fun trySpawning() {

        val world = world!!

        if ( world.isClient || entity != null ) return

        MusicPlayerEntity(this)

    }

    fun isPlaying(): Boolean { return cachedState.get(PLAYING) }

    fun setPlaying( isPlaying: Boolean ) {

        val next = cachedState.with( PLAYING, isPlaying )

        world!!.setBlockState( pos, next )

    }

    private fun onMissingHost( next: Set<PlayerEntity> ) {

        val floppy = items.first()

        val world = world as ServerWorld

        val hasHost = host( world, floppy ) != null

        if ( hasHost || next.isEmpty() ) return

        NBT.get(floppy).putUuid( "Host", next.first().uuid )

        world.chunkManager.markForUpdate(pos)

    }

    /** Get the list of users synced / listening to the block entity, including the host. */
    fun playersListening(): Set<PlayerEntity> {

        val players = world!!.players

        val next = mutableSetOf<PlayerEntity>()

        for ( uuid in listeners.values ) {

            val player = players.find { it.uuid == uuid } ?: continue

            next.add(player)

        }

        onMissingHost(next);        return next

    }

    fun play() { sendAction("play") };      fun pause() { sendAction("pause") }

    private fun sendAction( actionName: String ) {

        val floppy = getStack(0);      if ( floppy.isEmpty ) return


        val netID = MusicPlayer.netID("action")

        val listeners = playersListening()


        val sender = Sender(netID) { it.write( actionName ).write(id) }

        sender.toClients(listeners)

    }

    private fun scheduleRead() {    Timer(5) { read() }     }

    fun read() {

        if ( world!!.isClient ) return

        fun write( buf: BufWrapper ) {

            buf.write(id)

            for ( i in 0 until size() ) buf.write( getStack(i) )

        }

        val id = MusicPlayer.netID("read");     val listeners = playersListening()

        val sender = Sender(id) { write(it) };      sender.toClients(listeners)

    }

    private fun entityTick() {

        val entity = entity ?: return;      if ( entity.blockPos == pos ) return

        entity.setPos(pos)

    }

    private fun mediaTick() {

        val isClient = world!!.isClient;    val floppy = items.first()

        if ( floppy.isEmpty ) return

        if (isClient) MusicPlayer.get(id).midiTick() else {

            // No listening button interaction. Can happen on disconnect.

            val isEmpty = playersListening().isEmpty()

            if ( isEmpty && isPlaying() ) setPlaying(false)

        }

    }

    private fun trackSlots() { items.forEachIndexed { i, stack ->

        if ( stack.isEmpty ) return@forEachIndexed

        val nbt = NBT.get(stack)

        nbt.putString( "BlockPos", pos!!.toShortString() )

        nbt.putInt( "Slot", i )

    } }

    private fun musicPlayerTick() { mediaTick(); trackSlots() }

}

/** This entity used as instruments holder and for particles. */
class MusicPlayerEntity( type: EntityType<MusicPlayerEntity>, world: World ) : Entity( type, world ) {

    constructor( blockEntity: MusicPlayerBlockEntity ) : this( Companion.type, blockEntity.world!! ) { spawn(blockEntity) }

    override fun initDataTracker() {}

    override fun readCustomDataFromNbt(nbt: NbtCompound) {}

    override fun writeCustomDataToNbt(nbt: NbtCompound) {}

    override fun onSpawnPacket(packet: EntitySpawnS2CPacket) {

        super.onSpawnPacket(packet)

        val blockEntity = get( world, blockPos ) ?: return

        spawn(blockEntity)

    }

    override fun createSpawnPacket(): Packet<*> { return EntitySpawnS2CPacket(this) }

    override fun getName(): Text { return Text.of( Translation.block("music_player") ) }

        fun setPos(blockPos: BlockPos) {

        val newPos = Vec3d.of(blockPos).add( 0.5, 0.0, 0.5 )

        setPosition(newPos)

    }

    private fun spawn( blockEntity: MusicPlayerBlockEntity ) {

        blockEntity.entity = this;      setPos( blockEntity.pos )


        val facing = world.getBlockState(blockPos).get(FACING)

        this.yaw = facing.asRotation()

        world!!.spawnEntity(this)

    }

    companion object : ModID {

        lateinit var type: EntityType<MusicPlayerEntity>

        class Renderer( context: EntityRendererFactory.Context ) : EntityRenderer<MusicPlayerEntity>(context) {
            override fun getTexture( entity: MusicPlayerEntity ): Identifier { return Identifier("") }
        }

    }

}

/** Handles all the music playback. Used for the clientside only. */
class MusicPlayer( val id: Int ) {

    /*
        Local files are linked to host (last owner) of the floppy disk.
        Online files are linked by the listening button.
     */

    /** Reads requests on world load. */
    var worldLoaded = false;        var path = ""

    var blockEntity: MusicPlayerBlockEntity? = null

    private var sequencer: Sequencer? = null

    private var pauseTick: Long = 0;                var spawnParticles = false

    private var onQuery = false;                    private var isInputStream = false

    var sound: CustomSound? = null;               init { list.add(this) }

    private val actions = mapOf( "play" to ::play, "pause" to ::pause )

    override fun toString(): String {

        if ( blockEntity == null ) return super.toString()

        return "Music Player: ${ pos() }"

    }

    private fun floppy(): ItemStack { return item(0) }
    private fun file(): ModFile { return ModFile(path) }
    private fun url(): URL { return URL(path) }

    private fun isPlaying(): Boolean { return blockEntity!!.isPlaying() }

    fun item(slot: Int): ItemStack {

        val items = blockEntity!!.items();      return items[slot]

    }

    fun stopSequencer() { sequencer!!.stop() };     fun pos(): BlockPos { return blockEntity!!.pos }

    private fun isHost(): Boolean {

        val host = host( world()!!, floppy() ) ?: return false

        return player().uuid == host.uuid

    }

    private fun updateEntities( isPlaying: Boolean ) {

        val world = world()!!;      get( world, pos() ) ?: return

        val renderer = client().worldRenderer as WorldRendererAccessor

        renderer.invokeUpdateEntitiesForSong( world, pos(), isPlaying )

    }

    private fun setPlaying( isPlaying: Boolean ) {

        updateEntities(isPlaying)

        if ( !isHost() ) return;    blockEntity!!.updateState( "set_playing", isPlaying )

    }

    fun inputStream(): InputStream {

        val isURL = isValidUrl(path)

        return if (isURL) url().openStream() else file().inputStream()

    }

    private fun isMidi(): Boolean { return path.endsWith(".mid") }

    private fun playMidi(): Boolean {

        if ( !isMidi() || !hasSequencer() ) return false

        val sequencer = sequencer!!


        try {

            val next = MidiSystem.getSequence( inputStream() )

            sequencer.sequence = next;          sound = null

        } catch ( e: Exception ) {

            warnInvalidMidi();    e.printStackTrace()

            return false

        }


        sequencer.start();      sequencer.tickPosition = pauseTick

        return true

    }

    private fun warnInvalidMidi() { warnConsole("$path #error.invalid_midi") }

    private fun warnMissingFile() { warnConsole("$path #error.missing_file") }

    private fun sound(): CustomSound? {

        return try { CustomSound(this) } catch (e: Exception ) {

            warnConsole("error.file_access");   e.printStackTrace()

            null

        }

    }

    private fun playSound(): Boolean {

        if (onQuery) { warnUser("message.file_on_query");       return false }


        val sound = sound() ?: return false

        if ( !sound.isValid() ) return false


        this.sound = sound;     sound.play();       return true

    }

    fun play() { coroutine.launch {

        setThreadName();    if ( !hasInput() ) return@launch

        val isPlaying = playMidi() || playSound();       setPlaying(isPlaying)

        if ( !isPlaying ) return@launch;        statusMessage("Playing...")

        startParticles()

    } }

    private fun startParticles() {

        if ( !isHost() ) return;    val netID = netID("particles")

        val sender = Sender(netID) { it.write( pos() ).write(id) }

        sender.toServer()

    }

    fun pause() { pause( blockEntity!!.onRepeat ) }

    private fun pause(stop: Boolean) {

        fun pause() {

            setThreadName();        spawnParticles = false

            if ( !isPlaying() ) return;      setPlaying(false)

            if ( hasSound() ) sound!!.fadeOut() else pauseMidi(stop)

            statusMessage("Stopping...")

        }

        val deferred = coroutine.async { pause() }

        runBlocking { deferred.await() }

    }

    private fun pauseMidi(stop: Boolean) {

        if ( !hasSequencer() ) return;      val sequencer = sequencer!!

        pauseTick = sequencer.tickPosition;     if (stop) pauseTick = 0


        for ( i in 0 until blockEntity!!.size() ) {

            val stack = item(i);    val item = stack.item

            if ( item is Instrument ) item.stopDeviceSounds(stack)

        }


        sequencer.stop()

    }

    /** Reads requests. Download media using yt-dl + ffmpeg. */
    private fun read() {


        setThreadName();        onQuery = true;     isInputStream = false

        modPrint("$this: Reading...")


        val validURL = isValidUrl(path);       if ( !validURL ) { sendFile(); return }


        // Application/octet-stream

        val connection = url().openConnection();        val type = connection.contentType ?: return


        isInputStream = type.contains("audio")

        if ( isInputStream ) statusMessage("URL Content Type: $type:")


        val ytdlp = YTDLP(path);   val info = ytdlp.info ?: return


        val output = ytdlp.output("ogg") ?: return

        val isWav = type.endsWith("wav")


        // Use yt-dlp and ffmpeg to convert wav to ogg directly.

        if ( isInputStream && !isWav || isStored(info) ) return


        if ( isLong(info) ) return


        try {

            warnUser("message.downloading"); warnUser( info.title )

            if ( !ytdlp.requestAudio() ) throw Exception("Failed to get audio from yt-dlp request!")


            val keepVideos = Config.client().keepVideos

            if ( keepVideos ) coroutine.launch { setThreadName(); ytdlp.requestVideo() }


            path = output;    warnUser("message.done")

        } catch ( e: Exception ) {

            warnUser("error.exec_ytdlp#: #error.check_console")

            ModFile(output).delete();      e.printStackTrace()

        }

    }

    private fun receiveFile( bytes: ByteArray, isFirst: Boolean ) {

        if ( file().exists() && isFirst ) Files.delete( file().toPath() )

        file().appendBytes(bytes)

    }

    private fun sendFile() {

        val isAllowed = isMidi() || path.endsWith(".mp3") || path.endsWith(".ogg")

        if ( !isAllowed || isHost() ) return

        val file = file();   if ( !file.exists() ) { warnMissingFile(); return }

        val bytes = file.readBytes();       val size = bytes.size

        val indices = bytes.indices

        for ( i in indices step maxData ) {

            var nextSize = size - i;        if ( nextSize > maxData ) nextSize = maxData

            val id = netID("share_file")

            val sender = Sender(id) {

                it.write( pos() ).write(nextSize)

                it.buf.writeBytes( bytes, i, nextSize )

                it.write( i == indices.first )

            }

            sender.toServer()

        }

    }

    /** Check if requested media is stored. */
    private fun isStored( info: MediaInfo ): Boolean {

        val directory = directories["streams"]!!

        val name = info.id + " - " + info.title


        directory.listFiles()!!.forEach {

            val file = it;      val name2 = file.name
            
            val ext = file.extension;       if ( this.path == it.path ) return true


            if ( !name2.contains(name) || ext != "ogg" ) return@forEach

            this.path = it.path;    warnUser("$name2 #message.file_found")


            return true

        }


        return false

    }

    fun hasInput(): Boolean {

        val isEmpty = floppy().isEmpty

        val isValid = isValidUrl(path) || file().exists()

        return path.isNotBlank() && isValid && !isEmpty

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

    fun spawnParticles(wave: ParticleEffect) {

        val isAllowed = Config.client().musicParticles

        if ( !spawnParticles || !isAllowed ) return


        val l1 = Random.nextInt(10)
        val l2 = Random.nextInt( 10, 15 )
        val l3 = Random.nextInt( 10, 15 )
        val l4 = Random.nextInt( 5, 15 )

        if ( blockEntity!!.onRepeat ) spawnParticles = false

        Timer(l4) { spawnParticles(wave) }

        val blockEntity = blockEntity ?: return
        val entity = blockEntity.entity ?: return
        val distance = Particles.MIN_DISTANCE
        val playerPos = player().blockPos

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

            if ( sequencer.isRunning ) midiPause(); return

        }


        val sequence = sequencer.sequence ?: return

        val finished = sequencer.tickPosition == sequence.tickLength

        if ( isPlaying() && finished ) midiPause()


    }

    private fun midiPause() {

        sequencer!!.tickPosition = 0

        val id = netID("pause")

        val sender = Sender(id) { it.write( pos() ) }

        sender.toServer()

    }

    private fun hasSequencer(): Boolean { return sequencer != null }

    private fun hasSound(): Boolean { return sound != null }

    private fun statusMessage( statusType: String ) {

        if ( blockEntity!!.onRepeat ) return

        modPrint("$this: $statusType \"$path\"")

    }

    // @Environment(EnvType.CLIENT)
    fun tryReading() {

        val floppy = floppy();           val isEmpty = floppy.isEmpty

        val hasData = NBT.has(floppy);      var path = ""


        if ( !isEmpty && hasData ) {

            path = NBT.get(floppy).getString("Path")

        }


        val isSame = this.path == path

        if ( isEmpty || !isSame ) pause(true)

        this.path = path;       if ( isEmpty || isSame ) return


        coroutine.launch { read();  onQuery = false }

    }

    companion object : ModID {

        val list = mutableListOf<MusicPlayer>()

        private fun create(id: Int): MusicPlayer { return MusicPlayer(id) }

        fun onDisconnect() { list.forEach {

            it.pauseMidi(true);    it.worldLoaded = false

        } }

        // fun has(id: Int): Boolean { return list.find { it.id == id } != null }

        fun get(id: Int): MusicPlayer {

            val musicPlayer = list.find { it.id == id }

            if ( musicPlayer != null ) return musicPlayer

            return create(id)

        }

        fun remove( world: World, id: Int ) {

            val netID = netID("remove")

            val sender = Sender(netID) { it.write(id) }

            sender.toClients(world)

        }

        fun isLong( info: MediaInfo ): Boolean {

            val max = Config.client().maxLength

            if ( info.duration < max ) return false


            val warning = Translation.get("error.long_stream")
                .replace( "X", "${ max / 60f }" )

            warnUser(warning); return true

        }

        fun networking() {


            var id = netID("pause")

            Receiver(id).register { server, _, buf ->

                val world = server.overworld;   val pos = buf.readBlockPos()


                serverSend(server) {

                    val blockEntity = get( world, pos ) ?: return@serverSend

                    blockEntity.pause()

                }

            }


            id = netID("share_file")

            val fileReceiver = Receiver(id)

            fileReceiver.register { server, sender, buf ->

                val world = server.overworld;           val netID = netID("share_file")

                val pos = buf.readBlockPos();           val size = buf.readInt()
                val bytes = buf.readBytes(size);        val isFirst = buf.readBoolean()

                serverSend(server) {

                    val blockEntity = get( world, pos ) ?: return@serverSend

                    val id = blockEntity.id;        val name = sender.name.string

                    val listeners = blockEntity.playersListening()

                    val sender = Sender( netID, sender ) {

                        it.write(size).write(bytes).write(id).write( isFirst ).write(name)

                    }

                    sender.toClients(listeners)

                }

            }


            id = netID("particles")

            val particlesReceiver = Receiver(id)

            particlesReceiver.register { server, _, buf ->

                val world = server.overworld

                val pos = buf.readBlockPos();   val id = buf.readInt()


                serverSend(server) {

                    val blockEntity = get( world, pos ) ?: return@serverSend

                    val isAllowed = Config.SERVER.data().musicParticles


                    if ( !blockEntity.isPlaying() || !isAllowed ) return@serverSend


                    val netID = netID("particles")

                    val waveType = ActionParticles.randomWave()

                    val listeners = blockEntity.playersListening()

                    val sender = Sender(netID) { it.write(waveType).write(id) }

                    sender.toClients(listeners)

                }

            }


            if ( !isClient() ) return


            particlesReceiver.register { buf ->

                val type = buf.readInt();       val id = buf.readInt()


                client().send {

                    val type = ActionParticles.waves[type]

                    val musicPlayer = get(id)

                    musicPlayer.spawnParticles = true

                    musicPlayer.spawnParticles(type)

                }

            }


            fileReceiver.register { buf ->

                val size = buf.readInt()

                val array = ByteArray(size);        buf.readBytes(array)

                val id = buf.readInt();             val isFirst = buf.readBoolean()

                val senderName = buf.readString()


                client().send {

                    warnUser( "$senderName #message.player_shared" )

                    val musicPlayer = get(id);          warnUser( musicPlayer.path )

                    musicPlayer.receiveFile( array, isFirst )

                }

            }


            id = netID("read")

            Receiver(id).register { buf ->

                val id = buf.readInt()


                val stacks = mutableListOf<ItemStack>()

                val size = MusicPlayerBlockEntity.INVENTORY_SIZE

                for ( i in 0 until size ) stacks.add( buf.readItemStack() )


                client().send {

                    val musicPlayer = get(id)
                    val blockEntity = musicPlayer.blockEntity!!


                    stacks.forEachIndexed { i, stack ->

                        val netStack = Instrument.find(stack)

                        blockEntity.setStack( i, netStack )

                    }


                    musicPlayer.tryReading()

                }

            }


            id = netID("remove")

            Receiver(id).register { buf ->

                val id = buf.readInt()


                client().send {

                    val musicPlayer = list.find { it.id == id } ?: return@send

                    list.remove(musicPlayer)

                }

            }


            id = netID("action")

            Receiver(id).register { buf ->

                val actionName = buf.readString();          val id = buf.readInt()


                client().send {

                    val musicPlayer = get(id)

                    val actions = musicPlayer.actions

                    val action = actions[actionName]!!;       action()

                }

            }


        }

        object ActionParticles {

            val waves = listOf( WAVE1, WAVE2, WAVE3, WAVE4 )

            fun randomWave(): Int { return waves.indices.random() }

            fun spawnNote( entity: Entity ) {

                world() ?: return

                val pos = entity.pos.add( Vec3d( 0.0, 1.25, 0.0 ) )

                val particle = Particles.spawnOne( ModParticles.SIMPLE_NOTE, pos ) as SimpleNoteParticle

                particle.addVelocityY( - 0.06 )

            }

            fun spawnWave( entity: Entity, wave: ParticleEffect, flip: Boolean ) {

                world() ?: return

                val yaw = rad( entity.yaw.toDouble() )

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
