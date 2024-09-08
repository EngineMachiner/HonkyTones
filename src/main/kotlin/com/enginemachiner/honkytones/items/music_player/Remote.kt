package com.enginemachiner.honkytones.items.music_player

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.NBT.trackSlot
import com.enginemachiner.honkytones.blocks.music_player.MusicPlayerBlockEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.ItemUsageContext
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Remote : Item( settings() ) {

    override fun clean(stack: ItemStack) {}

    override fun trackTick( stack: ItemStack, slot: Int ) { trackSlot(stack, slot) }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val action = super.use( world, user, hand );        if ( world.isClient ) return action


        val stack = user.getStackInHand(hand);          val nbt = nbt(stack)


        val onBlock = nbt.contains("onBlock")

        if ( onBlock ) nbt.remove("onBlock")

        if ( !nbt.contains("BlockPos") || onBlock ) return action


        val pos = NBT.blockPos(stack)

        val blockEntity = MusicPlayerBlockEntity.get( world, pos ) ?: return action

        Timer(1) { user.openHandledScreen(blockEntity) };        return action

    }

    override fun useOnBlock( context: ItemUsageContext ): ActionResult {

        val action = super.useOnBlock(context)

        val stack = context.stack;      val nbt = nbt(stack)

        val player = context.player ?: return action

        val blockEntity = context.world.getBlockEntity( context.blockPos )

        if ( blockEntity is MusicPlayerBlockEntity || !player.isSneaking ) return action


        nbt.putBoolean( "onBlock", true )

        val contains = nbt.contains("BlockPos")

        if (contains) sendMessage( Translations.unlink, player )

        nbt.remove("BlockPos")


        return action

    }

    companion object {

        private const val DAMAGE = 20

        private object Translations {

            val link = Translation.item("remote.link")
            val unlink = Translation.item("remote.unlink")

        }

        private fun settings(): Settings { return modItemSettings().maxDamage(DAMAGE) }

        private fun sendMessage( s: String, player: PlayerEntity? ) {

            Message( s, player ).send(true)

        }

        fun link( pos: BlockPos, player: PlayerEntity ): Boolean {

            val stack = player.itemsHand.find { it.item is Remote } ?: return false

            if ( stack.item !is Remote ) return false


            val player = stack.holder as PlayerEntity;          val world = player.world

            if ( world.isClient ) return false


            val nbt = nbt(stack);       val posString = pos.toShortString()

            nbt.putString( "BlockPos", posString );         sendMessage( Translations.link, player )

            return true

        }

    }

}