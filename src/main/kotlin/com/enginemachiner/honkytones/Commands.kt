package com.enginemachiner.honkytones

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource

// Check FileImpl.kt as well
object Commands : DedicatedServerModInitializer {

    @Environment(EnvType.CLIENT)
    fun client() {

        ClientCommandRegistrationCallback.EVENT.register {
                dispatcher: CommandDispatcher<FabricClientCommandSource>,
                _: CommandRegistryAccess ->

            val literal1 = ClientCommandManager.literal(Base.MOD_NAME)

            val boolKeys = Base.clientConfigKeys[ Boolean::class ]!!
            for ( key in boolKeys ) {
                val literal2 = ClientCommandManager.literal(key)
                val boolArgument = ClientCommandManager.argument( "bool", BoolArgumentType.bool() )
                val command = literal1.then( literal2.then( boolArgument.executes {
                    val b = BoolArgumentType.getBool(it, "bool")
                    clientConfig[key] = b;      0
                } ) )
                dispatcher.register(command)
            }

            val intKeys = Base.clientConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {
                val literal2 = ClientCommandManager.literal(key)
                val intArgument = ClientCommandManager.argument( "int", IntegerArgumentType.integer() )
                val command = literal1.then( literal2.then( intArgument.executes {

                    var allow = true
                    val i = IntegerArgumentType.getInteger(it, "int")

                    val error = Translation.get("honkytones.error.$key")

                    if ( key == "audio_quality" ) allow = i in 0..10
                    if ( key == "max_length" ) allow = i > 0

                    if (allow) { clientConfig[key] = i }
                    else printMessage( it.source.player, error )

                    0

                } ) )
                dispatcher.register(command)
            }

            var literal2 = ClientCommandManager.literal("help")
            var literal3 = ClientCommandManager.literal("tips")
            var command = literal1.then( literal2.then( literal3.executes {

                var s = ""

                for ( i in 1 .. 11 ) { val tip = Translation.get("honkytones.help.tip$i");    s += "\n-$tip \n" }

                printMessage( it.source.player, s )

                0

            } ) )
            dispatcher.register(command)

            literal2 = ClientCommandManager.literal("help")
            literal3 = ClientCommandManager.literal("commands")
            command = literal1.then( literal2.then( literal3.executes {

                val clientHelp = mutableMapOf(

                    "writeDeviceInfo" to Translation.get("honkytones.help.write_device_info"),

                    "mobsParticles" to Translation.get("honkytones.help.mobs_particles"),

                    "playerParticles" to Translation.get("honkytones.help.player_particles"),

                    "restoreDefaults" to Translation.get("honkytones.help.restore_defaults"),

                    "keep_downloads" to Translation.get("honkytones.help.keep_downloads"),

                    "keep_videos" to Translation.get("honkytones.help.keep_videos"),

                    "audio_quality" to Translation.get("honkytones.help.audio_quality")

                )

                var s = ""

                for (message in clientHelp.entries) {
                    s += '\n' + "§6" + message.key + "§f - " + message.value + '\n'
                }

                printMessage( it.source.player, s )

                0

            } ) )
            dispatcher.register(command)

            literal2 = ClientCommandManager.literal("restoreDefaults")
            command = literal1.then( literal2.executes {

                Base.clientConfigFile.setDefaultProperties()
                clientConfig.clear()
                Base.buildClientConfigMaps()

                val s = Translation.get("honkytones.message.config_restore")
                printMessage( it.source.player, s )

                0

            } )
            dispatcher.register(command)

        }

    }

    private fun server() {

        CommandRegistrationCallback.EVENT.register {
                dispatcher: CommandDispatcher<ServerCommandSource>,
                _: CommandRegistryAccess,
                _: CommandManager.RegistrationEnvironment ->

            val literal1 = CommandManager.literal(Base.MOD_NAME)

            val boolKeys = Base.serverConfigKeys[ Boolean::class ]!!
            for ( key in boolKeys ) {
                val literal2 = CommandManager.literal(key)
                val boolArgument = CommandManager.argument( "bool", BoolArgumentType.bool() )
                val command = literal1.then( literal2.then( boolArgument.executes {
                    val b = BoolArgumentType.getBool(it, "bool")
                    serverConfig[key] = b;      0
                } ) )
                dispatcher.register(command)
            }

            val intKeys = Base.serverConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {
                val literal2 = CommandManager.literal(key)
                val intArgument = CommandManager.argument( "int", IntegerArgumentType.integer() )
                val min = serverConfig[key] as Int
                val command = literal1.then( literal2.then( intArgument.executes {

                    var allow = true
                    val i = IntegerArgumentType.getInteger(it, "int")

                    val error = Translation.get("honkytones.error.$key")

                    if ( key == "mobsPlayingDelay" ) allow = i >= min

                    if (allow) { serverConfig[key] = i }
                    else printMessage( it.source.player!!, error )

                    0

                } ) )
                dispatcher.register(command)
            }

            var literal2 = CommandManager.literal("help")
            val literal3 = CommandManager.literal("commands")
            var command = literal1.then( literal2.then( literal3.executes {

                val serverHelp = mutableMapOf(

                    "debugMode" to Translation.get("honkytones.help.debug_mode"),

                    "mobsPlayingDelay" to Translation.get("honkytones.help.mobs_playing_delay"),

                    "mobsParticles" to Translation.get("honkytones.help.mobs_particles"),

                    "playerParticles" to Translation.get("honkytones.help.player_particles"),

                    "restoreDefaults" to Translation.get("honkytones.help.restore_defaults")

                )

                var s = ""

                for (message in serverHelp.entries) {
                    s += '\n' + "§6" + message.key + "§f - " + message.value + '\n'
                }

                printMessage( it.source.player!!, s )

                0

            } ) )
            dispatcher.register(command)

            literal2 = CommandManager.literal("restoreDefaults")
            command = literal1.then( literal2.executes {

                Base.serverConfigFile.setDefaultProperties()
                serverConfig.clear()
                Base.buildServerConfigMaps()

                val s = Translation.get("honkytones.message.config_restore")
                printMessage( it.source.player!!, s )

                0

            } )
            dispatcher.register(command)

        }
    }

    override fun onInitializeServer() { server() }

}