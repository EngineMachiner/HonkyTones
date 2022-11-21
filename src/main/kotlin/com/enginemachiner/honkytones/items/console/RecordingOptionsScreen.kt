package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.Base
import com.enginemachiner.honkytones.ChannelTextFieldWidget
import com.enginemachiner.honkytones.Network
import com.enginemachiner.honkytones.RestrictedFile
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import javax.sound.midi.*

class RecordingOptionsScreen( private val screen: DigitalConsoleScreen )
: Screen( Text.of("") ) {

    private var fileNameField: TextFieldWidget? = null
    private var channelField: ChannelTextFieldWidget? = null
    private var yesButton: ButtonWidget? = null
    private var noButton: ButtonWidget? = null

    override fun shouldPause(): Boolean { return false }

    override fun init() {

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

        fileNameField = TextFieldWidget( textRenderer, x, y, w, h, Text.of("") )
        fileNameField!!.setMaxLength(32 * 5);       fileNameField!!.text = ""
        addSelectableChild(fileNameField)

        val w4 = w * 0.075f
        channelField = ChannelTextFieldWidget( textRenderer,
            (x + w4 - w4 * 0.5f + w2 * 1.875).toInt(),
            y + 4 * h,
            w4.toInt(), h,
        )
        channelField!!.text = ""
        addSelectableChild(channelField)

        yesButton = createButton( 0f, 0f, 0f ) {

            client!!.setScreen(screen)
            screen.isRecording = true;          screen.recordCheckbox!!.onPress()

            var fileName = fileNameField!!.text
            if ( !fileName.endsWith(".mid") ) fileName += ".mid"
            screen.fileName = fileName

            screen.channel = channelField!!.text.toInt()

            sequencer.open()

            sequencer.startRecording()

            var nbt = screen.screenHandler.consoleStack.nbt!!
            nbt = nbt.getCompound(Base.MOD_NAME)
            nbt.putBoolean("shouldDamage", true)
            Network.sendNbtToServer(nbt)

        }
        yesButton!!.message = Text.of("Start recording")
        addSelectableChild(yesButton)

        noButton = createButton( -125f, 0f, 0f ) { close() }
        noButton!!.message = Text.of("Cancel")
        addSelectableChild(noButton)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)

        var overwrite = ""
        var filePath = fileNameField!!.text
        if ( !filePath.endsWith(".mid") ) filePath += ".mid"
        val path = Base.paths["midis"]!!.path + "/$filePath"
        if ( RestrictedFile(path).isFile ) overwrite = "(overwrite?)"

        textRenderer.draw(
            matrices, "MIDI File Name: $overwrite",
            fileNameField!!.x.toFloat(), fileNameField!!.y.toFloat() - 12,
            0xFFFFFF
        )
        fileNameField!!.render(matrices, mouseX, mouseY, delta)

        textRenderer.draw(
            matrices, "MIDI Channel:",
            channelField!!.x.toFloat(), channelField!!.y.toFloat() - 12,
            0xFFFFFF
        )
        channelField!!.render(matrices, mouseX, mouseY, delta)

        yesButton!!.render(matrices, mouseX, mouseY, delta)
        noButton!!.render(matrices, mouseX, mouseY, delta)

    }

    override fun close() {
        client!!.setScreen(screen)
        screen.willRecord = false;      screen.timeStamp = -1f
    }

    companion object {

        val sequencer: Sequencer = MidiSystem.getSequencer()
        private var sequence = Sequence( Sequence.PPQ, 4 )

        private class RecordingReceiver : Receiver {
            override fun close() {}
            override fun send( message: MidiMessage?, timeStamp: Long ) {
                sequence.tracks[0].add( MidiEvent(message, timeStamp) )
            }
        }

        init {

            sequence.createTrack()
            sequencer.sequence = sequence

            val transmitters = sequencer.transmitters
            for ( transmitter in transmitters ) transmitter.receiver = RecordingReceiver()

            for ( i in 0..15 ) sequencer.recordEnable( sequence.tracks[0], i )

        }

    }

}