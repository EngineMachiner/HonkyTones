package com.enginemachiner.honkytones.client

import com.enginemachiner.harmony.client.Command
import com.enginemachiner.harmony.Translation
import com.enginemachiner.harmony.client.ClientLiteral
import com.enginemachiner.harmony.client.sendMessage
import com.enginemachiner.honkytones.Commands.arguments
import com.enginemachiner.honkytones.Commands.description
import com.enginemachiner.honkytones.Commands.tip

object Commands {

    fun register() { Client.bool().int().help().restore().path().midi() }

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

                            sendMessage("error.out_of_range"); return@executes -1

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

                    sendMessage(s); 0

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


                    sendMessage(s); 0

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

                    sendMessage("message.config_restore"); 0

                }

                val command = main.then(final);         dispatcher.register(command)

            }

            return Client

        }

        fun path(): Client {

            val type = arguments.string();      val data = config.data()

            client.register { dispatcher, main ->

                val ffmpeg = client.literal("ffmpegDirectory")

                var argument = client.argument(type).executes {

                    val next = arguments.string(it)

                    data.ffmpegDirectory = next;        config.toMap()

                    FFmpeg.refresh(); 0

                }


                var command = main.then( ffmpeg.then(argument) )

                dispatcher.register(command)


                val ytdlp = client.literal("ytdlpPath")

                argument = client.argument(type).executes {

                    val next = arguments.string(it)

                    data.ytdlpPath = next;      config.toMap()

                    YTDLP.refresh(); 0

                }


                command = main.then( ytdlp.then(argument) )

                dispatcher.register(command)

            }

            return Client

        }

        fun midi(): Client {

            client.register { dispatcher, main ->

                val reloadDevices = client.literal("reloadDevices").executes {

                    sendMessage("message.midi_reload")

                    MIDI.setup(); 0

                }


                val command = main.then(reloadDevices)

                dispatcher.register(command)

            }

            return Client

        }

    }

}