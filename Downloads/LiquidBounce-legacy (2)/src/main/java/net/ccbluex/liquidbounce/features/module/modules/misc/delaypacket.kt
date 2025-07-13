package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.timing.TickTimer
import net.minecraft.client.Minecraft
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.entity.player.EntityPlayer

object delayPacket : Module("delayPacket", Category.MISC) {

    private val tickTimer = TickTimer()
    override val mc: Minecraft = Minecraft.getMinecraft()

    private fun createPositionRotationPacket(player: EntityPlayer): C03PacketPlayer {
        val packet = C03PacketPlayer()
        // 필드 직접 세팅 (필드가 public var 여야 함)
        packet.x = player.posX
        packet.y = player.posY
        packet.z = player.posZ
        packet.yaw = player.rotationYaw
        packet.pitch = player.rotationPitch
        packet.onGround = player.onGround
        return packet
    }

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet
        if (packet is C03PacketPlayer) {
            event.cancelEvent() // 모든 무브먼트 패킷 즉시 캔슬
        }
    }

    val onUpdate = handler<UpdateEvent> {
        tickTimer.update()
        if (tickTimer.hasTimePassed(7)) {
            val player = mc.thePlayer ?: return@handler
            val posPacket = createPositionRotationPacket(player)
            sendPacketDirect(posPacket)
            tickTimer.reset()
        }
    }

    override fun onDisable() {
        tickTimer.reset()
    }

    private fun sendPacketDirect(packet: C03PacketPlayer) {
        mc.thePlayer?.sendQueue?.addToSendQueue(packet)
    }
}
