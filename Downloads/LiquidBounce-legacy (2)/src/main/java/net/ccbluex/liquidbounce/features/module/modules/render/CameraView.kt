/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.CameraPositionEvent
import net.ccbluex.liquidbounce.event.EventState
import net.ccbluex.liquidbounce.event.MotionEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold

object CameraView : Module("CameraView", Category.RENDER, gameDetecting = false) {

    private val customY by float("CustomY", 0f, -10f..10f)
    private val saveLastGroundY by boolean("SaveLastGroundY", true)
    private val onScaffold by boolean("OnScaffold", true)
    private val onF5 by boolean("OnF5", true)

    private var launchY: Double? = null

    override fun onEnable() {
        mc.thePlayer?.run {
            launchY = posY
        }
    }

    val onMotion = handler<MotionEvent> { event ->
        if (event.eventState != EventState.POST) return@handler

        mc.thePlayer?.run {
            if (!saveLastGroundY || (onGround || ticksExisted == 1)) {
                launchY = posY
            }
        }
    }

    val onCameraUpdate = handler<CameraPositionEvent> { event ->
        mc.thePlayer?.run {
            val currentLaunchY = launchY ?: return@handler
            if (onScaffold && !Scaffold.handleEvents()) return@handler
            if (onF5 && mc.gameSettings.thirdPersonView == 0) return@handler

            event.withY(currentLaunchY + customY)
        }
    }
}