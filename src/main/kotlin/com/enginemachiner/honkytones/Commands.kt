package com.enginemachiner.honkytones

import com.enginemachiner.harmony.*
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource

object Commands {

    val arguments = Command.Arguments

    fun register() {

        Server.bool().int().help().restore()

        if ( !isClient() ) return

        Client.bool().int().help().restore().path()

    }

    private fun tip( i: Int ): String {

        val tip = "#help.tip$i#";    return "\n- $tip \n"

    }

    private fun description( commandName: String, translationKey: String ): String {

        return "\n§6$commandName§f - #$translationKey#\n"

    }

    // @Environment(EnvType.CLIENT)
    private object Client {

        val client = Command.Client;        val config = Config.CLIENT


        /** Register commands with boolean arguments. */
        fun bool(): Client {

            val type = arguments.bool();        val keys = config.keys( Boolean::class )

            client.register { dispatcher, main ->

                for ( key in keys ) {

                    val sub = client.literal(key)


                    val argument = client.argument(type).executes {

                        config.set( key, arguments.bool(it) ); 0

                    }


                    val command = main.then( sub.then( argument ) )

                    dispatcher.register(command)

                }

            }

            return Client

        }


        /** Register commands with integer arguments. */
        fun int(): Client {

            val type = arguments.int();         val keys = config.keys( Int::class )

            client.register { dispatcher, main ->

                for ( key in keys ) {

                    val sub = client.literal(key)

                    val argument = client.argument(type).executes {

                        val i = arguments.int(it)


                        if ( key == "max_length" && i <= 0 ) {

                            warnUser("error.out_of_range"); return@executes 0

                        }


                        config.set( key, i ); 0

                    }


                    val command = main.then( sub.then( argument ) )

                    dispatcher.register(command)

                }

            }

            return Client

        }

        /** Register help commands. */
        fun help(): Client {

            fun sub(): ClientLiteral { return client.literal("help") }

            client.register { dispatcher, main ->

                val end = client.literal("tips").executes {

                    var s = "";         for ( i in 1..10 ) s += tip(i)

                    warnUser(s); 0

                }

                val command = main.then( sub().then( end ) )

                dispatcher.register(command)

            }


            client.register { dispatcher, main ->

                val end = client.literal("commands").executes {

                    val key = "restore_defaults"

                    var s = description( key, "help.$key" )


                    config.keys().forEach {

                        val translationKey = "help.$it"

                        if ( !Translation.has( translationKey ) ) return@forEach

                        s += description( it, translationKey )

                    }


                    warnUser(s); 0

                }


                val command = main.then( sub().then( end ) )

                dispatcher.register(command)

            }

            return Client

        }

        /** Registers restore to defaults command. */
        fun restore(): Client {

            client.register { dispatcher, main ->

                val final = client.literal("restoreDefaults").executes {

                    config.setDefaults()

                    warnUser("message.config_restore"); 0

                }

                val command = main.then(final);         dispatcher.register(command)

            }

            return Client

        }

        fun path() {

            val type = arguments.string();      val data = config.data()

            client.register { dispatcher, main ->

                val ffmpeg = client.literal("ffmpeg_directory")

                var argument = client.argument(type).executes {

                    val next = arguments.string(it)

                    data.ffmpegDirectory = next;        config.toMap()

                    FFmpeg.refresh(); 0

                }


                var command = main.then( ffmpeg.then(argument) )

                dispatcher.register(command)


                val ytdlp = client.literal("ytdlp_path")

                argument = client.argument(type).executes {

                    val next = arguments.string(it)

                    data.ytdlpPath = next;      config.toMap()

                    YTDLP.refresh(); 0

                }


                command = main.then( ytdlp.then(argument) )

                dispatcher.register(command)

            }

        }

    }

    private object Server {

        val server = Command.Server;        val config = Config.SERVER

        fun warn( s: String, ctx: CommandContext<ServerCommandSource> ) {

            warnUser( ctx.source.player, s )

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

                            warn( "error.out_of_range", it );   return@executes 0

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