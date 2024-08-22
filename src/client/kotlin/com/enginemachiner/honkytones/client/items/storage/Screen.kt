package com.enginemachiner.honkytones.client.items.storage

import com.enginemachiner.harmony.client.Texture
import com.enginemachiner.harmony.textureID
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler.Companion.type
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.HandledScreens
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.text.Text

class StorageScreen(
    handler: StorageScreenHandler, playerInventory: PlayerInventory, text: Text
) : HandledScreen<StorageScreenHandler>( handler, playerInventory, text ) {

    init { titleX += 9; titleY += 8;        playerInventoryTitleY -= 7 }

    private val texture = Texture(textureID) {

        it.setSize(176f, 150f);    it.center(width, height)

    }

    override fun init() { super.init();       texture.init() }

    override fun drawBackground( context: DrawContext, delta: Float, mouseX: Int, mouseY: Int ) {

        texture.draw(context)

    }

    override fun render( context: DrawContext, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(context);         super.render( context, mouseX, mouseY, delta )

        drawMouseoverTooltip( context, mouseX, mouseY )

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean {

        return texture.isClickOutsideBounds( mouseX, mouseY )

    }

    companion object {

        private val textureID = textureID("item/storage_slots.png")

        fun register() { HandledScreens.register( type, ::StorageScreen ) }

    }

}