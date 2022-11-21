package com.enginemachiner.honkytones

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.fabricmc.api.DedicatedServerModInitializer
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource

// Check FileImpl.kt as well

object Commands : DedicatedServerModInitializer {

    private val clientHelp = mutableMapOf(

        "writeDeviceInfo" to "Overwrite the instrument name with " +
                "the MIDI device name and channel.",

        "mobsParticles" to "Enable / Disable mob particles",

        "playerParticles" to "Enable / Disable player particles",

        "restoreDefaults" to "Restore commands settings / config to their default values.",

        "keep_downloads" to "Keep downloads permanently when requesting web content",

        "keep_videos" to "Keep videos when requesting web content",

        "audio_quality" to "Choose from 1 to 10"

    )

    private val serverHelp = mutableMapOf(

        "debugMode" to "Show debug messages on console and networking stuff.",

        "mobsPlayingDelay" to "Change the delay of mobs playing (Has to be > 120).",

        "mobsParticles" to "Enable / Disable mob particles",

        "playerParticles" to "Enable / Disable player particles",

        "restoreDefaults" to "Restore commands settings / config to their default values."

    )

    fun client() {

        val clientDispatcher = ClientCommandManager.DISPATCHER

        val cl1 = ClientCommandManager.literal(Base.MOD_NAME)

        val boolKeys = Base.clientConfigKeys[ Boolean::class ]!!
        for ( key in boolKeys ) {
            val cl2 = ClientCommandManager.literal(key)
            val cArgBool = ClientCommandManager.argument( "bool", BoolArgumentType.bool() )
            val cCommand = cl1.then( cl2.then( cArgBool.executes {
                val b = BoolArgumentType.getBool(it, "bool")
                clientConfig[key] = b;      0
            } ) )
            clientDispatcher.register(cCommand)
        }

        val intKeys = Base.clientConfigKeys[ Int::class ]!!
        for ( key in intKeys ) {
            val cl2 = ClientCommandManager.literal(key)
            val cArgInt = ClientCommandManager.argument( "int", IntegerArgumentType.integer() )
            val cCommand = cl1.then( cl2.then( cArgInt.executes {

                var b = true;       var error = "ERROR"
                val i = IntegerArgumentType.getInteger(it, "int")

                if ( key == "audio_quality" ) {
                    b = i in 0..10
                    error = "Number is not between 0 and 10"
                }

                if (b) { clientConfig[key] = i }
                else printMessage( it.source.player, error )

                0

            } ) )
            clientDispatcher.register(cCommand)
        }

        var cl2 = ClientCommandManager.literal("help")
        var cl3 = ClientCommandManager.literal("tips")
        var cCommand = cl1.then( cl2.then( cl3.executes {

            var s = "\n- If you click the clean button on the menu§2 3 times§f you "
            s += "can reset the instrument's name. \n"

            s += "\n- Instruments might have§2 other uses§f than just playing sounds. \n"
            s += "\n- You can§2 force attack§f interactive mobs by§2 sneaking§f while attacking. \n"

            s += "\n- Floppy disk §2local midi§f files will be localized by " +
                    "the§2 last player§f that picked it up. \n"

            s += "\n- A floppy track can be stopped by dragging it out of the slot in" +
                    "the Music Player"

            s += "\n- Play / pause can be done with redstone trigger"

            s += "\n- Music players can play less than 15 min streams and midi files \n"

            s += "\n- A digital console can record midi and play with the keyboard input" +
                    " in case you don't have controller \n"

            s += "\n- A musical storage can store honkytones stacks and has a couple more" +
                    " internal features \n"

            s += "\n- You can use the player's sound slider to change the global mod volume \n"

            s += "\n- You can use mute players by using an instrument and sneaking \n"

            printMessage( it.source.player, s )

            0

        } ) )
        clientDispatcher.register(cCommand)

        cl2 = ClientCommandManager.literal("help")
        cl3 = ClientCommandManager.literal("commands")
        cCommand = cl1.then( cl2.then( cl3.executes {

            var s = ""

            for (msg in clientHelp.entries) {
                s += '\n' + "§6" + msg.key + "§f - " + msg.value + '\n'
            }

            printMessage( it.source.player, s )

            0

        } ) )
        clientDispatcher.register(cCommand)

        cl2 = ClientCommandManager.literal("restoreDefaults")
        cCommand = cl1.then( cl2.executes {

            Base.clientConfigFile.storeProperties()
            clientConfig.clear()
            Base.buildConfigMaps()

            val s = "HonkyTones config restored!"
            printMessage( it.source.player, s )

            0

        } )
        clientDispatcher.register(cCommand)

    }

    private fun server() {

        CommandRegistrationCallback.EVENT.register {
                dispatcher: CommandDispatcher<ServerCommandSource>, _: Boolean ->

            val sl1 = CommandManager.literal(Base.MOD_NAME)

            val boolKeys = Base.serverConfigKeys[ Boolean::class ]!!
            for ( key in boolKeys ) {
                val sl2 = CommandManager.literal(key)
                val sArgBool = CommandManager.argument( "bool", BoolArgumentType.bool() )
                val cCommand = sl1.then( sl2.then( sArgBool.executes {
                    val b = BoolArgumentType.getBool(it, "bool")
                    serverConfig[key] = b;      0
                } ) )
                dispatcher.register(cCommand)
            }

            val intKeys = Base.serverConfigKeys[ Int::class ]!!
            for ( key in intKeys ) {
                val sl2 = CommandManager.literal(key)
                val sArgInt = CommandManager.argument( "int", IntegerArgumentType.integer() )
                val min = serverConfig[key] as Int
                val sCommand = sl1.then( sl2.then( sArgInt.executes {

                    var b = true;   var error = "ERROR"
                    val i = IntegerArgumentType.getInteger(it, "int")

                    if ( key == "mobsPlayingDelay" ) {
                        b = i >= min
                        error = "Number has to be minimum 120"
                    }

                    if (b) { serverConfig[key] = i }
                    else printMessage( it.source.player, error )

                    0

                } ) )
                dispatcher.register(sCommand)
            }

            var sl2 = CommandManager.literal("help")
            val sl3 = CommandManager.literal("commands")
            var sCommand = sl1.then( sl2.then( sl3.executes {

                var s = ""

                for (msg in serverHelp.entries) {
                    s += '\n' + "§6" + msg.key + "§f - " + msg.value + '\n'
                }

                printMessage( it.source.player, s )

                0

            } ) )
            dispatcher.register(sCommand)

            sl2 = CommandManager.literal("restoreDefaults")
            sCommand = sl1.then( sl2.executes {

                Base.serverConfigFile.storeProperties()
                serverConfig.clear()
                Base.buildConfigMaps()

                val s = "HonkyTones config restored!"
                printMessage( it.source.player, s )

                0

            } )
            dispatcher.register(sCommand)

        }
    }

    override fun onInitializeServer() { server() }

}