package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Base.Companion.paths
import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.Instrument
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.client.gui.widget.CheckboxWidget
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.util.InputUtil
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
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import java.awt.Color
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequence
import javax.sound.midi.ShortMessage

class DigitalConsoleScreenHandler( syncID: Int, playerInv: PlayerInventory,
                                   inv: Inventory ) : ScreenHandler( type, syncID ) {

    constructor( syncID: Int, playerInv: PlayerInventory )
            : this( syncID, playerInv, SimpleInventory(1) )

    constructor( stack: ItemStack, syncID: Int, playerInv: PlayerInventory )
            : this( syncID, playerInv, CustomInventory( stack, 1 ) )

    private val player = playerInv.player!!
    private val world = player.world
    val consoleStack: ItemStack = player.handItems.find { it.item is DigitalConsole }!!

    init {

        if ( !playerInv.contains( inv.getStack(0) ) ) {
            inv.setStack(0, ItemStack.EMPTY);       inv.markDirty()
        }

        checkSize( inv, inv.size() )
        inv.onOpen( player )

        val slot = Slot( inv, 0, 220, 75 * 2 + 10 )
        addSlot( slot )

    }

    override fun onClosed(player: PlayerEntity?) {

        if ( world.isClient ) {

            val stack = CustomInventory(consoleStack, 1)
                .getStack(0)

            if ( !stack.isEmpty ) {
                stack.holder = player
                val instrument = stack.item as Instrument
                instrument.stopAllNotes(stack, world)
            }

        }

        super.onClosed(player)

    }

    override fun canUse(player: PlayerEntity?): Boolean { return true }

    override fun quickMove(player: PlayerEntity?, index: Int): ItemStack { return slots[index].stack }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType?,
                              player: PlayerEntity? ) {

        val screenTitle = Translation.get("item.honkytones.digitalconsole.select")
        val screenFactory = SimpleNamedScreenHandlerFactory( {
                syncID: Int, playerInv: PlayerInventory, _: PlayerEntity ->
                PickStackScreenHandler( consoleStack, syncID, playerInv )
        }, Text.of(screenTitle) )

        player!!.openHandledScreen( screenFactory )

    }

    companion object {

        private val id = Identifier( Base.MOD_NAME, "digitalconsole" )
        lateinit var type: ScreenHandlerType<DigitalConsoleScreenHandler>

        fun register() {
            type = ScreenHandlerType( ::DigitalConsoleScreenHandler, FeatureFlags.VANILLA_FEATURES )
            Registry.register( Registries.SCREEN_HANDLER, id, type )
        }

    }

}

