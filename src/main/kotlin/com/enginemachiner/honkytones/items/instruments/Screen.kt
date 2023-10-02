package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import com.enginemachiner.honkytones.NBT.networkNBT
import com.enginemachiner.honkytones.NBT.keepDisplay
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.Clipboard
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import java.awt.Color
import javax.sound.midi.MidiSystem

@Environment(EnvType.CLIENT)
class InstrumentsScreen( private val stack: ItemStack ) : Screen( Text.of("Instrument Screen") ) {

    private var sequenceField: TextFieldWidget? = null
    private var channelField: MidiChannelField? = null

    private var clearButton: ButtonWidget? = null

    private var actionButton: ButtonWidget? = null
    private var deviceButton: ButtonWidget? = null
    private var centerNotesButton: ButtonWidget? = null
    private var copyButton: ButtonWidget? = null

    private var volumeSlider: SliderWidget? = null

    //

    private val devices = MidiSystem.getMidiDeviceInfo()
    private val devicesNames = mutableSetOf("None")

    private val actions = mutableSetOf( "Melee", "Push" )

    //

    private var nbt = NBT.get(stack)
    private var sequence = nbt.getString("Sequence")
    private var action = nbt.getString("Action")
    private var deviceName = nbt.getString("MIDI Device")
    private var channel = nbt.getInt("MIDI Channel")
    private var volume = nbt.getFloat("Volume")
    private var shouldCenter = nbt.getBoolean("Center Notes")

    private val instrument = stack.item as Instrument
    private val instrumentName = instrument.name.string

    override fun shouldPause(): Boolean { return false }

    override fun init() {

        // Read the devices names and set initial device name.

        readDevices();      checkDeviceName()

        // Trombone thrust.

        if ( instrument is Trombone ) actions.add("Thrust")


        // Ranged enchantment.

        stack.enchantments.forEach {

            it as NbtCompound

            if ( it.getString("id") != "$MOD_NAME:ranged" ) return@forEach

            actions.add("Ranged")

        }

        val x = ( width * 0.125f ).toInt();     val y = ( height * 0.16f * 1.5f ).toInt()

        val w = ( width * 0.75f ).toInt();      val h = ( 240 * 0.08f ).toInt()

        val w2 = ( w * 0.35f ).toInt()


        // Sequence field.

        sequenceField = TextFieldWidget( textRenderer, x, y, w, h, Text.of(sequence) )

        sequenceField!!.setMaxLength(400);      sequenceField!!.text = sequence

        addSelectableChild(sequenceField)


        // Clear button.

        val clearTitle = Translation.item("gui.clear")

        clearButton = createButton( x, y, 0f, 0f, w, h, w2, 0f ) { sequenceField!!.text = "" }

        clearButton!!.message = Text.of(clearTitle);    addSelectableChild(clearButton)


        // Action button.

        val actionTitle = Translation.item("gui.action")

        val actionValues = mapOf(
            "Melee" to Translation.item("gui.melee"),
            "Push" to Translation.item("gui.push"),
            "Thrust" to Translation.item("gui.thrust"),
            "Ranged" to Translation.item("gui.ranged")
        )

        var translation = actionValues[action]

        actionButton = createButton( x, y, - w2.toFloat(), 0f, w, h, w2, 0f ) {

            action = cycle( action, actions )

            translation = actionValues[action]

            it.message = Text.of("$actionTitle: $translation")

        }

        actionButton!!.message = Text.of("$actionTitle: $translation")

        addSelectableChild(actionButton)


        // MIDI device button.

        val chatLimit = ( w2 * 0.125f ).toInt()
        val deviceTitle = Translation.item("gui.device")

        deviceButton = createButton( x, y, - w2.toFloat(), h * 1.25f, w, h, w2, 0f ) {

            resetTween()

            deviceName = cycle( deviceName, devicesNames )

            val s = shorten( "$deviceTitle: $deviceName", chatLimit )

            it.message = Text.of(s)

        }

        val s = shorten( "$deviceTitle: $deviceName", chatLimit )

        deviceButton!!.message = Text.of(s);    addSelectableChild(deviceButton)


        // MIDI Channel field.

        val w4 = w * 0.075f

        channelField = MidiChannelField( textRenderer,
            ( x + w4 - w4 * 0.5f + w2 * 1.875f ).toInt(),
            ( y + 2.75f * h ).toInt(),
            w4.toInt(), h
        )

        channelField!!.setMaxLength(2);     channelField!!.text = channel.toString()

        addSelectableChild(channelField)


        // Volume slider.

        val volumeTitle = Translation.item("gui.volume")

        volumeSlider = CustomSlider(
            ( x + w * 0.5 + w2 * 0.05 - w2 * 0.5f ).toInt(),
            ( y + h * 1.5 + h * 1.25f * 2f ).toInt(),
            w2, ( h * 1.1f ).toInt(),
            volumeTitle, volume
        )

        addSelectableChild(volumeSlider)


        // Center notes button.

        val on = Translation.get("gui.on")
        val off = Translation.get("gui.off")
        val centerTitle = Translation.item("gui.center")

        val switch = mutableMapOf( true to on, false to off )

        centerNotesButton = createButton( x, y, - w2 * 0.5f, h * 1.25f * 3f, w, h, w2, 0f ) {

            shouldCenter = !shouldCenter;       val value = switch[shouldCenter]

            it.message = Text.of("$centerTitle: $value")

        }

        val value = switch[shouldCenter]

        centerNotesButton!!.message = Text.of("$centerTitle: $value")

        addSelectableChild(centerNotesButton)

        copyButton = createButton( x, y, w * 0.513f, - 56f, ( w * 0.25 ).toInt(), h, w2, 0f ) {
            Clipboard().setClipboard( client!!.window.handle, sequenceField!!.text )
        }

        copyButton!!.message = Text.of(copyTitle);      addSelectableChild(copyButton)

    }

