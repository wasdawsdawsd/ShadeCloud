/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.Animations.animations
import net.ccbluex.liquidbounce.features.module.modules.render.Animations.defaultAnimation
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.util.MathHelper
import org.lwjgl.opengl.GL11.glTranslated
import org.lwjgl.opengl.GL11.glTranslatef

/**
 * Animations module
 *
 * This module affects the blocking animation. It allows the user to customize the animation.
 * If you are looking forward to contribute to this module, please name your animation with a reasonable name. Do not name them after clients or yourself.
 * Please credit from where you got the animation from and make sure they are willing to contribute.
 * If they are not willing to contribute, please do not add the animation to this module.
 *
 * If you are looking for the animation classes, please look at the [Animation] class. It allows you to create your own animation.
 * After making your animation class, please add it to the [animations] array. It should automatically be added to the list and show up in the GUI.
 *
 * By default, the module uses the [OneSevenAnimation] animation. If you want to change the default animation, please change the [defaultAnimation] variable.
 * Default animations are even used when the module is disabled.
 *
 * If another variables from the renderItemInFirstPerson method are needed, please let me know or pass them by yourself.
 *
 * @author CCBlueX
 */
object Animations : Module("Animations", Category.RENDER, gameDetecting = false) {

    // Default animation
    val defaultAnimation = OneSevenAnimation()

    private val animations = arrayOf(
        OneSevenAnimation(),
        OldPushdownAnimation(),
        NewPushdownAnimation(),
        OldAnimation(),
        HeliumAnimation(),
        ArgonAnimation(),
        CesiumAnimation(),
        SulfurAnimation(),
        ShadeAnimation(),
        DriftAnimation(),
        WaveAnimation(),
        TwistAnimation(),
        PulseAnimation()

    )

    private val animationMode by choices("Mode", animations.map { it.name }.toTypedArray(), "NewPushdown")
    val oddSwing by boolean("OddSwing", false)
    val swingSpeed by int("SwingSpeed", 15, 0..20)

    val handItemScale by float("ItemScale", 0f, -5f..5f)
    val handX by float("X", 0f, -5f..5f)
    val handY by float("Y", 0f, -5f..5f)
    val handPosX by float("PositionRotationX", 0f, -50f..50f)
    val handPosY by float("PositionRotationY", 0f, -50f..50f)
    val handPosZ by float("PositionRotationZ", 0f, -50f..50f)

    fun getAnimation() = animations.firstOrNull { it.name == animationMode }

}

/**
 * Sword Animation
 *
 * This class allows you to create your own animation.
 * It transforms the item in the hand and the known functions from Mojang are directly accessible as well.
 *
 * @author CCBlueX
 */
abstract class Animation(val name: String) : MinecraftInstance {
    abstract fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer)

    /**
     * Transforms the block in the hand
     *
     * @author Mojang
     */
    protected fun doBlockTransformations() {
        translate(-0.5f, 0.2f, 0f)
        rotate(30f, 0f, 1f, 0f)
        rotate(-80f, 1f, 0f, 0f)
        rotate(60f, 0f, 1f, 0f)
    }

    /**
     * Transforms the item in the hand
     *
     * @author Mojang
     */
    protected fun transformFirstPersonItem(equipProgress: Float, swingProgress: Float) {
        translate(0.56f, -0.52f, -0.71999997f)
        translate(0f, equipProgress * -0.6f, 0f)
        rotate(45f, 0f, 1f, 0f)
        val f = MathHelper.sin(swingProgress * swingProgress * 3.1415927f)
        val f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * 3.1415927f)
        rotate(f * -20f, 0f, 1f, 0f)
        rotate(f1 * -20f, 0f, 0f, 1f)
        rotate(f1 * -80f, 1f, 0f, 0f)
        scale(0.4f, 0.4f, 0.4f)
    }

}

/**
 * OneSeven animation (default). Similar to the 1.7 blocking animation.
 *
 * @author CCBlueX
 */
