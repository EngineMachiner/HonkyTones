package com.enginemachiner.honkytones

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.sound.SoundCategory
import net.minecraft.text.*
import java.awt.Color
import javax.sound.midi.MidiSystem

class CustomSlider(
    x: Int, y: Int, w: Int, h: Int,
    private val name: String, float: Float,
) : SliderWidget(x, y, w, h, null, float.toDouble()) {

    init {
        val int = (value * 100).toInt()
        message = Text.of("$name: $int%")
    }

    override fun updateMessage() {
        val int = (value * 100).toInt()
        message = Text.of("$name: $int%")
    }

    override fun applyValue() {}

}

// Client screen
class Menu(private val stack: ItemStack) : Screen( LiteralText("HonkyTones") ) {

    private var sequenceField: TextFieldWidget? = null
    private var channelField: TextFieldWidget? = null

    private var clearButton: ButtonWidget? = null
    private var actionButton: ButtonWidget? = null
    private var deviceButton: ButtonWidget? = null
    private var centerNotesButton: ButtonWidget? = null

    private var volumeSlider: SliderWidget? = null

    private val inst = stack.item as Instrument
    private var nbt = stack.tag!!
    private val devices = MidiSystem.getMidiDeviceInfo()
    private val devicesNames = mutableSetOf<String>()
    private val actions = setOf("Attack","Play","Push")
    private val defName = inst.getName().string

    // Former itemTag values
    private var sequence = nbt.getString("Sequence")
    private var action = nbt.getString("Action")
    private var name = nbt.getString("MIDI Device")
    private var channel = nbt.getInt("MIDI Channel")
    private var volume = nbt.getFloat("Volume")
    private var shouldCenter = nbt.getBoolean("Center Notes")

    override fun onClose() {

        val v = volumeSlider!!.message.asString()
        val vol = v.filter { it.isDigit() }.toFloat()
        val channel = channelField!!.text

        nbt.putString("Sequence", sequenceField!!.text)
        inst.subsequence = sequenceField!!.text
        nbt.putString("Action", action)
        nbt.putString("MIDI Device", name)
        nbt.putInt("MIDI Channel", channel.toInt())
        nbt.putFloat("Volume", vol * 0.01f)
        nbt.putBoolean("Center Notes", shouldCenter)

        val s = "$defName - $name - $channel"
        stack.setCustomName( Text.of(s) )

        super.onClose()

    }

    private fun stringCut(s: String, lim: Int): String {
        if (s.length > lim) { return s.substring( 0, lim ) + "..." }
        return s
    }

    private fun <T: Any> next(value: T, col: Collection<T>): T {
        var i = col.indexOf(value) + 1;   if (i > col.size - 1) { i = 0 }
        return col.elementAt(i)
    }

    override fun init() {

        // Stop sounds in single-player
        if (client!!.isInSingleplayer) {
            val manager = client!!.soundManager
            val sounds = manager.keys.filter {
                it.namespace.contains("honkytones")
            }
            for (id in sounds) { manager.stopSounds(id, SoundCategory.PLAYERS) }
        }

        // Device names and set current device name
        for ( device in devices ) { devicesNames.add(device.name) }

        // Based dimensions
        val x = (width * 0.5 - width * 0.75 * 0.5).toInt()
        val y = (height * 0.08 * 1.5).toInt()
        val w = (width * 0.75).toInt()
        val h = (240 * 0.08).toInt()
        val w2 = (w * 0.35).toInt()

        // Button template creation function
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

        // Sequence field
        sequenceField = TextFieldWidget(textRenderer, x, y, w, h, Text.of(""))
        sequenceField!!.setMaxLength(32 * 5)
        sequenceField!!.text = sequence
        addSelectableChild(sequenceField)


        // Clear button
        clearButton = createButton( 0f, 0f, 0f ) { sequenceField!!.text = "" }
        clearButton!!.message = Text.of("Clear")
        addSelectableChild(clearButton)


        // Action button
        actionButton = createButton( - w2.toFloat(), 0f, 0f ) {
            action = next(action, actions)
            it.message = Text.of("Action: $action")
        }
        actionButton!!.message = Text.of("Action: $action")
        addSelectableChild(actionButton)


        // MIDI device button
        val charLim = ( w2 * 0.125f ).toInt()
        deviceButton = createButton( - w2.toFloat(), h * 1.25f, 0f ) {
            resetTween( tweenStack["deviceName"]!! )
            name = next(name, devicesNames)
            it.message = Text.of( stringCut( "Device: $name", charLim ) )
        }
        deviceButton!!.message = Text.of( stringCut( "Device: $name", charLim ) )
        addSelectableChild(deviceButton)


        // Channel field
        val w4 = w * 0.075f
        channelField = TextFieldWidget( textRenderer,
            (x + w4 - w4 * 0.5f + w2 * 1.875).toInt(),
            (y + 2.75 * h).toInt(),
            w4.toInt(), h,
            Text.of("")
        )
        channelField!!.setMaxLength(2)
        channelField!!.text = channel.toString()
        addSelectableChild(channelField)


        volumeSlider = CustomSlider(
            (x + w * 0.5 + w2 * 0.05 - w2 * 0.5f).toInt(),
            (y + h * 1.5 + h * 1.25f * 2f).toInt(),
            w2, (h * 1.1f).toInt(),
            "Volume", volume
        )
        addSelectableChild(volumeSlider)


        // Find missing notes the nearest range note
        val switch = mutableMapOf( true to "On", false to "Off")
        centerNotesButton = createButton( - w2 * 0.5f, h * 1.25f * 3f, 0f ) {
            shouldCenter = !shouldCenter
            it.message = Text.of("Center notes: ${switch[shouldCenter]}")
        }
        centerNotesButton!!.message = Text.of("Center notes: ${switch[shouldCenter]}")
        addSelectableChild(centerNotesButton)

    }

