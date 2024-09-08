package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

object Commands {

    val arguments = Command.Arguments

    fun register() { Server.bool().int().help().restore() }

    fun tip( i: Int ): String {

        val tip = "/@help.tip$i/@";    return "\n- $tip \n"

    }

    fun description( commandName: String, translationKey: String ): String {

        return "\n§6$commandName§f - /@$translationKey/@\n"

    }

    private object Server {

        val server = Command.Server;        val config = Config.SERVER

        fun warn( message: String, ctx: CommandContext<ServerCommandSource> ) {

            Message( message, ctx.source.player ).send()

        }


        /** Register commands with boolean arguments. */
        fun bool(): Server {

            val type = arguments.bool();        val keys = config.keys( Boolean::class )

            server.register { dispatcher, main ->

                for ( key in keys ) {

                    val sub = server.literal(key)


                    val argument = server.argument(type).executes {

                        config.set( key, arguments.bool(it) ); 0

                    }


                    val command = main.then( sub.then( argument ) )

                    dispatcher.register(command)

                }

            }

            return Server

        }


        /** Register commands with integer arguments. */
        fun int(): Server {

            val type = arguments.int();         val keys = config.keys( Int::class )

            val min = ServerConfigFile.MOBS_PLAYING_DELAY

            server.register { dispatcher, main ->

                val mobsPlayingKey = "mobs_playing_delay"

                for ( key in keys ) {

                    val sub = server.literal(key)

                    val argument = server.argument(type).executes {

                        val i = arguments.int(it)


                        if ( key == mobsPlayingKey && i < min ) {

                            warn( "error.out_of_range", it );   return@executes -1

                        }


                        config.set( key, i ); 0

                    }


                    val command = main.then( sub.then( argument ) )

                    dispatcher.register(command)

                }

            }

            return Server

        }

        /** Register help commands. */
        fun help(): Server {

            fun sub(): Literal { return server.literal("help") }

            server.register { dispatcher, main ->

                val end = server.literal("tips").executes {

                    var s = "";         for ( i in 1..10 ) s += tip(i)

                    warn( s, it ); 0

                }

                val command = main.then( sub().then( end ) )

                dispatcher.register(command)

            }


            server.register { dispatcher, main ->

                val end = server.literal("commands").executes {

                    val key = "restore_defaults"

                    var s = description( key, "help.$key" )


                    config.keys().forEach {

                        val translationKey = "help.$it"

                        if ( !Translation.has( translationKey ) ) return@forEach

                        s += description( it, translationKey )

                    }


                    warn( s, it ); 0

                }


                val command = main.then( sub().then( end ) )

                dispatcher.register(command)

            }

            return Server

        }

        /** Registers restore to defaults command. */
        fun restore(): Server {

            server.register { dispatcher, main ->

                val final = server.literal("restoreDefaults").executes {

                    config.setDefaults();       warn( "message.config_restore", it ); 0

                }

                val command = main.then(final)

                dispatcher.register(command)

            }

            return Server

        }

    }

}