class OneSevenAnimation : Animation("OneSeven") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
        translate(-0.5f, 0.2f, 0f)
    }

}

class OldAnimation : Animation("Old") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
    }
}

/**
 * Old Pushdown animation.
 */
class OldPushdownAnimation : Animation("OldPushdown") {

    /**
     * @author CzechHek. Taken from Animations script.
     */
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        translate(0.56, -0.52, -0.5)
        translate(0.0, -f.toDouble() * 0.3, 0.0)
        rotate(45.5f, 0f, 1f, 0f)
        val var3 = MathHelper.sin(0f)
        val var4 = MathHelper.sin(0f)
        rotate((var3 * -20f), 0f, 1f, 0f)
        rotate((var4 * -20f), 0f, 0f, 1f)
        rotate((var4 * -80f), 1f, 0f, 0f)
        scale(0.32, 0.32, 0.32)
        val var15 = MathHelper.sin((MathHelper.sqrt_float(f1) * 3.1415927f))
        rotate((-var15 * 125 / 1.75f), 3.95f, 0.35f, 8f)
        rotate(-var15 * 35, 0f, (var15 / 100f), -10f)
        translate(-1.0, 0.6, -0.0)
        rotate(30f, 0f, 1f, 0f)
        rotate(-80f, 1f, 0f, 0f)
        rotate(60f, 0f, 1f, 0f)
        glTranslated(1.05, 0.35, 0.4)
        glTranslatef(-1f, 0f, 0f)
    }

}

/**
 * New Pushdown animation.
 * @author EclipsesDev
 *
 * Taken from NightX Moon Animation (I made it smoother here xd)
 */
class NewPushdownAnimation : Animation("NewPushdown") {

    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val x = Animations.handPosX - 0.08
        val y = Animations.handPosY + 0.12
        val z = Animations.handPosZ.toDouble()
        translate(x, y, z)

        val var9 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        translate(0.0, 0.0, 0.0)

        transformFirstPersonItem(f / 1.4f, 0.0f)

        rotate(-var9 * 65.0f / 2.0f, var9 / 2.0f, 1.0f, 4.0f)
        rotate(-var9 * 60.0f, 1.0f, var9 / 3.0f, -0.0f)
        doBlockTransformations()

        scale(1.0, 1.0, 1.0)
    }

}

/**
 * Helium animation.
 * @author 182exe
 */
class HeliumAnimation : Animation("Helium") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, 0.0f)
        val c0 = MathHelper.sin(f1 * f * 3.1415927f)
        val c1 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        rotate(-c1 * 55.0f, 30.0f, c0 / 5.0f, 0.0f)
        doBlockTransformations()
    }
}

/**
 * Argon animation.
 * @author 182exe
 */
class ArgonAnimation : Animation("Argon") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 2.5f, f1)
        val c2 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        val c3 = MathHelper.cos(MathHelper.sqrt_float(f) * 3.1415927f)
        rotate(c3 * 50.0f / 10.0f, -c2, -0.0f, 100.0f)
        rotate(c2 * 50.0f, 200.0f, -c2 / 2.0f, -0.0f)
        translate(0.0, 0.3, 0.0)
        doBlockTransformations()
    }
}

/**
 * Cesium animation.
 * @author 182exe
 */
class CesiumAnimation : Animation("Cesium") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val c4 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        rotate(-c4 * 10.0f / 20.0f, c4 / 2.0f, 0.0f, 4.0f)
        rotate(-c4 * 30.0f, 0.0f, c4 / 3.0f, 0.0f)
        rotate(-c4 * 10.0f, 1.0f, c4 / 10.0f, 0.0f)
        translate(0.0, 0.2, 0.0)
        doBlockTransformations()
    }
}

/**
 * Sulfur animation.
 * @author 182exe
 */
class SulfurAnimation : Animation("Sulfur") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val c5 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        val c6 = MathHelper.cos(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        rotate(-c5 * 30.0f, c5 / 10.0f, c6 / 10.0f, 0.0f)
        translate(c5 / 1.5, 0.2, 0.0)
        doBlockTransformations()
    }
}

