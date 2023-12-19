package com.enginemachiner.honkytones

import com.enginemachiner.honkytones.Init.Companion.MOD_NAME
import org.apache.commons.lang3.SystemUtils
import java.io.File

/** Get the environment / system path if there is one. */
fun envPath(path: String): String {

    if ( path[0] != '$' ) return path

    val envKey = Regex("\\$[A-Z]*").find(path)!!.value

    val subPath = path.replace( "$envKey/", "" )

    val directories = System.getenv( envKey.substring(1) ) ?: return path

    var separator = ':';    if ( SystemUtils.IS_OS_WINDOWS ) separator = ';'

    val list = directories.split(separator)

    list.forEach {

        val path = it + "\\$subPath";       if ( appExists(path) ) return path

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

fun urlFileName(url: String): String {

    return url.replace( Regex("[\\\\/:*?\"<>|]"), "_" )
        .replace( " ", "_" )

}