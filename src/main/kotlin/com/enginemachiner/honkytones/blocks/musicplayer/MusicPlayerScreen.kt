package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.Base
import com.enginemachiner.honkytones.Network
import com.enginemachiner.honkytones.SpecificItemToSlotScreenHandler
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerEntity.Companion.invSize
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.enginemachiner.honkytones.printMessage
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
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
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import kotlin.math.roundToInt

class MusicPlayerScreenHandler( syncID: Int, val playerInv: PlayerInventory, val inv: Inventory )
    : SpecificItemToSlotScreenHandler( type, syncID ) {

    private val player = playerInv.player
    val world: World = player.world
    var pos: BlockPos? = null

    constructor( syncID: Int, playerInv: PlayerInventory, buf: PacketByteBuf )
            : this( syncID, playerInv, SimpleInventory(invSize) ) {
        pos = buf.readBlockPos()
    }

    init {

        checkSize( inv, inv.size() )
        inv.onOpen( playerInv.player )

        val w = 18;     val x = 8;     val y = 46

        // Music Player slots
        for ( j in 0 .. 15 ) addSlot( Slot( inv, j, ( j - 3 ) * w, w ) )
        addSlot( Slot( inv, 16, 12 * w, w * 2 + 4 ) )

        // Player Inventory
        for ( i in 0 .. 2 ) { for ( j in 0 .. 8 ) {
            val index = j + i * 9 + 9
            addSlot( Slot( playerInv, index, w * j + x, w * ( i + 6 ) - y + 13 ) )
        } }

        for ( j in 0 .. 8 ) {
            addSlot( Slot( playerInv, j, w * j + x, w * 10 - y - 1 ) )
        }

        update()

    }

    override fun transferSlot( player: PlayerEntity?, index: Int ): ItemStack {

        var currentStack = ItemStack.EMPTY;    val currentSlot = slots[index]

        if ( currentSlot.hasStack() ) {

            val stack = currentSlot.stack
            currentStack = stack.copy()

            val item = currentStack.item

            // Swap and quick move for Floppies
            if ( item is FloppyDisk ) {

                val b = index != 16
                if ( slots[16].hasStack() && slots[index].hasStack() && b ) {

                    val temp = slots[16].stack
                    slots[16].stack = currentStack
                    slots[index].stack = temp

                } else {

                    insertItem(stack, 16, index, false)
                    insertItem(stack, 16, slots.size, true)

                }

                printIfEmptyFloppy(slots[16].stack, world, 16)
                update();       return ItemStack.EMPTY

            }

            if ( item is Instrument ) {
                stack.holder = player
                item.stopAllNotes(stack, world)
            }

            val isNotAllowed = index < inv.size()
                    && !insertItem(stack, inv.size(), slots.size, true)
                    || !insertItem(stack, 0, inv.size(), false)

            update()

            if ( isNotAllowed ) return ItemStack.EMPTY

            if ( stack.isEmpty ) currentSlot.stack = ItemStack.EMPTY
            else currentSlot.markDirty()

        }

        return currentStack

    }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType?,
                             player: PlayerEntity? ) {

        // Should add instruments and floppy disk only but can move inventory freely

        // THROW action out of bounds
        if ( slotIndex == -999 ) super.onSlotClick(slotIndex, button, actionType, player)

        // slotIndex < 0 are used for networking internals
        if ( slotIndex < 0 ) return

        val slot = slots[slotIndex]
        val slotStack = slot.stack
        val slotItem = slotStack.item
        val cursorItem = cursorStack.item

        // Only allow MIDI Channel instruments slots and Floppy Disk slot
        val b1 = isAllowed(slotItem, cursorItem, Instrument::class, slotIndex != 16)
        val b2 = isAllowed(slotItem, cursorItem, FloppyDisk::class, slotIndex >= 16)
        val b3 = b1 || b2;

        if ( !b3 ) {
            // Can move all other items in PlayerInventory
            val b = slotIndex > 16
            if ( actionType == SlotActionType.PICKUP && b ) {
                super.onSlotClick(slotIndex, button, actionType, player)
            }
            return
        }

        printIfEmptyFloppy(cursorStack, world, slotIndex)

        super.onSlotClick(slotIndex, button, actionType, player)

        // Sync clients on changes
        if ( slotIndex <= 16 && actionType == SlotActionType.PICKUP ) update()

    }

    override fun canUse( player: PlayerEntity? ): Boolean { return inv.canPlayerUse(player) }

    companion object {

        private val id = Identifier(Base.MOD_NAME, "musicplayer")
        lateinit var type: ScreenHandlerType<MusicPlayerScreenHandler>

        fun register() {

            type = ExtendedScreenHandlerType {
                    syncId: Int, inventory: PlayerInventory, buf: PacketByteBuf ->
                MusicPlayerScreenHandler(syncId, inventory, buf)
            }

            Registry.register( Registry.SCREEN_HANDLER, id, type )

        }

    }

    private fun printIfEmptyFloppy( stack: ItemStack, world: World, index: Int ) {
        val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)
        if ( stack.item is FloppyDisk && index == 16
            && nbt.getString("path").isEmpty() && world.isClient ) {
            printMessage("Floppy Disk is empty!")
        }
    }

    private fun update() {

        if ( !world.isClient ) {

            val entity = inv as MusicPlayerEntity;      entity.networkOnClients()

            var nbt = entity.getStack(16).nbt ?: return
            nbt = nbt.getCompound(Base.MOD_NAME)

            entity.currentPath = nbt.getString("path")
            entity.isStream = Network.isValidUrl(entity.currentPath)

        }

    }

}

