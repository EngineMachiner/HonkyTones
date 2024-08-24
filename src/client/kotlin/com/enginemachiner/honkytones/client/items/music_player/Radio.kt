package com.enginemachiner.honkytones.client.items.music_player

import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.Translation
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.honkytones.client.blocks.music_player.MusicPlayer
import com.enginemachiner.honkytones.items.music_player.RadioItem.Companion.registeredItem
import net.minecraft.item.ItemStack

object Radio : ModID, ColorItem {

    private object Translations {

        val link = Translation.item("radio.link")
        val unlink = Translation.item("radio.unlink")

    }

    private var current: MusicPlayer? = null

    private fun clear() { current?.removeRadio();          current = null }

    fun removeUse( radio: ItemStack? ) {

        val radio = radio ?: return;        val nbt = nbt(radio)

        nbt.remove("onUse");        send(nbt)

    }

    fun unlink( id: Int ) {

        if ( current?.id != id ) return

        Message( Translations.unlink ).send(true);          clear()

    }

    private fun link(slot: Int) {

        val stack = inventory().getStack(slot);         val nbt = nbt(stack)

        val id = nbt.getInt("PlayerID")

        if ( current?.id == id ) return


        Message( Translations.link ).send(true)

        val player = MusicPlayer.get(id)


        current = player;           player.radio = stack

    }

    fun registerColorProvider() { registerColorProvider(registeredItem) }

    fun networking() {

        var id = netID("link")

        Receiver(id).register {

            val slot = it.readInt();        client().send { link(slot) }

        }


        id = netID("unlink")

        Receiver(id).register {

            val id = it.readInt();        client().send { unlink(id) }

        }


        id = netID("clear")

        Receiver(id).register {  client().send { clear() }  }

    }

}