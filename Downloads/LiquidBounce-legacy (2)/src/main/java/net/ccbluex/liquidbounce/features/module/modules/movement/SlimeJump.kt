/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.JumpEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.block.block
import net.minecraft.block.BlockSlime

object SlimeJump : Module("SlimeJump", Category.MOVEMENT) {

    private val motion by float("Motion", 0.42f, 0.2f..1f)
    private val mode by choices("Mode", arrayOf("Set", "Add"), "Add")

    val onJump = handler<JumpEvent> { event ->
        val thePlayer = mc.thePlayer ?: return@handler

        if (mc.thePlayer != null && mc.theWorld != null && thePlayer.position.down().block is BlockSlime) {
            event.cancelEvent()

            when (mode.lowercase()) {
                "set" -> thePlayer.motionY = motion.toDouble()
                "add" -> thePlayer.motionY += motion
            }
        }
    }
}