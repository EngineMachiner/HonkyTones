package com.enginemachiner.honkytones.items.instruments

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.saveDisplay
import com.enginemachiner.harmony.NBT.sendNBT
import com.enginemachiner.honkytones.Config
import com.enginemachiner.honkytones.MidiChannelField
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import javax.sound.midi.MidiSystem
import kotlin.math.roundToInt

// @Environment(EnvType.CLIENT)
class InstrumentsScreen( private val stack: ItemStack ) : Screen( Text.of("Instrument Screen") ) {

    private val deviceInfo = MidiSystem.getMidiDeviceInfo()
    private val devices = mutableSetOf("None")

    private val actions = mutableSetOf( "Melee", "Push" )

    private val nbt = NBT.get(stack)

    private val sequence = nbt.getString("lastSequence")
    private var action = nbt.getString("Action")
    private var deviceName = nbt.getString("MIDI Device")
    private val channel = nbt.getInt("MIDI Channel")
    private val volume = nbt.getFloat("Volume")
    private var shouldCenter = nbt.getBoolean("Center Notes")

    private val instrument = stack.item as Instrument
    private val name = instrument.name.string

    private fun readDevices() {

        deviceInfo.forEach {

            val info = MidiSystem.getMidiDevice(it)

            if ( !info.isOpen || info.maxTransmitters == 0 ) return@forEach

            devices.add( it.name )

        }

    }

    private fun checkDeviceName() {

        if ( deviceName.isNotEmpty() ) return

        deviceName = devices.elementAt(0)

    }

    private fun onTrombone() {

        if ( instrument is Trombone ) actions.add("Thrust")

    }

    private fun onRanged() {

        val enchantments = EnchantmentHelper.get(stack).keys

        val isRanged = enchantments.find { it is RangedEnchantment } != null

        if (isRanged) actions.add("Ranged")

    }

    init {

        readDevices();      checkDeviceName();      onTrombone();       onRanged()

    }

    private var widgetWidth = 0f


    private val nameTitle = RenderText {

        it.x = width.toFloat();    it.y = it.height()

        it.offsetX( - it.width() - 10f )

    }

    private val sequenceTitle = RenderText {

        it.setPos(sequenceField);      it.y -= 13f

    }

    private val channelTitle = RenderText {

        it.setPos(channelField);      it.addPos( -45f, 3f )

    }

    private val deviceText = FadingText {

        it.text = deviceName;       it.centerX(width);      it.y = height * 0.8f

    }

    private val renderTexts = setOf( nameTitle, sequenceTitle, channelTitle )


    private var volumeSlider: VolumeSlider? = null

    private var sequenceField: SequenceField? = null

    private var channelField: MidiChannelField? = null


    private var clearButton: ClearButton? = null

    private var copyButton: CopyButton? = null


    private var currentAction = Translations.actions[action]

    private fun actionMessage(): String { return "${ Translations.action }: $currentAction" }

    private var actionButton: Button? = null



    private fun deviceMessage(): String { return "${ Translations.device }: $deviceName" }

    private var deviceButton: Button? = null


    private val states = mutableMapOf( true to Translations.on,     false to Translations.off )


    private fun centerMessage(): String {

        val title = Translations.center;        val value = states[shouldCenter]

        return "$title: $value"

    }

    private var centerButton: Button? = null

    private fun send( function: () -> Unit ) { function(); sendNBT(nbt) }

    override fun shouldPause(): Boolean { return false }

    private fun initTexts() {

        val renderer = textRenderer

        nameTitle.init( name, renderer )

        sequenceTitle.init( "${ Translations.sequence }:", renderer )

        channelTitle.init( "${ Translations.channel }: ", renderer )

        deviceText.init(renderer)

    }

