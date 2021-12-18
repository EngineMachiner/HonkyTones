package com.enginemachiner.honkytones

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.sound.SoundCategory
import net.minecraft.text.*
import net.minecraft.util.Identifier
import javax.sound.midi.MidiSystem

class CustomSlider(
    x: Int, y: Int, w: Int, h: Int, t: Text, d: Double, f: Float
): SliderWidget(x, y, w, h, t, d) {

    private var v = f
    override fun updateMessage() {
        val int = (v * 100).toInt()
        message = Text.of("Volume: $int%")
    }

    override fun applyValue() { v = (value * 100).toInt() / 100f }

}

// Client screen

class Menu(private val inst: Instrument) : Screen( LiteralText("HonkyTones") ) {

    private var sequenceField: TextFieldWidget? = null
    private var clearButton: ButtonWidget? = null
    private var actionButton: ButtonWidget? = null
    private var deviceButton: ButtonWidget? = null
    private var channelField: TextFieldWidget? = null
    private var volumeSlider: SliderWidget? = null
    private var centerNotesButton: ButtonWidget? = null

    private val clientI = MinecraftClient.getInstance()
    private val tag = clientI.player!!.mainHandStack.orCreateNbt
    private val devices = MidiSystem.getMidiDeviceInfo()
    private val actions = setOf("Attack","Play","Push")
    private val sounds = setOf(
        getSoundInstance("honkytones:drumset-g4"),
        getSoundInstance("honkytones:keyboard-c3"),
        getSoundInstance("honkytones:trombone-g3")
    )
    private val clickSound = getSoundInstance("ui.button.click")

    // Temporal

    private var sequence = tag.getString("Sequence")
    private var action = tag.getString("Action")

    private var index = tag.getInt("MIDI Device Index")
    private var name = MidiSystem.getMidiDeviceInfo()[index].name

    private var channel = tag.getInt("MIDI Channel")

    private var volume = tag.getFloat("Volume")

    private var center = tag.getBoolean("Center Notes")

    override fun onClose() {

        // Send data to the server
        // I didn't want to use a shared screen for this

        val s = channelField!!.text
        channel = if (s.isNotEmpty()) s.toInt() else 1

        val buf = PacketByteBufs.create()

        buf.writeString("" + sequenceField!!.text)
        buf.writeString("" + action)

        buf.writeInt(index)
        buf.writeInt(channel)

        val vol = volumeSlider!!.message.asString().filter { it.isDigit() }
        buf.writeFloat( vol.toFloat() / 100f )

        buf.writeBoolean(center)

        ClientPlayNetworking.send( Identifier(netID + "client-screen"), buf )

        super.onClose()

    }

    private fun checkIndex(index: Int): Int {
        for ( (newIndex, info) in devices.withIndex() ) {
            if ( info.name == name ) { return newIndex }
        }
        if ( index > devices.size - 1 ) { return 0 }
        return index
    }

