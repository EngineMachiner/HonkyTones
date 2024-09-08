package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.client.Receiver
import com.enginemachiner.harmony.client.client
import com.enginemachiner.harmony.client.player
import com.enginemachiner.harmony.hands
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier

fun registerHandReceiver( id: Identifier, function: (ItemStack) -> Unit ) {

    Receiver(id).register {

        val handIndex = it.readInt()


        client().send {

            val hand = hands[handIndex];        val stack = player().getStackInHand(hand)

            function(stack)

        }

    }

}