    override fun close() {

        val volumeString = volumeSlider!!.message.string
        val volume = volumeString.filter { it.isDigit() }.toFloat()

        val channel = channelField!!.text

        nbt.putString( "Sequence", sequenceField!!.text )
        nbt.putString( "SequenceInput", sequenceField!!.text )
        nbt.putString( "Action", action )
        nbt.putString( "MIDI Device", deviceName )
        nbt.putInt( "MIDI Channel", channel.toInt() )
        nbt.putFloat( "Volume", volume * 0.01f )
        nbt.putBoolean( "Center Notes", shouldCenter )

        if ( clientConfig["write_device_info"] as Boolean ) {

            val newName = "$instrumentName - $deviceName - $channel"

            stack.setCustomName( Text.of(newName) )

        }

        if ( deviceName == "None" ) stack.removeCustomName()

        keepDisplay( stack, nbt );      networkNBT(nbt);        super.close()

    }

    override fun tick() {

        channelField!!.tick()

        val sequenceField = sequenceField!!;        val text = sequenceField.text

        if ( text.contains( Regex("[a-z]") ) ) sequenceField.text = text.uppercase()

    }

    override fun render( context: DrawContext, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(context)

        children().forEach {

            if ( instrument is DrumSet && it == centerNotesButton ) return@forEach

            it as Drawable;     it.render( context, mouseX, mouseY, delta )

        }

        val title = stack.name.string
        context.drawText( textRenderer, title, width - ( title.length * 5f ).toInt() - 20, 10, 0xFFFFFF, false )

        context.drawText( textRenderer, "$sequenceTitle:", sequenceField!!.x, sequenceField!!.y - 12, 0xFFFFFF, false )

        context.drawText( textRenderer, "$channelTitle: ", channelField!!.x - 45, channelField!!.y + 5, 0xFFFFFF, false )

        // Show device name on change using a "tween".
        deviceNameDraw(context)

    }

    // The first value is the color.
    // The second value is an incremental.
    private val tweenStack = mutableListOf( 0, 0 )

    private fun readDevices() {

        devices.forEach {

            val info = MidiSystem.getMidiDevice(it)

            if ( !info.isOpen || info.maxTransmitters == 0 ) return@forEach

            devicesNames.add( it.name )

        }

    }

    private fun checkDeviceName() {

        if ( deviceName.isNotEmpty() ) return

        deviceName = devicesNames.elementAt(0)

    }

    private fun resetTween() { tweenStack.replaceAll { 0 } }

    private fun deviceNameDraw(context: DrawContext) {

        val t = tweenStack

        if ( t[1] > 400 ) return

        if ( t[0] < 255 && t[1] > 200 ) t[0]++

        val string = deviceName;        t[1]++
        val text = Text.of(string).asOrderedText()

        val color = Color( 255 - t[0], 255 - t[0], 255 - t[0] )

        context.drawCenteredTextWithShadow( textRenderer, text, ( width * 0.5f ).toInt(), height - 47, color.rgb )

    }

    companion object {

        private val sequenceTitle = Translation.item("gui.sequence")
        private val channelTitle = Translation.item("gui.channel")
        private val copyTitle = Translation.item("gui.copy")

    }

}
