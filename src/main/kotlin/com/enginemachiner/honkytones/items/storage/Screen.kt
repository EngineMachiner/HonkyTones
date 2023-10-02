package com.enginemachiner.honkytones.items.storage

import com.enginemachiner.honkytones.ModID
import com.enginemachiner.honkytones.lookupSlot
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
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class StorageScreenHandler(
    syncID: Int,    playerInventory: PlayerInventory,   private val inventory: Inventory
) : ScreenHandler( type, syncID ) {


    constructor( syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, SimpleInventory( MusicalStorageInventory.INVENTORY_SIZE ) )

    constructor( stack: ItemStack, syncID: Int, playerInventory: PlayerInventory ) : this( syncID, playerInventory, MusicalStorageInventory(stack) )


    private val player = playerInventory.player

    private val stack = player.handItems.find { it.item is MusicalStorage }!!

    private val storage = stack.item as MusicalStorage

    private val stackSlot: Int?


    init {

        storage.open(stack)

        checkSize( inventory, inventory.size() );   inventory.onOpen(player)

        val w = 18;     val x = 8;      val y = - w;      val x2 = - w * 4

        // Chest inventory.
        for ( i in 0 .. 5 ) { for ( j in 0 .. 15 ) {

            val index = j + i * 16;     val x = w * j + x + x2 + 9

            val y = w * i - y

            addSlot( Slot( inventory, index, x, y ) )

        } }

        // Player Inventory.
        for ( i in 0 .. 2 ) { for ( j in 0 .. 8 ) {

            val index = j + i * 9 + 9;      val x = w * j + x

            val y = w * ( i + 6 ) - y + 13

            addSlot( Slot( playerInventory, index, x, y ) )

        } }

        for ( j in 0 .. 8 ) {

            val x = w * j + x;      val y = w * 10 - y - 1

            addSlot( Slot( playerInventory, j, x, y ) )

        }

        stackSlot = lookupSlot( slots, stack )

    }

    override fun onClosed(player: PlayerEntity) {

        super.onClosed(player);        storage.close(stack);       inventory.markDirty()

    }

    override fun canUse(player: PlayerEntity): Boolean { return inventory.canPlayerUse(player) }

    override fun quickMove( player: PlayerEntity, index: Int ): ItemStack {

        var currentStack = ItemStack.EMPTY;    val currentSlot = slots[index]

        if ( currentSlot.hasStack() ) {

            val stack = currentSlot.stack;      currentStack = stack.copy()

            val isNotAllowed = index < inventory.size()
                    && !insertItem( stack, inventory.size(), slots.size, true )
                    || !insertItem( stack, 0, inventory.size(), false )

            if (isNotAllowed) return ItemStack.EMPTY

            if ( stack.isEmpty ) currentSlot.stack = ItemStack.EMPTY else currentSlot.markDirty()

        }

        return currentStack

    }

    override fun onSlotClick(
        slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity
    ) {

        // THROW action.
        if ( slotIndex == -999 ) super.onSlotClick( slotIndex, button, actionType, player )

        // slotIndex < 0 are used for networking internals.
        if ( slotIndex < 0 ) return;        val slotStack = slots[slotIndex].stack

        val isSlotModItem = isModItem(slotStack)
        val hasModItem = isSlotModItem || isModItem(cursorStack)

        if ( !isSlotModItem && actionType == SlotActionType.QUICK_MOVE ) return

        // You can't put the chest in the chest.
        val isStorageSlot = stackSlot == slotIndex

        // Can move the hotbar items and the inventory around freely.
        // Can't move non honkytones stacks in the storage.
        if ( slotIndex < inventory.size() && !hasModItem || isStorageSlot ) return

        super.onSlotClick( slotIndex, button, actionType, player )

    }

    companion object : ModID {

        val type = ScreenHandlerType( ::StorageScreenHandler, FeatureFlags.VANILLA_FEATURES )

        fun register() { Registry.register( Registries.SCREEN_HANDLER, classID(), type ) }

    }

}

@Environment(EnvType.CLIENT)
class StorageScreen(
    handler: StorageScreenHandler, playerInv: PlayerInventory, text: Text
) : HandledScreen<StorageScreenHandler>( handler, playerInv, text ) {

    private val texture = Identifier("textures/gui/container/generic_54.png")
    private val rows = 6

    init { backgroundHeight += 60;     playerInventoryTitleY += 55;     titleX += 40 }

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        RenderSystem.setShader( GameRenderer::getPositionTexProgram )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, texture )

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2

        val spaceX = 86;       val padX = 20

        var x = centerX + spaceX + padX;      val w = backgroundWidth
        var h = rows * 18 + 30

        // Bottom border.
        drawTexture( matrices, x, centerY + h, 43, backgroundHeight - 7, w - 3, 3 )

        x = centerX - spaceX + padX
        drawTexture( matrices, x, centerY + h, -3, backgroundHeight - 7, w - 3, 3 )

        // Player inventory.
        h = 96
        drawTexture( matrices, centerX, centerY + rows * 18 + 17, 0, 126, w, h )

        // Storage inventory.
        h = rows * 18 + 30
        drawTexture( matrices, x, centerY, -3, 0, w - 3, h )

        x = centerX + spaceX + padX
        drawTexture( matrices, x, centerY, 43, 0, w - 43, h )

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

    }

    override fun isClickOutsideBounds(
        mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int
    ): Boolean {

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2

        val topX1 = centerX - backgroundWidth * 0.35f
        val topX2 = centerX + backgroundWidth * 1.35f

        val topY2 = centerY + backgroundHeight * 0.55f

        val onY = mouseY < centerY || mouseY >= topY2

        val box1 = super.isClickOutsideBounds( mouseX, mouseY, left, top, button )

        val box2 = onY || mouseX < topX1;     val box3 = onY || mouseX > topX2

        return box1 && ( box2 || box3 )

    }

    companion object {

        fun register() { HandledScreens.register( StorageScreenHandler.type, ::StorageScreen ) }

    }

}