@Environment(EnvType.CLIENT)
class DigitalConsoleScreen( handler: DigitalConsoleScreenHandler,
                            playerInv: PlayerInventory, title: Text
) : HandledScreen<DigitalConsoleScreenHandler>( handler, playerInv, title ) {

    private val player = playerInv.player
    private var consoleNbt = handler.consoleStack.nbt!!.getCompound(Base.MOD_NAME)

    var fileName = "";          var channel = 1
    var recordCheckbox: CheckboxWidget? = null;     var timeStamp: Float = -1f
    var willRecord = false;     var isRecording = false

    private val keybinds = mutableSetOf(
        C_KeyBind, Db_KeyBind,  D_KeyBind, Eb_KeyBind, E_KeyBind, F_KeyBind, Gb_KeyBind,
        G_KeyBind, Ab_KeyBind, A_KeyBind, Bb_KeyBind, B_KeyBind
    )

    private val noteKeys = mutableMapOf(
        "c" to false, "db" to false, "d" to false, "eb" to false,
        "e" to false, "f" to false, "gb" to false, "g" to false,
        "ab" to false, "a" to false, "bb" to false, "b" to false
    )

    init { playerInventoryTitleY -= 1000;      titleY += 12 }

    override fun init() {

        val recordTitle = Translation.get("item.honkytones.digitalconsole.record")
        recordCheckbox = CheckboxWidget(
            25, height - 100,
            20, 20,
            Text.of(recordTitle), false
        )

        super.init()

    }

    override fun close() {
        if ( recordCheckbox!!.isChecked ) stopRecording()
        super.close()
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        recordCheckbox!!.mouseClicked(mouseX, mouseY, button)
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun shouldPause(): Boolean { return false }

    override fun drawBackground( matrices: MatrixStack?, delta: Float, mouseX: Int,
                                 mouseY: Int ) {

        RenderSystem.setShader(GameRenderer::getPositionTexProgram)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.setShaderTexture(0, TEXTURE)

        var centerX = (width - 18) / 2;         var centerY = (height - 18) / 2

        drawTexture(matrices, centerX + 140, centerY + 85, 7, 17, 18, 18)

        drawTexture(matrices, centerX + 140, centerY + 79, 4, 0, 18, 6)
        drawTexture(matrices, centerX + 140, centerY + 103, 4, 216, 18, 6)

        drawTexture(matrices, centerX + 134, centerY + 79, 0, 0, 6, 24)
        drawTexture(matrices, centerX + 158, centerY + 79, 170, 0, 6, 24)

        drawTexture(matrices, centerX + 134, centerY + 103, 0, 216, 6, 18)
        drawTexture(matrices, centerX + 158, centerY + 103, 170, 216, 6, 18)

        centerX = ( width - 256 ) / 2;        centerY = ( height - 256 ) / 2

        RenderSystem.setShaderTexture(0, CONSOLE_BACK_TEX)
        drawTexture(matrices, centerX, centerY, 0, 0, 256, 256)

        centerX = ( width - 64 ) / 2;         centerY = ( height - 64 ) / 2

        renderNoteButton( FIRST_KEY_TEX, noteKeys["c"], matrices, centerX - 83, centerY + 23, 64, 64 )

        val flatsCenterX = ( width - 32 ) / 2
        val flatsCenterY = ( height - 32 ) / 2

        renderNoteButton( FLAT_TEX, noteKeys["db"], matrices, flatsCenterX - 68, flatsCenterY + 7, 32, 32 )
        renderNoteButton( MIDDLE_KEY_TEX, noteKeys["d"], matrices, centerX - 55, centerY + 23, 64, 64 )
        renderNoteButton( FLAT_TEX, noteKeys["eb"], matrices, flatsCenterX - 41, flatsCenterY + 7, 32, 32 )
        renderNoteButton( LAST_KEY_TEX, noteKeys["e"], matrices, centerX - 27, centerY + 23, 64, 64 )
        renderNoteButton( LAST_KEY_FLIP_TEX, noteKeys["f"], matrices, centerX + 1, centerY + 23, 64, 64 )

        val list = mutableListOf( noteKeys["gb"], noteKeys["ab"], noteKeys["bb"] )
        for ( i in 0..2 ) renderNoteButton( FLAT_TEX, list[i], matrices, flatsCenterX + 15 + 28 * i, flatsCenterY + 7, 32, 32 )

        renderNoteButton( MIDDLE_KEY_TEX, noteKeys["g"], matrices, centerX + 29, centerY + 23, 64, 64 )
        renderNoteButton( MIDDLE_KEY_TEX, noteKeys["a"], matrices, centerX + 57, centerY + 23, 64, 64 )
        renderNoteButton( FIRST_KEY_FLIP_TEX, noteKeys["b"], matrices, centerX + 85, centerY + 23, 64, 64 )

    }

    @Verify("MIDI Timing on record")
    override fun keyPressed( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {

        for ( keybind in keybinds ) {
            if ( keybind.matchesKey(keyCode, scanCode) ) {

                val index = keybinds.indexOf(keybind)
                val key = noteKeys.keys.elementAt(index)

                if ( noteKeys[key]!! ) break

                noteKeys[key] = true

                val stack = handler.stacks[0];      val item = stack.item
                if ( item is Instrument ) {

                    val octave = consoleNbt.getInt("Octave")
                    val sounds = item.getSounds(stack, "notes")

                    // 60 is C4
                    var i = ( 60 + index ) + 12 * ( octave - 4 )
                    i = item.getIndexIfCentered(stack, i);    if ( i == -1 ) break

                    // Could this cause nbt async
                    val sound = sounds[i] ?: break
                    val nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
                    nbt.putBoolean("isOnUse", true)

                    stack.holder = player
                    sound.playSound(stack)
                    nbt.putBoolean("isOnUse", false)

                    if ( canRecord() ) {

                        val channel = channel - 1
                        val transmitters = sequencer!!.transmitters
                        val receiver = transmitters[0].receiver

                        val volume = 127 * nbt.getFloat("Volume")

                        val message = ShortMessage()
                        message.setMessage( ShortMessage.NOTE_ON, channel, i, volume.toInt() )

                        receiver.send( message, sequencer.microsecondPosition / 1750000 )

                    }

                }

            }
        }

        if ( octUp_KeyBind.matchesKey(keyCode, scanCode) ) {
            val oct = consoleNbt.getInt("Octave") + 1
            if (oct < 8) consoleNbt.putInt("Octave", oct)
        }

        if ( octDown_KeyBind.matchesKey(keyCode, scanCode) ) {
            val oct = consoleNbt.getInt("Octave") - 1
            if (oct > -2) consoleNbt.putInt("Octave", oct)
        }

        return super.keyPressed(keyCode, scanCode, modifiers)

    }

    private fun canRecord(): Boolean {
        return isRecording && sequencer != null
    }

    @Verify("MIDI Timing on record")
    override fun keyReleased( keyCode: Int, scanCode: Int, modifiers: Int ): Boolean {

        for ( keybind in keybinds ) {
            if ( keybind.matchesKey( keyCode, scanCode ) ) {

                val index = keybinds.indexOf(keybind)
                val key = noteKeys.keys.elementAt(index)

                noteKeys[key] = false

                val stack = handler.stacks[0];      val item = stack.item
                if ( item is Instrument ) {

                    val octave = consoleNbt.getInt("Octave")
                    val sounds = item.getSounds(stack, "notes")

                    // 60 is C4
                    var i = ( 60 + index ) + 12 * ( octave - 4 )
                    i = item.getIndexIfCentered(stack, i);    if (i == -1) break

                    val sound = sounds[i] ?: break

                    if ( item !is DrumSet ) sound.stopSound(stack)

                    if ( canRecord() ) {

                        val channel = channel - 1
                        val transmitters = sequencer!!.transmitters
                        val receiver = transmitters[0].receiver

                        val message = ShortMessage()
                        message.setMessage( ShortMessage.NOTE_OFF, channel, i, 0 )

                        receiver.send( message, sequencer.microsecondPosition / 1750000 )

                    }

                }

            }
        }

        return super.keyReleased(keyCode, scanCode, modifiers)

    }

    override fun render( matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)

        val octaveTitle = Translation.get("item.honkytones.gui.octave")
        val octaveString = "$octaveTitle: " + consoleNbt.getInt("Octave")
        textRenderer.draw(matrices, octaveString, width * 0.5f + 10, height * 0.5f - 65, Color(1f, 1f, 1f).rgb )

        val recordCheckbox = recordCheckbox!!
        recordCheckbox.renderButton(matrices, mouseX, mouseY, delta)

        if ( recordCheckbox.isChecked && !willRecord ) {

            if ( !hasMidiSystemSequencer() ) {
                recordCheckbox.onPress();       return
            }

            // Release all keys
            for ( entry in noteKeys ) entry.setValue(false)            
            
            client!!.setScreen( RecordingOptionsScreen(this) )
            willRecord = true;      timeStamp = 0f

        }

        if ( canRecord() ) {
            sequencer!!.tickPosition++
            if ( !recordCheckbox.isChecked ) stopRecording()

        }

        if ( timeStamp >= 0 && canRecord() ) {
            // 1000 equivalent to second ticks
            val timeStamp = sequencer!!.tickPosition
            val minutes = timeStamp / ( 300f * 60 )
            val seconds = ( timeStamp / 300f ) % 60
            val stringFormat = String.format( "%d:%02d", minutes.toInt(), seconds.toInt() )
            textRenderer.draw(matrices, stringFormat, width * 0.5f + 3, height * 0.5f - 49, Color(1f, 1f, 1f).rgb )
        }

    }

    private fun stopRecording() {

        isRecording = false;        willRecord = false;     timeStamp = -1f

        if ( canRecord() ) {
            sequencer!!.stop();       sequencer.close()
        }

        try {

            val file = RestrictedFile( paths["midis"]!!.path + "/$fileName" )
            MidiSystem.write( sequence, 0, file )

            val s = Translation.get("honkytones.message.file_written")
                .replace("X", fileName)

            printMessage(s)

        } catch ( e: Exception ) {
            printMessage( Translation.get("honkytones.error.file_written") )
            printMessage( Translation.get("honkytones.message.check_console") )
            e.printStackTrace()
        }

        // Clean the recording
        if ( canRecord() ) {
            sequencer!!.sequence = sequence
            sequencer.tickPosition = -1
        }

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean { return false }

    private fun renderNoteButton(
        textureID: Identifier, keyBindBool: Boolean?, matrices: MatrixStack?,
        x: Int, y: Int, w: Int, h: Int
    ) {

        RenderSystem.setShaderTexture( 0, textureID )
        if ( keyBindBool!! ) RenderSystem.setShaderColor(0.25f, 1f, 0.25f, 1f)
        drawTexture( matrices, x, y, 0f, 0f, w, h, w, h )
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

    }

    companion object {

        private val PATH = Base.MOD_NAME + ":textures/item/console/"
        private val TEXTURE = Identifier("textures/gui/container/generic_54.png")
        private val CONSOLE_BACK_TEX = Identifier(PATH + "back.png")
        private val FIRST_KEY_TEX = Identifier(PATH + "0.png")
        private val MIDDLE_KEY_TEX = Identifier(PATH + "1.png")
        private val LAST_KEY_TEX = Identifier(PATH + "2.png")
        private val LAST_KEY_FLIP_TEX = Identifier(PATH + "3.png")
        private val FIRST_KEY_FLIP_TEX = Identifier(PATH + "4.png")
        private val FLAT_TEX = Identifier(PATH + "flat.png")

        val sequencer = RecordingOptionsScreen.sequencer
        var sequence: Sequence? = null

        lateinit var octUp_KeyBind: KeyBinding;        lateinit var octDown_KeyBind: KeyBinding

        lateinit var C_KeyBind: KeyBinding
        lateinit var Db_KeyBind: KeyBinding;        lateinit var D_KeyBind: KeyBinding
        lateinit var Eb_KeyBind: KeyBinding;        lateinit var E_KeyBind: KeyBinding
        lateinit var F_KeyBind: KeyBinding
        lateinit var Gb_KeyBind: KeyBinding;        lateinit var G_KeyBind: KeyBinding
        lateinit var Ab_KeyBind: KeyBinding;        lateinit var A_KeyBind: KeyBinding
        lateinit var Bb_KeyBind: KeyBinding;        lateinit var B_KeyBind: KeyBinding

        init {
            if ( sequencer != null ) sequence = sequencer.sequence
        }

        fun registerKeyBindings() {

            var category = Base.MOD_NAME + ".digitalconsole"
            category = "category.$category"

            val key = "key." + Base.MOD_NAME

            val map = mutableMapOf<String, KeyBinding>()
            for ( name in NoteData.octave ) {

                val s = name.lowercase().replace("_","-flat")
                val key = "$key.play-$s"
                val keybind = KeyBinding(
                    key,      InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,        category
                )

                map[name] = KeyBindingHelper.registerKeyBinding( keybind )

            }

            octUp_KeyBind = KeyBindingHelper.registerKeyBinding( KeyBinding(
                "$key.octave-up",      InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,        category
            ) )

            octDown_KeyBind = KeyBindingHelper.registerKeyBinding( KeyBinding(
                "$key.octave-down",      InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,        category
            ) )

            C_KeyBind = map.values.elementAt(0)
            Db_KeyBind = map.values.elementAt(1);   D_KeyBind = map.values.elementAt(2)
            Eb_KeyBind = map.values.elementAt(3);   E_KeyBind = map.values.elementAt(4)
            F_KeyBind = map.values.elementAt(5)
            Gb_KeyBind = map.values.elementAt(6);   G_KeyBind = map.values.elementAt(7)
            Ab_KeyBind = map.values.elementAt(8);   A_KeyBind = map.values.elementAt(9)
            Bb_KeyBind = map.values.elementAt(10);  B_KeyBind = map.values.elementAt(11)

        }

        fun register() {
            HandledScreens.register(DigitalConsoleScreenHandler.type, ::DigitalConsoleScreen)
            PickStackScreen.register()
        }

    }

}

class PickStackScreenHandler( syncID: Int, playerInv: PlayerInventory ) : ScreenHandler( type, syncID ) {

    constructor( stack: ItemStack, syncID: Int, playerInv: PlayerInventory )
            : this( syncID, playerInv ) {
        this.stack = stack;         console = stack.item as DigitalConsole
    }

    // Use defaults
    private var stack = ItemStack.EMPTY
    private var console = getRegisteredItem("digitalconsole") as DigitalConsole

    init {

        val w = 18;     val x = 8;      val y = w * 6 - 21

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

    override fun canUse(player: PlayerEntity?): Boolean { return true }

    override fun quickMove(player: PlayerEntity?, index: Int): ItemStack { return ItemStack.EMPTY }

    override fun onSlotClick( slotIndex: Int, button: Int, actionType: SlotActionType?, player: PlayerEntity? ) {

        if (slotIndex < 0) return

        val stack = slots[slotIndex].stack
        if (stack.item is Instrument) {

            val inv = CustomInventory(this.stack, 1)
            inv.setStack(0, stack);     inv.markDirty()

            onClosed(player)

            val screen = console.createMenu(this.stack)
            player!!.openHandledScreen(screen)

        }

    }

    companion object {

        private val id = Identifier(Base.MOD_NAME, "digitalconsole_pick")
        lateinit var type: ScreenHandlerType<PickStackScreenHandler>

        fun register() {
            type = ScreenHandlerType( ::PickStackScreenHandler, FeatureFlags.VANILLA_FEATURES )
            Registry.register( Registries.SCREEN_HANDLER, id, type )
        }

    }

}

@Environment(EnvType.CLIENT)
class PickStackScreen( handler: PickStackScreenHandler, playerInv: PlayerInventory, title: Text )
    : HandledScreen<PickStackScreenHandler>(handler, playerInv, title) {

    init {
        titleY += 17
        playerInventoryTitleY -= 1000;      backgroundHeight -= 20
    }

    override fun shouldPause(): Boolean { return false }

    override fun drawBackground(matrices: MatrixStack?, delta: Float, mouseX: Int, mouseY: Int) {

        RenderSystem.setShader( GameRenderer::getPositionTexProgram )
        RenderSystem.setShaderColor( 1f, 1f, 1f, 1f )
        RenderSystem.setShaderTexture( 0, TEXTURE )

        val centerX = ( width - backgroundWidth ) / 2
        val centerY = ( height - backgroundHeight ) / 2
        val w = backgroundWidth;        val h = 6 * 18 + 17

        drawTexture(matrices, centerX, centerY + 20, 0, 126, w, h)
        drawTexture(matrices, centerX, centerY + 18, 0, 0, w, 3)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)
        drawMouseoverTooltip(matrices, mouseX, mouseY)
    }

    companion object {

        private val TEXTURE = Identifier("textures/gui/container/generic_54.png")

        fun register() { HandledScreens.register(PickStackScreenHandler.type, ::PickStackScreen) }

    }

}
