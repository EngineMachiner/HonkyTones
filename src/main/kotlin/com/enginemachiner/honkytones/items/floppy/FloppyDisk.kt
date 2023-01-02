package com.enginemachiner.honkytones.items.floppy

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.getVideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World

class FloppyDisk : Item( createDefaultItemSettings().maxDamage( seed ) ) {

    override fun inventoryTick( stack: ItemStack?, world: World?, entity: Entity?,
                                slot: Int, selected: Boolean ) {

        if ( entity!!.isPlayer ) {

            if ( !world!!.isClient ) {

                var tag = stack!!.orCreateTag
                if ( !tag.contains(Base.MOD_NAME) ) {
                    resetSeed();   loadNbtData(stack, entity)
                }

                tag = tag.getCompound(Base.MOD_NAME)

                trackHandOnNbt(stack, entity)
                trackPlayerOnNbt(tag, entity, world)

                if ( tag.contains("isDone") && entity is LivingEntity ) {
                    tag.remove("isDone")
                    stack.damage( 5, entity ) { sendStatus(it, stack) }
                }

            } else {

                var tag = stack!!.orCreateTag
                tag = tag.getCompound(Base.MOD_NAME)

                // Name sync
                if ( tag.contains("queryInterrupted") || tag.contains("yt-dlp") ) {

                    //println( "yt-dlp " + nbt.contains("yt-dlp") )
                    //println( "queryInterrupted " + nbt.contains("queryInterrupted") )

                    if ( tag.contains("yt-dlp") ) tag.remove("yt-dlp")
                    if ( tag.contains("queryInterrupted") ) tag.remove("queryInterrupted")

                    tag.putBoolean("onQuery", true)
                    Network.sendNbtToServer(tag)

                    val handIndex = tag.getInt("hand")
                    coroutine.launch {

                        Thread.currentThread().name = "FloppyQuery thread"

                        val path = tag.getString("path")
                        val info = getVideoInfo(path) ?: return@launch

                        entity as PlayerEntity
                        if ( !entity.inventory.contains(stack) ) return@launch
                        stack.setCustomName( Text.of( info.title ) )
                        writeDisplayOnNbt( stack, tag )

                        // Forced Swap
                        var id = Identifier(Base.MOD_NAME, "swap_floppy")
                        val buf = PacketByteBufs.create()
                        buf.writeInt( tag.getInt("id") )
                        buf.writeInt(handIndex)
                        ClientPlayNetworking.send( id, buf )

                        val screen = MinecraftClient.getInstance().currentScreen
                        if ( screen is FloppyDiskScreen ) {
                            id = Identifier(Base.MOD_NAME, "close_screen")
                            ClientPlayNetworking.send( id, PacketByteBufs.empty() )
                        }

                        tag.remove("onQuery")
                        Network.sendNbtToServer(tag)

                    }

                }

            }

        }

    }

    override fun use( world: World?, user: PlayerEntity?, hand: Hand?
    ): TypedActionResult<ItemStack> {

        val stack = user!!.getStackInHand(hand)

        if ( world!!.isClient ) {
            val client = MinecraftClient.getInstance()
            client.openScreen( FloppyDiskScreen(stack) )
        }
        return super.use(world, user, hand)

    }

    companion object {

        private var seed = (2..3).random()
        private val coroutine = CoroutineScope( Dispatchers.IO )

        fun fileNotFoundMsg(fileName: String): String {
            val missingMessage = Translation.get("honkytones.error.midi-missing")
            return "$fileName $missingMessage"
        }

        private fun resetSeed() { seed = (2..3).random() }

        fun networking() {

            var id = Identifier( Base.MOD_NAME, "swap_floppy" )
            ServerPlayNetworking.registerGlobalReceiver(id) {

                    server: MinecraftServer, player: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, buf: PacketByteBuf, _: PacketSender ->

                val hashId = buf.readInt()
                val handIndex = buf.readInt()

                server.send( ServerTask( server.ticks ) {

                    val hand = hands[handIndex]
                    val currentStack = player.getStackInHand(hand)

                    var floppy: ItemStack? = null
                    val inv = player.inventory

                    var slot = 0
                    for ( i in 0 until inv.size() ) {
                        val stack = inv.getStack(i)
                        val tag = stack.orCreateTag.getCompound( Base.MOD_NAME )
                        if ( tag.getInt("id") == hashId ) {
                            slot = i;   floppy = stack
                        }
                    }
                    if ( floppy == null ) return@ServerTask

                    player.inventory.setStack( slot, currentStack )
                    player.setStackInHand( hand, floppy )

                } )

            }

            id = Identifier( Base.MOD_NAME, "close_screen" )
            ServerPlayNetworking.registerGlobalReceiver(id) {

                    server: MinecraftServer, player: ServerPlayerEntity,
                    _: ServerPlayNetworkHandler, _: PacketByteBuf, _: PacketSender ->

                server.send( ServerTask( server.ticks ) {
                    player.closeScreenHandler()
                    player.closeHandledScreen()
                } )

            }

            if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return

        }

    }

    private fun loadNbtData( stack: ItemStack, entity: Entity ) {

        val nbt = NbtCompound()

        nbt.putString("PlayerUUID", entity.uuidAsString)

        nbt.putString("path", "");      nbt.putInt("seed", seed)
        nbt.putInt( "id", stack.hashCode() )

        // Midi files
        nbt.putFloat("Rate", 1f)
        nbt.putInt("timesWritten", 0)

        // Stream files
        nbt.putFloat("Volume", 1f)

        stack.tag!!.put(Base.MOD_NAME, nbt)

    }


}