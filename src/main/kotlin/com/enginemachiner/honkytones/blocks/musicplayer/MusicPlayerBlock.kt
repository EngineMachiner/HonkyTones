package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.BlockWithEntity
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.Init.Companion.directories
import com.enginemachiner.honkytones.Init.Companion.registerBlock
import com.enginemachiner.honkytones.Particles.Companion.WAVE1
import com.enginemachiner.honkytones.Particles.Companion.WAVE2
import com.enginemachiner.honkytones.Particles.Companion.WAVE3
import com.enginemachiner.honkytones.Particles.Companion.WAVE4
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock.Companion.FACING
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock.Companion.PLAYING
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity.Companion.INVENTORY_SIZE
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.sound.ExternalSound
import com.sapher.youtubedl.YoutubeDLException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.block.entity.BlockEntityClientSerializable
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendereregistry.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityTicker
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
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
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.particle.ParticleEffect
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
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
import java.net.URL
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val coroutine = CoroutineScope( Dispatchers.IO )

private fun lookupPlayer( world: World, floppy: ItemStack ): PlayerEntity? {

    val players = world.players;   if ( !NBT.has(floppy) ) return null

    val nbt = NBT.get(floppy);     return players.find { it.id == nbt.getInt("PlayerID") }

}

// TODO: Make parrots dance.
// TODO: Save client listening states (nbt?) so users are not forced to change it themselves.
// TODO: Make a remote class to change the music player settings. (rate, volume, listen)
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

        val action = ActionResult.CONSUME

        val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        player.openHandledScreen(musicPlayer);      return action

    }

    override fun onBreak( world: World, pos: BlockPos, state: BlockState, player: PlayerEntity ) {

        val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        val isPlaying = blockEntity.isPlaying();      val entity = blockEntity.entity!!

        val drop = !isPlaying || player.isCreative

        if (drop) drop( world, pos, blockEntity ) else explode(entity)

        entity.remove( Entity.RemovalReason.DISCARDED );    super.onBreak( world, pos, state, player )

    }

    @Deprecated("Deprecated in Java")
    override fun neighborUpdate(
        state: BlockState, world: World, pos: BlockPos,
        block: Block, fromPos: BlockPos, notify: Boolean
    ) {

        val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        var isPowered = world.getReceivedStrongRedstonePower(pos) > 9
                || world.getReceivedRedstonePower(pos) > 9

        val from = fromPos.add( 1, 0, 0 )

        isPowered = isPowered && world.isReceivingRedstonePower(from)

        if ( musicPlayer.isPowered == isPowered ) return

        musicPlayer.isPowered = isPowered;      if ( !isPowered ) return

        if ( !musicPlayer.isPlaying() ) musicPlayer.play() else {

            musicPlayer.pause();    if ( musicPlayer.repeatOnPlay ) musicPlayer.play()

        }

    }

    override fun <T : BlockEntity> getTicker(
        world: World, state: BlockState, type: BlockEntityType<T>
    ): BlockEntityTicker<T> {

        val id = MusicPlayerBlockEntity.classID()

        val blockEntity = Registry.BLOCK_ENTITY_TYPE.get(id)

        return checkType( type, blockEntity ) {

            world: World, blockPos: BlockPos, _: BlockState, _: Any ->

            MusicPlayerBlockEntity.tick( world, blockPos )

        }!!

    }

    private fun drop( world: World, pos: BlockPos, musicPlayer: MusicPlayerBlockEntity ) {
        for ( i in 0..16 ) dropStack( world, pos, musicPlayer.getStack(i) )
    }

    private fun explode(entity: MusicPlayerEntity) {

        val world = entity.world

        world.createExplosion( entity, entity.x, entity.y, entity.z, 0.75f, Explosion.DestructionType.DESTROY )

        world.createExplosion( entity, entity.x, entity.y, entity.z, 5f, true, Explosion.DestructionType.BREAK )

    }

    companion object {

        val FACING: DirectionProperty = Properties.HORIZONTAL_FACING
        val PLAYING: BooleanProperty = BooleanProperty.of("playing")

        /** Register the block, the block entity and the entity. */
        fun register() {

            val settings = FabricBlockSettings.of( Material.WOOD ).strength(1.0f)

            val block = MusicPlayerBlock(settings)
            val registerBlock = registerBlock( block, defaultSettings() )

            var id = MusicPlayerBlockEntity.classID()
            val builder1 = FabricBlockEntityTypeBuilder.create( ::MusicPlayerBlockEntity, registerBlock )

            /* TODO: This could mess the ticker!
            val registry = Registry.BLOCK
            for ( i in 0 until registry.size() ) builder1.addBlock( registry[i] )
            // Because of the tick that checks new positions and that could be weird on any block states.
             */

            MusicPlayerBlockEntity.type = Registry.register( Registry.BLOCK_ENTITY_TYPE, id, builder1.build() )

            id = MusicPlayerEntity.classID()
            val builder2 = FabricEntityTypeBuilder.create( SpawnGroup.MISC, ::MusicPlayerEntity ).build()

            MusicPlayerEntity.type = Registry.register( Registry.ENTITY_TYPE, id, builder2 )

            if ( !isClient() ) return

            EntityRendererRegistry.INSTANCE.register( builder2 ) { MusicPlayerEntity.Companion.Renderer(it) }

        }

    }

}

