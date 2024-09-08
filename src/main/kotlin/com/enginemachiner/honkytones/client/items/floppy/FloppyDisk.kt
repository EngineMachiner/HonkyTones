package com.enginemachiner.honkytones.client.items.floppy

import com.enginemachiner.harmony.Coroutine
import com.enginemachiner.harmony.ModID
import com.enginemachiner.harmony.NBT
import com.enginemachiner.harmony.NBT.nbt
import com.enginemachiner.harmony.NBT.trackSlot
import com.enginemachiner.harmony.client.*
import com.enginemachiner.harmony.client.NBT.saveDisplay
import com.enginemachiner.harmony.client.NBT.send
import com.enginemachiner.harmony.modID
import com.enginemachiner.honkytones.client.YTDLP
import com.enginemachiner.honkytones.client.registerHandReceiver
import com.enginemachiner.honkytones.items.FloppyDisk.Companion.isState
import com.enginemachiner.honkytones.items.FloppyDisk.Companion.registeredItem
import com.enginemachiner.honkytones.items.FloppyDisk.Companion.setState
import kotlinx.coroutines.Job
import net.minecraft.item.ItemStack
import net.minecraft.text.Text

object FloppyDisk : ModID, ColorItem {

    private val coroutine = Coroutine("Floppy Disk")

    private val jobs = mutableMapOf<ItemStack, Job>()

    private fun cancelJob( stack: ItemStack ) {

        val job = jobs[stack] ?: return;        job.cancel()

    }

    /** Queries the source title when requested. */
    private fun query( stack: ItemStack, slot: Int ) {

        if ( stack.isEmpty || !NBT.has(stack) ) return


        trackSlot( stack, slot )

        val player = player();          val nbt = nbt(stack)

        val isState = isState( stack, "Processing" )

        if ( !YTDLP.exists() || isState ) return


        setState( stack, "Processing" );        send(nbt)


        cancelJob(stack)

        jobs[stack] = coroutine.launch {

            val path = nbt.getString("Path")

            val info = YTDLP(path).info ?: return@launch


            val slot = nbt.getInt("Slot")

            val stack2 = player.inventory.getStack(slot)

            val isSame = !stack2.isEmpty && NBT.equals( stack, stack2 ) && isState( stack2, "Processing" )

            if ( !isSame ) return@launch


            val name = Text.of( info.title );       stack2.setCustomName(name)

            saveDisplay(stack2, nbt);         nbt.remove("nameState")


            closeScreen();      send(nbt)

        }

    }

    private fun closeScreen() {

        val screen = currentScreen() ?: return;         if ( screen.isPauseScreen ) return

        val id = modID("close_screen");         Sender(id).toServer()

    }

    fun registerColorProvider() { registerColorProvider(registeredItem) }

    fun networking() {

        var netID = netID("set_screen")

        registerHandReceiver(netID) {

            val screen = FloppyDiskScreen(it);      client().setScreen(screen)

        }


        netID = netID("query")

        Receiver(netID).register {

            val slot = it.readInt()


            client().send { val stack = stack(slot);        query(stack, slot) }

        }

    }

}