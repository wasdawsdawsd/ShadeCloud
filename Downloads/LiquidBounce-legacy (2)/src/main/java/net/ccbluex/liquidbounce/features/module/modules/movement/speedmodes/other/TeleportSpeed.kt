package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.event.MoveEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import kotlin.math.cos
import kotlin.math.sin

object TeleportSpeed : SpeedMode("TeleportSpeed") {
    private val timer = MSTimer()

    override fun onEnable() {
        super.onEnable()
        timer.reset()
    }

    override fun onMove(event: MoveEvent) {
        if (!mc.thePlayer.onGround) return

        var forward = mc.thePlayer.moveForward.toDouble()
        var strafe = mc.thePlayer.moveStrafing.toDouble()

        // 방향 입력이 없을 경우 이동 없음
        if ((forward == 0.0 && strafe == 0.0) || !timer.hasTimePassed(300)) {
            event.x = 0.0
            event.z = 0.0
            return
        }

        var yaw = mc.thePlayer.rotationYaw.toDouble()

        // 방향에 따라 회전 보정
        if (forward != 0.0) {
            if (strafe > 0.0) {
                yaw += if (forward > 0.0) -45.0 else 45.0
            } else if (strafe < 0.0) {
                yaw += if (forward > 0.0) 45.0 else -45.0
            }
            strafe = 0.0
            forward = if (forward > 0.0) 1.0 else -1.0
        }

        val rad = Math.toRadians(yaw)
        val speed = 2.5

        val x = (forward * -sin(rad) + strafe * cos(rad)) * speed
        val z = (forward * cos(rad) + strafe * -sin(rad)) * speed

        event.x = x
        event.z = z

        timer.reset()
    }
}