class MusicPlayerBlockEntity( pos: BlockPos, state: BlockState ) : BlockEntity( type, pos, state ),
    ExtendedScreenHandlerFactory, CustomInventory, BlockEntityClientSerializable {

    val usersListening = mutableSetOf<PlayerEntity>();  var entity: MusicPlayerEntity? = null

    /** Avoids more than one redstone triggers at the same time. */
    var isPowered = false;    var id = this.hashCode();     var repeatOnPlay = false

    /** Linked to the user sync / listening state. It's used for the screen sync button. */
    @Environment(EnvType.CLIENT) var isListening = false

    private val items = DefaultedList.ofSize( INVENTORY_SIZE, ItemStack.EMPTY )

    override fun items(): DefaultedList<ItemStack> { return items }

    override fun readNbt(nbt: NbtCompound) {

        super.readNbt(nbt);     Inventories.readNbt( nbt, items )

        id = nbt.getInt("ID")

        repeatOnPlay = nbt.getBoolean("repeatOnPlay")


        val world = world ?: return;   if ( !world.isClient ) return

        val musicPlayer = MusicPlayer.get(id);      val blockEntity = musicPlayer.blockEntity

        if ( blockEntity != null ) { isListening = blockEntity.isListening;      entity = blockEntity.entity }

        musicPlayer.blockEntity = this;     musicPlayer.setMIDIReceiver()

    }

    override fun writeNbt(nbt: NbtCompound): NbtCompound {

        super.writeNbt(nbt)

        if ( !nbt.contains("ID") ) nbt.putInt( "ID", id )

        nbt.putBoolean( "repeatOnPlay", repeatOnPlay )

        Inventories.writeNbt( nbt, items );    init()

        return nbt

    }

    override fun fromClientTag(tag: NbtCompound) { readNbt(tag) }
    override fun toClientTag(tag: NbtCompound): NbtCompound { return writeNbt(tag) }

    override fun markDirty() { super<BlockEntity>.markDirty() }

    override fun markRemoved() {

        val world = world!!

        if ( !world.isClient ) { pause(); MusicPlayer.remove( world, id ) }

        super.markRemoved()

    }

    override fun toUpdatePacket(): BlockEntityUpdateS2CPacket? {

        init();    return super.toUpdatePacket()

    }

    // Thinking with hoppers.

    override fun canExtract( slot: Int, stack: ItemStack, direction: Direction ): Boolean {

        val item = stack.item;        if ( slot == 16 && item is FloppyDisk ) { pause();  scheduleRead() }

        return true

    }

    override fun canInsert( slot: Int, stack: ItemStack, direction: Direction? ): Boolean {

        val item = stack.item;        if ( slot < 16 && item !is Instrument ) return false

        if ( slot == 16 && item is FloppyDisk ) scheduleRead() else return false

        return true

    }

    override fun createMenu( syncID: Int, inventory: PlayerInventory, player: PlayerEntity ): ScreenHandler {

        return MusicPlayerScreenHandler( syncID, inventory, this as Inventory )

    }

    override fun getDisplayName(): Text {

        val title = Translation.block("music_player");      return Text.of("§1$title")

    }

    override fun writeScreenOpeningData( player: ServerPlayerEntity, buf: PacketByteBuf ) { buf.writeBlockPos(pos) }

    companion object : ModID {

        const val INVENTORY_SIZE = 16 + 1;      lateinit var type: BlockEntityType<MusicPlayerBlockEntity>

        fun tick( world: World, pos: BlockPos ) {

            val blockEntity = world.getBlockEntity(pos)

            if ( blockEntity !is MusicPlayerBlockEntity ) return

            blockEntity.entityTick();      blockEntity.musicPlayerTick()

        }

        fun networking() {

            var id = netID("set_user_listening")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, player: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos();   val add = buf.readBoolean()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    val list = musicPlayer.usersListening;      if (add) list.add(player) else list.remove(player)

                } )

            }

            id = netID("set_playing_state")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, _: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos();   val isPlaying = buf.readBoolean()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) ?: return@ServerTask

                    musicPlayer as MusicPlayerBlockEntity

                    musicPlayer.setPlaying(isPlaying)

                } )

            }

            id = netID("set_repeat")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, _: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos();   val onRepeat = buf.readBoolean()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    musicPlayer.repeatOnPlay = onRepeat;    musicPlayer.markDirty()

                } )

            }

            MusicPlayer.networking()

        }

    }

    @Environment(EnvType.CLIENT)
    fun setUserListeningState(isListening: Boolean) {

        val id = netID("set_user_listening")

        val buf = PacketByteBufs.create().writeBlockPos(pos);   buf.writeBoolean(isListening)

        ClientPlayNetworking.send( id, buf )

    }

    @Environment(EnvType.CLIENT)
    fun setRepeatMode(repeatPlay: Boolean) {

        val id = netID("set_repeat")

        val buf = PacketByteBufs.create().writeBlockPos(pos);   buf.writeBoolean(repeatPlay)

        ClientPlayNetworking.send( id, buf )

    }

    @Environment(EnvType.CLIENT)
    fun clientInit() {

        val listenAll = clientConfig["listen_all"] as Boolean

        if ( !listenAll ) return;     isListening = true;    setUserListeningState(true)

    }

    private fun init() {

        val world = world!!;    if ( world.isClient || entity != null ) return

        val nextState = world.getBlockState(pos).with( PLAYING, false )

        world.setBlockState( pos, nextState )

        spawnEntity( MusicPlayerEntity(this) )

    }

    fun isPlaying(): Boolean { return cachedState.get(PLAYING) }

    fun setPlaying(isPlaying: Boolean) {

        val next = cachedState.with( PLAYING, isPlaying )

        world!!.setBlockState( pos, next )

    }

    /** Get the list of users synced / listening to the block entity, including the owner of the floppy. */
    fun usersListening(floppy: ItemStack): Set<PlayerEntity> {

        val users = usersListening.toMutableSet()

        for ( user in users ) if ( user.isRemoved ) usersListening.remove(user)

        val owner = lookupPlayer( world!!, floppy ) ?: return users

        if ( !users.contains(owner) ) users.add(owner)

        return users

    }

    fun spawnEntity( entity: MusicPlayerEntity ) {

        world!!.spawnEntity(entity);    entity.init();   this.entity = entity

    }

    fun play() { sendAction {

        val buf = PacketByteBufs.create().writeString("play")

        buf.writeInt( this.id );    buf

    } }

    fun pause() { sendAction {

        val buf = PacketByteBufs.create().writeString("pause")

        buf.writeInt( this.id );    buf

    } }

    private fun sendAction( buf: () -> PacketByteBuf ) {

        val floppy = getStack(16);     if ( floppy.isEmpty ) return

        val id = MusicPlayer.netID("action")

        usersListening(floppy).forEach { ServerPlayNetworking.send( it as ServerPlayerEntity, id, buf() ) }

    }

    private fun scheduleRead() { Timer(5) { read() } }

    private fun read() { read( getStack(16) ) }

    fun read(floppy: ItemStack) {

        val id = MusicPlayer.netID("read");     if ( !NBT.has(floppy) ) return

        val buf = PacketByteBufs.create();          buf.writeInt( this.id )

        for ( i in 0 .. 16 ) buf.writeItemStack( getStack(i) )

        usersListening(floppy).forEach { ServerPlayNetworking.send( it as ServerPlayerEntity, id, buf ) }

    }

    /** Updates entity position and networks it. */
    private fun entityTick() {

        if ( world!!.isClient ) return;           val entity = entity ?: return

        if ( entity.blockPos == pos ) return;     entity.setPos(pos)

        val floppy = getStack(16);          val id = MusicPlayer.netID("position")

        val buf = PacketByteBufs.create().writeBlockPos(pos); buf.writeInt( this.id )

        for ( player in usersListening(floppy) ) ServerPlayNetworking.send( player as ServerPlayerEntity, id, buf )

    }

    private fun musicPlayerTick() {

        val floppy = getStack(16)

        if ( !world!!.isClient ) {

            val isEmpty = usersListening(floppy).isEmpty() && isPlaying()

            if (isEmpty) setPlaying(false);       return

        }

        MusicPlayer.get(id).tick()

    }

}

