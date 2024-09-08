package com.enginemachiner.honkytones.blocks.music_player

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.Timer
import com.enginemachiner.honkytones.Config
import com.enginemachiner.honkytones.ModParticles.WAVE1
import com.enginemachiner.honkytones.ModParticles.WAVE2
import com.enginemachiner.honkytones.ModParticles.WAVE3
import com.enginemachiner.honkytones.ModParticles.WAVE4
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlock.Companion.FACING
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlock.Companion.PLAYING
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity.Companion.get
import com.enginemachiner.honkytones.items.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import com.enginemachiner.honkytones.items.music_player.RadioItem
import com.enginemachiner.honkytones.items.music_player.Remote
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
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import net.minecraft.world.explosion.Explosion
import java.util.*

fun host( world: World, floppy: ItemStack ): PlayerEntity? {

    if ( !NBT.has(floppy) ) return null;        val nbt = nbt(floppy)

    if ( !nbt.containsUuid("Host") ) return null


    val uuid = nbt.getUuid("Host")

    return world.getPlayerByUuid(uuid)

}

class MusicPlayerBlock(settings: Settings) : BlockWithEntity(settings) {

    @Deprecated( "Deprecated in Java", ReplaceWith( "BlockRenderType.MODEL", "net.minecraft.block.BlockRenderType" ) )
    override fun getRenderType( state: BlockState ): BlockRenderType { return BlockRenderType.MODEL }

    override fun createBlockEntity( pos: BlockPos, state: BlockState ): BlockEntity {
        return MusicPlayerBlockEntity( pos, state )
    }

    override fun appendProperties( builder: StateManager.Builder<Block, BlockState> ) {
        builder.add( *arrayOf( FACING, PLAYING ) )
    }

    override fun getPlacementState( context: ItemPlacementContext ): BlockState {

        val direction = context.playerFacing.opposite

        return defaultState.with( FACING, direction ).with( PLAYING, false )

    }

    @Deprecated("Deprecated in Java")
    override fun onUse(
        state: BlockState, world: World, pos: BlockPos,
        player: PlayerEntity, hand: Hand, hit: BlockHitResult
    ): ActionResult {

        val action = ActionResult.SUCCESS

        val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity


        val isRemote = Remote.link( pos, player )

        val isRadio = RadioItem.blockUse( blockEntity.id, player )


        if ( isRadio || isRemote ) return action


        if ( !world.isClient ) blockEntity.init()

        player.openHandledScreen(blockEntity);          return action

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

        blockEntity.trigger()

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

    private fun dropStacks( world: World, pos: BlockPos, blockEntity: MusicPlayerBlockEntity ) {

        for ( i in 0..16 ) dropStack( world, pos, blockEntity.getStack(i) )

    }

    private fun explode( entity: MusicPlayerEntity ) {

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

            //for ( i in 0 until registry.size() ) builder1.addBlock( registry[i] )

            MusicPlayerBlockEntity.type = Registry.register( Registry.BLOCK_ENTITY_TYPE, id, builder1.build() )


            id = MusicPlayerEntity.classID()

            val builder2 = FabricEntityTypeBuilder.create( SpawnGroup.MISC, ::MusicPlayerEntity ).build()

            MusicPlayerEntity.type = Registry.register( Registry.ENTITY_TYPE, id, builder2 )

        }

    }

}

class MusicPlayerBlockEntity( pos: BlockPos, state: BlockState? ) : BlockEntity( type, pos, state ), ExtendedScreenHandlerFactory, HarmonyInventory {

    private val listeners = mutableMapOf<String, UUID>()

    var entity: MusicPlayerEntity? = null;      var id = this.hashCode()

    var isTriggered = false;      var onRepeat = false

    private val items = DefaultedList.ofSize( INVENTORY_SIZE, ItemStack.EMPTY )

    fun trigger() {

        if ( !isPlaying() ) { play(); return }


        pause();        if ( onRepeat ) play()

    }

    private fun isClient(): Boolean { return world!!.isClient }

    private fun hasFloppy(): Boolean { return !items.first().isEmpty }

    override fun items(): DefaultedList<ItemStack> { return items }

