package com.enginemachiner.honkytones.items.console

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Init.Companion.directories
import com.enginemachiner.honkytones.NBT.networkNBT
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import javax.sound.midi.*

@Environment(EnvType.CLIENT)
class RecordingScreen( private val screen: DigitalConsoleScreen ) : Screen( Text.of("RecordingScreen") ) {

    private val stack = screen.screenHandler.stack

    private var fileNameField: TextFieldWidget? = null
    private var channelField: MidiChannelField? = null
    private var yesButton: ButtonWidget? = null
    private var noButton: ButtonWidget? = null

    override fun shouldPause(): Boolean { return false }

    override fun init() {

        loadTranslations()

        val x = ( width * 0.125f ).toInt()
        val y = ( height * 0.08f * 1.5f ).toInt()

        val w = ( width * 0.75f ).toInt()
        val h = ( 240 * 0.08f ).toInt()

        val w2 = ( w * 0.35f ).toInt()

        fileNameField = TextFieldWidget( textRenderer, x, y, w, h, Text.of("File Field") )

        fileNameField!!.setMaxLength(160);      addSelectableChild(fileNameField)

        val w4 = w * 0.075f
        channelField = MidiChannelField( textRenderer,
            ( x + w4 - w4 * 0.5f + w2 * 1.875 ).toInt(), y + 4 * h, w4.toInt(), h,
        )

        addSelectableChild(channelField)

        yesButton = createButton( x, y, 0f, 0f, w, h, w2, 0f ) {

            client!!.setScreen(screen)

            screen.isRecording = true;          screen.recordCheckbox!!.onPress()

            var fileName = fileNameField!!.text

            if ( !fileName.endsWith(".mid") ) fileName += ".mid"

            screen.recordingFileName = fileName;        sequencer!!.start()

            screen.channel = channelField!!.text.toInt()

            val nbt = NBT.get(stack);       nbt.putBoolean( "damage", true )

            networkNBT(nbt)

        }

        yesButton!!.message = Text.of(startTitle)

        addSelectableChild(yesButton)

        noButton = createButton( x, y, - w * 0.38f, 0f, w, h, w2, 0f ) { close() }

        noButton!!.message = Text.of(cancelTitle)

        addSelectableChild(noButton)

    }

    override fun tick() { channelField!!.tick() }

    override fun render( matrices: MatrixStack?, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        children().forEach { it as Drawable;     it.render( matrices, mouseX, mouseY, delta ) }

        var overwrite = "";       var filePath = fileNameField!!.text

        if ( !filePath.endsWith(".mid") ) filePath += ".mid"

        val path = directories["midis"]!!.path + "/$filePath"

        if ( ModFile(path).isFile ) overwrite = "($overwriteTitle)"

        textRenderer.draw(
            matrices, "$fileNameTitle: $overwrite",
            fileNameField!!.x.toFloat(), fileNameField!!.y.toFloat() - 12,
            0xFFFFFF
        )

        textRenderer.draw(
            matrices, "$channelTitle:",
            channelField!!.x.toFloat(), channelField!!.y.toFloat() - 12,
            0xFFFFFF
        )

    }

    override fun close() { client!!.setScreen(screen);  screen.willRecord = false }

    private fun loadTranslations() {

        if (translationsLoaded) return;     translationsLoaded = true

        fileNameTitle = Translation.item("gui.file.name")
        cancelTitle = Translation.item("gui.cancel")
        startTitle = Translation.item("gui.file.start")
        channelTitle = Translation.item("gui.file.channel")
        overwriteTitle = Translation.item("gui.file.overwrite")

    }

    companion object {

        private var fileNameTitle = "";     private var cancelTitle = ""
        private var startTitle = "";        private var channelTitle = ""
        private var overwriteTitle = "";    private var translationsLoaded = false

        var sequencer: Sequencer? = null

        private var sequence = Sequence( Sequence.PPQ, 10 )

        init { init() }

        private fun init() {

            if ( !Midi.hasSystemSequencer() ) return

            sequencer = MidiSystem.getSequencer();      val sequencer = sequencer!!

            if ( !sequencer.isOpen ) sequencer.open();      sequencer.sequence = sequence

        }

    }

}