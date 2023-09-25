package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.Init.Companion.directories
import com.enginemachiner.honkytones.Init.Companion.registerBlock
import com.enginemachiner.honkytones.Particles.Companion.WAVE1
import com.enginemachiner.honkytones.Particles.Companion.WAVE2
import com.enginemachiner.honkytones.Particles.Companion.WAVE3
import com.enginemachiner.honkytones.Particles.Companion.WAVE4
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock.Companion.FACING
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlock.Companion.PLAYING
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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
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
import net.minecraft.network.listener.ClientPlayPacketListener
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.particle.ParticleEffect
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
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
import net.minecraft.world.World
import java.net.URL
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// TODO: Make parrots dance.
class MusicPlayerBlock(settings: Settings) : BlockWithEntity(settings), CanBeMuted {

    init { defaultState = defaultState.with( PLAYING, false ) }

    @Deprecated( "Deprecated in Java", ReplaceWith( "BlockRenderType.MODEL", "net.minecraft.block.BlockRenderType" ) )
    override fun getRenderType( state: BlockState? ): BlockRenderType { return BlockRenderType.MODEL }

    override fun createBlockEntity( pos: BlockPos?, state: BlockState? ): BlockEntity {
        return MusicPlayerBlockEntity( pos!!, state!! )
    }

    override fun appendProperties( builder: StateManager.Builder<Block, BlockState>? ) {
        builder!!.add( *arrayOf( FACING, PLAYING ) )
    }

    override fun getPlacementState( context: ItemPlacementContext? ): BlockState? {
        val direction = context!!.playerFacing.opposite;        return defaultState!!.with( FACING, direction )
    }

    @Deprecated("Deprecated in Java")
    override fun onUse(
        state: BlockState?, world: World?, pos: BlockPos?,
        player: PlayerEntity?, hand: Hand?, hit: BlockHitResult?
    ): ActionResult {

        val player = player!!;      val action = ActionResult.CONSUME

        val musicPlayer = world!!.getBlockEntity(pos) as MusicPlayerBlockEntity

        val entity = musicPlayer.entity!!

        val mute = mute( player, entity );          if (mute) return action

        player.openHandledScreen(musicPlayer);      return action

    }

    override fun onBreak( world: World?, pos: BlockPos?, state: BlockState?, player: PlayerEntity? ) {

        val musicPlayer = world!!.getBlockEntity(pos) as MusicPlayerBlockEntity

        val isPlaying = musicPlayer.isPlaying;      val entity = musicPlayer.entity!!

        super.onBreak( world, pos, state, player )

        val drop = !isPlaying || player!!.isCreative
        if (drop) drop( world, pos, musicPlayer ) else explode(entity)

        entity.remove( Entity.RemovalReason.DISCARDED )

    }

    @Deprecated("Deprecated in Java")
    override fun neighborUpdate(
        state: BlockState?, world: World?, pos: BlockPos?,
        block: Block?, fromPos: BlockPos?, notify: Boolean
    ) {

        val world = world!!;    val pos = pos!!

        val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

        var isPowered = world.getReceivedStrongRedstonePower(pos) > 9
                || world.getReceivedRedstonePower(pos) > 9

        val from = fromPos!!.add( 1, 0, 0 )
        isPowered = isPowered && world.isReceivingRedstonePower(from)

        if ( !isPowered || musicPlayer.isTriggered ) { musicPlayer.isTriggered = false; return }

        musicPlayer.isTriggered = true;      musicPlayer.isPlaying = !musicPlayer.isPlaying

        if ( musicPlayer.isPlaying ) musicPlayer.play() else musicPlayer.pause()

    }

    override fun <T : BlockEntity?> getTicker(
        world: World?, state: BlockState?, type: BlockEntityType<T>?
    ): BlockEntityTicker<T>? {

        val id = MusicPlayerBlockEntity.classID()

        val blockEntity = Registries.BLOCK_ENTITY_TYPE.get(id)

        return checkType( type, blockEntity ) {

            world: World, blockPos: BlockPos, _: BlockState, _: Any ->

            MusicPlayerBlockEntity.tick( world, blockPos )

        }

    }