    override fun toInitialChunkDataNbt(): NbtCompound { return createNbt() }

    fun init() {

        val world = world ?: return


        val netID = netID("init")

        val sender = Sender(netID) {

            it.write(pos).write(id)

            for ( i in 0 until size() ) it.write( items[i] )

        }

        sender.toClients(world)

    }

    override fun readNbt(nbt: NbtCompound) {

        super.readNbt(nbt);     Inventories.readNbt(nbt, items)

        id = nbt.getInt("ID");      readListeners(nbt)

        onRepeat = nbt.getBoolean("Repeat")

    }

    override fun writeNbt(nbt: NbtCompound) {

        if ( !nbt.contains("ID") ) nbt.putInt( "ID", id )

        nbt.putBoolean( "Repeat", onRepeat );     writeListeners(nbt)

        Inventories.writeNbt( nbt, items );     super.writeNbt(nbt)


        trySpawning();          Timer(250) { init() }

    }

    private fun remove(id: Int) {

        val netID = netID("remove")

        val sender = Sender(netID) { it.write(id) }

        sender.toClients( world!! )

    }

    override fun markRemoved() {

        if ( !world!!.isClient ) { pause(); remove(id) }

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

        if ( slot > 0 && item !is InstrumentItem ) return false

        if ( slot == 0 && item is FloppyDisk ) scheduleRead() else return false

        return true

    }

    override fun removeStack( slot: Int ): ItemStack {

        read();     return super.removeStack(slot)

    }

    override fun setStack( slot: Int, stack: ItemStack ) {

        super.setStack(slot, stack);        read();         onEmptyFloppy(stack)

    }

    private fun onEmptyFloppy(stack: ItemStack) {

        val floppy = items.first();     val isSame = stack == floppy

        if ( isClient() || !hasFloppy() || !isSame ) return


        val nbt = nbt(floppy);       val path = nbt.getString("Path")

        val isBlank = path.isBlank();       if ( !isBlank ) return


        val host = host( world!!, floppy )

        Message( "message.empty", host ).send()

    }

    override fun createMenu( syncID: Int, playerInventory: PlayerInventory, player: PlayerEntity ): ScreenHandler {

        val inventory = this as Inventory;          val context = ScreenHandlerContext.create(world, pos)

        return MusicPlayerScreenHandler( syncID, playerInventory, inventory, context )

    }

    override fun getDisplayName(): Text {

        val title = Translation.block("music_player")

        return Text.of("§1$title")

    }

    override fun writeScreenOpeningData( player: ServerPlayerEntity, buf: PacketByteBuf ) {

        buf.writeBlockPos(pos);         buf.writeInt(id);           buf.writeBoolean( isPlaying() )

        buf.writeBoolean(onRepeat);         for ( i in 0 until size() ) buf.writeItemStack( items[i] )

    }

    private fun sendStates( netID: String, state: Boolean ) {

        val netID = netID(netID)

        val sender = Sender(netID) { it.write(id).write(state) }

        sender.toClients( playersListening() )

    }