class MusicPlayerScreen( handler: MusicPlayerScreenHandler,
                         playerInv: PlayerInventory, text: Text
) : HandledScreen<MusicPlayerScreenHandler>( handler, playerInv, text ) {

    private val world = handler.world
    private val TEXTURE = Identifier("textures/gui/container/generic_54.png")
    private var optionsWidget = MusicPlayerWidget( 30, 15, 100, 20, handler )
    private var trustButton: ButtonWidget? = null

    init { backgroundHeight -= 10;     titleX += 55;     playerInventoryTitleY -= 10 }

    // This is run each time the window resizes
    override fun init() {

        addSelectableChild(optionsWidget)

        // Based dimensions
        val x = (width * 0.5 - width * 0.75 * 0.5).toInt()
        val y = (height * 0.08 * 1.5).toInt()
        val w = (width * 0.75).toInt()
        val h = (240 * 0.08).toInt()
        val w2 = (w * 0.35).toInt()

        fun createButton(
            x2: Float, y2: Float, w3:
            Float, func: (butt: ButtonWidget) -> Unit
        ): ButtonWidget {
            return ButtonWidget(
                (x + w * 0.5 + w2 * 0.05 + x2).toInt(),
                (y + h * 1.5 + y2).toInt(),
                (w2 + w3).toInt(),     (h * 1.1).toInt(),
                Text.of("")
            ) { func(it) }
        }

        val musicPlayer = world.getBlockEntity(handler.pos) as MusicPlayerEntity
        val switch = mutableMapOf( true to "On", false to "Off")
        val isTrusting = musicPlayer.isUserTrusting
        trustButton = createButton( - (w2 * 1.8).toFloat(), height * 0.65f, 10f ) {

            musicPlayer.isUserTrusting = !musicPlayer.isUserTrusting
            val isTrusting = musicPlayer.isUserTrusting

            val id = Identifier( Base.MOD_NAME, "add_or_remove_trusted_user" )
            val buf = PacketByteBufs.create().writeBlockPos( handler.pos )
            buf.writeBoolean(isTrusting)
            ClientPlayNetworking.send( id, buf )

            it.message = Text.of("Sync downloads: ${switch[isTrusting]}")

        }
        trustButton!!.message = Text.of("Sync downloads: ${switch[isTrusting]}")

        super.init()

    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        optionsWidget.mouseReleased(mouseX, mouseY, button)
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if ( trustButton!!.isHovered ) trustButton!!.mouseClicked(mouseX, mouseY, button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        optionsWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun drawBackground(matrices: MatrixStack?, delta: Float, mouseX: Int, mouseY: Int) {

        RenderSystem.setShader( GameRenderer::getPositionTexShader )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, TEXTURE )

        val w = backgroundWidth
        val backgroundHeight = backgroundHeight + 10
        val height = height + 10
        val centerX = ( width - w ) / 2
        val centerY = ( height - w ) / 2

        // Top slots
        drawTexture(matrices, centerX - 62, centerY + 5, 0, 0, w, 35)
        drawTexture(matrices, centerX + 71, centerY + 5, 7, 0, w - 7, 35)

        // Blank space
        drawTexture(matrices, centerX - 62, centerY + 40, 0, 4, w, 13)
        drawTexture(matrices, centerX + 71, centerY + 40, 7, 4, w - 7, 13)

        drawTexture(matrices, centerX - 62, centerY + 52, 0, 4, w, 13)
        drawTexture(matrices, centerX + 71, centerY + 52, 7, 4, w - 7, 13)
        drawTexture(matrices, centerX, centerY + 64, 0, 4, w, 2)

        // Music player slots bottom texture
        drawTexture(matrices, centerX - 62, centerY + 64, 0, backgroundHeight - 7 + 60, w - 113, 3)
        drawTexture(matrices, centerX + 71 + 102, centerY + 64, 7 + 102, backgroundHeight - 7 + 60, w - 7 - 102, 3)

        // Floppy disk slot
        drawTexture(matrices, centerX + 215, centerY + 44, 7, 17, 18, 18)

        // Player Inv
        drawTexture(matrices, centerX, centerY + 66, 0, 126, w, 128)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)
        drawMouseoverTooltip(matrices, mouseX, mouseY)

        val musicPlayer = world.getBlockEntity(handler.pos) as MusicPlayerEntity
        val floppyStack = musicPlayer.getStack(16)
        val path = floppyStack.orCreateNbt
            .getCompound(Base.MOD_NAME)
            .getString("path")

        var exists = path.isNotBlank() && !Network.isValidUrl(path)
        exists = exists || musicPlayer.isStream
        if ( floppyStack.item is FloppyDisk && exists ) {
            optionsWidget.render(matrices, mouseX, mouseY, delta)
        }

        trustButton!!.render(matrices, mouseX, mouseY, delta)

    }

    override fun isClickOutsideBounds(mouseX: Double, mouseY: Double, left: Int, top: Int,
                                      button: Int): Boolean {

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2

        val topX1 = centerX - backgroundWidth * 0.35f
        val topX2 = centerX + backgroundWidth * 1.35f

        val topY2 = centerY + backgroundHeight * 0.38f

        val b = mouseY < centerY || mouseY >= topY2
        val box1 = super.isClickOutsideBounds(mouseX, mouseY, left, top, button)
        val box2 = b || mouseX < topX1;     val box3 = b || mouseX > topX2
        return box1 && ( box2 || box3 )

    }

    override fun shouldPause(): Boolean { return false }

    companion object {

        fun register() {
            HandledScreens.register(MusicPlayerScreenHandler.type, ::MusicPlayerScreen)
        }

        class MusicPlayerWidget(
            x: Int, y: Int, w: Int, h: Int,
            private val handler: MusicPlayerScreenHandler,
        ) : SliderWidget(x, y, w, h, Text.of(""), 1.0 ) {

            var name = ""
            private var nbtName = "";       private var proportion = 2f;
            private val world = handler.playerInv.player.world
            private val entity = world.getBlockEntity(handler.pos) as MusicPlayerEntity
            private var stack = entity.getStack(16)
            private var notation = 0

            init { update() }

            override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double,
                deltaY: Double ): Boolean {
                if ( !isFocused ) return false
                return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
            }

            override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
                if ( !isFocused ) return false;   isFocused = false
                return super.mouseReleased(mouseX, mouseY, button)
            }

            override fun onClick(mouseX: Double, mouseY: Double) {
                if ( !visible ) return
                isFocused = true;   super.onClick(mouseX, mouseY)
            }

            override fun updateMessage() { message = Text.of("$name: $notation%") }

            override fun applyValue() {

                val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)
                val value = value * proportion

                var b = nbt.getFloat(nbtName).toDouble() != value
                b = b && nbtName.isNotBlank()

                if ( !stack.isEmpty && b ) {

                    val value = value.toFloat()

                    if ( nbtName == "Rate" ) entity.sequencer.tempoFactor = value

                    nbt.putFloat(nbtName, value)

                    val id = Identifier( Base.MOD_NAME, "musicplayer_slider_data" )
                    val buf = PacketByteBufs.create()
                    buf.writeBlockPos(entity.pos);      buf.writeFloat(value)
                    buf.writeString(nbtName)
                    ClientPlayNetworking.send(id, buf)

                }

                setTextNotation()

            }

            override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

                super.render(matrices, mouseX, mouseY, delta)

                val tempStack = handler.inv.getStack(16)

                val tempNbt = tempStack.orCreateNbt.getCompound(Base.MOD_NAME)
                val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)

                if ( nbt.getInt("id") != tempNbt.getInt("id") ) {
                    stack = tempStack;  update()
                }

                visible = !tempStack.isEmpty

                val hasStream = entity.isStream

                if ( !hasStream && nbtName != "Rate" ) {
                    nbtName = "Rate";      name = "BPM Rate"
                    proportion = 2f;        update()
                }

                if ( hasStream && nbtName != "Volume" ) {
                    nbtName = "Volume";      name = "Volume"
                    proportion = 1f;        update()
                }

            }

            private fun setTextNotation() { notation = (value * proportion * 100).roundToInt() }

            private fun update() {
                value = getChange() / proportion
                applyValue();    updateMessage()
            }

            private fun getChange(): Double {

                var change = 1.0
                val nbt = stack.orCreateNbt.getCompound(Base.MOD_NAME)

                if ( nbt!!.contains(nbtName) ) change = nbt.getFloat(nbtName).toDouble()

                return change

            }

        }

    }

}