    private fun drop( world: World?, pos: BlockPos?, musicPlayer: MusicPlayerBlockEntity ) {
        for ( i in 0..16 ) dropStack( world, pos, musicPlayer.getStack(i) )
    }

    private fun explode(entity: MusicPlayerEntity) {

        val world = entity.world

        world.createExplosion( entity, entity.x, entity.y, entity.z, 0.75f, World.ExplosionSourceType.TNT )

        world.createExplosion( entity, entity.x, entity.y, entity.z, 5f, true, World.ExplosionSourceType.TNT )

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
            val builder1 = FabricBlockEntityTypeBuilder.create( ::MusicPlayerBlockEntity, registerBlock ).build()

            MusicPlayerBlockEntity.type = Registry.register( Registries.BLOCK_ENTITY_TYPE, id, builder1 )

            id = MusicPlayerEntity.classID()
            val builder2 = FabricEntityTypeBuilder.create( SpawnGroup.MISC, ::MusicPlayerEntity ).build()

            MusicPlayerEntity.type = Registry.register( Registries.ENTITY_TYPE, id, builder2 )

            if ( !isClient() ) return

            EntityRendererRegistry.register( builder2 ) { MusicPlayerEntity.Companion.Renderer(it) }

        }

    }

}

class MusicPlayerBlockEntity( pos: BlockPos, state: BlockState ) : BlockEntity( type, pos, state ), ExtendedScreenHandlerFactory, CustomInventory {

    val syncedUsers = mutableSetOf<PlayerEntity>();     var entity: MusicPlayerEntity? = null

    /** Avoids more than one redstone triggers at the same time. */
    var isTriggered = false;        var path = "";          var isPlaying = false

    @Environment(EnvType.CLIENT) var willBreak = false
    @Environment(EnvType.CLIENT) var spawnParticles = false
    @Environment(EnvType.CLIENT) var isDirectAudio = false
    @Environment(EnvType.CLIENT) var sequencer: Sequencer? = null
    @Environment(EnvType.CLIENT) var pauseTick: Long = 0
    @Environment(EnvType.CLIENT) var sound: ExternalSound? = null

    /** Linked to the user sync state. It's used for the screen sync button. */
    @Environment(EnvType.CLIENT) var isSynced = false
    @Environment(EnvType.CLIENT) var onQuery = false

    private val items = DefaultedList.ofSize( INVENTORY_SIZE, ItemStack.EMPTY )

    override fun items(): DefaultedList<ItemStack> { return items }

    override fun readNbt( nbt: NbtCompound? ) {

        super.readNbt(nbt);     Inventories.readNbt( nbt, items )

    }

    override fun writeNbt( nbt: NbtCompound? ) {

        Inventories.writeNbt( nbt, items );    super.writeNbt(nbt);     setup()

    }

    override fun markDirty() { super<BlockEntity>.markDirty() }

    override fun toUpdatePacket(): Packet<ClientPlayPacketListener>? {

        setup();    return BlockEntityUpdateS2CPacket.create(this)

    }

    override fun markRemoved() {

        willBreak = true;       if ( world!!.isClient ) pauseOnClient(true)

        super.markRemoved()

    }

    // Thinking with hoppers.

    override fun canExtract( slot: Int, stack: ItemStack?, direction: Direction? ): Boolean {

        val item = stack!!.item;        if ( slot == 16 && item is FloppyDisk ) { pause();  scheduleRead() }

        return true

    }

    override fun canInsert( slot: Int, stack: ItemStack?, direction: Direction? ): Boolean {

        val item = stack!!.item;        if ( slot < 16 && item !is Instrument ) return false

        if ( slot == 16 && item is FloppyDisk ) scheduleRead() else return false

        return true

    }

    override fun createMenu( syncID: Int, inventory: PlayerInventory?, player: PlayerEntity? ): ScreenHandler {
        return MusicPlayerScreenHandler( syncID, inventory!!, this as Inventory )
    }

    override fun getDisplayName(): Text {

        val title = Translation.block("music_player");      return Text.of("§1$title")

    }

