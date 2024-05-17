package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.harmony.*
import com.enginemachiner.honkytones.CanBeMuted.Companion.isMuted
import com.enginemachiner.honkytones.blocks.musicplayer.MusicPlayerBlockEntity.Companion.INVENTORY_SIZE
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import kotlin.math.roundToInt

class MusicPlayerScreenHandler(

    syncID: Int,    private val playerInventory: PlayerInventory,

    private val inventory: Inventory,       private val context: ScreenHandlerContext

) : HarmonyScreenHandler( type, syncID ) {


    var pos: BlockPos? = null


    private val player = playerInventory.player


    constructor( syncID: Int, playerInventory: PlayerInventory, buf: PacketByteBuf ) : this( syncID, playerInventory, SimpleInventory(INVENTORY_SIZE), ScreenHandlerContext.EMPTY ) { pos = buf.readBlockPos() }


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

        if ( stack.item !is Instrument ) return false

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

    override fun transferSlot( player: PlayerEntity, slotIndex: Int ): ItemStack {

        val slot = slots[slotIndex];        val stack = slot.stack;         val isEmpty = stack.isEmpty


        if (isEmpty) return ItemStack.EMPTY


        val onPlayer = insertInstrument(slotIndex) || insertFloppy(slotIndex)
        val success = onPlayer || insertAny(slotIndex)

        if ( !success ) return ItemStack.EMPTY

        if ( onPlayer && !player.world.isClient ) ( inventory as MusicPlayerBlockEntity ).read()

        slot.markDirty();       return stack.copy()

    }

    /** Place instruments and floppy disks only and move inventory freely. */
    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity ) {

        val size = inventory.size();        val onSlots = slotIndex < size

        val onInstrumentSlots = slotIndex > 0 && slotIndex < size - 1

        val onFloppySlot = slotIndex == 0


        fun click() { super.onSlotClick(slotIndex, button, actionType, player) }

        fun canPickUp(): Boolean {

            return canPickUp(onInstrumentSlots) { it.item is Instrument }
                    || canPickUp(onFloppySlot) { it.item is FloppyDisk }
                    || !onSlots

        }

        fun onSwap(): Boolean {

            if ( actionType != SlotActionType.SWAP ) return true

            val canSwap = onInstrumentSlots && canSwap(button, slotIndex) { it.item is Instrument }
                    || onFloppySlot && canSwap(button, slotIndex) { it.item is FloppyDisk }

            return canSwap && onSlots || !onSlots

        }


        // slotIndex < 0 are used for networking internals.

        if ( slotIndex < 0 ) { click(); return }


        if ( !canPickUp() || !onSwap() ) return

        click()

    }

    override fun canUse(player: PlayerEntity): Boolean {

        return canUse( context, player, MusicPlayerBlock.registryBlock )

    }

    companion object: ModID {

        val type = ExtendedScreenHandlerType { id, inventory, buf ->

            MusicPlayerScreenHandler( id, inventory, buf )

        }

        fun register() {

            Registry.register( Registry.SCREEN_HANDLER, classID(), type )

            if ( !isClient() ) return

            HandledScreens.register( type, ::MusicPlayerScreen )

        }

    }

}

