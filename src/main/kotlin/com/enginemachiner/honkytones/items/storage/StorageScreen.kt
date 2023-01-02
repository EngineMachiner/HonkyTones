package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.honkytones.Base
import com.enginemachiner.honkytones.isModItem
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import net.minecraft.world.World

class StorageScreenHandler( syncID: Int,
                            private val playerInv: PlayerInventory,
                            private val inv: Inventory ) : ScreenHandler( type, syncID ) {

    private var isOffHand = false
    private val stack: ItemStack
    private val storage: MusicalStorage
    private val player: PlayerEntity = playerInv.player
    private val world: World = player.world

    constructor( syncID: Int, playerInv: PlayerInventory )
            : this( syncID, playerInv, SimpleInventory( MusicalStorageInventory.invSize ) )

    constructor( stack: ItemStack, syncID: Int, playerInv: PlayerInventory )
            : this( syncID, playerInv, MusicalStorageInventory(stack) )

    init {

        stack = player.itemsHand.find { it.item is MusicalStorage }!!
        storage = stack.item as MusicalStorage

        if ( stack == player.offHandStack ) isOffHand = true

        storage.open(stack, player, world)

        checkSize( inv, inv.size() )
        inv.onOpen(player)

        val w = 18;     val x = 8;      val y = - w;      val x2 = - w * 4

        // Chest inventory
        for ( i in 0 .. 5 ) { for ( j in 0 .. 15 ) {
            val index = j + i * 16;     val x = w * j + x + x2 + 9
            val y = w * i - y
            addSlot( Slot( inv, index, x, y ) )
        } }

        // Player Inventory
        for ( i in 0 .. 2 ) { for ( j in 0 .. 8 ) {
            val index = j + i * 9 + 9;      val x = w * j + x
            val y = w * ( i + 6 ) - y + 13
            addSlot( Slot( playerInv, index, x, y ) )
        } }

        for ( j in 0 .. 8 ) {
            val x = w * j + x;      val y = w * 10 - y - 1
            addSlot( Slot( playerInv, j, x, y ) )
        }

    }

    override fun close( player: PlayerEntity? ) {
        super.close(player);        storage.close(stack, player!!, world)
        inv.markDirty()
    }

    override fun canUse(player: PlayerEntity?): Boolean { return inv.canPlayerUse(player) }

    override fun transferSlot( player: PlayerEntity?, index: Int ): ItemStack {

        var currentStack = ItemStack.EMPTY;    val currentSlot = slots[index]

        if ( currentSlot.hasStack() ) {

            val stack = currentSlot.stack
            currentStack = stack.copy()

            val isNotAllowed = index < inv.size()
                    && !insertItem(stack, inv.size(), slots.size, true)
                    || !insertItem(stack, 0, inv.size(), false)

            if ( isNotAllowed ) return ItemStack.EMPTY

            if ( stack.isEmpty ) currentSlot.stack = ItemStack.EMPTY
            else currentSlot.markDirty()

        }

        return currentStack

    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType?,
                             player: PlayerEntity?) {

        // THROW action out of bounds
        if ( slotIndex == -999 ) super.onSlotClick(slotIndex, button, actionType, player)

        // slotIndex < 0 are used for networking internals
        if ( slotIndex < 0 ) return

        val slotStack = slots[slotIndex].stack
        val b0 = isModItem(slotStack.item)
        val b1 = b0 || isModItem(cursorStack.item)

        if (!b0 && actionType == SlotActionType.QUICK_MOVE) return

        // You can't put the chest in the chest
        val mainIndex = ( inv.size() + 1 ) + ( 8 * 3 + 1 ) + ( playerInv.selectedSlot + 1 )
        val b2 = mainIndex != slotIndex || isOffHand

        // Can move the hotbar items and the inventory around freely
        // Can't move non honkytones stacks in the storage
        if ( slotIndex < inv.size() && !b1 || !b2 ) return

        super.onSlotClick(slotIndex, button, actionType, player)

    }

    companion object {

        private val id = Identifier(Base.MOD_NAME, "musicalstorage")
        lateinit var type: ScreenHandlerType<StorageScreenHandler>

        fun register() {
            type = ScreenHandlerType(::StorageScreenHandler)
            Registry.register( Registry.SCREEN_HANDLER, id, type )
        }

    }

}

@Environment(EnvType.CLIENT)
class StorageScreen(handler: StorageScreenHandler, playerInv: PlayerInventory, text: Text)
    : HandledScreen<StorageScreenHandler>( handler, playerInv, text ) {

    private val TEXTURE = Identifier("textures/gui/container/generic_54.png")
    private val storageRows = 6

    init {
        backgroundHeight += 60;     playerInventoryTitleY += 55
        titleX += 40
    }

    override fun drawBackground(matrices: MatrixStack?, delta: Float, mouseX: Int, mouseY: Int) {

        RenderSystem.setShader( GameRenderer::getPositionTexShader )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, TEXTURE )

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2
        val spacing = 86;       val add = 20

        var x = centerX + spacing + add;      val w = backgroundWidth
        var h = storageRows * 18 + 17 + 13

        // Bottom border
        drawTexture(matrices, x, centerY + h, 7 + 36, backgroundHeight - 7, w - 3, 3)

        x = centerX - spacing + add
        drawTexture(matrices, x, centerY + h, -3, backgroundHeight - 7, w - 3, 3)

        // Player inv
        h = 96
        drawTexture(matrices, centerX, centerY + storageRows * 18 + 17, 0, 126, w, h)

        // Storage inv
        h = storageRows * 18 + 17 + 13
        drawTexture(matrices, x, centerY, -3, 0, w - 3, h)

        x = centerX + spacing + add
        drawTexture(matrices, x, centerY, 7 + 36, 0, w - 7 - 36, h)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)
        drawMouseoverTooltip(matrices, mouseX, mouseY)
    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int,
                                       button: Int ): Boolean {

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2

        val topX1 = centerX - backgroundWidth * 0.35f
        val topX2 = centerX + backgroundWidth * 1.35f

        val topY2 = centerY + backgroundHeight * 0.55f

        val b = mouseY < centerY || mouseY >= topY2
        val box1 = super.isClickOutsideBounds(mouseX, mouseY, left, top, button)
        val box2 = b || mouseX < topX1;     val box3 = b || mouseX > topX2
        return box1 && ( box2 || box3 )

    }

    companion object {

        fun register() {
            HandledScreens.register(StorageScreenHandler.type, ::StorageScreen)
        }

    }

}