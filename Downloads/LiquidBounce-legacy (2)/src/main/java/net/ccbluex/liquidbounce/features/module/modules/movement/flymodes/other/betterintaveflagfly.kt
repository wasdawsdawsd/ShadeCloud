package net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.other

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.modules.movement.Fly
import net.ccbluex.liquidbounce.features.module.modules.movement.flymodes.FlyMode
import net.minecraft.network.play.server.S27PacketExplosion

object betterintaveflagfly : FlyMode("betterintaveflagfly") {
    private var boosting = false
    private var boostTicks = 0

    override fun onEnable() {
        boosting = false
        boostTicks = 0
    }

    override fun onPacket(event: PacketEvent) {
        if (event.packet is S27PacketExplosion) {
            // S27PacketExplosion은 서버에서 Knockback 혹은 반작용을 줄 때 보내는 패킷
            boosting = true
            boostTicks = 0
        }
    }

    override fun onUpdate() {
        val player = mc.thePlayer ?: return

        if (boosting) {
            val yawRad = Math.toRadians(player.rotationYaw.toDouble())

            // 초기에는 강하게, 점점 줄어드는 속도
            val speed = 1.8 - (boostTicks * 0.05).coerceAtLeast(0.0)  // 서서히 감속
            val randomX = (Math.random() - 0.5) * 0.03
            val randomZ = (Math.random() - 0.5) * 0.03
            val yMotion = 0.36 - (boostTicks * 0.01) + (Math.random() - 0.5) * 0.03

            player.motionX = -Math.sin(yawRad) * speed + randomX
            player.motionZ =  Math.cos(yawRad) * speed + randomZ
            player.motionY = yMotion.coerceAtLeast(0.0)

            player.fallDistance = 0f
            boostTicks++

            // 비행 지속 시간 (Tick 제한)
            if (boostTicks >= Fly.maxFlyTicksValue) {
                boosting = false
                player.motionX = 0.0
                player.motionZ = 0.0
            }
        }
    }

    override fun onDisable() {
        boosting = false
        boostTicks = 0
    }
}
