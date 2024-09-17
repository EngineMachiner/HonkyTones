package com.enginemachiner.honkytones.client.items.console

import com.enginemachiner.honkytones.client.HonkyTones.Companion.directories
import com.enginemachiner.honkytones.client.MidiChannelField
import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.client.Button
import com.enginemachiner.harmony.client.RenderText
import com.enginemachiner.harmony.client.TextField
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text

class RecordingScreen( private val lastScreen: DigitalConsoleScreen ) : Screen( Text.of("Recording Screen") ) {

    private var pathTitle: PathText? = null
    private val channelTitle = RenderText { it.setPos(channelField);      it.addPos( -68f, 3f ) }

    private var pathField: TextField? = null
    private var channelField: MidiChannelField? = null
    private var recordButton: Button? = null
    private var cancelButton: Button? = null

    private var widgetWidth = 0f

    override fun isPauseScreen(): Boolean { return false }

    private fun addChildren() {

        val widgets = setOf( pathField, channelField, recordButton, cancelButton )

        widgets.forEach { addDrawableChild(it) }

    }

    private fun path(): String {

        var path = pathField!!.text

        if ( !path.endsWith(".mid") ) path += ".mid"

        return path

    }

    private fun channel(): Int { return channelField!!.text.toInt() - 1 }

    private fun record() {

        val screen = lastScreen;        onClose();       channelField!!.checkField()

        screen.path = path();       screen.channel = channel()

        screen.box!!.check();       screen.record()

    }

    override fun init() {

        widgetWidth = width * 0.125f;        val w = widgetWidth


        var x = width * 0.5f

        pathField = TextField( x, height * 0.25f, width * 0.75f, 20f, "Path Field", textRenderer ) { it.setMaxLength(160) }

        pathTitle = PathText(pathField!!) { it.init( Translations.fileName, textRenderer ) }

        val pathField = pathField!!


        channelField = MidiChannelField( width * 0.325f, pathField.y + 35f, 20f, 15f, "Midi Channel Field", textRenderer )

        channelTitle.init(Translations.channel, textRenderer )


        x += pathField.width * 0.5f - w * 1.5f

        recordButton = Button( x, pathField.y + 35f, w, 20f, Translations.record) { record() }


        x += w + 1f

        cancelButton = Button( x, pathField.y + 35f, w, 20f, Translations.cancel) { onClose() }


        addChildren()

    }

    override fun tick() { channelField!!.tick() }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        val renderTexts = setOf( channelTitle, pathTitle )

        renderTexts.forEach { it!!.render(matrices) }

    }

    override fun onClose() { client!!.setScreen(lastScreen) }

    private companion object {

        object Translations {

            val fileName = Translation.item("gui.file.name")
            val cancel = Translation.item("gui.cancel")
            val record = Translation.item("gui.file.record")
            val channel = Translation.item("gui.file.channel")
            val overwrite = Translation.item("gui.file.overwrite")

        }

        class PathText(

            private val pathField: TextField,       init: (RenderText) -> Unit

        ) : RenderText() {

            init { init(this);      setPos(pathField);      y -= height() * 1.25f + 1 }

            private val former = text

            override fun render( matrices: MatrixStack, color: Int ) {

                val name = pathField.text
                val directory = directories["midis"]!!.path
                var path = "$directory\\$name"

                if ( !path.endsWith(".mid") ) path += ".mid"

                val isFile = ModFile(path).isFile

                if (isFile) text = former + " (${Translations.overwrite})"

                super.render(matrices, color)

            }

        }

    }

}