package com.enginemachiner.honkytones

import net.fabricmc.api.EnvType
import net.fabricmc.loader.impl.FabricLoaderImpl
import java.io.File
import java.io.FileWriter
import java.util.*

object FileImpl {}

open class RestrictedFile( s: String ) : File(s) {

    init {

        val parse = s.replace("/", "\\")
        if ( !parse.startsWith(Base.MOD_NAME + "\\") && parse.isNotEmpty() ) {
            printMessage( "Access Denied" )
            setExecutable(false);     setReadable(false);     setWritable(false)
        }

    }

}

open class ConfigFile( s: String ): RestrictedFile( configPath + s ) {

    val properties = Properties()
    private var shouldCreate = true

    init { shouldCreate = verify(shouldCreate);    creation() }

    open fun verify( shouldCreate: Boolean ): Boolean { return shouldCreate }

    private fun creation() {

        val dir = RestrictedFile(configPath)
        if ( !dir.exists() ) dir.mkdirs()

        if ( !shouldCreate ) return
        if ( !exists() || length() == 0L ) createNewFile()
        else properties.load( inputStream() )

    }

    override fun createNewFile(): Boolean {

        val b = super.createNewFile()

        properties.load( inputStream() )
        storeProperties()

        return b

    }

    companion object {
        private const val configPath = Base.MOD_NAME + "/config/"
    }

    open fun storeProperties() {
        properties.store( outputStream(), "\n HonkyTones main configuration \n" )
    }

    fun updateProperties( map: Map<String, Any> ) {
        for ( entry in map ) properties.setProperty(entry.key, "${entry.value}")
        properties.store( outputStream(), "\n HonkyTones main configuration \n" )
    }

}

class ClientConfigFile(path: String) : ConfigFile(path) {

    override fun verify( shouldCreate: Boolean ): Boolean {
        if ( FabricLoaderImpl.INSTANCE.environmentType != EnvType.CLIENT ) return false
        return shouldCreate
    }

    override fun storeProperties() {

        properties.setProperty("mobsParticles", "true")
        properties.setProperty("writeDeviceInfo", "true")
        properties.setProperty("playerParticles", "true")
        properties.setProperty("keep_downloads", "false")
        properties.setProperty("keep_videos", "false")
        properties.setProperty("audio_quality", "5")
        properties.setProperty("max_length", "1200") // 60 * 20 -> 20 min

        super.storeProperties()

    }

}

class ServerConfigFile(path: String) : ConfigFile(path) {

    override fun storeProperties() {

        properties.setProperty("debugMode", "false")
        properties.setProperty("mobsPlayingDelay", "120")
        properties.setProperty("mobsParticles", "true")
        properties.setProperty("playerParticles", "true")
        properties.setProperty("allowPushingPlayers", "false")

        super.storeProperties()

    }

}