    private fun resetTween(list: MutableList<Int>) {
        for ( i in 0 until list.size) { list[i] = 0 }
    }

    // 1st -> incremental, 2nd -> frame-time
    private val tweenStack = mutableMapOf( "deviceName" to mutableListOf(0, 0) )
    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)

        sequenceField!!.render(matrices, mouseX, mouseY, delta)
        actionButton!!.render(matrices, mouseX, mouseY, delta)
        deviceButton!!.render(matrices, mouseX, mouseY, delta)
        channelField!!.render(matrices, mouseX, mouseY, delta)
        volumeSlider!!.render(matrices, mouseX, mouseY, delta)
        clearButton!!.render(matrices, mouseX, mouseY, delta)
        if (inst.name != "drumset") {
            centerNotesButton!!.render(matrices, mouseX, mouseY, delta)
        }

        // Channel input restrictions
        val caseOne = channelField!!.text.isBlank() && !channelField!!.isFocused
        val caseTwo = channelField!!.text.length == 1
                && channelField!!.text.contains(Regex("[^1-9]"))

        var caseThree = channelField!!.text.length == 2
        caseThree = caseThree && ( channelField!!.text[0] != '1'
                || channelField!!.text[1].toString().contains(Regex("[^0-6]")) )

        if ( caseOne || caseTwo || caseThree ) { channelField!!.text = "1" }

        // Subtitles
        textRenderer.draw(
            matrices, "Sequence:",
            sequenceField!!.x.toFloat(), sequenceField!!.y.toFloat() - 10,
            0xFFFFFF
        )

        textRenderer.draw(
            matrices, "Channel: ",
            channelField!!.x.toFloat() - 45, channelField!!.y.toFloat() + 5,
            0xFFFFFF
        )

        // Show device name on change
        val tween = tweenStack["deviceName"]!!
        if ( tween[1] < 400 ) {
            if (tween[0] < 255 && tween[1] > 200) tween[0]++
            val s = name; tween[1]++
            val color = Color( 255 - tween[0], 255 - tween[0], 255 - tween[0] )
            textRenderer.drawWithShadow(
                matrices, s,
                ( width * 0.5f - s.length * 2.5f ), height - 60f,
                color.rgb
            )
        }

    }

}

/*
// Shared screen (CLIENT-SERVER)
// These two belong to each other
// First create your type of screen, then the handler
val MENU: ScreenHandlerType<MenuScreenHandler> = ScreenHandlerRegistry.registerSimple(
    Identifier(Base.MOD_ID, "honkytones-menu")
) { syncID: Int, _: PlayerInventory? -> MenuScreenHandler(syncID) }
class MenuScreenHandler(syncId: Int) : ScreenHandler(MENU, syncId) {
    override fun canUse(player: PlayerEntity?): Boolean {
        return player!!.inventory.canPlayerUse(player)
    }
}
// Create my screen
class MenuScreen(
    handler: MenuScreenHandler?, inventory: PlayerInventory?, title: Text?
) : HandledScreen<MenuScreenHandler?>(handler, inventory, title) {
    override fun drawBackground(matrices: MatrixStack?, delta: Float, mouseX: Int, mouseY: Int) {}
}
// Create factory
class MenuScreenFactory : NamedScreenHandlerFactory {
    override fun createMenu(syncId: Int, inv: PlayerInventory?, player: PlayerEntity?): ScreenHandler {
        return MenuScreenHandler(syncId)
    }
    override fun getDisplayName(): Text { return LiteralText("HonkyTones") }
}
class Menu : ClientModInitializer {
    override fun onInitializeClient() {
        // Screens
        ScreenRegistry.register(MENU) {
                honkyTonesScreenHandler: MenuScreenHandler,
                playerInventory: PlayerInventory, text: Text ->
            MenuScreen(honkyTonesScreenHandler, playerInventory, text)
        }
    }
}
*/