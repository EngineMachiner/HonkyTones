package com.enginemachiner.honkytones.client.items.storage

import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.client.Texture
import com.enginemachiner.harmony.textureID
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler
import com.enginemachiner.honkytones.items.storage.StorageScreenHandler.Companion.type
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.util.math.MatrixStack
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

    override fun drawBackground( matrices: MatrixStack, delta: Float, mouseX: Int, mouseY: Int ) {

        texture.draw(matrices)

    }

    override fun render( matrices: MatrixStack, mouseX: Int, mouseY: Int, delta: Float ) {

        renderBackground(matrices);         super.render( matrices, mouseX, mouseY, delta )

        drawMouseoverTooltip( matrices, mouseX, mouseY )

    }

    override fun isClickOutsideBounds( mouseX: Double, mouseY: Double, left: Int, top: Int, button: Int ): Boolean {

        return texture.isClickOutsideBounds( mouseX, mouseY )

    }

    companion object : ModID {

        private val textureID = textureID("item/storage_slots.png")

        fun register() { ScreenRegistry.register( type, ::StorageScreen ) }

    }

}