/**
 * Shade animation.
 * Smooth animation that gives the feeling of the item being gently pushed downward,
 * with a subtle horizontal sway to enhance fluidity.
 *
 * @author Div
 */
class ShadeAnimation : Animation("Shade") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        // Basic equip animation
        transformFirstPersonItem(f / 1.5f, 0.0f)

        // Smooth downward pressure effect
        val press = MathHelper.sin(MathHelper.sqrt_float(f1) * Math.PI.toFloat()).toDouble()
        val slightShake = MathHelper.cos(f1 * Math.PI.toFloat()) * 0.1f

        // Move item downward smoothly
        translate(0.0, -press * 0.3, 0.0)

        // Slight horizontal sway
        translate(slightShake.toDouble(), 0.0, 0.0)

        // Gentle rotations for realism
        rotate((press * 30f).toFloat(), 1f, 0f, 0f)
        rotate((slightShake * 20f).toFloat(), 0f, 1f, 0f)

        // Blocking transform
        doBlockTransformations()

        // Slightly reduce scale
        scale(0.9, 0.9, 0.9)
    }
}
/**
 * Drift animation.
 * Creates a smooth drifting rotation while the item swings, mimicking a drift-like motion curve.
 * It moves sideways with sinusoidal sway and spins in a dynamic arc.
 *
 * @author Div
 */
class DriftAnimation : Animation("Drift") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        // Base transform with slightly reduced equip progress
        transformFirstPersonItem(f / 1.8f, 0.0f)

        val swing = MathHelper.sin(MathHelper.sqrt_float(f1) * Math.PI.toFloat())
        val sway = MathHelper.cos(f1 * 3.1415927f) * 0.2f
        val drift = MathHelper.sin(f1 * 3.1415927f) * 0.3f

        // Lateral drift motion (side slide)
        translate(drift.toDouble(), sway.toDouble() * 0.2, 0.0)

        // Smooth arc rotation (like drifting a car)
        rotate(-swing * 40f, 0f, 1f, 0f)
        rotate(sway * 25f, 1f, 0f, 0f)
        rotate(sway * 15f, 0f, 0f, 1f)

        // Add standard blocking animation
        doBlockTransformations()

        // Slightly scale up for dynamic effect
        scale(1.05, 1.05, 1.05)
    }
}
/**
 * Wave animation.
 * Smooth wave-like motion, ideal for idle or elegant blocking animations.
 *
 * @author Div
 */
class WaveAnimation : Animation("Wave") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 1.7f, 0.0f)

        val wave = MathHelper.sin(f1 * Math.PI.toFloat()) * 0.15f
        val sway = MathHelper.cos(f1 * Math.PI.toFloat()) * 0.1f

        translate(0.0, wave.toDouble(), 0.0)
        rotate(sway * 15f, 0f, 1f, 0f)
        rotate(wave * 10f, 1f, 0f, 0f)

        doBlockTransformations()
        scale(0.95, 0.95, 0.95)
    }
}
/**
 * Twist animation.
 * Adds a twisting motion to the item, creating a dynamic spin-like block style.
 *
 * @author Div
 */
class TwistAnimation : Animation("Twist") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 1.6f, 0.0f)

        val twist = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)

        translate(0.0, 0.0, 0.0)
        rotate(twist * 60f, 0f, 0f, 1f)
        rotate(twist * 40f, 1f, 0f, 0f)
        rotate(twist * 30f, 0f, 1f, 0f)

        doBlockTransformations()
        scale(1.1, 1.1, 1.1)
    }
}
/**
 * Pulse animation.
 * Creates a pulsing effect by scaling the item rhythmically as it blocks.
 *
 * @author Div
 */
class PulseAnimation : Animation("Pulse") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 1.5f, 0.0f)

        val pulse = 1.0 + MathHelper.sin(f1 * 3.1415927f) * 0.15
        scale(pulse, pulse, pulse)

        rotate(30f, 0f, 1f, 0f)
        doBlockTransformations()
    }
}
