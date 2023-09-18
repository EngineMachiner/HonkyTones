package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.NBT.networkNBT
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity.Companion.INVENTORY_SIZE
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import kotlin.math.roundToInt

class MusicPlayerScreenHandler(
    syncID: Int, playerInventory: PlayerInventory, private val inventory: Inventory
) : StrictSlotScreen( type, syncID ) {

    private val player = playerInventory.player;        val world: World = player.world

    var pos: BlockPos? = null

    constructor( syncID: Int, playerInventory: PlayerInventory, buf: PacketByteBuf ) : this( syncID, playerInventory, SimpleInventory(INVENTORY_SIZE) ) {

        pos = buf.readBlockPos()

    }

    init {

        checkSize( inventory, inventory.size() );       inventory.onOpen(player)

        val w = 18;     val x = 8;     val y = 46

        // Music Player inventory.

        // Floppy slot.
        var slot = Slot( inventory, 16, 12 * w, w * 2 + 4 )

        // Instruments slots.
        for ( j in 0 .. 15 ) {

            val slot = Slot( inventory, j, ( j - 3 ) * w, w )

            addSlot(slot)

        }

        addSlot(slot)

        // Player inventory.
        for ( i in 0 .. 2 ) { for ( j in 0 .. 8 ) {

            val index = j + i * 9 + 9;      val x = w * j + x
            val y = w * ( i + 6 ) - y + 13

            slot = Slot( playerInventory, index, x, y )

            addSlot(slot)

        } }

        for ( j in 0 .. 8 ) {

            val x = w * j + x;      val y = w * 10 - y - 1

            slot = Slot( playerInventory, j, x, y )

            addSlot(slot)

        }

    }

    private fun transferInstrument(slotIndex: Int): Boolean {

        val slot = slots[slotIndex];        val stack = slot.stack

        val item = stack.item;              val size = inventory.size()

        if ( item !is Instrument ) return false

        if ( slotIndex > size ) insertItem( stack, 0, size, false )
        else insertItem( stack, size, slots.size, true )

        return true

    }

    private fun transferFloppy(slotIndex: Int): Boolean {

        val slot = slots[slotIndex];        val stack = slot.stack

        val item = stack.item;              val floppySlot = slots[16]

        if ( item !is FloppyDisk ) return false

        val swap = floppySlot.hasStack() && slot.hasStack() && slotIndex != 16

        if (swap) {

            val temp = floppySlot.stack

            floppySlot.stack = stack;       slot.stack = temp

            slot.markDirty();       updateClients(16);       return false

        } else {

            if ( slotIndex != 16 ) insertItem( stack, 16, slotIndex, false )
            else insertItem( stack, 16, slots.size, true )

        }

        return true

    }

    override fun transferSlot( player: PlayerEntity?, slotIndex: Int ): ItemStack {

        val slot = slots[slotIndex]

        val instrument = transferInstrument(slotIndex)
        val floppy = transferFloppy(slotIndex)
        val transfer = floppy || instrument

        if ( !transfer ) return ItemStack.EMPTY

        updateClients(16)

        return slot.stack.copy()

    }

    /** Place instruments and floppy disks only and move inventory freely. */
    override fun onSlotClick(
        slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity?
    ) {

        fun onSlotClick() { super.onSlotClick( slotIndex, button, actionType, player ) }

        // 1. THROW action out of bounds.
        // 2. slotIndex < 0 are used for networking internals.
        if ( slotIndex == -999 ) { onSlotClick(); return } else if ( slotIndex < 0 ) return

        val slot = slots[slotIndex];        val stack = slot.stack

        // Only allow instruments and floppy disks.
        val isInstrument = isAllowed( stack, cursorStack, Instrument::class, slotIndex != 16 )
        val isFloppy = isAllowed( stack, cursorStack, FloppyDisk::class, slotIndex >= 16 )
        val isAny = isInstrument || isFloppy

        // Can move all other items in PlayerInventory
        val pickUp = actionType == SlotActionType.PICKUP
        if ( !isAny ) { if ( pickUp && slotIndex > 16 ) onSlotClick(); return }

        onSlotClick();      if ( isFloppy ) onEmptyFloppy()

        if ( slotIndex <= 16 && pickUp ) updateClients(slotIndex)

    }

    override fun canUse( player: PlayerEntity? ): Boolean { return inventory.canPlayerUse(player) }

    companion object: ModID {

        val type = ExtendedScreenHandlerType {
            syncID: Int, inventory: PlayerInventory, buf: PacketByteBuf ->

            MusicPlayerScreenHandler( syncID, inventory, buf )
        }

        fun register() { Registry.register( Registry.SCREEN_HANDLER, classID(), type ) }

    }

    private fun onEmptyFloppy() {

        val stack = stacks[16]

        if ( !world.isClient || stack.isEmpty ) return

        val nbt = NBT.get(stack);       val path = nbt.getString("Path")

        val isEmpty = stack.item is FloppyDisk && path.isBlank()

        if ( !isEmpty ) return;   warnUser( Translation.get("message.empty") )

    }

    private fun trackPos(slotIndex: Int) {

        val stack = slots[slotIndex].stack;        if ( stack.isEmpty ) return

        val nbt = NBT.get(stack);                   var pos = pos

        if ( !world.isClient ) pos = ( inventory as MusicPlayerBlockEntity ).pos

        nbt.putString( "BlockPos", pos!!.toShortString() )

        nbt.putInt( "Slot", slotIndex )

    }

    private fun updateClients(slotIndex: Int) {

        trackPos(slotIndex)

        if ( world.isClient ) return;       var floppyStack = stacks[16]

        val musicPlayer = inventory as MusicPlayerBlockEntity

        if ( floppyStack.nbt == null ) floppyStack = cursorStack

        musicPlayer.read(floppyStack)

    }

}

