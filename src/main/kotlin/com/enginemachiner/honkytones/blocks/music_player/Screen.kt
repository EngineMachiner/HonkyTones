package com.enginemachiner.honkytones.blocks.music_player

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity.Companion.INVENTORY_SIZE
import com.enginemachiner.honkytones.items.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import com.enginemachiner.honkytones.items.music_player.Remote
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry

class MusicPlayerScreenHandler(

    syncID: Int,    private val playerInventory: PlayerInventory,

    private val inventory: Inventory,       private val context: ScreenHandlerContext

) : HarmonyScreenHandler( type, syncID ) {


    private val player = playerInventory.player

    val isClient = player.world.isClient


    var pos: BlockPos? = null;          var id: Int? = null

    var isPlaying = false;          var onRepeat = false


    constructor( syncID: Int, playerInventory: PlayerInventory, buf: PacketByteBuf ) : this( syncID, playerInventory, SimpleInventory(INVENTORY_SIZE), ScreenHandlerContext.EMPTY ) {

        pos = buf.readBlockPos();           id = buf.readInt()

        isPlaying = buf.readBoolean();          onRepeat = buf.readBoolean()

        for ( i in 0 until INVENTORY_SIZE ) slots[i].stack = buf.readItemStack()

    }


    init {

        checkSize( inventory, inventory.size() );       inventory.onOpen(player)


        addSlot( Slot( inventory, 0, 80, 9 ) ) // Floppy.


        // Instrument slots.

        val slots1 = slots( 1, 8, 17f, 37f, inventory, 1 )

        val slots2 = slots( 1, 8, 17f, 59f, inventory, slots1.size + 1 )

        ( slots1 + slots2 ).forEach { addSlot(it) }


        playerSlots( 8f, 94f, playerInventory ).forEach { addSlot(it) }

    }

    private fun insertInstrument( slotIndex: Int ): Boolean {

        val stack = stacks[slotIndex]

        val limit = inventory.size()

        if ( stack.item !is InstrumentItem ) return false

        return insertItem( slotIndex, 1, limit )

    }

    private fun insertFloppy( slotIndex: Int ): Boolean {

        val stack = stacks[slotIndex]

        if ( stack.item !is FloppyDisk ) return false

        return insertItem( slotIndex, 0, 1 )

    }

    private fun insertAny( slotIndex: Int ): Boolean {

        val start = inventory.size()

        val limit = start + playerInventory.size() * 0.5f

        return insertItem( slotIndex, start, limit.toInt() )

    }

    private fun forceListen() {

        if ( isClient ) return

        val netID = netID("screen_listen_on")

        Sender(netID).toClient(player)

    }

    override fun transferSlot( player: PlayerEntity, slotIndex: Int ): ItemStack {

        val slot = slots[slotIndex];        val stack = slot.stack;         val isEmpty = stack.isEmpty


        if (isEmpty) return ItemStack.EMPTY


        val onPlayer = insertInstrument(slotIndex) || insertFloppy(slotIndex)
        val success = onPlayer || insertAny(slotIndex)

        if ( !success ) return ItemStack.EMPTY

        if ( onPlayer && !isClient ) {

            val blockEntity = inventory as MusicPlayerBlockEntity

            blockEntity.read()

        }

        forceListen();      slot.markDirty();       return stack.copy()

    }

    /** Place instruments and floppy disks only and move inventory freely. */
    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val size = inventory.size();        val onSlots = slotIndex < size

        val onInstrumentSlots = slotIndex in 1 ..< size

        val onFloppySlot = slotIndex == 0


        if ( hasRemote() && onSlots ) return


        fun click() { super.onSlotClick( slotIndex, button, actionType, player ) }

        fun canPickUp(): Boolean {

            val canPickUp = canPickUp(onInstrumentSlots) { it.item is InstrumentItem }
                    || canPickUp(onFloppySlot) { it.item is FloppyDisk }

            if ( canPickUp ) forceListen()

            return canPickUp || !onSlots

        }

        fun onSwap(): Boolean {

            if ( actionType != SlotActionType.SWAP ) return true

            val canSwap = onInstrumentSlots && canSwap(button, slotIndex) { it.item is InstrumentItem }
                    || onFloppySlot && canSwap(button, slotIndex) { it.item is FloppyDisk }

            return canSwap && onSlots || !onSlots

        }


        // slotIndex < 0 are used for networking internals.

        if ( slotIndex < 0 ) { click(); return }


        if ( !canPickUp() || !onSwap() ) return

        click()

    }

    private fun remote(): ItemStack? {

        fun pos(): BlockPos? {

            if ( isClient ) return pos


            val blockEntity = inventory as MusicPlayerBlockEntity

            return blockEntity.pos

        }


        return player.handItems.find {

            it.item is Remote && pos() == NBT.blockPos(it)

        }

    }

    fun hasRemote(): Boolean { return remote() != null }

    override fun canUse( player: PlayerEntity ): Boolean {

        val exists = context.get { world, pos -> world.getBlockEntity(pos) is MusicPlayerBlockEntity }

        if ( hasRemote() && exists.get() ) return true

        return canUse( context, player, MusicPlayerBlock.registryBlock )

    }

    companion object: ModID {

        val type = ExtendedScreenHandlerType { id, inventory, buf ->

            MusicPlayerScreenHandler( id, inventory, buf )

        }

        fun register() {

            Registry.register( Registry.SCREEN_HANDLER, classID(), type )

        }

    }

}