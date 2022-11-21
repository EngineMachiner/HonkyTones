package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.mixins.ChestBlockEntityAccessor
import com.enginemachiner.honkytones.mixins.LidAnimatorAccessor
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Quaternion
import net.minecraft.world.World

class MusicalStorageInventory( stack: ItemStack ) : CustomInventory(stack, invSize) {
    companion object { const val invSize = 96 /* 16 * 6 */ }
}

// All the mod items can be stored here, and they should have their functionality
class MusicalStorage : Item( createDefaultItemSettings() ), ExtendedScreenHandlerFactory {

    override fun inventoryTick(stack: ItemStack?, world: World?,
                               entity: Entity?, slot: Int, selected: Boolean) {

        addRendererBlocks(stack!!, world!!)
        if ( !world.isClient ) {

            var nbt = stack.nbt!!

            if ( !nbt.contains(Base.MOD_NAME) ) loadNbtData(stack)

            nbt = nbt.getCompound(Base.MOD_NAME)

            if ( stacks.elementAtOrNull( nbt.getInt("Index") ) == null ) {
                nbt.putInt( "Index", stacks.size );     stacks.add(stack)
            }

            trackHandOnNbt(stack, entity!!)

        }

    }

    override fun use(world: World?, user: PlayerEntity?, hand: Hand?)
    : TypedActionResult<ItemStack> {

        val stack = user!!.getStackInHand(hand)

        if (world!!.isClient) return TypedActionResult.pass(stack)
        val action = TypedActionResult.consume(stack)

        val mainStack = user.mainHandStack;       val offStack = user.offHandStack
        val mainItem = mainStack.item;          val offItem = offStack.item

        if ( mainItem is MusicalStorage && offItem is MusicalStorage ) {
            val msg = menuMsg.replace("%item%", "storages")
            user.sendMessage(Text.of(msg), true)
            return action
        }

        var selected = mainStack
        if (offItem is MusicalStorage) selected = offStack

        selected.holder = user

        user.openHandledScreen( createMenu(selected) )

        return action

    }

    companion object {

        val renderMap = mutableMapOf< Int, MutableList<ChestBlockEntity> >()
        val stacks = mutableListOf<ItemStack>()
        val itemToRegister = MusicalStorage()

        fun registerRender() {

            fun onFirstPerson(matrix: MatrixStack) {
                matrix.translate(0.3, 0.25, 0.0)
                matrix.scale( 0.55f, 0.55f, 0.55f )
            }

            fun onThirdPerson(matrix: MatrixStack) {
                matrix.translate(0.3, 0.65, 0.3)
                matrix.scale( 0.4f, 0.4f, 0.4f )
                matrix.multiply( Quaternion.fromEulerXyz(0.75f, 0.0f, 0f) )
            }

            fun onGUI(matrix: MatrixStack) {
                matrix.translate(0.075, 0.23, 0.0)
                matrix.scale( 0.625f, 0.625f, 0.625f )
                matrix.multiply( Quaternion.fromEulerXyz(0.55f, 0.8f, 0f) )
            }

            fun onGround(matrix: MatrixStack) {
                matrix.translate(0.25, 0.25, 0.25)
                matrix.scale( 0.5f, 0.5f, 0.5f )
                matrix.multiply( Quaternion.fromEulerXyz(0.0f, 0.0f, 0.0f) )
            }

            val dynamicRenderer = BuiltinItemRendererRegistry.DynamicItemRenderer {
                        stack: ItemStack, mode: ModelTransformation.Mode,
                        matrix: MatrixStack, vertex: VertexConsumerProvider,
                        light: Int, overlay: Int ->

                val client = MinecraftClient.getInstance()

                val storage = stack.item as MusicalStorage
                var nbt = stack.orCreateNbt

                if ( !nbt.contains(Base.MOD_NAME) ) storage.loadNbtData(stack)

                nbt = nbt.getCompound(Base.MOD_NAME)

                var hash = nbt.getInt("hashID")

                val b = !nbt.contains("hashID") || !renderMap.contains(hash)
                if (b) storage.addRendererBlocks(stack, client.world!!)

                hash = nbt.getInt("hashID")
                val blocksList = renderMap[hash]!!
                val renderer = client.blockEntityRenderDispatcher
                val modeName = mode.name

                var b1 = modeName.contains("FIRST")
                var b2 = modeName.contains("THIRD");    var b3 = b1 || b2
                if ( b3 ) {

                    if (b1) onFirstPerson(matrix);      if (b2) onThirdPerson(matrix)

                    val blockEntity = blocksList[0];    blockEntity as ChestBlockEntityAccessor
                    val lid = blockEntity.lidAnimator;  lid as LidAnimatorAccessor

                    // Custom lid animation
                    if ( lid.progress < 1f && lid.open ) lid.progress -= 0.09f
                    else if ( lid.progress > 0f && !lid.open ) lid.progress += 0.09f

                    lid.step()
                    renderer.renderEntity(blockEntity, matrix, vertex, light, overlay)
                }

                b1 = modeName == "GUI";     b2 = modeName == "GROUND";      b3 = b1 || b2
                if ( b3 ) {
                    if ( b1 ) onGUI(matrix);    if ( b2 ) onGround(matrix)
                    renderer.renderEntity(blocksList[1], matrix, vertex, light, overlay)
                }

            }

            BuiltinItemRendererRegistry.INSTANCE.register(
                { itemToRegister }, dynamicRenderer
            )

        }

        fun networking() {

            fun clientsAnimations( netName: String, func: (item: MusicalStorage, stack: ItemStack,
                                          player: PlayerEntity, world: World) -> Unit ) {

                if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

                val id = Identifier( Base.MOD_NAME, netName )
                ClientPlayNetworking.registerGlobalReceiver(id) {
                        client: MinecraftClient,
                        _: ClientPlayNetworkHandler,
                        buf: PacketByteBuf, _: PacketSender ->

                    val uuid = buf.readString()
                    val world = client.world as World

                    client.send {

                        val player = findByUUID(client, uuid) ?: return@send
                        player as PlayerEntity
                        for ( stack in player.itemsHand ) {
                            val item = stack.item
                            if ( item is MusicalStorage ) {
                                val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
                                nbt.putBoolean("stopAnimation", true)
                                func( item, stack, player, world )
                            }
                        }

                    }

                }

            }

            clientsAnimations( "musicalstorage_open" ) {
                    item: MusicalStorage, stack: ItemStack,
                    player: PlayerEntity, world: World ->
                item.openRenderedChest(stack, player, world)
            }

            clientsAnimations( "musicalstorage_close" ) {
                    item: MusicalStorage, stack: ItemStack,
                    player: PlayerEntity, world: World ->
                item.closeRenderedChest(stack, player, world)
            }

        }

    }

