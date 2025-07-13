package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.*
import net.minecraft.network.play.server.*
import net.minecraft.util.Vec3
import kotlin.math.*

// 임시 BoolValue 클래스 정의 (실제 LiquidBounce 구조에 맞게 수정 필요)
class BoolValue(private val name: String, private var enabled: Boolean) {
    fun get() = enabled
    fun set(value: Boolean) {
        enabled = value
    }
}

object BloxdPhysics : Module("BloxdPhysics", Category.MOVEMENT) {

    private const val DELTA = 1.0 / 30.0
    private const val JUMP_MOTION = 0.41999998688697815

    private val timerEnabled = BoolValue("Timer", true)
    private val spiderEnabled = BoolValue("Spider", true)

    private var bhopJumps = 0
    private var knockbackTime = 0L
    private var wasClimbing = false

    private val pBody = PlayerPhysics()

    init {
        handler<StrafeEvent> {
            if (mc.thePlayer == null || mc.theWorld == null) return@handler

            if (timerEnabled.get()) {
                mc.timer.timerSpeed = 1.5f
            }

            if (mc.thePlayer.onGround && pBody.velocity.yCoord < 0) {
                pBody.velocity = Vec3(0.0, 0.0, 0.0)
            }

            if (mc.thePlayer.onGround && mc.thePlayer.motionY == JUMP_MOTION) {
                bhopJumps = min(bhopJumps + 1, 3)
                pBody.impulse = pBody.impulse.add(Vec3(0.0, 8.0, 0.0))
            }

            if (spiderEnabled.get()) {
                if (mc.thePlayer.isCollidedHorizontally &&
                    (abs(it.forward) > 0 || abs(it.strafe) > 0)) {
                    pBody.velocity = Vec3(0.0, 8.0, 0.0)
                    wasClimbing = true
                } else if (wasClimbing) {
                    pBody.velocity = Vec3(0.0, 0.0, 0.0)
                    wasClimbing = false
                }
            }

            // groundTicks 변수 수정: onGround 상태를 누적하는 로직이 아니므로 1 또는 0만 됨.
            // 필요하면 외부 변수로 누적 로직 추가하세요.
            val groundTicks = if (mc.thePlayer.onGround) 1 else 0
            if (groundTicks > 5) bhopJumps = 0

            val bps = when {
                knockbackTime > System.currentTimeMillis() -> 1.0
                mc.thePlayer.isUsingItem -> 0.06
                else -> 0.26 + 0.025 * bhopJumps
            }

            val move = getMoveVec(it.forward, it.strafe, mc.thePlayer.rotationYaw, bps)
            it.cancelEvent()
            mc.thePlayer.motionX = move.xCoord
            mc.thePlayer.motionZ = move.zCoord

            mc.thePlayer.motionY = if (mc.theWorld.getChunkFromBlockCoords(mc.thePlayer.position).isLoaded || mc.thePlayer.posY <= 0)
                pBody.getMotionForTick().yCoord * DELTA
            else 0.0
        }

        handler<PacketEvent> {
            when (val packet = it.packet) {
                is S3FPacketCustomPayload -> {
                    if (packet.channelName == "bloxd:resyncphysics") {
                        val data = packet.bufferData
                        pBody.impulse = Vec3(0.0, 0.0, 0.0)
                        pBody.force = Vec3(0.0, 0.0, 0.0)
                        pBody.velocity = Vec3(
                            data.readFloat().toDouble(),
                            data.readFloat().toDouble(),
                            data.readFloat().toDouble()
                        )
                        bhopJumps = 0
                    }
                }

                is S12PacketEntityVelocity -> {
                    if (mc.thePlayer != null && packet.entityID == mc.thePlayer.entityId) {
                        knockbackTime = System.currentTimeMillis() + 1300
                    }
                }
            }
        }
    }

    private fun getMoveVec(forward: Float, strafe: Float, yaw: Float, bps: Double): Vec3 {
        var f = forward.toDouble()
        var s = strafe.toDouble()

        val sqrt = sqrt(f * f + s * s)
        if (sqrt > 1) {
            f /= sqrt
            s /= sqrt
        }

        val sin = sin(Math.toRadians(yaw.toDouble()))
        val cos = cos(Math.toRadians(yaw.toDouble()))

        return Vec3(s * cos - f * sin, 0.0, f * cos + s * sin).scale(bps)
    }

    // Vec3 확장 함수 추가 (scale 함수가 없어서 직접 만듦)
    private fun Vec3.scale(factor: Double): Vec3 =
        Vec3(this.xCoord * factor, this.yCoord * factor, this.zCoord * factor)

    private class PlayerPhysics {
        var impulse: Vec3 = Vec3(0.0, 0.0, 0.0)
        var force: Vec3 = Vec3(0.0, 0.0, 0.0)
        var velocity: Vec3 = Vec3(0.0, 0.0, 0.0)
        private val gravity = Vec3(0.0, -10.0, 0.0)
        private val gravityMul = 2.0
        private val mass = 1.0

        fun getMotionForTick(): Vec3 {
            val massDiv = 1.0 / mass
            val f = force.scale(massDiv).add(gravity).scale(gravityMul)
            val i = impulse.scale(massDiv)
            val total = i.add(f.scale(DELTA))
            velocity = velocity.add(total)

            force = Vec3(0.0, 0.0, 0.0)
            impulse = Vec3(0.0, 0.0, 0.0)
            return velocity
        }
    }
}
