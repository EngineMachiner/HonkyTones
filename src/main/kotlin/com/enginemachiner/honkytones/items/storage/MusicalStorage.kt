package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.mixin.chest.ChestBlockEntityAccessor
import com.enginemachiner.honkytones.mixin.chest.LidAnimatorAccessor
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
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
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Quaternion
import net.minecraft.world.World

class MusicalStorageInventory(stack: ItemStack) : StackInventory( stack, INVENTORY_SIZE ) {
    companion object { const val INVENTORY_SIZE = 96 } /* 16 * 6 */
}

/** All the mod items can be stored here and instruments can be played while stored. */
class MusicalStorage : Item( defaultSettings() ), StackMenu {

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();        nbt.putInt( "ID", stack.hashCode() )

        return nbt

    }

    override fun inventoryTick(
        stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean
    ) {

        super.inventoryTick( stack, world, entity, slot, selected )

        createModels(stack)

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand)

        val canOpen = canOpenMenu( user, stack )

        val action = TypedActionResult.consume(stack)

        if ( world.isClient ) return TypedActionResult.pass(stack)

        if ( !canOpen ) return action

        user.openHandledScreen( createMenu(stack) )

        return action

    }

    companion object : ModID {

        val registryItem = MusicalStorage()

        // This is needed on server as well for animations
        val chests = mutableMapOf<Int, Models>()

        class Models {

            val hand = ChestBlockEntity( BlockPos.ORIGIN, Blocks.CHEST.defaultState )
            val world = ChestBlockEntity( BlockPos.ORIGIN, Blocks.CHEST.defaultState )

        }

        @Environment(EnvType.CLIENT)
        private fun onPersonView(
            mode: ModelTransformation.Mode, matrix: MatrixStack,
            vertex: VertexConsumerProvider, light: Int, overlay: Int,
            models: Models
        ) {

            fun onFirstPerson(matrix: MatrixStack) {
                matrix.translate( 0.3, 0.25, 0.0 )
                matrix.scale( 0.55f, 0.55f, 0.55f )
            }

            fun onThirdPerson(matrix: MatrixStack) {
                matrix.translate( 0.3, 0.65, 0.3 )
                matrix.scale( 0.4f, 0.4f, 0.4f )
                matrix.multiply( Quaternion.fromEulerXyz( 0.75f, 0.0f, 0f ) )
            }

            val modeName = mode.name

            val dispatcher = client().blockEntityRenderDispatcher

            val isFirst = modeName.contains("FIRST")
            val isThird = modeName.contains("THIRD")
            val isAny = isFirst || isThird

            if ( !isAny ) return

            if (isFirst) onFirstPerson(matrix);      if (isThird) onThirdPerson(matrix)

            // Chest mixin animation.

            val chest = models.hand;            chest as ChestBlockEntityAccessor
            val lid = chest.lidAnimator;        lid as LidAnimatorAccessor

            if ( lid.progress < 1f && lid.open ) lid.progress -= 0.095f
            else if ( lid.progress > 0f && !lid.open ) lid.progress += 0.095f

            lid.progress = lid.progress.coerceIn( ( 0f..1f ) )

            lid.step()

            //

            dispatcher.renderEntity(chest, matrix, vertex, light, overlay)

        }

        @Environment(EnvType.CLIENT)
        private fun onWorldView(
            mode: ModelTransformation.Mode, matrix: MatrixStack,
            vertex: VertexConsumerProvider, light: Int, overlay: Int,
            models: Models
        ) {

            fun onGUI(matrix: MatrixStack) {
                matrix.translate( 0.075, 0.23, 0.0 )
                matrix.scale( 0.625f, 0.625f, 0.625f )
                matrix.multiply( Quaternion.fromEulerXyz( 0.55f, 0.8f, 0f ) )
            }

            fun onGround(matrix: MatrixStack) {
                matrix.translate( 0.25, 0.25, 0.25 )
                matrix.scale( 0.5f, 0.5f, 0.5f )
            }

            val modeName = mode.name

            val dispatcher = client().blockEntityRenderDispatcher

            val isGUI = modeName == "GUI";      val isOnGround = modeName == "GROUND"

            val isAny = isGUI || isOnGround

            if ( !isAny ) return

            if (isGUI) onGUI(matrix);       if (isOnGround) onGround(matrix)

            dispatcher.renderEntity( models.world, matrix, vertex, light, overlay )

        }

        @Environment(EnvType.CLIENT)
        fun registerRender() {

            val dynamicRenderer = BuiltinItemRendererRegistry.DynamicItemRenderer {

                stack: ItemStack, mode: ModelTransformation.Mode, matrix: MatrixStack,
                vertex: VertexConsumerProvider, light: Int, overlay: Int ->

                val storage = stack.item as MusicalStorage

                if ( !NBT.has(stack) ) storage.setupNBT(stack)

                val nbt = NBT.get(stack);       val id = nbt.getInt("ID")

                if ( chests[id] == null ) { storage.createModels(stack) }

                val models = chests[id]!!

                onPersonView( mode, matrix, vertex, light, overlay, models )
                onWorldView( mode, matrix, vertex, light, overlay, models )

            }

            BuiltinItemRendererRegistry.INSTANCE.register( { registryItem }, dynamicRenderer )

        }

        fun networking() {

            if ( !isClient() ) return

            fun clientsAnimations( netID: String, action: (stack: ItemStack) -> Unit ) {

                val id = netID(netID)

                ClientPlayNetworking.registerGlobalReceiver(id) {

                    client: MinecraftClient, _: ClientPlayNetworkHandler,
                    buf: PacketByteBuf, _: PacketSender ->

                    val id = buf.readInt()

                    client.send {

                        val player = entity(id) ?: return@send

                        player as PlayerEntity

                        player.handItems.forEach {

                            if ( it.item !is MusicalStorage ) return@forEach

                            it.holder = player;     action(it)

                        }

                    }

                }

            }

            clientsAnimations("open") {

                stack: ItemStack ->

                val storage = stack.item as MusicalStorage

                storage.open( stack, false )

            }

            clientsAnimations("close") {

                stack: ItemStack ->

                val storage = stack.item as MusicalStorage

                storage.close( stack, false )

            }

        }

    }

    /** Adds the rendering chest models. */
    private fun createModels(stack: ItemStack) {

        val id = NBT.get(stack).getInt("ID")

        if ( chests[id] != null ) return

        chests[id] = Models()

    }

    fun open(stack: ItemStack) { open( stack, true ) }

    @Verify("Vanilla chest OPEN animation.")
    fun open( stack: ItemStack, shouldNetwork: Boolean ) {


        createModels(stack)


        val user = stack.holder!! as PlayerEntity;  val world = user.world

        val nbt = NBT.get(stack);                   val stackID = nbt.getInt("ID")

        val handChest = chests[stackID]!!.hand;     val state = handChest.cachedState

        val accessor = handChest as ChestBlockEntityAccessor


        accessor.stateManager.openContainer( user, world, user.blockPos, state )

        handChest.onSyncedBlockEvent( 1, 1 )


        if ( world.isClient || !shouldNetwork ) return

        val id = netID("open")

        val buf = PacketByteBufs.create();          buf.writeInt( user.id )

        val players = world.players.filter { it != user }

        players.forEach { ServerPlayNetworking.send( it as ServerPlayerEntity, id, buf ) }


    }

    fun close(stack: ItemStack) { close( stack, true ) }

    @Verify("Vanilla chest CLOSE animation.")
    fun close( stack: ItemStack, shouldNetwork: Boolean ) {

        val user = stack.holder!! as PlayerEntity

        val world = user.world

        val nbt = NBT.get(stack);                    val stackID = nbt.getInt("ID")

        val handChest = chests[stackID]!!.hand;      val state = handChest.cachedState

        val accessor = handChest as ChestBlockEntityAccessor


        accessor.stateManager.closeContainer( user, world, user.blockPos, state )

        handChest.onSyncedBlockEvent( 1, 0 )


        if ( world.isClient || !shouldNetwork ) return

        val id = netID("close")

        val buf = PacketByteBufs.create();          buf.writeInt( user.id )

        val players = world.players.filter { it != user }

        players.forEach { ServerPlayNetworking.send( it as ServerPlayerEntity, id, buf ) }


    }

    private fun createMenu(stack: ItemStack): NamedScreenHandlerFactory {

        val title = Translation.item("musical_storage")

        return SimpleNamedScreenHandlerFactory(

            {
                syncID: Int, playerInv: PlayerInventory, _: PlayerEntity ->

                StorageScreenHandler( stack, syncID, playerInv )
            },

            Text.of("§1$title")

        )

    }

}