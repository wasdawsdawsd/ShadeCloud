package net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.other

import net.ccbluex.liquidbounce.features.module.modules.movement.Speed
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.customAirStrafe
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.customAirTimer
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.customAirTimerTick
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.customGroundStrafe
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.customGroundTimer
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.customY
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.notOnConsuming
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.notOnFalling
import net.ccbluex.liquidbounce.features.module.modules.movement.Speed.notOnVoid
import net.ccbluex.liquidbounce.features.module.modules.movement.speedmodes.SpeedMode
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.extensions.stopXZ
import net.ccbluex.liquidbounce.utils.extensions.stopY
import net.ccbluex.liquidbounce.utils.extensions.tryJump
import net.ccbluex.liquidbounce.utils.movement.FallingPlayer
import net.ccbluex.liquidbounce.utils.movement.MovementUtils.strafe
import net.minecraft.item.ItemBucketMilk
import net.minecraft.item.ItemFood
import net.minecraft.item.ItemPotion

object ChatGPT : SpeedMode("ChatGPT") {

    override fun onMotion() {
        val player = mc.thePlayer ?: return
        val heldItem = player.heldItem

        val fallingPlayer = FallingPlayer()
        if (notOnVoid && fallingPlayer.findCollision(500) == null
            || notOnFalling && player.fallDistance > 2.5f
            || notOnConsuming && player.isUsingItem
            && (heldItem.item is ItemFood
                    || heldItem.item is ItemPotion
                    || heldItem.item is ItemBucketMilk)
        ) {
            if (player.onGround) player.tryJump()
            mc.timer.timerSpeed = 1f
            return
        }

        if (player.isMoving) {
            if (player.onGround) {
                if (customGroundStrafe > 0) {
                    strafe(customGroundStrafe)
                }
                mc.timer.timerSpeed = customGroundTimer
                player.motionY = customY.toDouble()
            } else {
                if (customAirStrafe > 0) {
                    strafe(customAirStrafe)
                }
                mc.timer.timerSpeed = if (player.ticksExisted % customAirTimerTick == 0) customAirTimer else 1f
            }
        } else {
            // 움직임 없으면 속도 초기화
            player.stopXZ()
            player.stopY()
            mc.timer.timerSpeed = 1f
        }
    }

    override fun onEnable() {
        val player = mc.thePlayer ?: return

        if (Speed.resetXZ) player.stopXZ()
        if (Speed.resetY) player.stopY()

        super.onEnable()
    }
}
