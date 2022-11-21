package com.enginemachiner.honkytones.blocks.musicplayer

import com.enginemachiner.honkytones.*
import com.enginemachiner.honkytones.Base.Companion.registerBlock
import com.enginemachiner.honkytones.items.floppy.FloppyDisk
import com.enginemachiner.honkytones.items.instruments.Instrument
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.PacketSender
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory
import net.minecraft.block.Block
import net.minecraft.block.BlockEntityProvider
import net.minecraft.block.BlockState
import net.minecraft.block.Material
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.SpawnGroup
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.Inventories
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.network.Packet
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.server.MinecraftServer
import net.minecraft.server.ServerTask
import net.minecraft.server.network.ServerPlayNetworkHandler
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import java.io.File
import java.util.*
import javax.sound.midi.MidiSystem
import javax.sound.midi.Sequencer

class MusicPlayerBlock(settings: Settings) : Block(settings), BlockEntityProvider {

    companion object {

        fun useBlockCallback( player: PlayerEntity, world: World,
                              blockHitResult: BlockHitResult ) {

            if ( !world.isClient ) {

                // The scheduler knows when the block is on use

                val pos = blockHitResult.blockPos
                val filter = RedstoneTracerData.traceBlocks
                val formerBlock = world.getBlockState( pos ).block
                logger.info(formerBlock)
                val b = world.blockTickScheduler.isScheduled(pos, formerBlock)
                if ( !RedstoneTracerData.isBlockOnFilter(formerBlock, filter) || b ) return

                // From trigger block to music player
                val tempTrace = mutableSetOf<BlockPos>()
                writeBlockTrace(world, blockHitResult.blockPos, tempTrace)

                if ( tempTrace.isEmpty() ) return

                val lastPos = tempTrace.last()
                val lastBlockEntity = world.getBlockEntity( lastPos )
                if ( lastBlockEntity is MusicPlayerEntity ) {
                    lastBlockEntity.blocksTrace = tempTrace.reversed().toMutableSet()
                } else return

                val block = world.getBlockState( lastPos ).block as MusicPlayerBlock
                block.playFile(lastBlockEntity, player, world.server!!)

            }

        }

    }

}

class MusicPlayerEntity(pos: BlockPos, state: BlockState)
    : BlockEntity(type, pos, state), ExtendedScreenHandlerFactory, ImplementedInventory {

    var blocksTrace = mutableSetOf<BlockPos>()

}