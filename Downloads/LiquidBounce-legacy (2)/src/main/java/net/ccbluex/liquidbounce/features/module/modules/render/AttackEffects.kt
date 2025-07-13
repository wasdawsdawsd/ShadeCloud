package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.block.Block
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.effect.EntityLightningBolt
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity
import net.minecraft.util.EnumParticleTypes

object AttackEffects : Module("AttackEffects", Category.RENDER) {

    private val particle by choices(
        "Particle",
        arrayOf("None", "Blood", "Lighting", "Fire", "Heart", "Water", "Smoke", "Magic", "Crits"), "Blood"
    )

    private val amount by int("ParticleAmount", 5, 1..20) { particle != "None" }

    private val sound by choices("Sound", arrayOf("None", "Hit", "Orb", "Pop", "Splash", "Lightning"), "BowHit")

    private val volume by float("Volume", 1f, 0.1f..5f) { sound != "None" }
    private val pitch by float("Pitch", 1f, 0.1f..5f) { sound != "None" }

    val onAttack = handler<AttackEvent> { event ->
        val target = event.targetEntity as? EntityLivingBase ?: return@handler

        repeat(amount) {
            doEffect(target)
        }

        doSound()
    }

    private fun doSound() {
        val player = mc.thePlayer

        when (sound) {
            "Hit" -> player.playSound("random.bowhit", volume, pitch)
            "Orb" -> player.playSound("random.orb", volume, pitch)
            "Pop" -> player.playSound("random.pop", volume, pitch)
            "Splash" -> player.playSound("random.splash", volume, pitch)
            "Lightning" -> player.playSound("ambient.weather.thunder", volume, pitch)
        }
    }

    private fun doEffect(target: EntityLivingBase) {
        when (particle) {
            "Blood" -> spawnBloodParticle(EnumParticleTypes.BLOCK_CRACK, target)
            "Crits" -> spawnEffectParticle(EnumParticleTypes.CRIT, target)
            "Magic" -> spawnEffectParticle(EnumParticleTypes.CRIT_MAGIC, target)
            "Lighting" -> spawnLightning(target)
            "Smoke" -> spawnEffectParticle(EnumParticleTypes.SMOKE_NORMAL, target)
            "Water" -> spawnEffectParticle(EnumParticleTypes.WATER_DROP, target)
            "Heart" -> spawnEffectParticle(EnumParticleTypes.HEART, target)
            "Fire" -> spawnEffectParticle(EnumParticleTypes.LAVA, target)
        }
    }

    private fun spawnBloodParticle(particleType: EnumParticleTypes, target: EntityLivingBase) {
        mc.theWorld.spawnParticle(
            particleType,
            target.posX, target.posY + target.height - 0.75, target.posZ,
            0.0, 0.0, 0.0,
            Block.getStateId(Blocks.redstone_block.defaultState)
        )
    }

    private fun spawnEffectParticle(particleType: EnumParticleTypes, target: EntityLivingBase) {
        mc.effectRenderer.spawnEffectParticle(
            particleType.particleID,
            target.posX, target.posY, target.posZ,
            target.posX, target.posY, target.posZ
        )
    }

    private fun spawnLightning(target: EntityLivingBase) {
        mc.netHandler.handleSpawnGlobalEntity(
            S2CPacketSpawnGlobalEntity(
                EntityLightningBolt(mc.theWorld, target.posX, target.posY, target.posZ)
            )
        )
    }

}