    override fun writeScreenOpeningData( player: ServerPlayerEntity?, buf: PacketByteBuf? ) { buf!!.writeBlockPos(pos) }

    companion object : ModID {

        const val INVENTORY_SIZE = 16 + 1;      lateinit var type: BlockEntityType<MusicPlayerBlockEntity>

        private val coroutine = CoroutineScope( Dispatchers.IO )

        private val particles = MusicPlayerEntity.Companion.ActionParticles

        fun networking() {

            var id = netID("set_user_sync")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, player: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos();   val add = buf.readBoolean()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    val list = musicPlayer.syncedUsers;      if (add) list.add(player) else list.remove(player)

                } )

            }

            id = netID("set_playing_state")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, _: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos();   val isPlaying = buf.readBoolean()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    world.setBlockState( musicPlayer.pos, musicPlayer.cachedState.with( PLAYING, isPlaying ) )

                    musicPlayer.isPlaying = isPlaying

                } )

            }

            id = netID("particles")
            ServerPlayNetworking.registerGlobalReceiver(id) {

                server: MinecraftServer, _: ServerPlayerEntity,
                _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val world = server.overworld;   val pos = buf.readBlockPos()

                server.send( ServerTask( server.ticks ) {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    val nbt = NBT.get( musicPlayer.getStack(16) )

                    val allow = serverConfig["music_particles"] as Boolean

                    if ( !musicPlayer.isPlaying || !allow ) return@ServerTask

                    val waveType = ( 0 until particles.waves.size ).random()
                    val buf = PacketByteBufs.create().writeBlockPos(pos)
                    buf.writeInt(waveType)

                    musicPlayer.getSyncedUsers(nbt).forEach {

                        ServerPlayNetworking.send( it as ServerPlayerEntity, netID("particles"), buf )

                    }

                } )

            }

            if ( !isClient() ) return

            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val world = client.world!!;     val pos = buf.readBlockPos()
                val type = buf.readInt()

                client.send {

                    val allow = clientConfig["music_particles"] as Boolean;     if ( !allow ) return@send

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    musicPlayer.spawnParticles = true

                    musicPlayer.spawnParticles( particles.waves[type] )

                }

            }

            id = netID("read")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val world = client.world!!;     val pos = buf.readBlockPos()

                val stacks = mutableListOf<ItemStack>()

                for ( i in 0 .. 16 ) stacks.add( buf.readItemStack() )

                client.send {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    for ( i in 0 .. 16 ) musicPlayer.setStack( i, stacks[i] )

                    val floppyStack = musicPlayer.getStack(16);      var path = ""

                    val isEmpty = floppyStack.isEmpty

                    if ( !isEmpty ) path = NBT.get(floppyStack).getString("Path")

                    val isSame = path == musicPlayer.path

                    if ( !isSame || isEmpty ) musicPlayer.pauseOnClient(true)

                    musicPlayer.path = path;         if ( isEmpty ) return@send

                    coroutine.launch { musicPlayer.preload() }

                }

            }

            id = netID("play")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos();       val world = client.world!!

                client.send {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    musicPlayer.playOnClient()

                }

            }

            id = netID("pause")
            ClientPlayNetworking.registerGlobalReceiver(id) {

                client: MinecraftClient, _: ClientPlayNetworkHandler,
                buf: PacketByteBuf, _: PacketSender ->

                val world = client.world!!;     val pos = buf.readBlockPos()

                client.send {

                    val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

                    musicPlayer.pauseOnClient()

                }

            }

        }

        fun tick( world: World, pos: BlockPos ) {

            val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

            // Entity position.

            val entity = musicPlayer.entity

            if ( entity != null && entity.blockPos != pos ) entity.setPosition( Vec3d.of(pos) )

            // Sequencer.

            val floppyStack = musicPlayer.getStack(16)

            if ( !world.isClient || floppyStack.isEmpty ) return

            musicPlayer.sequencerTick()

        }

    }

    @Environment(EnvType.CLIENT)
    fun inputExists(): Boolean { return isValidUrl(path) || ModFile(path).exists() }

    @Environment(EnvType.CLIENT)
    fun setUserSyncStatus(isSynced: Boolean) {

        val id = MusicPlayerBlockEntity.netID("set_user_sync")

        val buf = PacketByteBufs.create().writeBlockPos(pos)
        buf.writeBoolean(isSynced)

        ClientPlayNetworking.send( id, buf )

    }

    @Environment(EnvType.CLIENT)
    fun playOnClient() {

        if ( path.isEmpty() ) return;       modPrint("$entity: Tried to play.")

        val nbt = NBT.get( getStack(16) );      val player = lookupPlayer(nbt) ?: return

        var isPlaying = false;     val isFormer = player() == player

        if (isFormer) isPlaying = playMidi();           val playSound = playSound()

        isPlaying = isPlaying || playSound

        // Let the former player change only the state of the block.

        if (isFormer) { setPlayingState(isPlaying);     postPlay() }

        if (playSound) this.isPlaying = true

    }

    @Environment(EnvType.CLIENT)
    private fun postPlay() {

        if ( !isPlaying ) return;       val id = netID("particles")

        val buf = PacketByteBufs.create().writeBlockPos(pos)

        ClientPlayNetworking.send( id, buf )

    }

    // Local files are linked to the last player having the floppy.
    // Online files are linked by the sync button.

    @Environment(EnvType.CLIENT)
    private fun playMidi(): Boolean {

        if ( !path.endsWith(".mid") || !hasSequencer() ) return false

        val sequencer = sequencer!!;        val file = ModFile(path)

        try {

            val input = if ( file.exists() ) file.inputStream() else URL(path).openStream()

            sequencer.sequence = MidiSystem.getSequence(input);     sound = null

        } catch ( e: Exception ) {

            warnUser( FloppyDisk.missingMessage(path) )
            warnUser( Translation.get("message.check_console") )

            e.printStackTrace();        return false

        }

        sequencer.start();  sequencer.tickPosition = pauseTick;  return true

    }

    /** Try to load direct url or local file. */
    @Environment(EnvType.CLIENT)
    private fun playSound(): Boolean {

        if ( path.endsWith(".mid") ) return false

        val warning = Translation.get("message.file_on_query")

        if (onQuery) { warnUser(warning); return false }

        loadSound();        val sound = sound ?: return false

        sound.play();       return true

    }

    /** Preloads yt-dl requests. (for now) */
    @Environment(EnvType.CLIENT)
    fun preload() {

        Thread.currentThread().name = "HonkyTones Preload thread"

        val path = path;        isDirectAudio = false

        val validURL = isValidUrl(path);       if ( !validURL ) return

        val connection = URL(path).openConnection()

        isDirectAudio = connection.contentType.contains("audio")

        if (isDirectAudio) { modPrint( "$entity: Direct Stream Content Type: " + connection.contentType ); return }

        if ( isCached(path) ) return;       onQuery = true

        modPrint("$entity: Starting request...")

        // Download source using yt-dl + ffmpeg.

        val info = infoRequest(path) ?: return

        val max = clientConfig["max_length"] as Int // Limit to max_length in config.

        if ( info.duration > max ) {

            val s = Translation.get("error.long_stream")
                .replace( "X", "${ max / 60f }" )

            warnUser(s); return

        }

        val streamsPath = directories["streams"]!!.path

        var filePath = "$streamsPath\\"

        var name = info.id + "-" + info.title + ".ogg"
        name = name.replace( Regex("[\\\\/:*?\"<>|]"), "_" )
            .replace( " ", "_" )

        filePath += name;       val outputFile = ModFile(filePath)

        outputFile.createNewFile()

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

        onQuery = false

    }

    @Environment(EnvType.CLIENT)
    private fun requestVideo(outputPath: String) {

        val request = MediaRequest(path)

        val outputPath = outputPath.replace( "%(ext)s", "mp4" )

        request.setOption("output $outputPath");    request.setOption( "format", 18 )

        executeYTDL(request)

    }

    @Environment(EnvType.CLIENT) fun pauseOnClient() { pauseOnClient(false) }

    @Environment(EnvType.CLIENT)
    fun pauseOnClient(stop: Boolean) {

        spawnParticles = false;     if ( !isPlaying ) return;       setPlayingState(false)

        if ( hasSound() ) sound!!.fadeOut() else {

            if ( !hasSequencer() ) return;          val sequencer = sequencer!!

            pauseTick = sequencer.tickPosition;     if (stop) pauseTick = 0

            sequencer.stop()

            for ( i in 0..15 ) {

                val stack = getStack(i);    val item = stack.item

                if ( item is Instrument ) item.stopDeviceSounds(stack)

            }

            modPrint("$entity: Stopped.")

        }

    }

    /** Verifies if the file was already downloaded. */
    @Environment(EnvType.CLIENT)
    fun isCached(path: String): Boolean {

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

    @Environment(EnvType.CLIENT)
    private fun loadSound() {

        if ( !inputExists() ) return

        try {

            val sound = ExternalSound( path, this )

            sound.entity = entity;       this.sound = sound

        } catch ( e: Exception ) {

            warnUser( Translation.get("error.file_access") )
            warnUser( Translation.get("message.check_console") )

            e.printStackTrace();    sound = null

        }

    }

    @Environment(EnvType.CLIENT)
    fun setPlayingState(isPlaying: Boolean) {

        if (willBreak) return;        this.isPlaying = isPlaying

        val id = netID("set_playing_state")

        val buf = PacketByteBufs.create().writeBlockPos(pos)
        buf.writeBoolean(isPlaying)

        ClientPlayNetworking.send( id, buf )

    }

    @Environment(EnvType.CLIENT)
    private fun sequencerTick() {

        val floppyStack = getStack(16)

        if ( hasSound() || floppyStack.isEmpty || !hasSequencer() ) return

        val nbt = NBT.get(floppyStack);     val sequencer = sequencer!!

        sequencer.tempoFactor = nbt.getFloat("Rate")

        if ( !sequencer.isRunning && isPlaying ) pauseOnClient(true)

    }

    @Environment(EnvType.CLIENT) fun hasSequencer(): Boolean { return sequencer != null }

    @Environment(EnvType.CLIENT) fun hasSound(): Boolean { return sound != null }

    @Environment(EnvType.CLIENT)
    private fun loadReceiver() {

        if ( !Midi.hasSystemSequencer() ) return;   sequencer = MidiSystem.getSequencer()

        val sequencer = sequencer!!;        if ( !sequencer.isOpen ) sequencer.open()

        val transmitters = sequencer.transmitters

        for ( transmitter in transmitters ) transmitter.receiver = MusicPlayerReceiver(this)

        sequencer.transmitter.receiver = MusicPlayerReceiver(this)

    }

    private fun setup() {

        val world = world!!

        if ( world.isClient || entity != null ) return

        entity = MusicPlayerEntity(this)

        entity!!.setup();       world.spawnEntity(entity)

        val nextState = world.getBlockState(pos).with( PLAYING, false )

        world.setBlockState( pos, nextState )

    }

    private fun lookupPlayer(nbt: NbtCompound): PlayerEntity? {

        val players = world!!.players

        return players.find { it.id == nbt.getInt("PlayerID") }

    }

    /** Get the list of users synced to the block entity, including the owner of the floppy. */
    private fun getSyncedUsers(nbt: NbtCompound): MutableSet<PlayerEntity> {

        val users = syncedUsers.toMutableSet()

        for ( user in users ) if ( user.isRemoved ) syncedUsers.remove(user)

        val owner = lookupPlayer(nbt) ?: return users

        if ( !users.contains(owner) ) users.add(owner)

        return users

    }

    fun play() {

        isPlaying = true;       val floppyStack = getStack(16)

        if ( floppyStack.isEmpty ) return;      val nbt = NBT.get(floppyStack)

        val buf = PacketByteBufs.create().writeBlockPos(pos)

        getSyncedUsers(nbt).forEach {

            ServerPlayNetworking.send( it as ServerPlayerEntity, netID("play"), buf )

        }

    }

    @Environment(EnvType.CLIENT)
    fun initClient() {

        loadReceiver();     val syncAll = clientConfig["sync_all"] as Boolean

        if ( !syncAll ) return;     isSynced = true

        setUserSyncStatus(true)

    }

    @Environment(EnvType.CLIENT)
    private fun spawnParticles( wave: ParticleEffect ) {

        if ( !spawnParticles || isRemoved ) return

        val entity = entity!!

        val l1 = Random.nextInt(10)
        val l2 = Random.nextInt( 10, 15 )
        val l3 = Random.nextInt( 10, 15 )
        val l4 = Random.nextInt( 5, 15 )

        Timer(l4) { spawnParticles(wave) }

        if ( isMuted(entity) ) return

        Timer(l1) { particles.spawnNote(entity) }

        Timer(l2) { particles.spawnWave( entity, wave, false ) }

        Timer(l3) { particles.spawnWave( entity, wave, true ) }

    }

    fun pause() {

        isPlaying = false;                      val floppyStack = getStack(16)

        if ( floppyStack.isEmpty ) return;      val nbt = NBT.get(floppyStack)

        val id = netID("pause");            val buf = PacketByteBufs.create().writeBlockPos(pos)

        for ( player in getSyncedUsers(nbt) ) ServerPlayNetworking.send( player as ServerPlayerEntity, id, buf )

    }

    private fun scheduleRead() { Timer(5) { read() } }

    private fun read() { read( getStack(16) ) }
    fun read(floppy: ItemStack) {

        val id = netID("read");         if ( !NBT.has(floppy) ) return

        val nbt = NBT.get(floppy);          val buf = PacketByteBufs.create()

        buf.writeBlockPos(pos)

        for ( i in 0 .. 16 ) buf.writeItemStack( getStack(i) )

        for ( player in getSyncedUsers(nbt) ) {

            ServerPlayNetworking.send( player as ServerPlayerEntity, id, buf )

        }

    }

}

