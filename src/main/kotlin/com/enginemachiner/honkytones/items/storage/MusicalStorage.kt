package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.mixin.chest.ChestBlockEntityAccessor
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.screen.NamedScreenHandlerFactory
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class MusicalStorageInventory(stack: ItemStack) : StackInventory( stack, INVENTORY_SIZE ) {
    companion object { const val INVENTORY_SIZE = 16 }
}

/** All the mod items can be stored here, and instruments
 * can be played while stored used a MIDI controller. */
class MusicalStorage : Item( modItemSettings() ), StackScreen {

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        val nbt = NbtCompound();        nbt.putInt( "ID", stack.hashCode() );       return nbt

    }

    override fun inventoryTick( stack: ItemStack, world: World, entity: Entity, slot: Int, selected: Boolean ) {

        super.inventoryTick(stack, world, entity, slot, selected);        createModels(stack)

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);          val canOpen = canOpenScreen(user, stack)

        val consume = TypedActionResult.consume(stack);         val pass = TypedActionResult.pass(stack)


        trackHolder(stack, user)

        if ( world.isClient ) return pass else if ( !canOpen ) return consume


        val screen = createMenu(stack);     user.openHandledScreen(screen)

        return consume

    }

    private fun sendAnimation( id: String, player: PlayerEntity, network: Boolean ) {

        val world = player.world;       if ( world.isClient || !network ) return


        val netID = netID(id);      val id = player.id

        val sender = Sender( netID, player ) { it.write(id) };      sender.toClients(world)

    }

    /** Adds the rendering chest models. */
    fun createModels(stack: ItemStack) {

        val id = NBT.id(stack);         if ( chests[id] != null ) return

        chests[id] = Models()

    }

    @BasedOn("Chest opening animation.")
    fun open( stack: ItemStack, network: Boolean = false ) {

        createModels(stack)


        val player = player(stack)

        val id = NBT.id(stack);         val handChest = chests[id]!!.hand


        val accessor = handChest as ChestBlockEntityAccessor

        val world = player.world;       val state = handChest.cachedState


        accessor.stateManager.openContainer( player, world, player.blockPos, state )

        handChest.onSyncedBlockEvent(1, 1)


        sendAnimation( "open", player, network )

    }

    @BasedOn("Chest closing animation.")
    fun close( stack: ItemStack, network: Boolean = false ) {

        val player = player(stack)

        val id = NBT.id(stack);         val handChest = chests[id]!!.hand


        val accessor = handChest as ChestBlockEntityAccessor

        val world = player.world;       val state = handChest.cachedState


        accessor.stateManager.closeContainer( player, world, player.blockPos, state )

        handChest.onSyncedBlockEvent(1, 0)


        sendAnimation( "close", player, network )

    }

    private fun createMenu(stack: ItemStack): NamedScreenHandlerFactory {

        val name = StorageScreenHandler.Companion.Translations.name
        val factory = StorageScreenHandler.factory(stack)
        val text = TranslatableText(name)

        return SimpleNamedScreenHandlerFactory(factory, text)

    }

    companion object : ModID {

        val registryItem = MusicalStorage()


        val chests = mutableMapOf<Int, Models>()

        private val pos: BlockPos = BlockPos.ORIGIN
        private val state: BlockState = Blocks.CHEST.defaultState

        class Models {

            val hand = ChestBlockEntity(pos, state)
            val world = ChestBlockEntity(pos, state)

        }

    }

}