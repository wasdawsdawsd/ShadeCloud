/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.intave

import net.ccbluex.liquidbounce.features.module.modules.movement.nowebmodes.NoWebMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.strafe

object IntaveNew : NoWebMode("IntaveNew") {
    override fun onUpdate() {
        val thePlayer = mc.thePlayer ?: return

        if (!thePlayer.isInWeb) {
            return
        }

        if (thePlayer.isMoving && thePlayer.moveStrafing == 0.0f) {
            if (thePlayer.onGround) {
                if (mc.thePlayer.ticksExisted % 3 == 0) {
                    strafe(0.734f)
                } else {
                    mc.thePlayer.tryJump()
                    strafe(0.346f)
                }
            }
        }
    }
}
