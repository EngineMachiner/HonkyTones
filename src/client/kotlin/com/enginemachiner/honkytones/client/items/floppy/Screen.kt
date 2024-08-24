package com.enginemachiner.honkytones.client.items.floppy

import com.enginemachiner.harmony.MOD_NAME
import com.enginemachiner.harmony.ModFile
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.Translation
import com.enginemachiner.harmony.client.ClearButton
import com.enginemachiner.harmony.client.CopyButton
import com.enginemachiner.harmony.client.NBT.saveDisplay
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.client.RenderText
import com.enginemachiner.harmony.client.TextField
import com.enginemachiner.harmony.shorten
import com.enginemachiner.honkytones.client.isValidUrl
import com.enginemachiner.honkytones.items.FloppyDisk.Companion.setState
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.text.Text

class FloppyDiskScreen( private val stack: ItemStack ) : Screen(screenTitle) {

    private val nbt = nbt(stack)

    private var path = nbt.getString("Path");   private val lastPath = path


    private val screenTitle = RenderText { it.setPos(10f) }

    private val floppyTitle = RenderText {

        var limit = width - screenTitle.width();        limit /= 8.25f

        it.text = shorten( it.text, limit )

        val x = width - textRenderer.getWidth( it.text )

        it.x = x.toFloat();     it.addPos( -10f, 10f )

    }

    private val pathTitle = RenderText { it.setPos(pathField);      it.y -= 13f }

    private val renderTexts = setOf( screenTitle, floppyTitle, pathTitle )

    private var pathField: TextField? = null
    private var copyButton: CopyButton? = null
    private var clearButton: ClearButton? = null

    private val name = stack.name.string


    private fun setStackName() {

        val name = Text.of(path);      stack.setCustomName(name)

    }

    private fun setLocalName() {

        val startsWith = path.startsWith(MOD_NAME)

        if ( !startsWith ) path = "$MOD_NAME/$path"

        if ( ModFile(path).isFile ) setStackName()

    }

    private fun setUrlName() {

        setState(stack, "Requested");    setStackName()

    }

    private fun checkName() {

        if ( !isValidUrl(path) ) setLocalName() else setUrlName()

    }

    private fun checkDisplay() {

        if ( path.isBlank() ) {

            nbt.putBoolean( "resetDisplay", true )

            nbt.remove("nameState")

        } else checkName()

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

        copyButton = CopyButton( x, y, w, 20f, pathField, Translations.copy )

        clearButton = ClearButton( x + w + 1f, y, w, 20f, pathField, Translations.clear )


        addChildren()


        val renderer = textRenderer

        screenTitle.init( Translations.screen, renderer )

        floppyTitle.init( name, renderer )

        pathTitle.init( "${ Translations.path }:", renderer )

    }

    override fun shouldPause(): Boolean { return false }

    override fun close() {

        path = pathField!!.text

        if ( lastPath == path ) { super.close(); return }


        val times = nbt.getInt("timesWritten") + 1


        nbt.put( "Settings", NbtCompound() )

        nbt.putInt( "timesWritten", times )

        checkDisplay();     saveDisplay( stack, nbt )

        nbt.putString( "Path", path )


        send(nbt);       super.close()

    }

    override fun render( context: DrawContext, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground( context, mouseX, mouseY, delta );     renderTexts.forEach { it.render(context) }

        super.render(context, mouseX, mouseY, delta)

    }

    private companion object {

        object Translations {

            val screen = Translation.item("floppy_disk.title")
            val path = Translation.item("gui.path")
            val copy = Translation.item("gui.copy")
            val clear = Translation.item("gui.clear")
        }

        val screenTitle: Text = Text.of( Translations.screen )

    }

}