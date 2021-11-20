package com.enginemachiner.honkytones

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

// note per input (hopefully midi controller compatible)
object HonkyTonesClientEntrypoint {

    private val setting = KeyBinding(
        "key.${Base.MOD_ID}", // name
        InputUtil.Type.MOUSE, // type
        GLFW.GLFW_MOUSE_BUTTON_RIGHT, // keycode
        "category.${Base.MOD_ID}.keyticks" // category
    )
    var fabricKeyBuilder = KeyBindingHelper.registerKeyBinding(setting)!!

}