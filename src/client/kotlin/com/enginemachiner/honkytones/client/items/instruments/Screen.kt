package com.enginemachiner.honkytones.client.items.instruments

import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.Translation
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.NBT.saveDisplay
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.cycle
import com.enginemachiner.honkytones.client.Config
import com.enginemachiner.honkytones.client.MidiChannelField
import com.enginemachiner.honkytones.items.instruments.DrumSet
import com.enginemachiner.honkytones.items.instruments.InstrumentItem
import com.enginemachiner.honkytones.items.instruments.RangedEnchantment
import com.enginemachiner.honkytones.items.instruments.Trombone
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import javax.sound.midi.MidiSystem
import kotlin.math.roundToInt

class InstrumentsScreen( private val stack: ItemStack ) : Screen(title) {

    private val devicesInfo = MidiSystem.getMidiDeviceInfo()
    private val devices = mutableSetOf("None")

    private val actions = mutableSetOf( "Melee", "Push" )

    private val nbt = nbt(stack)

    private val channel = nbt.getInt("MIDI Channel")
    private val sequence = nbt.getString("lastSequence")
    private var action = nbt.getString("Action")
    private var deviceName = nbt.getString("MIDI Device")
    private val volume = nbt.getFloat("Volume")
    private var center = nbt.getBoolean("Center Notes")

    private val instrument = stack.item as InstrumentItem
    private val instrumentName = instrument.name.string

    private fun readDevices() {

        devicesInfo.forEach {

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

        if ( instrument !is Trombone ) return;      actions.add("Thrust")

    }

    private fun onRanged() {

        val enchantments = EnchantmentHelper.get(stack).keys

        val isRanged = enchantments.find { it is RangedEnchantment } != null

        if (isRanged) actions.add("Ranged")

    }

    init { readDevices();      checkDeviceName();      onTrombone();       onRanged() }

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

        val title = Translations.center;        val value = states[center]

        return "$title: $value"

    }

    private var centerButton: Button? = null

    private fun update( function: () -> Unit ) { function(); send(nbt) }

    override fun shouldPause(): Boolean { return false }

    private fun initTexts() {

        val renderer = textRenderer

        nameTitle.init( instrumentName, renderer )

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

            update { nbt.putFloat("Volume", value) }

        }

        channelField = MidiChannelField( x + 15f, y(), 20f, 15f, "Midi Channel Field", textRenderer ) {

            it.text = "$channel"

        }


        i--;    y = y()

        x = width * 0.5f;   x += ( sequenceField.width - w ) * 0.5f;    x -= w * 0.25f

        deviceButton = Button( x, y, w * 1.5f, 20f, ::deviceMessage ) {

            deviceText.reset()


            deviceName = cycle( devices, deviceName )

            update { nbt.putString( "MIDI Device", deviceName ) }


            it.updateMessage()

        }

        centerButton = Button( x, y(), w * 1.5f, 20f, ::centerMessage ) {

            center = !center;       it.updateMessage()

            update { nbt.putBoolean( "Center Notes", center ) }

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


        val next = "$instrumentName - $deviceName - $channel"

        stack.setCustomName( Text.of(next) )


    }

    private fun channel(): Int { return channelField!!.text.toInt() }

    override fun close() {

        channelField!!.checkField()

        val sequence = sequenceField!!.text

        nbt.putString( "lastSequence", sequence )
        nbt.putString( "Sequence", sequence )
        nbt.putString( "Action", action )

        nbt.putInt( "MIDI Channel", channel() )

        nameStack();        if ( deviceName == "None" ) stack.removeCustomName()

        saveDisplay( stack, nbt );      send(nbt);        super.close()

    }

    override fun tick() { channelField!!.tick();        sequenceField!!.tick() }

    private fun renderChildren( context: DrawContext, mouseX: Int, mouseY: Int, delta: Float ) {

        children().forEach {

            if ( instrument is DrumSet && it == centerButton ) return@forEach

            it as Drawable;     it.render( context, mouseX, mouseY, delta )

        }

    }

    private fun renderTexts(context: DrawContext) {

        renderTexts.forEach { it.render(context) }

        deviceText.render(context)

    }

    override fun render( context: DrawContext, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground( context, mouseX, mouseY, delta );     renderChildren( context, mouseX, mouseY, delta )

        renderTexts(context)

    }

    private companion object {

        val title: Text = Text.of("Instrument Screen")

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

            x: Float, y: Float,     w: Float, h: Float,

            message: String, renderer: TextRenderer, init: (TextField) -> Unit

        ) : TextField( x, y, w, h, message, renderer, init ) {

            private fun onLowercase() {

                val isLower = text.contains( Regex("[a-z]") )

                if ( !isLower ) return;         text = text.uppercase()

            }

            fun tick() { onLowercase() }

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
