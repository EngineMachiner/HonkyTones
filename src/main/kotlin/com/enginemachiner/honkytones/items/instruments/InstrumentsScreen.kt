package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.honkytones.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text
import java.awt.Color
import javax.sound.midi.MidiSystem

// Client screen
@Environment(EnvType.CLIENT)
class InstrumentsScreen( private val stack: ItemStack )
    : Screen( Text.of("") ) {

    private var sequenceField: TextFieldWidget? = null
    private var channelField: ChannelTextFieldWidget? = null

    private var clearButton: ButtonWidget? = null
    private var clearButtonCounter = 0
    private var actionButton: ButtonWidget? = null
    private var deviceButton: ButtonWidget? = null
    private var centerNotesButton: ButtonWidget? = null

    private var volumeSlider: SliderWidget? = null

    private val instrument = stack.item as Instrument
    private var nbt = stack.nbt!!.getCompound(Base.MOD_NAME)
    private val devices = MidiSystem.getMidiDeviceInfo()
    private val devicesNames = mutableSetOf<String>()
    private val actions = mutableSetOf("Melee", "Push")
    private val instrumentName = instrument.getName().string

    // Former stored values
    private var sequence = nbt.getString("Sequence")
    private var action = nbt.getString("Action")
    private var deviceName = nbt.getString("MIDI Device")
    private var channel = nbt.getInt("MIDI Channel")
    private var volume = nbt.getFloat("Volume")
    private var shouldCenter = nbt.getBoolean("Center Notes")

    override fun shouldPause(): Boolean { return false }

    override fun close() {

        val v = volumeSlider!!.message.asString()
        val vol = v.filter { it.isDigit() }.toFloat()
        val channel = channelField!!.text

        nbt.putString("Sequence", sequenceField!!.text)
        nbt.putString("TempSequence", sequenceField!!.text)
        nbt.putString("Action", action)
        nbt.putString("MIDI Device", deviceName)
        nbt.putInt( "MIDI Channel", channel.toInt() )
        nbt.putFloat("Volume", vol * 0.01f)
        nbt.putBoolean("Center Notes", shouldCenter)

        // Clear stack custom name when command enabled
        // and clear button is clicked 3 times
        if ( clientConfig["writeDeviceInfo"] as Boolean ) {
            val s = "$instrumentName - $deviceName - $channel"
            stack.setCustomName(Text.of(s))
        }

        if ( clearButtonCounter == 3 ) nbt.putBoolean("removeName", true)

        writeDisplayOnNbt( stack, nbt )

        // Send nbt to server
        Network.sendNbtToServer(nbt)
        super.close()

    }

    override fun init() {

        if ( instrument is Trombone ) actions.add("Thrust")
        for ( enchantNbt in stack.enchantments ) {
            val nbt = enchantNbt as NbtCompound
            if ( nbt.getString("id") == Base.MOD_NAME + ":ranged" ) {
                actions.add("Ranged");      break
            }
        }

        // Device names and set current device name
        for ( device in devices ) { devicesNames.add(device.name) }

        // Based dimensions
        val x = (width * 0.5f - width * 0.75f * 0.5f).toInt()
        val y = (height * 0.08f * 1.5f).toInt()
        val w = (width * 0.75f).toInt()
        val h = (240 * 0.08f).toInt()
        val w2 = (w * 0.35f).toInt()

        // Sequence field
        sequenceField = TextFieldWidget( textRenderer, x, y, w, h, Text.of(sequence) )
        sequenceField!!.setMaxLength(32 * 5)
        sequenceField!!.text = sequence
        addSelectableChild(sequenceField)

        val clearTitle = Translation.get("item.honkytones.gui.clear")
        clearButton = createButton( x, y, 0f, 0f, w, h, w2, 0f ) {
            sequenceField!!.text = "";      clearButtonCounter++
        }
        clearButton!!.message = Text.of(clearTitle)
        addSelectableChild(clearButton)


        val actionTitle = Translation.get("item.honkytones.gui.action")
        val actionValues = mapOf(
            "Melee" to Translation.get("item.honkytones.gui.melee"),
            "Push" to Translation.get("item.honkytones.gui.push"),
            "Ranged" to Translation.get("item.honkytones.gui.ranged")
        )
        var translation = actionValues[action]
        actionButton = createButton( x, y, - w2.toFloat(), 0f, w, h, w2, 0f ) {
            action = getValueAfterValue(action, actions)
            translation = actionValues[action]
            it.message = Text.of("$actionTitle: $translation")
        }
        actionButton!!.message = Text.of("$actionTitle: $translation")
        addSelectableChild(actionButton)


        // MIDI device button
        val charLim = ( w2 * 0.125f ).toInt()
        val deviceTitle = Translation.get("item.honkytones.gui.device")
        deviceButton = createButton( x, y, - w2.toFloat(), h * 1.25f, w, h, w2, 0f ) {
            resetTween( tweenStack["deviceName"]!! )
            deviceName = getValueAfterValue(deviceName, devicesNames)
            it.message = Text.of( stringCut( "$deviceTitle: $deviceName", charLim ) )
        }
        deviceButton!!.message = Text.of( stringCut( "$deviceTitle: $deviceName", charLim ) )
        addSelectableChild(deviceButton)


        // Channel field
        val w4 = w * 0.075f
        channelField = ChannelTextFieldWidget( textRenderer,
            (x + w4 - w4 * 0.5f + w2 * 1.875).toInt(),
            (y + 2.75 * h).toInt(),
            w4.toInt(), h
        )
        channelField!!.setMaxLength(2)
        channelField!!.text = channel.toString()
        addSelectableChild(channelField)


        val volumeTitle = Translation.get("item.honkytones.gui.volume")
        volumeSlider = CustomSlider(
            (x + w * 0.5 + w2 * 0.05 - w2 * 0.5f).toInt(),
            (y + h * 1.5 + h * 1.25f * 2f).toInt(),
            w2, (h * 1.1f).toInt(),
            volumeTitle, volume
        )
        addSelectableChild(volumeSlider)


        // Find missing notes the nearest range note
        val on = Translation.get("honkytones.gui.on")
        val off = Translation.get("honkytones.gui.off")
        val centerTitle = Translation.get("item.honkytones.gui.center")

        val switch = mutableMapOf( true to on, false to off )

        centerNotesButton = createButton( x, y, - w2 * 0.5f, h * 1.25f * 3f, w, h, w2, 0f ) {
            shouldCenter = !shouldCenter
            it.message = Text.of("$centerTitle: ${ switch[shouldCenter] }")
        }
        centerNotesButton!!.message = Text.of("$centerTitle: ${ switch[shouldCenter] }")
        addSelectableChild(centerNotesButton)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)

        sequenceField!!.render(matrices, mouseX, mouseY, delta)
        actionButton!!.render(matrices, mouseX, mouseY, delta)
        deviceButton!!.render(matrices, mouseX, mouseY, delta)
        channelField!!.render(matrices, mouseX, mouseY, delta)
        volumeSlider!!.render(matrices, mouseX, mouseY, delta)
        clearButton!!.render(matrices, mouseX, mouseY, delta)

        if ( instrument.name != "drumset" ) centerNotesButton!!.render(matrices, mouseX, mouseY, delta)

        val b = sequenceField!!.text.isNotEmpty() && clearButtonCounter > 0
        if (b) clearButtonCounter = 0

        // Subtitles
        textRenderer.draw(
            matrices, "$sequenceTitle:",
            sequenceField!!.x.toFloat(), sequenceField!!.y.toFloat() - 12,
            0xFFFFFF
        )

        textRenderer.draw(
            matrices, "$channelTitle: ",
            channelField!!.x.toFloat() - 45, channelField!!.y.toFloat() + 5,
            0xFFFFFF
        )

        // Show device name on change
        val tween = tweenStack["deviceName"]!!
        if ( tween[1] < 400 ) {

            if (tween[0] < 255 && tween[1] > 200) tween[0]++

            val string = deviceName; tween[1]++
            val color = Color( 255 - tween[0], 255 - tween[0], 255 - tween[0] )

            val x = ( width * 0.5f - string.length * 2.5f )
            textRenderer.drawWithShadow(
                matrices, string,
                x, height - 60f,
                color.rgb
            )

        }

    }

    companion object {
        val sequenceTitle = Translation.get("item.honkytones.gui.sequence")
        val channelTitle = Translation.get("item.honkytones.gui.channel")
    }

    // 1st -> incremental, 2nd -> frame-time
    private val tweenStack = mutableMapOf( "deviceName" to mutableListOf(0, 0) )

    private fun resetTween(list: MutableList<Int>) {
        for ( i in 0 until list.size) { list[i] = 0 }
    }

}