    private fun assign() {

        var x = width * 0.5f;       var w = widgetWidth * 0.5f

        var i = 0;      fun y(): Float { return 25f * i++ + 50f }


        sequenceField = SequenceField( x, y(), width * 0.75f, 20f, "Sequence Field", textRenderer ) {

            it.setMaxLength(400);      it.text = sequence

        }

        val sequenceField = sequenceField!!


        var y = y();        x += sequenceField.width * 0.5f - w

        clearButton = ClearButton( x, y, w, 20f, sequenceField, Translations.clear ) {

            it.offsetX( - w * 0.525f )

        }

        copyButton = CopyButton( x, y, w, 20f, sequenceField, Translations.copy ) {

            it.offsetX( w * 0.5f )

        }


        w = widgetWidth

        x = width * 0.5f;   x -= ( sequenceField.width - w ) * 0.5f

        volumeSlider = VolumeSlider( x, y(), w, 20f, volume ) {

            val value = it.value()

            send { nbt.putFloat("Volume", value) }

        }

        channelField = MidiChannelField( x + 15f, y(), 20f, 15f, "Midi Channel Field", textRenderer ) {

            it.text = "$channel"

        }


        i--;    y = y()

        x = width * 0.5f;   x += ( sequenceField.width - w ) * 0.5f;    x -= w * 0.25f

        deviceButton = Button( x, y, w * 1.5f, 20f, ::deviceMessage ) {

            deviceText.reset()


            deviceName = cycle( devices, deviceName )

            send { nbt.putString( "MIDI Device", deviceName ) }


            it.updateMessage()

        }

        centerButton = Button( x, y(), w * 1.5f, 20f, ::centerMessage ) {

            shouldCenter = !shouldCenter;       it.updateMessage()

            send { nbt.putBoolean( "Center Notes", shouldCenter ) }

        }


        actionButton = Button( x, y(), w, 20f, ::actionMessage ) {

            action = cycle( actions, action )

            currentAction = Translations.actions[action]

            it.updateMessage()

        }


    }

    private fun addChildren() {

        val widgets = setOf(

            sequenceField, clearButton, copyButton,

            volumeSlider, channelField, centerButton, deviceButton,

            actionButton

        )

        widgets.forEach { addDrawableChild(it) }

    }

    override fun init() {

        super.init();   widgetWidth = width * 0.25f

        assign();       addChildren();      initTexts()

    }

    private fun nameStack() {


        val nameStack = Config.client().writeDeviceName

        if ( !nameStack ) return


        val next = "$name - $deviceName - $channel"

        stack.setCustomName( Text.of(next) )


    }

    override fun close() {

        channelField!!.checkField()

        val sequence = sequenceField!!.text

        nbt.putString( "lastSequence", sequence )
        nbt.putString( "Sequence", sequence )
        nbt.putString( "Action", action )

        nbt.putInt( "MIDI Channel", channel )

        nameStack();    if ( deviceName == "None" ) stack.removeCustomName()

        saveDisplay( stack, nbt );      sendNBT(nbt);        super.close()

    }

    override fun tick() { channelField!!.tick();        sequenceField!!.tick() }

    private fun renderChildren( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        children().forEach {

            if ( instrument is DrumSet && it == centerButton ) return@forEach

            it as Drawable;     it.render( matrices, mouseX, mouseY, delta )

        }

    }

    private fun renderTexts(matrices: MatrixStack) {

        renderTexts.forEach { it.render(matrices) }

        deviceText.render(matrices)

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);     renderChildren( matrices, mouseX, mouseY, delta )

        renderTexts(matrices)

    }

    private companion object {

        object Translations {

            val on = Translation.get("gui.on")
            val off = Translation.get("gui.off")

            val sequence = Translation.item("gui.sequence")
            val channel = Translation.item("gui.channel")
            val copy = Translation.item("gui.copy")

            val volume = Translation.item("gui.volume")
            val clear = Translation.item("gui.clear")
            val action = Translation.item("gui.action")

            val center = Translation.item("gui.center")
            val device = Translation.item("gui.device")

            val actions = mapOf(
                "Melee" to Translation.item("gui.melee"),
                "Push" to Translation.item("gui.push"),
                "Thrust" to Translation.item("gui.thrust"),
                "Ranged" to Translation.item("gui.ranged")
            )

        }

        class SequenceField(

            x: Float, y: Float,       w: Float, h: Float,

            message: String, renderer: TextRenderer, init: (TextField) -> Unit

        ) : TextField( x, y, w, h, message, renderer, init ) {

            private fun onLowercase() {

                val isLower = text.contains( Regex("[a-z]") )

                if ( !isLower ) return;         text = text.uppercase()

            }

            override fun tick() { onLowercase();  super.tick() }

        }

        class VolumeSlider(

            x: Float, y: Float, w: Float, h: Float,

            value: Float,      action: (VolumeSlider) -> Unit

        ) : Slider( x, y, w, h, value, { it as VolumeSlider;   action(it) } ) {

            override fun format( value: Double ): String {

                val title = Translations.volume

                val i = ( value * 100 ).roundToInt()

                return "$title: $i%"

            }

        }

    }

}
