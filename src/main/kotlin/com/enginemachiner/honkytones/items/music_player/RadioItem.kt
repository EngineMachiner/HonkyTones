package com.enginemachiner.honkytones.items.music_player

import com.enginemachiner.harmony.*
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.NBT.trackSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.ToolMaterial
import net.minecraft.nbt.NbtCompound
import net.minecraft.recipe.Ingredient
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Hand
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import java.awt.Color
import kotlin.random.Random

class RadioItem : ToolItem( material, settings() ), ColorItem {

    override fun className(): String { return RadioItem.className() }

    private fun trackDamage(stack: ItemStack) {

        val nbt = nbt(stack);       if ( !nbt.contains("onUse") ) return

        damage(stack)

    }

    private fun onSneak( stack: ItemStack ): Boolean {

        val player = stack.holder as PlayerEntity

        if ( !player.isSneaking ) return false


        playTuneSound(player)

        val nbt = nbt(stack);       unlink(stack);      nbt.remove("PlayerID")

        return true

    }

    override fun use( world: World, user: PlayerEntity, hand: Hand ): TypedActionResult<ItemStack> {

        val stack = user.getStackInHand(hand);           val nbt = nbt(stack)

        val action = super.use( world, user, hand );        val contains = nbt.contains("PlayerID")

        if ( world.isClient || !contains || onSneak(stack) ) return action


        clear(user);        link(stack);        return action

    }

    override fun trackTick( stack: ItemStack, slot: Int ) {

        trackSlot(stack, slot);     trackDamage(stack)

    }

    override fun getSetupNBT(stack: ItemStack): NbtCompound {

        return setColor( NbtCompound() )

    }

    override fun color(): Color {

        val h = Random.nextInt( 100 + 1 ) * 0.01f
        val b = Random.nextInt( 75, 100 + 1 ) * 0.01f

        return Color.getHSBColor( h, 0.4f, b )

    }

    companion object : ModID {

        private val material = RedstoneBattery()

        lateinit var registeredItem: RadioItem

        private val tuneInSounds = mutableListOf<SoundEvent>()

        private const val PLAY_TICKS = 20000 // Around 15 minutes.

        override fun className(): String { return "radio" }

        fun registerItem() {

            registeredItem = RadioItem();         Register.item(registeredItem)

        }

        fun registerSound() {

            for ( i in 1 .. 3 ) {

                val sound = Register.sound("radio$i")

                tuneInSounds.add(sound)

            }

        }

        private fun playTuneSound( player: PlayerEntity ) {

            val world = player.world;           val category = SoundCategory.MASTER

            val volume = 1f;           val pitch = ( 75..125 ).random() * 0.01f

            world.playSoundFromEntity( null, player, tuneInSounds.random(), category, volume, pitch )

        }

        private fun settings(): Settings {

            return modItemSettings().maxDamage( PLAY_TICKS )

        }

        private fun clear( player: PlayerEntity ) {

            val netID = netID("clear");         Sender(netID).toClient(player)

        }

        fun unlink( stack: ItemStack ) {

            val nbt = nbt(stack);       val player = stack.holder as PlayerEntity

            if ( !nbt.contains("PlayerID") ) return


            val id = nbt.getInt("PlayerID");        val netID = netID("unlink")

            val sender = Sender(netID) { it.write(id) };        sender.toClient(player)

        }

        fun blockUse( id: Int, player: PlayerEntity ): Boolean {

            val stack = player.handItems.find { it.item is RadioItem } ?: return false

            if ( stack.item !is RadioItem ) return false


            val nbt = nbt(stack)

            val player = stack.holder as PlayerEntity;      val world = player.world

            if ( world.isClient ) return true


            nbt.putInt( "PlayerID", id );       clear(player);        link(stack)


            return true

        }

        fun link( stack: ItemStack ) {

            val nbt = nbt(stack);       val slot = nbt.getInt("Slot")

            val netID = netID("link");      nbt.putBoolean( "onUse", true )

            val sender = Sender(netID) { it.write(slot) }

            val player = stack.holder as PlayerEntity;          playTuneSound(player)

            Timer(1) { sender.toClient(player) }

        }

        private class RedstoneBattery : ToolMaterial {

            override fun getDurability(): Int { return 0 }
            override fun getMiningSpeedMultiplier(): Float { return 0f }
            override fun getAttackDamage(): Float { return 0f }
            override fun getMiningLevel(): Int { return 0 }
            override fun getEnchantability(): Int { return 0 }
            override fun getRepairIngredient(): Ingredient { return Ingredient.ofItems( Items.REDSTONE ) }

        }

    }

}