package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.*
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import javax.sound.midi.*

@Environment(EnvType.CLIENT)
class RecordingOptionsScreen( private val screen: DigitalConsoleScreen )
: Screen( Text.of("") ) {

    private var fileNameField: TextFieldWidget? = null
    private var channelField: ChannelTextFieldWidget? = null
    private var yesButton: ButtonWidget? = null
    private var noButton: ButtonWidget? = null

    override fun isPauseScreen(): Boolean { return false }

    override fun init() {

        fileNameTitle = Translation.get("item.honkytones.gui.file.name")
        cancelTitle = Translation.get("item.honkytones.gui.cancel")
        startTitle = Translation.get("item.honkytones.gui.file.start")
        channelTitle = Translation.get("item.honkytones.gui.file.channel")
        overwriteTitle = Translation.get("item.honkytones.gui.file.overwrite")

        // Based dimensions
        val x = (width * 0.5 - width * 0.75 * 0.5).toInt()
        val y = (height * 0.08 * 1.5).toInt()
        val w = (width * 0.75).toInt()
        val h = (240 * 0.08).toInt()
        val w2 = (w * 0.35).toInt()

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

        yesButton = createButton( x, y, 0f, 0f, w, h, w2, 0f ) {

            client!!.openScreen(screen)
            screen.isRecording = true;          screen.recordCheckbox!!.onPress()

            var fileName = fileNameField!!.text
            if ( !fileName.endsWith(".mid") ) fileName += ".mid"
            screen.fileName = fileName

            screen.channel = channelField!!.text.toInt()

            sequencer!!.open()

            sequencer!!.startRecording()

            var tag = screen.screenHandler.consoleStack.tag!!
            tag = tag.getCompound(Base.MOD_NAME)
            tag.putBoolean("shouldDamage", true)
            Network.sendNbtToServer(tag)

        }
        yesButton!!.message = Text.of(startTitle)
        addSelectableChild(yesButton)

        noButton = createButton( x, y, -125f, 0f, w, h, w2, 0f ) { onClose() }
        noButton!!.message = Text.of(cancelTitle)
        addSelectableChild(noButton)

    }

    override fun render(matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float) {

        renderBackground(matrices)
        super.render(matrices, mouseX, mouseY, delta)

        var overwriteString = ""
        var filePath = fileNameField!!.text
        if ( !filePath.endsWith(".mid") ) filePath += ".mid"
        val path = Base.paths["midis"]!!.path + "/$filePath"
        if ( RestrictedFile(path).isFile ) overwriteString = "($overwriteTitle)"

        textRenderer.draw(
            matrices, "$fileNameTitle: $overwriteString",
            fileNameField!!.x.toFloat(), fileNameField!!.y.toFloat() - 12,
            0xFFFFFF
        )
        fileNameField!!.render(matrices, mouseX, mouseY, delta)

        textRenderer.draw(
            matrices, "$channelTitle:",
            channelField!!.x.toFloat(), channelField!!.y.toFloat() - 12,
            0xFFFFFF
        )
        channelField!!.render(matrices, mouseX, mouseY, delta)

        yesButton!!.render(matrices, mouseX, mouseY, delta)
        noButton!!.render(matrices, mouseX, mouseY, delta)

    }

    override fun onClose() {
        client!!.openScreen(screen)
        screen.willRecord = false;      screen.timeStamp = -1f
    }

    companion object {

        private var fileNameTitle = ""
        private var cancelTitle = ""
        private var startTitle = ""
        private var channelTitle = ""
        private var overwriteTitle = ""

        var sequencer: Sequencer? = null
        private var sequence = Sequence( Sequence.PPQ, 4 )

        private class RecordingReceiver : Receiver {
            override fun close() {}
            override fun send( message: MidiMessage?, timeStamp: Long ) {
                sequence.tracks[0].add( MidiEvent(message, timeStamp) )
            }
        }

        init {

            if ( hasMidiSystemSequencer() ) {

                sequencer = MidiSystem.getSequencer()

                val sequencer = sequencer!!

                sequence.createTrack()
                sequencer.sequence = sequence

                val transmitters = sequencer.transmitters
                for ( transmitter in transmitters ) transmitter.receiver = RecordingReceiver()

                for ( i in 0..15 ) sequencer.recordEnable( sequence.tracks[0], i )

            }

        }

    }

}