/** This entity used as instruments holder and for particles. */
class MusicPlayerEntity( type: EntityType<MusicPlayerEntity>, world: World ) : Entity( type, world ) {

    constructor( blockEntity: MusicPlayerBlockEntity ) : this( Companion.type, blockEntity.world!! ) {

        setPos( blockEntity.pos )

    }

    override fun initDataTracker() {}

    override fun readCustomDataFromNbt(nbt: NbtCompound) {}

    override fun writeCustomDataToNbt(nbt: NbtCompound) {}

    override fun onSpawnPacket(packet: EntitySpawnS2CPacket) {

        super.onSpawnPacket(packet)

        val musicPlayer = world.getBlockEntity(blockPos)

        if ( musicPlayer !is MusicPlayerBlockEntity ) return

        val entity = musicPlayer.entity;    if ( entity != null && !entity.isRemoved ) return

        musicPlayer.spawnEntity(this);    musicPlayer.clientInit()

    }

    override fun createSpawnPacket(): Packet<*> { return EntitySpawnS2CPacket(this) }

    override fun getName(): Text { return Text.of( Translation.block("music_player") ) }

    fun init() {

        val facing = world.getBlockState(blockPos).get(FACING)

        this.yaw = facing.asRotation()

    }

    fun setPos(blockPos: BlockPos) {

        val newPos = Vec3d.of(blockPos).add( 0.5, 0.0, 0.5 )

        setPosition(newPos)

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

    var blockEntity: MusicPlayerBlockEntity? = null;    var path = ""

    private var sequencer: Sequencer? = null;       private var isPlaying = false

    private var pauseTick: Long = 0;                var spawnParticles = false

    private var onQuery = false;                    private var isDirectAudio = false

    var sound: ExternalSound? = null;               init { list.add(this) }

    val items: DefaultedList<ItemStack> = DefaultedList.ofSize( INVENTORY_SIZE, ItemStack.EMPTY )

    private val actions = mapOf( "play" to ::play, "pause" to ::pause )

    override fun toString(): String { return "Music Player: ${ pos() }" }

    fun stopSequencer() { sequencer!!.stop() };     fun pos(): BlockPos { return blockEntity!!.pos }

    fun isFormerPlayer(): Boolean {

        val floppy = items[16];     val player = lookupPlayer( world()!!, floppy )

        return player() == player

    }

    private fun setPlaying(isPlaying: Boolean) {

        val floppy = items[16];     this.isPlaying = isPlaying

        if ( !isFormerPlayer() && !floppy.isEmpty ) return

        val id = MusicPlayerBlockEntity.netID("set_playing_state")

        val buf = PacketByteBufs.create().writeBlockPos( pos() );   buf.writeBoolean(isPlaying)

        ClientPlayNetworking.send( id, buf )

    }

    private fun isMidi(): Boolean { return path.endsWith(".mid") }

    // Local files are linked to the last player having the floppy.
    // Online files are linked by the listening button.

    private fun playMidi(): Boolean {

        if ( !isMidi() || !hasSequencer() ) return false;       val sequencer = sequencer!!

        try {

            val input = if (isDirectAudio) URL(path).openStream() else ModFile(path).inputStream()

            sequencer.sequence = MidiSystem.getSequence(input);     sound = null

        } catch ( e: Exception ) {

            warnUser( FloppyDisk.missingMessage(path) )

            warnUser( Translation.get("message.check_console") )

            e.printStackTrace();        return false

        }

        sequencer.start();      sequencer.tickPosition = pauseTick;     return true

    }

    /** Try to load direct url or local file. */
    private fun playSound(): Boolean {

        if ( isMidi() ) return false

        val warning = Translation.get("message.file_on_query")

        if (onQuery) { warnUser(warning); return false }

        loadSound();        val sound = sound ?: return false

        if ( !sound.isValid() ) return false

        sound.play();       return true

    }

    private fun loadSound() {

        if ( !inputExists() ) return

        sound = try { ExternalSound(this) } catch ( e: Exception ) {

            warnUser( Translation.get("error.file_access") )

            warnUser( Translation.get("message.check_console") )

            e.printStackTrace();    null

        }

    }

    fun play() {

        if ( path.isEmpty() ) return

        var playMidi = false;       if ( isFormerPlayer() ) playMidi = playMidi()

        val isPlaying = playMidi || playSound();       setPlaying(isPlaying)

        if ( isMidi() && !isFormerPlayer() ) statusMessage("Listening...")

        if ( !isPlaying ) return;   statusMessage("Playing...")

        startParticles()

    }

    private fun startParticles() {

        if ( !isFormerPlayer() ) return;    val id = netID("particles")

        val buf = PacketByteBufs.create().writeBlockPos( pos() );   buf.writeInt( this.id )

        ClientPlayNetworking.send( id, buf )

    }

    fun pause() { pause( blockEntity!!.repeatOnPlay ) }

    fun pause(stop: Boolean) {

        spawnParticles = false;     if ( !isPlaying ) return;   setPlaying(false)

        if ( hasSound() ) sound!!.fadeOut() else {

            if ( !hasSequencer() ) return;          val sequencer = sequencer!!

            pauseTick = sequencer.tickPosition;     if (stop) pauseTick = 0

            for ( i in 0..15 ) {

                val stack = items[16];    val item = stack.item

                if ( item is Instrument ) item.stopDeviceSounds(stack)

            }

            sequencer.stop()

        }

        statusMessage("Stopping...")

    }

    fun pauseOnMidiHost() { if ( !isFormerPlayer() || !isMidi() ) return; pause() }

    /** Loads youtube-dl requests. */
    fun read() {

        Thread.currentThread().name = "HonkyTones Loading thread";      isDirectAudio = false

        val validURL = isValidUrl(path);       if ( !validURL ) return

        val connection = URL(path).openConnection();        val type = connection.contentType

        isDirectAudio = type != null && type.contains("audio");     if ( isMidi() ) return

        if (isDirectAudio) statusMessage( "Direct Stream Format: " + connection.contentType + ":" )

        if ( isDirectAudio || isCached(path) ) return;       onQuery = true;     modPrint("$this: Starting request...")

        val info = infoRequest(path) ?: return // Download sources using yt-dl + ffmpeg.

        val max = clientConfig["max_length"] as Int // Limit to max_length in config.

        if ( info.duration > max ) {

            val warning = Translation.get("error.long_stream")
                .replace( "X", "${ max / 60f }" )

            warnUser(warning); return

        }

        val streamsPath = directories["streams"]!!.path;        var filePath = "$streamsPath\\"

        var name = info.id + "-" + info.title + ".ogg"
        name = name.replace( Regex("[\\\\/:*?\"<>|]"), "_" )
            .replace( " ", "_" )

        filePath += name;       val outputFile = ModFile(filePath);     outputFile.createNewFile()

        try {

            val request = MediaRequest(path);   request.setOption( "format", 139 )

            val outputPath = outputFile.path.replace( ".ogg", ".%(ext)s" )

            request.setOption("output $outputPath")

            val keepVideos = clientConfig["keep_videos"] as Boolean

            if (keepVideos) coroutine.launch { requestVideo(outputPath) }

            warnUser( Translation.get("message.downloading") ); warnUser( info.title )

            if ( executeYTDL(request).isEmpty() ) throw Exception("Request failed!")

            val quality = clientConfig["audio_quality"] as Int

            val convertPath = outputPath.replace( "%(ext)s", "m4a" )

            val builder = FFmpegImpl.builder ?: throw YoutubeDLException("Missing ffmpeg!")

            builder.setInput(convertPath).addOutput(filePath).setFormat("ogg")
                .setAudioCodec("libvorbis").setAudioQuality( quality.toDouble() )

            FFmpegImpl.builder = FFmpegBuilder()

            val executor = FFmpegImpl.executor ?: throw YoutubeDLException("Missing ffmpeg!")

            executor.createJob(builder).run();      ModFile(convertPath).delete()

            this.path = filePath;                   warnUser( Translation.get("message.done") )

        } catch ( e: Exception ) {

            val warning = Translation.get("error.exec_ytdl") + ": " + Translation.get("error.check_console")

            warnUser(warning);  warnUser( Translation.get("message.check_console") )

            outputFile.delete();    e.printStackTrace()

        }

    }

    private fun requestVideo(outputPath: String) {

        val request = MediaRequest(path)

        val outputPath = outputPath.replace( "%(ext)s", "mp4" )

        request.setOption("output $outputPath");    request.setOption( "format", 18 )

        executeYTDL(request)

    }

    /** Verifies if the file was already downloaded. */
    private fun isCached(path: String): Boolean {

        val directory = directories["streams"]!!

        directory.listFiles()!!.forEach {

            val file = it;      val name = file.name

            val id = name.substringBefore("-")

            if ( this.path == it.path ) return true

            if ( !path.contains(id) ) return@forEach

            val extension = file.extension;     if ( extension != "ogg" ) return@forEach

            val message = Translation.get("message.file_found")

            this.path = it.path;    warnUser("$name $message");     return true

        }

        return false

    }

    fun inputExists(): Boolean { return isValidUrl(path) || ModFile(path).exists() }

    fun setMIDIReceiver() {

        if ( sequencer != null || !Midi.hasSystemSequencer() ) return

        sequencer = MidiSystem.getSequencer()

        val sequencer = sequencer!!;        if ( !sequencer.isOpen ) sequencer.open()

        val transmitters = sequencer.transmitters

        for ( transmitter in transmitters ) transmitter.receiver = MusicPlayerReceiver(this)

        sequencer.transmitter.receiver = MusicPlayerReceiver(this)

    }

    fun spawnParticles(wave: ParticleEffect) {

        val allow = clientConfig["music_particles"] as Boolean

        if ( !spawnParticles || !allow ) return

        val l1 = Random.nextInt(10)
        val l2 = Random.nextInt( 10, 15 )
        val l3 = Random.nextInt( 10, 15 )
        val l4 = Random.nextInt( 5, 15 )

        if ( blockEntity!!.repeatOnPlay ) spawnParticles = false

        Timer(l4) { spawnParticles(wave) }

        val blockEntity = blockEntity ?: return

        val entity = blockEntity.entity ?: return

        val distance = Particles.MIN_DISTANCE

        val isNear = player()!!.blockPos.isWithinDistance( entity.pos, distance )

        if ( isMuted(entity) || !isNear ) return

        Timer(l1) { ActionParticles.spawnNote(entity) }

        Timer(l2) { ActionParticles.spawnWave( entity, wave, false ) }

        Timer(l3) { ActionParticles.spawnWave( entity, wave, true ) }

    }

    fun tick() { midiTick() }

    private fun midiTick() {

        if ( hasSound() || !hasSequencer() ) return

        val floppy = items[16];     val sequencer = sequencer!!

        if ( floppy.isEmpty ) { if ( sequencer.isRunning ) sendPause(); return }

        val nbt = NBT.get(floppy)

        sequencer.tempoFactor = nbt.getFloat("Rate")

        val sequence = sequencer.sequence ?: return

        val finished = sequencer.tickPosition == sequence.tickLength

        if ( isPlaying && finished ) sendPause()

    }

    private fun sendPause() {

        sequencer!!.tickPosition = 0

        val id = netID("send_pause");      val buf = PacketByteBufs.create()

        buf.writeBlockPos( pos() );     ClientPlayNetworking.send( id, buf )

    }

    private fun hasSequencer(): Boolean { return sequencer != null }

    private fun hasSound(): Boolean { return sound !== null }

    private fun statusMessage( statusType: String ) {

        if ( blockEntity!!.repeatOnPlay ) return

        modPrint("$this: $statusType \"$path\"")

    }

    companion object : ModID {

        val list = mutableListOf<MusicPlayer>()

        private fun create(id: Int): MusicPlayer {

            val musicPlayer = MusicPlayer(id);      list.add(musicPlayer)

            return musicPlayer

        }

        fun get(id: Int): MusicPlayer {

            val musicPlayer = list.find { it.id == id }

            if ( musicPlayer != null ) return musicPlayer

            return create(id)

        }

        fun remove( world: World, id: Int ) {

            val buf = PacketByteBufs.create();      val players = world.players

            buf.writeInt(id);      val id = netID("remove")

            players.forEach { ServerPlayNetworking.send( it as ServerPlayerEntity, id, buf ) }

        }

        fun networking() {

            var id = netID("send_pause")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, _: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    musicPlayer.pause()

                } )

            }

