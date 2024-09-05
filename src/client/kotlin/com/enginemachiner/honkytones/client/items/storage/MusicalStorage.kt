package com.enginemachiner.honkytones.client.items.storage

import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.NBT
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.Renderer
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.client.entity
import com.enginemachiner.honkytones.LidAnimatorBehaviour
import com.enginemachiner.honkytones.items.storage.MusicalStorage
import com.enginemachiner.honkytones.items.storage.MusicalStorage.Companion.chests
import com.enginemachiner.honkytones.items.storage.MusicalStorage.Companion.registryItem
import com.enginemachiner.honkytones.mixin.chest.ChestBlockEntityAccessor
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Quaternion

private typealias Models = MusicalStorage.Companion.Models
private typealias OnReceiver = ( stack: ItemStack, item: MusicalStorage ) -> Unit

object MusicalStorage : ModID {

    private fun onPersonView(
        mode: ModelTransformation.Mode, matrix: MatrixStack,
        vertex: VertexConsumerProvider, light: Int, overlay: Int,
        models: Models
    ) {

        fun onFirstPerson( matrix: MatrixStack ) {
            matrix.translate( 0.3, 0.25, 0.0 )
            matrix.scale( 0.55f, 0.55f, 0.55f )
        }

        fun onThirdPerson( matrix: MatrixStack ) {
            matrix.translate( 0.3, 0.65, 0.3 )
            matrix.scale( 0.4f, 0.4f, 0.4f )
            matrix.multiply( Quaternion.fromEulerXyz(0.75f, 0.0f, 0f) )
        }

        val modeName = mode.name;       val dispatcher = client().blockEntityRenderDispatcher

        val isFirst = modeName.contains("FIRST")
        val isThird = modeName.contains("THIRD")

        val isAny = isFirst || isThird;         if ( !isAny ) return

        if (isFirst) onFirstPerson(matrix);      if (isThird) onThirdPerson(matrix)


        val chest = models.hand;    chest as ChestBlockEntityAccessor

        val lid = chest.lidAnimator as LidAnimatorBehaviour

        lid.`honkyTones$renderStep`()

        dispatcher.renderEntity(chest, matrix, vertex, light, overlay)

    }

    private fun onWorldView(
        mode: ModelTransformation.Mode, matrix: MatrixStack,
        vertex: VertexConsumerProvider, light: Int, overlay: Int,
        models: Models
    ) {

        fun onGUI(matrix: MatrixStack) {
            matrix.translate( 0.075, 0.23, 0.0 )
            matrix.scale( 0.625f, 0.625f, 0.625f )
            matrix.multiply( Quaternion.fromEulerXyz(0.55f, 0.8f, 0f) )
        }

        fun onGround(matrix: MatrixStack) {
            matrix.translate( 0.25, 0.25, 0.25 )
            matrix.scale( 0.5f, 0.5f, 0.5f )
        }


        val modeName = mode.name;       val dispatcher = client().blockEntityRenderDispatcher


        val isGUI = modeName == "GUI";          val isOnGround = modeName == "GROUND"

        val isAny = isGUI || isOnGround;        if ( !isAny ) return


        if (isGUI) onGUI(matrix);           if (isOnGround) onGround(matrix)


        val chest = models.world

        dispatcher.renderEntity(chest, matrix, vertex, light, overlay)

    }

    fun registerRender() {

        val renderer = Renderer.Item.create {

            stack, mode, matrix, vertex, light, overlay ->

            val storage = stack.item as MusicalStorage

            if ( !NBT.has(stack) ) storage.setupNBT(stack)


            val nbt = nbt(stack);       val id = nbt.getInt("ID")

            if ( chests[id] == null ) storage.createModels(stack)


            val models = chests[id]!!

            onPersonView( mode, matrix, vertex, light, overlay, models )
            onWorldView( mode, matrix, vertex, light, overlay, models )

        }

        Renderer.Item.register( { registryItem }, renderer )

    }

    /** Register animation receiver. */
    private fun registerReceiver( netID: String, onReceiver: OnReceiver ) {

        val id = netID(netID);          val receiver = Receiver(id)

        receiver.register { val id = it.readInt()

            client().send {

                val player = entity(id) ?: return@send;         player as PlayerEntity

                player.handItems.forEach {

                    val item = it.item;         if ( item !is MusicalStorage ) return@forEach

                    it.holder = player;         onReceiver( it, item )

                }

            }

        }

    }

    fun register() {

        StorageScreen.register()

        registerReceiver("open") { stack, storage -> storage.open(stack) }
        registerReceiver("close") { stack, storage -> storage.close(stack) }

    }

}