    override fun init() {

        // Stop sounds in singleplayer
        if ( client!!.isInSingleplayer ) {
            for (id in client!!.soundManager.keys) {
                if (id.namespace.contains("honkytones")) {
                    client!!.soundManager.stopSounds(id, SoundCategory.PLAYERS)
                }
            }
        }

        for ( sound in sounds ) {
            sound.volume = client!!.options.getSoundVolume(SoundCategory.PLAYERS)
        }
        clickSound.volume = client!!.options.getSoundVolume(SoundCategory.MASTER)


        val x = (width * 0.5 - width * 0.75 * 0.5).toInt()
        val y = (height * 0.08 * 1.5).toInt()
        val w = (width * 0.75).toInt()
        val h = (240 * 0.08).toInt()


        // Sequence field
        sequenceField = TextFieldWidget(
            textRenderer,
            x, y, w, h, Text.of("")
        )
        sequenceField!!.setMaxLength(32 * 5)
        sequenceField!!.text = sequence
        addSelectableChild(sequenceField)


        val w2 = (w * 0.35).toInt()
        fun getTemplate( x2: Float, y2: Float, w3: Float, func: (butt: ButtonWidget) -> Unit ): ButtonWidget {
            return ButtonWidget(
                (x + w * 0.5 + w2 * 0.05 + x2).toInt(),
                (y + h * 1.5 + y2).toInt(),
                (w2 + w3).toInt(),     (h * 1.1).toInt(),
                Text.of("")
            ) { func(it) }
        }

        // Clear button
        clearButton = getTemplate( 0f, 0f, 0f ) {
            sequenceField!!.text = ""
        }
        clearButton!!.message = Text.of("Clear")
        addSelectableChild(clearButton)


        // Action button
        var actionIndex = actions.indexOf( action )
        actionButton = getTemplate( - w2.toFloat(), 0f, 0f ) {
            actionIndex++
            if (actionIndex > actions.size - 1) { actionIndex = 0 }
            action = actions.elementAt(actionIndex)
            it.message = Text.of("Action: $action")
        }
        actionButton!!.message = Text.of("Action: $action")
        addSelectableChild(actionButton)


        // MIDI device
        var deviceIndex = tag.getInt("MIDI Device Index")
        deviceIndex = checkIndex(deviceIndex)
        var s: String
        deviceButton = getTemplate( - w2.toFloat(), h * 1.25f, 0f ) {

            cooldownMap["device"] = cooldownDefault["device"]!!

            deviceIndex++
            if (deviceIndex > devices.size - 1) { deviceIndex = 0 }

            index = deviceIndex
            name = MidiSystem.getMidiDeviceInfo()[index].name

            s = "Device: " + devices[deviceIndex].name
            val range = w2 * 0.125f
            if (s.length > range) { s = s.substring( 0, range.toInt() ) + "..." }
            it.message = Text.of(s)

        }

        s = "Device: " + devices[deviceIndex].name
        val range = w2 * 0.125f
        if (s.length > range) { s = s.substring( 0, range.toInt() ) + "..." }
        deviceButton!!.message = Text.of(s)

        addSelectableChild(deviceButton)


        // Channel field
        val w4 = w * 0.075f
        channelField = TextFieldWidget(
            textRenderer,
            (x + w4 - w4 * 0.5f + w2 * 1.875).toInt(),
            (y + 2.75 * h).toInt(), w4.toInt(), h, Text.of("")
        )
        channelField!!.setMaxLength(2)
        channelField!!.text = channel.toString()
        addSelectableChild(channelField)


        val int = (volume * 100).toInt()
        volumeSlider = CustomSlider(
            (x + w * 0.5 + w2 * 0.05 - w2 * 0.5f).toInt(),
            (y + h * 1.5 + h * 1.25f * 2f).toInt(),
            w2, (h * 1.1f).toInt(), Text.of("Volume: $int%"), volume.toDouble(), volume
        )
        addSelectableChild(volumeSlider)


        // Tweak notes out of range
        centerNotesButton = getTemplate( - w2 * 0.5f, h * 1.25f * 3f, 0f ) {
            center = !center
            val string = if (center) { "On" } else "Off"
            it.message = Text.of("Center notes: $string")
        }
        val string = if (center) { "On" } else "Off"
        centerNotesButton!!.message = Text.of("Center notes: $string")
        addSelectableChild(centerNotesButton)


        cooldownMap = cooldownDefault.toMutableMap()
    }

    private val cooldownDefault = mutableMapOf( "device" to 200f )
    private var cooldownMap = cooldownDefault.toMutableMap()

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)

        sequenceField!!.render(matrices, mouseX, mouseY, delta)
        actionButton!!.render(matrices, mouseX, mouseY, delta)
        deviceButton!!.render(matrices, mouseX, mouseY, delta)
        channelField!!.render(matrices, mouseX, mouseY, delta)
        volumeSlider!!.render(matrices, mouseX, mouseY, delta)
        if (inst.instrumentName != "drumset") {
            centerNotesButton!!.render(matrices, mouseX, mouseY, delta)
        }

        val caseOne = channelField!!.text.isBlank() && !channelField!!.isFocused
        val caseTwo = channelField!!.text.length == 1 && channelField!!.text.contains(Regex("[^1-9]"))

        var caseThree = channelField!!.text.length == 2
        caseThree = caseThree && ( channelField!!.text[0] != '1' || channelField!!.text[1].toString().contains(Regex("[^0-6]")) )

        if ( caseOne || caseTwo || caseThree ) { channelField!!.text = "1" }

        textRenderer.draw(
            matrices, "Sequence:",
            sequenceField!!.x.toFloat(), sequenceField!!.y.toFloat() - 10,
            0xFFFFFF
        )

        clearButton!!.render(matrices, mouseX, mouseY, delta)

        textRenderer.draw(
            matrices, "Channel: ",
            channelField!!.x.toFloat() - 45, channelField!!.y.toFloat() + 5,
            0xFFFFFF
        )

        // Show device
        var n = cooldownMap["device"]!!
        if ( n > 0 ) {
            val n2 = n + 10
            n -= 0.25f;    cooldownMap["device"] = n
            index = checkIndex(index)
            val s = devices[index].name
            textRenderer.drawWithShadow(
                matrices, s,
                ( width * 0.5f - s.length * 2.5f ), height - 60f,
                (0xFFFFFF * n2 * 5).toInt()
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