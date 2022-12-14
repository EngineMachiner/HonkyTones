package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.YTDLRequest
import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Base.Companion.registerBlock
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.sapher.youtubedl.YoutubeDLException
import com.enginemachiner.honkytones.executeYTDL
import com.enginemachiner.honkytones.getVideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityTicker
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
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
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
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
import java.io.File
import java.util.*
import javax.sound.midi.InvalidMidiDataException
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer
import kotlin.concurrent.schedule

class MusicPlayerBlock(settings: Settings) : BlockWithEntity(settings), CanBeMuted {

    @Deprecated( "Deprecated in Java", ReplaceWith("BlockRenderType.MODEL", "net.minecraft.block.BlockRenderType") )
    override fun getRenderType(state: BlockState?): BlockRenderType {
        return BlockRenderType.MODEL
    }

    override fun createBlockEntity(pos: BlockPos?, state: BlockState?): BlockEntity {
        return MusicPlayerEntity(pos!!, state!!)
    }

    // *arrayOf
    override fun appendProperties(builder: StateManager.Builder<Block, BlockState>?) {
        builder!!.add( FACING )
    }

    override fun getPlacementState(ctx: ItemPlacementContext?): BlockState? {
        val direction = ctx!!.playerFacing.opposite
        return defaultState!!.with( FACING, direction )
    }

    @Deprecated("Deprecated in Java")
    override fun onUse( state: BlockState?, world: World?, pos: BlockPos?,
                        player: PlayerEntity?, hand: Hand?, hit: BlockHitResult?
    ): ActionResult {

        // ActionResults help to block other actions
        if (world!!.isClient) return ActionResult.SUCCESS

        val defAction = ActionResult.CONSUME

        val entity = world.getBlockEntity(pos) as MusicPlayerEntity

        // Player mute
        val offset = Vec3d( 0.5, -0.5, 0.5 )
        if ( shouldBlacklist( player!!, entity.companion!!, offset ) ) return defAction

        if ( world.isClient ) {
            val stack = player.getStackInHand(hand);      val item = stack.item
            if ( item is Instrument ) item.stopAllNotes(stack, world)
        }

        player.openHandledScreen( entity )
        return defAction

    }

    @Deprecated("Deprecated in Java")
    override fun neighborUpdate( state: BlockState?, world: World?, pos: BlockPos?,
                                 block: Block?, fromPos: BlockPos?, notify: Boolean ) {

        val world = world!!;    val pos = pos!!

        world.server ?: return

        val entity = world.getBlockEntity(pos) as MusicPlayerEntity
        val b2 = world.getReceivedRedstonePower(pos) > 9

        if ( !b2 && entity.isTriggered ) entity.isTriggered = false
        if ( entity.isTriggered ) return

        if ( b2 ) {

            entity.isTriggered = true
            entity.isPlaying = !entity.isPlaying

            if ( entity.isPlaying ) entity.playFile()
            else entity.pauseFile()

        }

    }

    override fun <T : BlockEntity?> getTicker( world: World?, state: BlockState?,
                                               type: BlockEntityType<T>? ): BlockEntityTicker<T>? {

        val id = Identifier( Base.MOD_NAME, "musicplayer_entity" )
        val registered = Registry.BLOCK_ENTITY_TYPE.get(id)
        return checkType(type, registered) { world: World, blockPos: BlockPos,
                                             _: BlockState, _: Any ->
            MusicPlayerEntity.tick(world, blockPos)
        }

    }

    override fun onBreak( world: World?, pos: BlockPos?, state: BlockState?, player: PlayerEntity? ) {

        val entity = world!!.getBlockEntity(pos) as MusicPlayerEntity
        for ( i in 0..16 ) dropStack( world, pos, entity.getStack(i) )
        entity.companion!!.remove( Entity.RemovalReason.DISCARDED )
        entity.sequencer.close()

        if (world.isClient) entity.sequencer.close()

        super.onBreak(world, pos, state, player)

    }

    companion object {

        private val FACING = Properties.HORIZONTAL_FACING

        fun register() {

            val settings = FabricBlockSettings.of( Material.WOOD ).strength(1.0f)

            val musicPlayer = MusicPlayerBlock( settings )
            val block = registerBlock("musicplayer", musicPlayer, createDefaultItemSettings() )

            val id = Identifier( Base.MOD_NAME, "musicplayer_entity" )
            val builder = FabricBlockEntityTypeBuilder.create( ::MusicPlayerEntity, block )
            MusicPlayerEntity.type =
                Registry.register( Registry.BLOCK_ENTITY_TYPE, id, builder.build() )

        }

    }

}

