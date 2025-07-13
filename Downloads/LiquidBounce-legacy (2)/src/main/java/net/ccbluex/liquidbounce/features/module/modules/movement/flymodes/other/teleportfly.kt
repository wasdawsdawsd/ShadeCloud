package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.ccbluex.liquidbounce.utils.extensions.toRadiansD
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import kotlin.math.cos
import kotlin.math.sin

object TeleportFly : FlyMode("TeleportFly") {
    private var startY = 0.0
    private val timer = MSTimer()

    override fun onEnable() {
        super.onEnable()
        startY = mc.thePlayer.posY
        timer.reset()
    }

    override fun onUpdate() {
        mc.timer.timerSpeed = 1.0f
        mc.thePlayer.motionY = 0.0
        mc.thePlayer.posY = startY
    }

    override fun onMove(event: MoveEvent) {
        val yaw = mc.thePlayer.rotationYaw.toRadiansD()

        if (mc.thePlayer.moveForward > 0 && timer.hasTimePassed(500)) {
            val offsetX = -sin(yaw) * 3.0
            val offsetZ = cos(yaw) * 3.0

            mc.thePlayer.setPosition(
                mc.thePlayer.posX + offsetX,
                startY,
                mc.thePlayer.posZ + offsetZ
            )
            timer.reset()

            event.x = 0.0
            event.z = 0.0
        } else {
            event.x = 0.0
            event.z = 0.0
        }
    }
}