/** This entity is created mainly to be set as the instruments stacks holder, so instruments can be played. */
class MusicPlayerEntity( type: EntityType<MusicPlayerEntity>, world: World ) : Entity( type, world ) {

    constructor( blockEntity: MusicPlayerBlockEntity ) : this( Companion.type, blockEntity.world!! ) {

        val pos = Vec3d.of( blockEntity.pos );      setPosition( pos.add( 0.5, 0.0, 0.5 ) )

    }

    override fun initDataTracker() {}

    override fun readCustomDataFromNbt( nbt: NbtCompound? ) {}

    override fun writeCustomDataToNbt( nbt: NbtCompound? ) {}

    override fun onSpawnPacket( packet: EntitySpawnS2CPacket? ) {

        super.onSpawnPacket(packet)

        val musicPlayer = world.getBlockEntity(blockPos) ?: return

        musicPlayer as MusicPlayerBlockEntity

        world.spawnEntity(this);    musicPlayer.initClient()

        musicPlayer.entity = this

    }

    override fun createSpawnPacket(): Packet<ClientPlayPacketListener>? { return EntitySpawnS2CPacket(this) }

    override fun getName(): Text { return Text.of("MusicPlayer") }

    fun setup() {

        val facing = world.getBlockState(blockPos).get(FACING);     this.yaw = facing.asRotation()

    }

    companion object : ModID {

        lateinit var type: EntityType<MusicPlayerEntity>

        class Renderer( context: EntityRendererFactory.Context? ) : EntityRenderer<MusicPlayerEntity>(context) {
            override fun getTexture( entity: MusicPlayerEntity? ): Identifier { return Identifier("") }
        }

        object ActionParticles {

            val waves = listOf( WAVE1, WAVE2, WAVE3, WAVE4 )

            @Environment(EnvType.CLIENT)
            fun spawnNote( entity: Entity ) {

                world() ?: return

                val pos = entity.pos.add( Vec3d( 0.0, 1.25, 0.0 ) )

                val particle = Particles.spawnOne( Particles.SIMPLE_NOTE, pos ) as SimpleNoteParticle

                particle.addVelocityY( - 0.06 )

            }

            @Environment(EnvType.CLIENT)
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