class MusicPlayerEntity(pos: BlockPos, state: BlockState)
: BlockEntity(type, pos, state), ExtendedScreenHandlerFactory, ImplementedInventory {

    var currentPath = ""
    var lastTickPosition: Long = 0
    var companion: MusicPlayerCompanion? = null

    @Environment(EnvType.CLIENT)
    var sequencer: Sequencer = MidiSystem.getSequencer()

    var streamInstance: SpecialSoundInstance? = null
    val trustedUsers = mutableSetOf<PlayerEntity>()

    var isUserTrusting = false;     var isStream = false
    var isTriggered = false;       var isPlaying = false

    private val items = DefaultedList.ofSize( invSize, ItemStack.EMPTY )

    override fun setWorld(world: World?) {

        super.setWorld(world)

        if (world != null) {

            if ( companion == null ) companion = MusicPlayerCompanion(this)

            if (world.isClient) {
                val transmitters = sequencer.transmitters
                for (transmitter in transmitters) transmitter.receiver = MusicPlayerReceiver(this)
                sequencer.transmitter.receiver = MusicPlayerReceiver(this)
                sequencer.open()
            }

        }

    }

    override fun readNbt(nbt: NbtCompound?) {
        super.readNbt(nbt)
        Inventories.readNbt(nbt, items)
    }

    override fun writeNbt(nbt: NbtCompound?): NbtCompound {
        Inventories.writeNbt(nbt, items)
        return super.writeNbt(nbt)
    }

    override fun getItems(): DefaultedList<ItemStack> { return items }

    override fun markDirty() { super<BlockEntity>.markDirty() }

    override fun canExtract(slot: Int, stack: ItemStack?, dir: Direction?): Boolean {
        if ( slot == 16 && stack!!.item is FloppyDisk ) {
            Timer().schedule( 250L ) { networkOnClients() }
        }
        return super.canExtract(slot, stack, dir)
    }

    // Hopper actions
    override fun canInsert(slot: Int, stack: ItemStack?, dir: Direction?): Boolean {
        if (slot < 16 && stack!!.item !is Instrument) return false
        if (slot == 16 && stack!!.item !is FloppyDisk) return false
        if (slot == 16 && stack!!.item is FloppyDisk) {
            Timer().schedule( 250L ) { networkOnClients() }
        }
        return super.canInsert(slot, stack, dir)
    }

    override fun createMenu( syncId: Int, inv: PlayerInventory?,
                             player: PlayerEntity? ): ScreenHandler {
        return MusicPlayerScreenHandler( syncId, inv!!, this as Inventory )
    }

    override fun getDisplayName(): Text { return Text.of("§1Music Player") }

    override fun writeScreenOpeningData( player: ServerPlayerEntity?, buf: PacketByteBuf? ) {
        buf!!.writeBlockPos(pos)
    }

    companion object {

        const val invSize = 16 + 1
        lateinit var type: BlockEntityType<MusicPlayerEntity>

        private val coroutine = CoroutineScope( Dispatchers.IO )

        fun networking() {

            var id = Identifier( Base.MOD_NAME, "musicplayer_slider_data" )
            ServerPlayNetworking.registerGlobalReceiver(id) {
                    server: MinecraftServer, _: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos();       val value = buf.readFloat()
                val nbtName = buf.readString()

                server.send( ServerTask( server.ticks + 1 ) {

                    val world = server.overworld
                    val entity = world.getBlockEntity(pos) as MusicPlayerEntity
                    val stack = entity.getStack(16)
                    if (stack.isEmpty) return@ServerTask

                    val nbt = stack.tag!!.getCompound(Base.MOD_NAME)
                    nbt.putFloat(nbtName, value)

                } )

            }

            id = Identifier( Base.MOD_NAME, "add_or_remove_trusted_user" )
            ServerPlayNetworking.registerGlobalReceiver(id) {
                    server: MinecraftServer, player: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos();       val shouldAdd = buf.readBoolean()

                server.send( ServerTask( server.ticks + 1 ) {

                    val world = server.overworld
                    val entity = world.getBlockEntity(pos) as MusicPlayerEntity
                    val list = entity.trustedUsers

                    if ( shouldAdd ) list.add( player )
                    else { if ( list.contains( player ) ) list.remove( player ) }

                } )

            }

            id = Identifier( Base.MOD_NAME, "set_musicplayer_states" )
            ServerPlayNetworking.registerGlobalReceiver(id) {
                    server: MinecraftServer, _: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos();       val isPlaying = buf.readBoolean()

                server.send( ServerTask( server.ticks + 1 ) {
                    val world = server.overworld
                    val entity = world.getBlockEntity(pos) as MusicPlayerEntity
                    entity.isPlaying = isPlaying
                } )

            }

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            id = Identifier( Base.MOD_NAME, "sync_to_clients" )
            ClientPlayNetworking.registerGlobalReceiver(id) {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos()
                val stackList = mutableListOf<ItemStack>()
                for ( i in 0 .. 16 ) stackList.add( buf.readItemStack() )

                client.send {

                    val world = client.world!!
                    val entity = world.getBlockEntity(pos) as MusicPlayerEntity
                    for ( i in 0 .. 16 ) entity.setStack( i, stackList[i] )

                    val floppyDisk = entity.getStack(16)

                    val nbt = floppyDisk.tag!!.getCompound(Base.MOD_NAME)

                    val wasSwapped = nbt.getString("path") != entity.currentPath

                    if ( floppyDisk.isEmpty ) {
                        entity.clientPause(true); return@send
                    }

                    if ( wasSwapped ) entity.clientPause(true)

                    val b = !floppyDisk.isEmpty || wasSwapped
                    if (b) coroutine.launch {
                        Thread.currentThread().name = "MusicPlayerBlockQuery thread"
                        entity.updateMedia()
                    }

                }

            }

            id = Identifier( Base.MOD_NAME, "play_file" )
            ClientPlayNetworking.registerGlobalReceiver(id) {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos()
                client.send {

                    val world = client.world!!
                    var musicPlayer = world.getBlockEntity(pos) ?: return@send

                    musicPlayer = musicPlayer as MusicPlayerEntity
                    val path = musicPlayer.currentPath;        val file = File(path)
                    val sequencePlayer = musicPlayer.sequencer
                    val sound = musicPlayer.streamInstance

                    if ( ( !file.exists() && !Network.isValidUrl(path) ) || path.isEmpty() ) return@send

                    musicPlayer.isPlaying = true

                    val nbt = musicPlayer.getStack(16).tag!!.getCompound(Base.MOD_NAME)

                    if ( sound != null || musicPlayer.isFileCached(path) ) {
                        sound!!.resetOrDone()
                        sound.setPlayState();      sound.volume = nbt.getFloat("Volume")
                        client.soundManager.play(sound);   return@send
                    }

                    sequencePlayer.start()
                    sequencePlayer.tempoFactor = nbt.getFloat("Rate")
                    sequencePlayer.tickPosition = musicPlayer.lastTickPosition

                }

            }

            id = Identifier( Base.MOD_NAME, "pause_file" )
            ClientPlayNetworking.registerGlobalReceiver(id) {
                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                val pos = buf.readBlockPos()
                client.send {
                    val world = client.world!!
                    val blockEntity = world.getBlockEntity(pos) ?: return@send
                    val musicPlayer = blockEntity as MusicPlayerEntity
                    musicPlayer.clientPause()
                }

            }

        }

        fun tick(world: World, pos: BlockPos) {

            if ( !world.isClient ) return

            val entity = world.getBlockEntity(pos) as MusicPlayerEntity
            val floppyStack = entity.getStack(16)

            if ( !floppyStack.isEmpty ) {

                val nbt = floppyStack.tag!!.getCompound(Base.MOD_NAME)
                val player =
                    findByUUID( MinecraftClient.getInstance(), nbt.getString("PlayerUUID") )

                if ( player == null ) entity.clientPause(true)

            }

        }

    }

    fun playFile() {

        val server = world!!.server!!

        val floppyStack = getStack(16)
        if ( floppyStack.isEmpty ) return

        val nbt = floppyStack.tag!!.getCompound(Base.MOD_NAME)

        var trustedList = trustedUsers.toMutableSet()
        val playerList = server.playerManager.playerList

        val uuid = nbt.getString("PlayerUUID")
        val player = playerList.find { it.uuidAsString == uuid } ?: return

        // If a midi will play, only allow the last user to play it
        if ( !isStream ) trustedList = mutableSetOf(player)
        else { if ( !trustedList.contains(player) ) trustedList.add(player) }

        server.send( ServerTask(server.ticks + 1) {

            val id = Identifier( Base.MOD_NAME, "play_file" )

            for ( player in trustedList ) {
                val buf = PacketByteBufs.create().writeBlockPos( pos )
                ServerPlayNetworking.send( player as ServerPlayerEntity?, id, buf )
            }

        } )

    }

    fun pauseFile() {

        val server = world!!.server!!
        val list = server.playerManager.playerList

        server.send( ServerTask(server.ticks + 1) {

            val id = Identifier( Base.MOD_NAME, "pause_file" )
            val buf = PacketByteBufs.create().writeBlockPos(pos)

            for ( player in list ) ServerPlayNetworking.send(player, id, buf)

        } )

    }

    fun networkOnClients() {

        val id = Identifier( Base.MOD_NAME, "sync_to_clients" )

        val buf = PacketByteBufs.create()
        buf.writeBlockPos( pos )

        val nbt = getStack(16).orCreateTag.getCompound(Base.MOD_NAME)

        for ( i in 0 .. 16 ) buf.writeItemStack( getStack(i) )

        val playerList = world!!.players
        val trustedUsers = trustedUsers.toMutableSet()

        val uuid = nbt.getString("PlayerUUID")
        val player = playerList.find { it.uuidAsString == uuid } ?: return

        if ( !trustedUsers.contains(player) ) trustedUsers.add(player)

        for ( player in trustedUsers ) {
            val player = player as ServerPlayerEntity
            ServerPlayNetworking.send( player, id, buf )
        }

    }

    fun updateMedia() {

        val floppyStack = getStack(16)
        val nbt = floppyStack.tag!!.getCompound(Base.MOD_NAME)

        var path = nbt.getString("path")
        val idChanged = currentPath != path && path.isNotBlank()

        if ( idChanged ) {

            if ( !Network.isValidUrl(path) ) {

                // Midi implementation

                if ( !path.startsWith(Base.MOD_NAME) ) path = Base.MOD_NAME + "/$path"

                val file = RestrictedFile(path)
                if ( !file.exists() || file.isDirectory ) return

                try {

                    val tempSeq = MidiSystem.getSequence(file)
                    sequencer.sequence = tempSeq

                    streamInstance = null

                } catch ( e: InvalidMidiDataException) {
                    printMessage( FloppyDisk.fileNotFoundMsg(path) )
                }

            } else {

                // Stream implementation

                if ( isFileCached(path) ) return

                val info = getVideoInfo(path) ?: return

                val max = clientConfig["max_length"] as Int
                if ( info.duration > max ) {
                    printMessage( "Stream is longer than $max minutes!" )
                    return
                }

                val streamsPath = Base.paths["streams"]!!.path
                var filePath = "$streamsPath\\"
                var name = info.id + "-" + info.title + ".ogg"
                name = name.replace( Regex("[\\\\/:*?\"<>|]"), "_" )
                name = name.replace( " ", "_" )
                filePath += name

                val outputFile = RestrictedFile( filePath )
                outputFile.createNewFile()

                try {

                    // Request web media

                    val request = YTDLRequest(path)

                    request.setOption("no-playlist")
                    request.setOption("no-mark-watched")

                    val outputPath = outputFile.path.replace(".ogg", ".%(ext)s")
                    request.setOption("output $outputPath")

                    if ( clientConfig["keep_videos"] as Boolean ) {
                        request.setOption("format", 17)
                        request.setOption("-k")
                    } else request.setOption("format", 139)

                    printMessage( "Loading stream request... " )
                    printMessage( info.title )

                    val convertPath = outputPath.replace("%(ext)s", "m4a")
                    if ( !RestrictedFile(convertPath).exists() ) {
                        outputPath.replace("%(ext)s", "3gp")
                    }

                    if ( executeYTDL(request).isEmpty() ) return

                    val quality = clientConfig["audio_quality"] as Int

                    val builder = FFmpegImpl.builder ?: return

                    builder.setInput(convertPath)
                        .addOutput(filePath)
                        .setFormat("ogg")
                        .setAudioCodec("libvorbis")
                        .setAudioQuality( quality.toDouble() )

                    val exe = FFmpegImpl.executor ?: return
                    exe.createJob(builder).run()

                    RestrictedFile(convertPath).delete()

                    printMessage("Done!")

                } catch ( e: YoutubeDLException ) {
                    printMessage("Unexpected yt-dlp error: Check console for more details.")
                    outputFile.delete();    e.printStackTrace();    return
                }

                setStream( outputFile )

            }

            isStream = Network.isValidUrl(path)
            currentPath = path

        }

    }

    fun clientPause() { clientPause(false) }

    fun clientPause( shouldStop: Boolean ) {

        isPlaying = false

        // Update state in server
        if ( Network.canNetwork() ) {
            val id = Identifier( Base.MOD_NAME, "set_musicplayer_states" )
            val buf = PacketByteBufs.create();      buf.writeBlockPos(pos)
            buf.writeBoolean(isPlaying)
            ClientPlayNetworking.send( id, buf )
        }

        if ( isStream ) {

            val sound = streamInstance ?: return

            val file = sound.file
            sound.setStopState();   setStream( file )

        } else {

            lastTickPosition = sequencer.tickPosition
            if (shouldStop) lastTickPosition = 0
            sequencer.stop()

            for ( i in 0..15 ) {
                val stack = getStack(i);    val item = stack.item
                if ( item is Instrument ) item.stopAllNotes(stack, world)
            }

        }

    }

    fun isFileCached(path: String): Boolean {

        val streamDir = Base.paths["streams"]!!

        // Avoid downloading again
        for ( file in streamDir.listFiles()!! ) {
            val hint = file.name.substringBefore("-")
            if ( path.contains(hint) && file.extension == "ogg" ) {

                printMessage("${file.name} file was found!")

                setStream(file)

                isStream = true;      currentPath = path

                if ( streamInstance != null ) {
                    printMessage("File has been loaded.")
                }

                return true

            }
        }

        return false

    }

    private fun setStream( file: File ) {

        try {
            val sound = SpecialSoundInstance( file, this )
            sound.entity = companion
            streamInstance = sound
        } catch ( e: Exception ) {
            printMessage("There was an issue opening the file!")
            streamInstance = null
            file.delete()
        }

    }

}

