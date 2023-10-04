package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
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
import net.minecraft.util.Language

class Commands : DedicatedServerModInitializer {

    override fun onInitializeServer() { server() }

    companion object {

        @Environment(EnvType.CLIENT)
        fun client() {

            ClientCommandRegistrationCallback.EVENT.register {

                    dispatcher: CommandDispatcher<FabricClientCommandSource>,
                    _: CommandRegistryAccess ->

                val literal1 = ClientCommandManager.literal(MOD_NAME)

                val boolKeys = clientConfigKeys[ Boolean::class ]!!
                for ( key in boolKeys ) {

                    val literal2 = ClientCommandManager.literal(key)
                    val boolArgument = ClientCommandManager.argument( "bool", BoolArgumentType.bool() )

                    val command = literal1.then( literal2.then( boolArgument.executes {

                        val bool = BoolArgumentType.getBool( it, "bool" )
                        clientConfig[key] = bool;      0

                    } ) )

                    dispatcher.register(command)

                }

                val intKeys = clientConfigKeys[ Int::class ]!!
                for ( key in intKeys ) {

                    val literal2 = ClientCommandManager.literal(key)
                    val intArgument = ClientCommandManager.argument( "int", IntegerArgumentType.integer() )

                    val command = literal1.then( literal2.then( intArgument.executes {

                        var allow = true
                        val i = IntegerArgumentType.getInteger( it, "int" )

                        val error = Translation.get("error.$key")

                        if ( key == "audio_quality" ) allow = i in 1..10
                        if ( key == "max_length" ) allow = i > 0

                        if (allow) clientConfig[key] = i else warnUser(error)

                        0

                    } ) )

                    dispatcher.register(command)

                }

                var literal2 = ClientCommandManager.literal("help")
                var literal3 = ClientCommandManager.literal("tips")
                var command = literal1.then( literal2.then( literal3.executes {

                    var s = ""

                    for ( i in 1..10 ) {

                        val tip = Translation.get("help.tip$i");    s += "\n-$tip \n"

                    }

                    warnUser(s);      0

                } ) )

                dispatcher.register(command)

                literal2 = ClientCommandManager.literal("help")
                literal3 = ClientCommandManager.literal("commands")
                command = literal1.then( literal2.then( literal3.executes {

                    val key = "restore_defaults";       val message = Translation.get("help.$key")

                    var s = '\n' + "§6" + key + "§f - " + message + '\n'

                    clientConfig.keys.forEach {

                        val key = "help.$it"

                        if ( !Translation.has(key) ) return@forEach

                        val message = Translation.get(key)

                        s += '\n' + "§6" + it + "§f - " + message + '\n'

                    }

                    warnUser(s);      0

                } ) )

                dispatcher.register(command)

                literal2 = ClientCommandManager.literal("restoreDefaults")
                command = literal1.then( literal2.executes {

                    clientConfigFile.setDefaultProperties();    clientConfig.clear()

                    readClientConfig();     val s = Translation.get("message.config_restore")

                    warnUser(s);          0

                } )

                dispatcher.register(command)

            }

        }

        private fun server() {

            CommandRegistrationCallback.EVENT.register {

                    dispatcher: CommandDispatcher<ServerCommandSource>,
                    _: CommandRegistryAccess, _: CommandManager.RegistrationEnvironment ->

                val literal1 = CommandManager.literal(MOD_NAME)

                val boolKeys = serverConfigKeys[ Boolean::class ]!!
                for ( key in boolKeys ) {

                    val literal2 = CommandManager.literal(key)
                    val boolArgument = CommandManager.argument( "bool", BoolArgumentType.bool() )

                    val command = literal1.then( literal2.then( boolArgument.executes {

                        val b = BoolArgumentType.getBool( it, "bool" )
                        serverConfig[key] = b;      0

                    } ) )

                    dispatcher.register(command)

                }

                val intKeys = serverConfigKeys[ Int::class ]!!
                for ( key in intKeys ) {

                    val min = serverConfig[key] as Int
                    val literal2 = CommandManager.literal(key)
                    val intArgument = CommandManager.argument( "int", IntegerArgumentType.integer() )
                    val command = literal1.then( literal2.then( intArgument.executes {

                        var allow = true
                        val i = IntegerArgumentType.getInteger( it, "int" )

                        val error = Translation.get("error.$key")

                        if ( key == "mobs_playing_delay" ) allow = i >= min

                        if (allow) serverConfig[key] = i
                        else warnPlayer( it.source.player!!, error )

                        0

                    } ) )

                    dispatcher.register(command)

                }

                var literal2 = CommandManager.literal("help")
                val literal3 = CommandManager.literal("commands")
                var command = literal1.then( literal2.then( literal3.executes {

                    val key = "restore_defaults";       val message = Translation.get("help.$key")

                    var s = '\n' + "§6" + it + "§f - " + message + '\n'

                    serverConfig.keys.forEach {

                        val key = "help.$it"

                        if ( !Language.getInstance().hasTranslation(key) ) return@forEach

                        val message = Translation.get(key)

                        s += '\n' + "§6" + it + "§f - " + message + '\n'

                    }

                    warnPlayer( it.source.player!!, s );        0

                } ) )

                dispatcher.register(command)

                literal2 = CommandManager.literal("restoreDefaults")
                command = literal1.then( literal2.executes {

                    serverConfigFile.setDefaultProperties();    serverConfig.clear()

                    readServerConfig();     val s = Translation.get("message.config_restore")

                    warnPlayer( it.source.player!!, s );        0

                } )

                dispatcher.register(command)

            }

        }

    }

}