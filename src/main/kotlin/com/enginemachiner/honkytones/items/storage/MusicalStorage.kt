package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.LidAnimatorBehaviour
import com.enginemachiner.honkytones.mixin.chest.ChestBlockEntityAccessor
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Quaternion
import net.minecraft.world.World

private typealias Action = ( stack: ItemStack, item: MusicalStorage ) -> Unit

class MusicalStorageInventory(stack: ItemStack) : StackInventory( stack, INVENTORY_SIZE ) {
    companion object { const val INVENTORY_SIZE = 16 }
}

/** All the mod items can be stored here and instruments can be played while stored. */
class MusicalStorage : Item( modItemSettings() ), StackScreen {

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

        val stack = user.getStackInHand(hand);      trackHolder(stack, user)

        val canOpen = canOpenScreen( user, stack )

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

        // @Environment(EnvType.CLIENT)
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

            val chest = models.hand;    chest as ChestBlockEntityAccessor

            val lid = chest.lidAnimator as LidAnimatorBehaviour

            lid.`honkyTones$renderStep`()

            dispatcher.renderEntity(chest, matrix, vertex, light, overlay)

        }

        // @Environment(EnvType.CLIENT)
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

        // @Environment(EnvType.CLIENT)
        fun registerRender() {

            val dynamicRenderer = BuiltinItemRendererRegistry.DynamicItemRenderer {

                stack, mode, matrix, vertex, light, overlay ->

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

        private fun registerAnimation( netID: String,   action: Action ) {

            val id = netID(netID);          val receiver = Receiver(id)

            receiver.register { buf ->      val id = buf.readInt()

                client().send {

                    val player = entity(id) ?: return@send;         player as PlayerEntity

                    player.handItems.forEach {

                        val item = it.item

                        if ( item !is MusicalStorage ) return@forEach

                        it.holder = player;         action( it, item )

                    }

                }

            }

        }

        fun networking() {

            if ( !isClient() ) return

            registerAnimation("open") { stack, storage -> storage.open(stack) }
            registerAnimation("close") { stack, storage -> storage.close(stack) }

        }

    }

    private fun id(stack: ItemStack): Int {

        val nbt = NBT.get(stack);       return nbt.getInt("ID")

    }

    private fun sendAnimation( id: String, player: PlayerEntity, shouldNetwork: Boolean ) {

        val world = player.world

        if ( world.isClient || !shouldNetwork ) return

        val netID = netID(id);      val id = player.id

        val sender = Sender( netID, player ) { it.write(id) }

        sender.toClients(world)

    }

    /** Adds the rendering chest models. */
    private fun createModels(stack: ItemStack) {

        val id = NBT.get(stack).getInt("ID")

        if ( chests[id] != null ) return

        chests[id] = Models()

    }

    @BasedOn("Chest opening animation.")
    fun open( stack: ItemStack, shouldNetwork: Boolean = false ) {

        createModels(stack)


        val player = stack.holder!! as PlayerEntity

        val id = id(stack);         val handChest = chests[id]!!.hand


        val accessor = handChest as ChestBlockEntityAccessor

        val world = player.world;       val state = handChest.cachedState


        accessor.stateManager.openContainer( player, world, player.blockPos, state )

        handChest.onSyncedBlockEvent( 1, 1 )


        sendAnimation( "open", player, shouldNetwork )


    }

    @BasedOn("Chest closing animation.")
    fun close( stack: ItemStack, shouldNetwork: Boolean = false ) {

        val player = stack.holder!! as PlayerEntity

        val id = id(stack);         val handChest = chests[id]!!.hand


        val accessor = handChest as ChestBlockEntityAccessor

        val world = player.world;       val state = handChest.cachedState


        accessor.stateManager.closeContainer( player, world, player.blockPos, state )

        handChest.onSyncedBlockEvent( 1, 0 )


        sendAnimation( "close", player, shouldNetwork )

    }

    private fun createMenu(stack: ItemStack): NamedScreenHandlerFactory {

        val name = StorageScreenHandler.Companion.Translations.name
        val factory = StorageScreenHandler.factory(stack)
        val text = Text.of(name)

        return SimpleNamedScreenHandlerFactory(factory, text)

    }

}