class MusicPlayerCompanion( type: EntityType<MusicPlayerCompanion>,
                            world: World ) : Entity(type, world) {

    constructor( entity: MusicPlayerEntity ) : this( Companion.type, entity.world!! ) {

        val pos = entity.pos
        setPos( pos.x.toDouble() + 0.5, pos.y.toDouble(), pos.z.toDouble() + 0.5 )

        // Network all the entity server data to the clients
        // Keep track of these entities for use
        entities.add(this)
        if ( world.isClient ) {
            val buf = PacketByteBufs.create()
            buf.writeBlockPos(BlockPos(pos))
            val id = Identifier( Base.MOD_NAME, "musicplayer_sync_uuid" )
            ClientPlayNetworking.send(id, buf)
        }

    }

    override fun onRemoved() { entities.remove(this) }

    override fun initDataTracker() {}

    override fun readCustomDataFromNbt(nbt: NbtCompound?) {}

    override fun writeCustomDataToNbt(nbt: NbtCompound?) {}

    override fun createSpawnPacket(): Packet<*> { return EntitySpawnS2CPacket(this) }

    companion object {

        val entities = mutableSetOf<MusicPlayerCompanion>()

        lateinit var type: EntityType<MusicPlayerCompanion>

        fun register() {

            val builder = FabricEntityTypeBuilder
                .create( SpawnGroup.MISC, ::MusicPlayerCompanion ).build()

            val id = Identifier( Base.MOD_NAME, "musicplayer_companion" )
            type = Registry.register( Registry.ENTITY_TYPE, id, builder )

        }

        fun networking() {

            val id = Identifier( Base.MOD_NAME, "musicplayer_sync_uuid" )
            ServerPlayNetworking.registerGlobalReceiver(id) {
                    server: MinecraftServer, sender: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf,
                    _: PacketSender ->

                val blockPos = buf.readBlockPos()
                val newBuf = PacketByteBufs.create()
                newBuf.writeBlockPos( blockPos )

                server.send( ServerTask( server.ticks + 1 ) {
                    val world = server.overworld
                    val musicPlayer = world.getBlockEntity(blockPos) as MusicPlayerEntity
                    EntitySpawnS2CPacket(musicPlayer.companion).write(newBuf)
                    ServerPlayNetworking.send( sender, id, newBuf )
                } )

            }

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

            ClientPlayNetworking.registerGlobalReceiver(id) {
                    client: MinecraftClient, _: ClientPlayNetworkHandler, buf: PacketByteBuf,
                    _: PacketSender ->

                val world = client.world!!;     val blockPos = buf.readBlockPos()
                val entity = EntitySpawnS2CPacket(buf)

                client.send {
                    val musicPlayer = world.getBlockEntity(blockPos) as MusicPlayerEntity
                    musicPlayer.companion!!.setUuid(entity.uuid)
                }

            }

        }

    }

}