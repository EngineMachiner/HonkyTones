package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import java.io.File
import java.nio.file.Paths

private fun envSearch( key: String, formerPath: String, directories: List<String> ): String? {

    for ( directory in directories ) {

        val path = Paths.get( directory, formerPath )

        val temp = "$path".replace( "\$$key\\", "" )
            .replace( "\$$key/", "" )

        if ( File(temp).canExecute() ) return temp

    }

    return null

}

/** Get the environment / system path if there is one. */
fun envPath( path: String, envKey: String ): String {

    if ( !path.startsWith( "\$" + envKey ) ) return path

    val sysPath = System.getenv(envKey)

    // Windows and Linux separators.
    for ( separator in listOf( ";", ":" ) ) {

        val directories = sysPath.split(separator)

        val envPath = envSearch( envKey, path, directories )

        if ( envPath != null ) return envPath

    }

    return path

}

/** Files that are in the honkytones folder. Like midi files. */
open class ModFile( private val name: String ) : File(name) {

    init { init() }

    private fun init() {

        val name = name.replace( "/", "\\" )

        if ( name.startsWith("$MOD_NAME\\") || name.isEmpty() ) return

        warnUser( Translation.get("error.denied") )

        setExecutable(false);     setReadable(false);     setWritable(false)

    }

}
