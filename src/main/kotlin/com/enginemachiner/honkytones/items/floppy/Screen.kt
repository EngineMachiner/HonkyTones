package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.saveDisplay
import com.enginemachiner.harmony.NBT.sendNBT
import com.enginemachiner.honkytones.isValidUrl
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text

// @Environment(EnvType.CLIENT)
class FloppyDiskScreen( private val stack: ItemStack ) : Screen( Text.of(screenTranslation) ) {

    private val nbt = NBT.get(stack)

    private var path = nbt.getString("Path");   private val lastPath = path

    private val screenTitle = RenderText { it.setPos(10f) }

    private val floppyTitle = RenderText {

        it.text = shorten( it.text, width / 7f )

        val x = width - textRenderer.getWidth( it.text )

        it.x = x.toFloat();     it.addPos( -10f, 10f )

    }

    private val pathTitle = RenderText { it.setPos(pathField);      it.y -= 13f }

    private val renderTexts = setOf( screenTitle, floppyTitle, pathTitle )

    private var pathField: TextField? = null
    private var copyButton: CopyButton? = null
    private var clearButton: ClearButton? = null

    private val name = stack.name.string

    private fun setStackName() { stack.setCustomName( Text.of(path) ) }

    private fun setLocalName() {

        val startsWith = path.startsWith(MOD_NAME)

        if ( !startsWith ) path = "$MOD_NAME/$path"

        if ( ModFile(path).isFile ) setStackName()

    }

    private fun setUrlName() {

        nbt.putBoolean( "onFetch", true );  setStackName()

    }

    private fun checkName() {

        if ( !isValidUrl(path) ) setLocalName() else setUrlName()

    }

    private fun checkDisplay() {

        if ( path.isBlank() ) nbt.putBoolean( "resetDisplay", true ) else checkName()

    }

    private fun addChildren() {

        val widgets = setOf( pathField, copyButton, clearButton )

        widgets.forEach { addDrawableChild(it) }

    }

    override fun init() {

        pathField = TextField( width * 0.5f, height * 0.25f, width * 0.75f, 20f, "Input Field", textRenderer ) {

            it.setMaxLength(250);       it.text = path

        }


        val w = width * 0.125f;      val pathField = pathField!!

        val x = width * 0.5f + w * 1.5f;        val y = pathField.y + 35f

        copyButton = CopyButton( x, y, w, 20f, pathField, copyTranslation )

        clearButton = ClearButton( x + w + 1f, y, w, 20f, pathField, clearTranslation )


        addChildren()


        val renderer = textRenderer

        screenTitle.init( screenTranslation, renderer )

        floppyTitle.init( name, renderer )

        pathTitle.init( "$pathTranslation:", renderer )

    }

    override fun shouldPause(): Boolean { return false }

    override fun close() {

        path = pathField!!.text

        if ( lastPath == path ) { super.close(); return }


        val times = nbt.getInt("timesWritten") + 1


        nbt.put( "Settings", NbtCompound() )

        nbt.putString( "Path", path );          nbt.putInt( "timesWritten", times )

        nbt.remove("displaySet");   checkDisplay();     saveDisplay( stack, nbt )


        sendNBT(nbt);       super.close()

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);     renderTexts.forEach { it.render(matrices) }

        super.render(matrices, mouseX, mouseY, delta)

    }

    private companion object {

        val screenTranslation = Translation.item("floppy_disk.title")
        val pathTranslation = Translation.item("gui.path")
        val copyTranslation = Translation.item("gui.copy")
        val clearTranslation = Translation.item("gui.clear")

    }

}