// @Environment(EnvType.CLIENT)
class MusicPlayerScreen(

    handler: MusicPlayerScreenHandler,      playerInventory: PlayerInventory,       text: Text

) : HarmonyHandledScreen<MusicPlayerScreenHandler>( handler, playerInventory, text ), ScreenRefresher {

    private var widgetWidth = 0f

    init { titleY -= 9;     playerInventoryTitleY += 9 }

    private val texture = Texture( textureID ) {

        it.setSize( 176f, 186f );       it.center(width, height)

    }

    private val pos = handler.pos;          private val world = client().world!!

    private val blockEntity = world.getBlockEntity(pos) as MusicPlayerBlockEntity
    private val musicPlayer = MusicPlayer.get( blockEntity.id )

    private var listenButton: Button? = null
    private var repeatButton: Button? = null
    private var volumeSlider: VolumeSlider? = null

    private val states = mutableMapOf( true to Translations.on,     false to Translations.off )

    private fun isListening(): Boolean { return blockEntity.isListening }

    private fun listenMessage(): String {

        val title = Translations.listen;        val value = states[ isListening() ]

        return "$title: $value"

    }

    private fun onRepeat(): Boolean { return blockEntity.onRepeat }

    private fun repeatMessage(): String {

        val title = Translations.repeat;        val value = states[ onRepeat() ]

        return "$title: $value"

    }

    override fun shouldPause(): Boolean { return false }

    override fun init() {

        super.init();       texture.init()


        widgetWidth = width * 0.2f;        val w = widgetWidth


        var x = width - texture.w - w - 35

        x *= 0.5f;      val h = 20f

        volumeSlider = VolumeSlider( x, h * 2f, w, h, musicPlayer )

        listenButton = Button( x, h * 3.5f, w, h, ::listenMessage ) {

            blockEntity.isListening = !isListening()

            blockEntity.updateState( "set_user_state", isListening() )

            it.updateMessage();     updateHandledScreens()

        }

        repeatButton = Button( x, h * 5f, w, h, ::repeatMessage ) {

            blockEntity.onRepeat = !onRepeat()

            blockEntity.updateState( "set_repeat", onRepeat() )

            updateHandledScreens();     it.updateMessage()

        }

        val widgets = listOf( listenButton, repeatButton )

        widgets.forEach { addDrawableChild(it) }

        addSlider( volumeSlider!! )

    }

    override fun refresh() {

        listenButton!!.updateMessage()

    }

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        texture.draw(matrices)

    }

    private fun shouldListen() {

        val shouldListen = MusicPlayerBlockEntity.shouldListen

        if ( !shouldListen || isListening() ) return

        MusicPlayerBlockEntity.shouldListen = false

        listenButton!!.onPress()

    }
    override fun handledScreenTick() {

        val volumeSlider = volumeSlider ?: return

        volumeSlider.tick();      shouldListen()

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean {

        return texture.isClickOutsideBounds(mouseX, mouseY)

    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {

        return isFocusedDragged(mouseX, mouseY, button, deltaX, deltaY)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)

    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {

        return wasElementDragged(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button)

    }

    companion object {

        private val textureID = textureID("block/music_player/slots.png")

        private object Translations {

            val on = Translation.get("gui.on")
            val off = Translation.get("gui.off")

            val volume = Translation.item("gui.volume")
            val listen = Translation.block("music_player.listen")

            val repeat = Translation.block("music_player.repeat")

        }

        private class VolumeSlider(

            x: Float, y: Float,     w: Float, h: Float,

            private val musicPlayer: MusicPlayer,

        ) : Slider( x, y, w, h ) {

            init { init() }

            private fun init() {

                visible = false;        if ( !visible() ) return

                value = volume();       updateMessage()

            }

            private fun visible(): Boolean {

                val entity = musicPlayer.blockEntity!!.entity!!

                return musicPlayer.hasInput() && !isMuted(entity)

            }

            private fun volume(): Double { return settings().getDouble("Volume") }

            private fun nbt(): NbtCompound { return NBT.get( floppy() ) }

            private fun settings(): NbtCompound {

                return FloppyDisk.settings( floppy(), player() )

            }

            private fun floppy(): ItemStack { return musicPlayer.item(0) }

            fun tick() { visible = visible();        check() }

            private fun check() {

                if ( !visible || value == volume() ) return

                value = volume();       updateMessage()

            }

            override fun applyValue() {

                settings().putDouble( "Volume", value )

                NBT.sendNBT( nbt() )

            }

            override fun format( value: Double ): String {

                val title = Translations.volume

                val i = ( value * 100 ).roundToInt()

                return "$title: $i%"

            }

        }

    }

}