            id = netID("particles")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, _: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos();   val id = buf.readInt()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    val floppy = musicPlayer.getStack(16)

                    val allow = serverConfig["music_particles"] as Boolean

                    if ( !musicPlayer.isPlaying() || !allow ) return@ServerTask

                    val waveType = ActionParticles.randomWave()

                    val buf = PacketByteBufs.create(); buf.writeInt(waveType); buf.writeInt(id)

                    musicPlayer.usersListening(floppy).forEach {

                        ServerPlayNetworking.send( it as ServerPlayerEntity, netID("particles"), buf )

                    }

                } )

            }

            if ( !isClient() ) return

            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val type = buf.readInt();       val id = buf.readInt()

                client.send {

                    val musicPlayer = get(id);  musicPlayer.spawnParticles = true

                    musicPlayer.spawnParticles( ActionParticles.waves[type] )

                }

            }

            id = netID("read")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val id = buf.readInt();     val stacks = mutableListOf<ItemStack>()

                for ( i in 0 .. 16 ) stacks.add( buf.readItemStack() )

                client.send {

                    val musicPlayer = get(id);      val items = musicPlayer.items

                    val blockEntity = musicPlayer.blockEntity

                    for ( i in 0 .. 16 ) {

                        val stack = stacks[i];      items[i] = stack

                        blockEntity!!.setStack( i, stack )

                    }

                    val floppy = items[16];     var path = ""

                    val isEmpty = floppy.isEmpty

                    if ( !isEmpty ) path = NBT.get(floppy).getString("Path")

                    val isSame = path == musicPlayer.path

                    if ( !isSame || isEmpty ) musicPlayer.pause(true)

                    musicPlayer.path = path;         if (isEmpty) return@send

                    coroutine.launch { musicPlayer.read();  musicPlayer.onQuery = false }

                }

            }

            id = netID("position")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val newPos = buf.readBlockPos();    val id = buf.readInt()

                client.send {

                    val entity = get(id).blockEntity!!.entity!!

                    entity.setPos(newPos)

                }

            }

            id = netID("remove")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val id = buf.readInt()

                client.send {

                    val musicPlayer = list.find { it.id == id } ?: return@send

                    list.remove(musicPlayer)

                }

            }

            id = netID("action")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val name = buf.readString();    val id = buf.readInt()

                client.send { get(id).actions[name]!!() }

            }

        }

        object ActionParticles {

            val waves = listOf( WAVE1, WAVE2, WAVE3, WAVE4 )

            fun randomWave(): Int { return waves.indices.random() }

            fun spawnNote( entity: Entity ) {

                world() ?: return

                val pos = entity.pos.add( Vec3d( 0.0, 1.25, 0.0 ) )

                val particle = Particles.spawnOne( Particles.SIMPLE_NOTE, pos ) as SimpleNoteParticle

                particle.addVelocityY( - 0.06 )

            }

            fun spawnWave( entity: Entity, wave: ParticleEffect, flip: Boolean ) {

                world() ?: return

                val yaw = degreeToRadians( entity.yaw.toDouble() )

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