    companion object : ModID {

        const val INVENTORY_SIZE = 16 + 1;      lateinit var type: BlockEntityType<MusicPlayerBlockEntity>

        override fun className(): String { return "music_player" }

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

                    val blockEntity = world.getBlockEntity(pos) ?: return@serverSend

                    blockEntity as MusicPlayerBlockEntity

                    val players = blockEntity.playersListening()


                    // About to have no listeners,
                    // There could be listening to button interaction.
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


                    blockEntity.sendStates( "set_playing", isPlaying )

                }

            }


            id = netID("set_repeat")

            Receiver(id).register { server, _, buf ->

                val world = server.overworld

                val pos = buf.readBlockPos();       val onRepeat = buf.readBoolean()


                serverSend(server) {

                    val blockEntity = get( world, pos ) ?: return@serverSend

                    blockEntity.onRepeat = onRepeat


                    blockEntity.sendStates( "set_repeat", onRepeat )

                }

            }



            id = netID("trigger")

            Receiver(id).register { server, _, buf ->

                val world = server.overworld;           val pos = buf.readBlockPos()


                serverSend(server) {

                    val blockEntity = get(world, pos) ?: return@serverSend

                    blockEntity.trigger()

                }

            }


            MusicPlayer.networking()


        }

    }

    private fun setPlayerSettings( player: PlayerEntity ) {

        val floppy = getStack(0);       if ( !hasFloppy() ) return

        FloppyDisk.settings( floppy, player )

    }

    /** Set the map, reading the stored listeners. */
    private fun readListeners( nbt: NbtCompound ) {

        world ?: return;       if ( isClient() ) return

        val listeners = nbt.get("Listeners") as NbtCompound

        listeners.keys.forEach { this.listeners[it] = listeners.getUuid(it) }

    }

    private fun writeListeners( nbt: NbtCompound ) {

        val data = NbtCompound()

        for ( (name, uuid) in listeners ) { data.putUuid( name, uuid ) }

        nbt.put( "Listeners", data )

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

        nbt(floppy).putUuid( "Host", next.first().uuid )

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

        if ( !hasFloppy() ) return


        val netID = netID("action")

        val listeners = playersListening()


        val sender = Sender(netID) { it.write( actionName ).write(id) }

        sender.toClients(listeners)

    }

    private fun scheduleRead() { read() }

    fun read() {

        if ( isClient() ) return

        fun write( buf: BufWrapper ) {

            buf.write(id)

            for ( i in 0 until size() ) buf.write( getStack(i) )

        }

        val id = MusicPlayer.netID("read");         val sender = Sender(id) { write(it) }

        Timer(1) { val listeners = playersListening();      sender.toClients(listeners) }

    }

    private fun entityTick() {

        val entity = entity ?: return;      if ( entity.blockPos == pos ) return

        entity.setPos(pos)

    }

    private fun midiTick() {

        val netID = netID("midi_tick")

        val sender = Sender(netID) { it.write(id) }

        sender.toClients( world!! )

    }

    private fun mediaTick() {

        if ( isClient() ) return;       midiTick()


        if ( !hasFloppy() ) return


        // No listening button interaction. Can happen on disconnect.

        val isEmpty = playersListening().isEmpty()

        if ( isEmpty && isPlaying() ) setPlaying(false)

    }

    private fun trackSlots() { items.forEachIndexed { i, stack ->

        if ( stack.isEmpty ) return@forEachIndexed

        val nbt = nbt(stack)

        nbt.putString( "BlockPos", pos!!.toShortString() )

        nbt.putInt( "Slot", i )

    } }

    private fun musicPlayerTick() { mediaTick(); trackSlots() }

}

/** This entity used as instrument holder and for particles. */
class MusicPlayerEntity( type: EntityType<MusicPlayerEntity>, world: World ) : Entity( type, world ) {

    constructor( blockEntity: MusicPlayerBlockEntity ) : this( Companion.type, blockEntity.world!! ) { spawn(blockEntity) }

    override fun initDataTracker() {}

    override fun readCustomDataFromNbt(nbt: NbtCompound) {}

    override fun writeCustomDataToNbt(nbt: NbtCompound) {}

    override fun createSpawnPacket(): Packet<*> { return EntitySpawnS2CPacket(this) }

    override fun getName(): Text { return Text.of( Translation.block("music_player") ) }

        fun setPos(blockPos: BlockPos) {

        val newPos = Vec3d.of(blockPos).add( 0.5, 0.0, 0.5 )

        setPosition(newPos)

    }

    private fun spawn( blockEntity: MusicPlayerBlockEntity ) {

        blockEntity.entity = this;      setPos( blockEntity.pos )


        val facing = blockEntity.cachedState.get(FACING)

        this.yaw = facing.asRotation();         world!!.spawnEntity(this)

    }

    companion object : ModID { lateinit var type: EntityType<MusicPlayerEntity> }

}

object MusicPlayer : ModID {

    object ActionParticles {

        val waves = listOf( WAVE1, WAVE2, WAVE3, WAVE4 )

        fun randomWave(): Int { return waves.indices.random() }

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

        Receiver(id).register { server, sender, buf ->

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

        Receiver(id).register { server, _, buf ->

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

    }

}