    private fun createMenu(stack: ItemStack): NamedScreenHandlerFactory {
        return SimpleNamedScreenHandlerFactory( {
                syncID: Int, playerInv: PlayerInventory, _: PlayerEntity ->
            StorageScreenHandler( stack, syncID, playerInv )
        }, Text.of("Musical Storage") )
    }

    private fun loadNbtData(stack: ItemStack) {

        val nbt = NbtCompound()

        nbt.putInt("Index", -1)
        nbt.putInt("hand", -1)

        stack.nbt!!.put( Base.MOD_NAME, nbt )

    }

    private fun addRendererBlocks(stack: ItemStack, world: World) {

        var hash = stack.hashCode()
        val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)

        if ( !nbt.contains("hashID") ) nbt.putInt( "hashID", hash )
        else hash = nbt.getInt("hashID")

        if ( renderMap[hash] != null ) return

        if ( world.isClient ) Network.sendNbtToServer(nbt)

        renderMap[hash] = mutableListOf()
        val list = renderMap[hash]!!

        for ( i in 0..1 ) {
            list.add( ChestBlockEntity( BlockPos.ORIGIN, Blocks.CHEST.defaultState ) )
        }

    }

    fun openRenderedChest(stack: ItemStack, user: PlayerEntity, world: World) {

        addRendererBlocks(stack, world)
        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        val hash = nbt.getInt("hashID")
        val worldRenderedBlock = renderMap[hash]!![0]
        val accessor = worldRenderedBlock as ChestBlockEntityAccessor
        val state = worldRenderedBlock.cachedState
        accessor.stateManager.openContainer(user, world, user.blockPos, state)
        worldRenderedBlock.onSyncedBlockEvent(1, 1)

        if ( nbt.getBoolean("stopAnimation") ) return

        if ( !world.isClient ) {

            val id = Identifier( Base.MOD_NAME, "musicalstorage_open")
            val buf = PacketByteBufs.create();  buf.writeString( user.uuidAsString )
            val players = world.players.filter { it != user }
            for ( player in players ) {
                val player = player as ServerPlayerEntity
                ServerPlayNetworking.send(player, id, buf)
            }

        }

    }

    fun closeRenderedChest( stack: ItemStack, user: PlayerEntity, world: World ) {

        val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
        val hash = nbt.getInt("hashID")
        val worldRenderedBlock = renderMap[hash]!![0]
        val accessor = worldRenderedBlock as ChestBlockEntityAccessor
        val state = worldRenderedBlock.cachedState
        accessor.stateManager.closeContainer(user, world, user.blockPos, state)
        worldRenderedBlock.onSyncedBlockEvent(1, 0)

        if ( nbt.getBoolean("stopAnimation") ) return

        if ( !world.isClient ) {
            val id = Identifier( Base.MOD_NAME, "musicalstorage_close")
            val buf = PacketByteBufs.create();  buf.writeString( user.uuidAsString )
            val players = world.players.filter { it != user }
            for ( player in players ) {
                val player = player as ServerPlayerEntity
                ServerPlayNetworking.send(player, id, buf)
            }
        }

    }

    override fun createMenu( syncId: Int, inv: PlayerInventory?, player: PlayerEntity? )
    : ScreenHandler {
        val stack = player!!.itemsHand.find { it.item is MusicalStorage }
        return StorageScreenHandler( stack!!, syncId, inv!! )
    }

    override fun getDisplayName(): Text { return Text.of("Musical Storage") }

    override fun writeScreenOpeningData( player: ServerPlayerEntity?, buf: PacketByteBuf? ) {}

}