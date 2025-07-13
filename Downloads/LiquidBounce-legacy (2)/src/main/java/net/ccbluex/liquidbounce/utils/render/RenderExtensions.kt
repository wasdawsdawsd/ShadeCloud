package net.ccbluex.liquidbounce.utils.render

import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.WorldRenderer

inline fun drawWithTessellatorWorldRenderer(drawAction: WorldRenderer.() -> Unit) {
    val instance = Tessellator.getInstance()
    try {
        instance.worldRenderer.drawAction()
    } finally {
        instance.draw()

        GlStateManager.resetColor()
    }
}