@Environment(EnvType.CLIENT)
class MusicPlayerScreen(
    handler: MusicPlayerScreenHandler, playerInventory: PlayerInventory, text: Text
) : HandledScreen<MusicPlayerScreenHandler>( handler, playerInventory, text ) {

    private val pos = handler.pos;          private val world = handler.world

    private val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

    private var syncButton: ButtonWidget? = null
    private var slider = Slider( 30, 15, 100, 20, handler )
    private val genericTexture = Identifier("textures/gui/container/generic_54.png")

    init { backgroundHeight -= 10;     titleX += 55;     playerInventoryTitleY -= 10 }

    // This is run each time the window resizes
    override fun init() {

        addSelectableChild(slider)

        rateTitle = Translation.block("music_player.rate")
        volumeTitle = Translation.item("gui.volume")

        val x = ( width * 0.125f ).toInt()
        val y = ( height * 0.12f ).toInt()
        val w = ( width * 0.75f ).toInt()
        val h = ( 240 * 0.08f ).toInt()
        val w2 = ( w * 0.35f ).toInt()

        val on = Translation.get("gui.on");     val off = Translation.get("gui.off")

        val downloadsText = Translation.block("music_player.downloads")

        val isSynced = musicPlayer.isSynced
        val switch = mutableMapOf( true to on, false to off )
        syncButton = createButton( x, y, - w2 * 1.8f, height * 0.65f, w, h, w2, 10f ) {

            musicPlayer.isSynced = !musicPlayer.isSynced

            val isSynced = musicPlayer.isSynced;        musicPlayer.setUserSyncStatus(isSynced)

            it.message = Text.of("$downloadsText: ${ switch[isSynced] }")

        }

        syncButton!!.message = Text.of("$downloadsText: ${ switch[isSynced] }")

        addDrawableChild(syncButton);       super.init()

    }

    override fun mouseReleased( mouseX: Double, mouseY: Double, button: Int ): Boolean {

        slider.mouseReleased( mouseX, mouseY, button )

        return super.mouseReleased( mouseX, mouseY, button )

    }

    override fun mouseDragged( mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double ): Boolean {

        slider.mouseDragged( mouseX, mouseY, button, deltaX, deltaY )

        return super.mouseDragged( mouseX, mouseY, button, deltaX, deltaY )

    }

    override fun drawBackground( matrices: MatrixStack?, delta: Float, mouseX: Int, mouseY: Int ) {

        RenderSystem.setShader( GameRenderer::getPositionTexShader )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, genericTexture )

        val w = backgroundWidth;                val height = height + 10
        val centerX = ( width - w ) / 2;        val centerY = ( height - w ) / 2

        val backgroundHeight = backgroundHeight + 10

        // Top slots.
        drawTexture( matrices, centerX - 62, centerY + 5, 0, 0, w, 35 )
        drawTexture( matrices, centerX + 71, centerY + 5, 7, 0, w - 7, 35 )

        // Blank space.
        drawTexture( matrices, centerX - 62, centerY + 40, 0, 4, w, 13 )
        drawTexture( matrices, centerX + 71, centerY + 40, 7, 4, w - 7, 13 )

        drawTexture( matrices, centerX - 62, centerY + 52, 0, 4, w, 13 )
        drawTexture( matrices, centerX + 71, centerY + 52, 7, 4, w - 7, 13 )
        drawTexture( matrices, centerX, centerY + 64, 0, 4, w, 2 )

        // Upper part texture.
        drawTexture( matrices, centerX - 62, centerY + 64, 0, backgroundHeight + 53, w - 113, 3 )
        drawTexture( matrices, centerX + 173, centerY + 64, 109, backgroundHeight + 53, w - 113, 3 )

        // Floppy disk slot.
        drawTexture( matrices, centerX + 215, centerY + 44, 7, 17, 18, 18 )

        // Player inventory.
        drawTexture( matrices, centerX, centerY + 66, 0, 126, w, 128 )

    }

    override fun render( matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

        children().forEach { it as Drawable;     it.render( matrices, mouseX, mouseY, delta ) }

    }

    override fun isClickOutsideBounds(
        mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int
    ): Boolean {

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2

        val topX1 = centerX - backgroundWidth * 0.35f
        val topX2 = centerX + backgroundWidth * 1.35f

        val topY2 = centerY + backgroundHeight * 0.38f

        val onY = mouseY < centerY || mouseY >= topY2

        val box1 = super.isClickOutsideBounds( mouseX, mouseY, left, top, button )

        val box2 = onY || mouseX < topX1;     val box3 = onY || mouseX > topX2

        return box1 && ( box2 || box3 )

    }

    override fun shouldPause(): Boolean { return false }

    companion object {

        var rateTitle = "";     var volumeTitle = ""

        fun register() { HandledScreens.register( MusicPlayerScreenHandler.type, ::MusicPlayerScreen ) }

        private class Slider(
            x: Int, y: Int, w: Int, h: Int,
            handler: MusicPlayerScreenHandler,
        ) : SliderWidget( x, y, w, h, Text.of("MusicPlayerSlider"), 1.0 ) {

            private val pos = handler.pos;      private val world = handler.world

            private val musicPlayer = world.getBlockEntity(pos) as MusicPlayerBlockEntity

            private var floppyStack = musicPlayer.getStack(16)

            private var init = false
            private var title = "";     private var valueText = 1
            private var key = "";       private var scale = 2f

            init { visible = false }

            override fun mouseDragged( mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double ): Boolean {

                if ( !isFocused ) return false

                return super.mouseDragged( mouseX, mouseY, button, deltaX, deltaY )

            }

            override fun mouseReleased( mouseX: Double, mouseY: Double, button: Int ): Boolean {

                if ( !isFocused ) return false;         isFocused = false

                return super.mouseReleased( mouseX, mouseY, button )

            }

            override fun onClick( mouseX: Double, mouseY: Double ) {

                if ( !visible ) return;         isFocused = true

                super.onClick( mouseX, mouseY )

            }

            override fun updateMessage() { message = Text.of("$title: $valueText%") }

            override fun applyValue() {

                val nbt = NBT.get(floppyStack);       val value1 = value * scale

                if ( nbt.getFloat(key).toDouble() == value1 && init ) return

                val value2 = value1.toFloat();      init = true

                nbt.putFloat( key, value2 );     networkNBT(nbt)

                setValueText()

            }

            override fun render( matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float ) {

                super.render( matrices, mouseX, mouseY, delta )

                floppyStack = musicPlayer.getStack(16)

                visible = !floppyStack.isEmpty;     if ( !visible ) return

                visible = musicPlayer.inputExists() && !isMuted( musicPlayer.entity!! )

                check(floppyStack)

            }

            private fun setValueText() { valueText = ( value * scale * 100 ).roundToInt() }

            private fun check(stack: ItemStack) {

                val nbt = NBT.get(stack);     val path = nbt.getString("Path")

                val isMidi = path.endsWith(".mid")

                if ( isMidi && key != "Rate" ) {
                    key = "Rate";      title = rateTitle
                    scale = 2f;        update()
                }

                if ( !isMidi && key != "Volume" ) {
                    key = "Volume";      title = volumeTitle
                    scale = 1f;        update()
                }

            }

            private fun update() {

                value = getValue() / scale

                applyValue();    updateMessage()

            }

            private fun getValue(): Double {

                return NBT.get(floppyStack).getFloat(key).toDouble()

            }

        }

    }

}
