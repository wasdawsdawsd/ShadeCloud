/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.utils.render

import co.uk.hexeption.utils.OutlineUtils
import net.ccbluex.liquidbounce.config.ColorValue
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.block.block
import net.ccbluex.liquidbounce.utils.block.center
import net.ccbluex.liquidbounce.utils.block.toVec
import net.ccbluex.liquidbounce.utils.client.ClientUtils.disableFastRender
import net.ccbluex.liquidbounce.utils.client.MinecraftInstance
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.io.flipSafely
import net.ccbluex.liquidbounce.utils.render.animation.AnimationUtil
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL14.glBlendFuncSeparate
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import javax.vecmath.Point3d
import kotlin.math.*

object RenderUtils : MinecraftInstance {
    // ARGB 0xff006fff
    const val CLIENT_COLOR = -16748545

    // ARGB 0x7f006fff
    const val CLIENT_COLOR_HALF_ALPHA = 2130735103

    private val glCapMap = mutableMapOf<Int, Boolean>()
    private val DISPLAY_LISTS_2D = IntArray(4) {
        glGenLists(1)
    }
    var deltaTime = 0

    /**
     * Useful for clipping any top-layered rectangle that falls outside a bottom-layered rectangle.
     */
    inline fun withClipping(main: () -> Unit, toClip: () -> Unit) {
        disableFastRender()
        OutlineUtils.checkSetupFBO()
        glPushMatrix()

        disableAlpha()

        glEnable(GL_STENCIL_TEST)
        glStencilFunc(GL_ALWAYS, 1, 1)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)
        glStencilMask(1)
        glClear(GL_STENCIL_BUFFER_BIT)

        main()

        glStencilFunc(GL_EQUAL, 1, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
        glStencilMask(0)

        toClip()

        glStencilMask(0xFF)
        glDisable(GL_STENCIL_TEST)

        enableAlpha()

        glPopMatrix()
    }

    inline fun withOutline(main: () -> Unit, toOutline: () -> Unit) {
        disableFastRender()
        OutlineUtils.checkSetupFBO()
        glPushMatrix()

        disableAlpha()

        glEnable(GL_STENCIL_TEST)
        glClear(GL_STENCIL_BUFFER_BIT)

        glStencilFunc(GL_ALWAYS, 1, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE)
        glStencilMask(1)

        main()

        glStencilFunc(GL_EQUAL, 0, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
        glStencilMask(0)

        toOutline()

        glStencilMask(0xFF)
        glDisable(GL_STENCIL_TEST)

        enableAlpha()

        glPopMatrix()
    }

    fun deltaTimeNormalized(ticks: Int = 1) = (deltaTime safeDivD ticks * 50.0).coerceAtMost(1.0)

    private const val CIRCLE_STEPS = 40

    private val circlePoints = Array(CIRCLE_STEPS + 1) {
        val theta = 2 * PI * it / CIRCLE_STEPS

        Point3d(-sin(theta), 0.0, cos(theta))
    }

    init {
        glNewList(DISPLAY_LISTS_2D[0], GL_COMPILE)
        quickDrawRect(-7f, 2f, -4f, 3f)
        quickDrawRect(4f, 2f, 7f, 3f)
        quickDrawRect(-7f, 0.5f, -6f, 3f)
        quickDrawRect(6f, 0.5f, 7f, 3f)
        glEndList()
        glNewList(DISPLAY_LISTS_2D[1], GL_COMPILE)
        quickDrawRect(-7f, 3f, -4f, 3.3f)
        quickDrawRect(4f, 3f, 7f, 3.3f)
        quickDrawRect(-7.3f, 0.5f, -7f, 3.3f)
        quickDrawRect(7f, 0.5f, 7.3f, 3.3f)
        glEndList()
        glNewList(DISPLAY_LISTS_2D[2], GL_COMPILE)
        quickDrawRect(4f, -20f, 7f, -19f)
        quickDrawRect(-7f, -20f, -4f, -19f)
        quickDrawRect(6f, -20f, 7f, -17.5f)
        quickDrawRect(-7f, -20f, -6f, -17.5f)
        glEndList()
        glNewList(DISPLAY_LISTS_2D[3], GL_COMPILE)
        quickDrawRect(7f, -20f, 7.3f, -17.5f)
        quickDrawRect(-7.3f, -20f, -7f, -17.5f)
        quickDrawRect(4f, -20.3f, 7.3f, -20f)
        quickDrawRect(-7.3f, -20.3f, -4f, -20f)
        glEndList()
    }

    @JvmStatic
    fun BlockPos.drawBlockDamageText(
        currentDamage: Float,
        font: FontRenderer,
        fontShadow: Boolean,
        color: Int,
        scale: Float,
    ) {
        require(currentDamage in 0f..1f)

        val renderManager = mc.renderManager

        val progress = (currentDamage * 100).coerceIn(0f, 100f).toInt()
        val progressText = "$progress%"

        glPushAttrib(GL_ENABLE_BIT)
        glPushMatrix()

        val (x, y, z) = this.center - renderManager.renderPos

        // Translate to block position
        glTranslated(x, y, z)

        glRotatef(-renderManager.playerViewY, 0F, 1F, 0F)
        glRotatef(renderManager.playerViewX, 1F, 0F, 0F)

        disableGlCap(GL_LIGHTING, GL_DEPTH_TEST)
        enableGlCap(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Scale
        val renderScale = (mc.thePlayer.getDistanceSq(this) / 8F).coerceAtLeast(1.5) / 150F * scale
        glScaled(-renderScale, -renderScale, renderScale)

        // Draw text
        val width = font.getStringWidth(progressText) * 0.5f
        font.drawString(
            progressText, -width, if (font == Fonts.minecraftFont) 1F else 1.5F, color, fontShadow
        )

        resetCaps()
        glPopMatrix()
        glPopAttrib()
    }

    fun drawBlockBox(blockPos: BlockPos, color: Color, outline: Boolean) {
        val renderManager = mc.renderManager

        val (x, y, z) = blockPos.toVec() - renderManager.renderPos

        var axisAlignedBB = AxisAlignedBB.fromBounds(x, y, z, x + 1.0, y + 1.0, z + 1.0)

        blockPos.block?.let { block ->
            val player = mc.thePlayer

            val pos = -player.interpolatedPosition(player.lastTickPos)

            val f = 0.002F.toDouble()

            block.setBlockBoundsBasedOnState(mc.theWorld, blockPos)

            axisAlignedBB = block.getSelectedBoundingBox(mc.theWorld, blockPos).expand(f, f, f).offset(pos)
        }

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        enableGlCap(GL_BLEND)
        disableGlCap(GL_TEXTURE_2D, GL_DEPTH_TEST)
        glDepthMask(false)
        glColor(color.red, color.green, color.blue, if (color.alpha != 255) color.alpha else if (outline) 26 else 35)
        drawFilledBox(axisAlignedBB)

        if (outline) {
            glLineWidth(1f)
            enableGlCap(GL_LINE_SMOOTH)
            glColor(color)
            drawSelectionBoundingBox(axisAlignedBB)
        }

        resetColor()
        glDepthMask(true)
        resetCaps()
    }

    fun drawSelectionBoundingBox(boundingBox: AxisAlignedBB) = drawWithTessellatorWorldRenderer {
        begin(GL_LINE_STRIP, DefaultVertexFormats.POSITION)

        // Lower Rectangle
        pos(boundingBox.minX, boundingBox.minY, boundingBox.minZ).endVertex()
        pos(boundingBox.minX, boundingBox.minY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.minY, boundingBox.minZ).endVertex()
        pos(boundingBox.minX, boundingBox.minY, boundingBox.minZ).endVertex()

        // Upper Rectangle
        pos(boundingBox.minX, boundingBox.maxY, boundingBox.minZ).endVertex()
        pos(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ).endVertex()
        pos(boundingBox.minX, boundingBox.maxY, boundingBox.minZ).endVertex()

        // Upper Rectangle
        pos(boundingBox.minX, boundingBox.maxY, boundingBox.maxZ).endVertex()
        pos(boundingBox.minX, boundingBox.minY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.minY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ).endVertex()
        pos(boundingBox.maxX, boundingBox.maxY, boundingBox.minZ).endVertex()
        pos(boundingBox.maxX, boundingBox.minY, boundingBox.minZ).endVertex()
    }

    fun drawCircle(
        entity: EntityLivingBase,
        speed: Float,
        height: ClosedFloatingPointRange<Float>,
        size: Float,
        filled: Boolean,
        withHeight: Boolean,
        circleY: ClosedFloatingPointRange<Float>? = null,
        startColor: Int,
        endColor: Int
    ) {
        val manager = mc.renderManager
        val positions = mutableListOf<DoubleArray>()

        val renderX = manager.viewerPosX
        val renderY = manager.viewerPosY
        val renderZ = manager.viewerPosZ

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_CULL_FACE)
        glEnable(GL_ALPHA_TEST)
        glDepthMask(false)
        glAlphaFunc(GL_GREATER, 0.0f)
        mc.entityRenderer.disableLightmap()

        shadeModel(GL_SMOOTH)

        val a1 = (startColor shr 24 and 255) / 255f
        val r1 = (startColor shr 16 and 255) / 255f
        val g1 = (startColor shr 8 and 255) / 255f
        val b1 = (startColor and 255) / 255f
        val a2 = (endColor shr 24 and 255) / 255f
        val r2 = (endColor shr 16 and 255) / 255f
        val g2 = (endColor shr 8 and 255) / 255f
        val b2 = (endColor and 255) / 255f

        val breathingT = AnimationUtil.breathe(speed)
        val entityHeight = (entity.hitBox.maxY - entity.hitBox.minY).toFloat()

        val width = (mc.renderManager.getEntityRenderObject<Entity>(entity)?.shadowSize ?: 0.5F) + size
        val animatedHeight = (0F..entityHeight).lerpWith((height.endInclusive..height.start).lerpWith(breathingT))
        val animatedCircleY = (0F..entityHeight).lerpWith(circleY?.lerpWith(breathingT) ?: 0F)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.worldRenderer

        if (filled) {
            buffer.begin(GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR)
        }

        entity.interpolatedPosition(entity.prevPos).let { pos ->
            circlePoints.forEachIndexed { index, it ->
                val p = pos + Vec3(it.x * width, it.y + animatedCircleY, it.z * width)
                positions += doubleArrayOf(p.xCoord, p.yCoord, p.zCoord)

                if (filled) {
                    buffer.pos(p.xCoord - renderX, p.yCoord - renderY, p.zCoord - renderZ).color(r1, g1, b1, a1)
                        .endVertex()
                }
            }
        }

        if (filled) {
            tessellator.draw()
        }

        if (withHeight) {
            buffer.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR)

            positions.forEachIndexed { index, pos ->
                val endPos = positions.getOrNull(index + 1) ?: return@forEachIndexed

                buffer.pos(pos[0] - renderX, pos[1] - renderY, pos[2] - renderZ).color(r1, g1, b1, a1).endVertex()
                buffer.pos(endPos[0] - renderX, endPos[1] - renderY, endPos[2] - renderZ).color(r1, g1, b1, a1)
                    .endVertex()
                buffer.pos(endPos[0] - renderX, endPos[1] - renderY + animatedHeight, endPos[2] - renderZ)
                    .color(r2, g2, b2, a2).endVertex()
                buffer.pos(pos[0] - renderX, pos[1] - renderY + animatedHeight, pos[2] - renderZ).color(r2, g2, b2, a2)
                    .endVertex()
            }
            tessellator.draw()
        }

        shadeModel(GL_FLAT)
        glDisable(GL_ALPHA_TEST)
        glDisable(GL_BLEND)
        glEnable(GL_CULL_FACE)
        glDepthMask(true)
        glEnable(GL_TEXTURE_2D)
        glPopMatrix()
        glPopAttrib()
    }

    fun drawHueCircle(position: Vec3, radius: Float, innerColor: Color, outerColor: Color) {
        val manager = mc.renderManager

        val renderX = manager.viewerPosX
        val renderY = manager.viewerPosY
        val renderZ = manager.viewerPosZ

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glEnable(GL_ALPHA_TEST)
        glAlphaFunc(GL_GREATER, 0.0f)
        mc.entityRenderer.disableLightmap()

        glBegin(GL_TRIANGLE_FAN)
        circlePoints.forEachIndexed { index, pos ->
            val innerX = pos.x * radius
            val innerZ = pos.z * radius

            val innerHue = ColorUtils.shiftHue(innerColor, (index / CIRCLE_STEPS).toInt())
            glColor4f(innerHue.red / 255f, innerHue.green / 255f, innerHue.blue / 255f, innerColor.alpha / 255f)
            glVertex3d(
                position.xCoord - renderX + innerX, position.yCoord - renderY, position.zCoord - renderZ + innerZ
            )
        }
        glEnd()

        glBegin(GL_LINE_LOOP)
        circlePoints.forEachIndexed { index, pos ->
            val outerX = pos.x * radius
            val outerZ = pos.z * radius

            val outerHue = ColorUtils.shiftHue(outerColor, (index / CIRCLE_STEPS).toInt())
            glColor4f(outerHue.red / 255f, outerHue.green / 255f, outerHue.alpha / 255f, outerColor.alpha / 255f)
            glVertex3d(
                position.xCoord - renderX + outerX, position.yCoord - renderY, position.zCoord - renderZ + outerZ
            )
        }
        glEnd()

        glEnable(GL_CULL_FACE)
        glEnable(GL_DEPTH_TEST)
        glDisable(GL_ALPHA_TEST)
        glDisable(GL_LINE_SMOOTH)
        glDisable(GL_BLEND)
        glEnable(GL_TEXTURE_2D)
        glPopMatrix()
        glPopAttrib()
    }

    /**
     * Draws a dome around the specified [pos]
     *
     * Only [GL_LINES], [GL_TRIANGLES] and [GL_QUADS] are allowed.
     */
    fun drawDome(pos: Vec3, hRadius: Double, vRadius: Double, lineWidth: Float? = null, color: Color, renderMode: Int) {
        require(renderMode in arrayOf(GL_LINES, GL_TRIANGLES, GL_QUADS))

        val manager = mc.renderManager ?: return

        val renderX = manager.viewerPosX
        val renderY = manager.viewerPosY
        val renderZ = manager.viewerPosZ
        val (posX, posY, posZ) = pos

        val vStep = Math.PI / (CIRCLE_STEPS / 2)
        val hStep = 2 * Math.PI / CIRCLE_STEPS

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        lineWidth?.let { glLineWidth(it) }
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glEnable(GL_ALPHA_TEST)
        glAlphaFunc(GL_GREATER, 0.0f)
        glTranslated(-renderX, -renderY, -renderZ)
        glColor(color)

        drawWithTessellatorWorldRenderer {
            begin(renderMode, DefaultVertexFormats.POSITION)

            val min = if (renderMode != GL_TRIANGLES) 0 to 0 else -1 to 1

            for (i in min.first until CIRCLE_STEPS / 2) {
                val vAngle1 = i * vStep
                val vAngle2 = (i + 1) * vStep

                for (j in min.second until CIRCLE_STEPS) {
                    val hAngle1 = j * hStep
                    val hAngle2 = (j + 1) * hStep

                    val p1 = calculateDomeVertex(posX, posY, posZ, vAngle1, hAngle1, hRadius, vRadius)
                    val p2 = calculateDomeVertex(posX, posY, posZ, vAngle2, hAngle1, hRadius, vRadius)
                    val p3 = calculateDomeVertex(posX, posY, posZ, vAngle2, hAngle2, hRadius, vRadius)
                    val p4 = calculateDomeVertex(posX, posY, posZ, vAngle1, hAngle2, hRadius, vRadius)

                    when (renderMode) {
                        GL_QUADS -> {
                            pos(p1[0], p1[1], p1[2]).endVertex()
                            pos(p2[0], p2[1], p2[2]).endVertex()
                            pos(p3[0], p3[1], p3[2]).endVertex()
                            pos(p4[0], p4[1], p4[2]).endVertex()
                        }

                        GL_TRIANGLES, GL_LINES -> {
                            pos(p1[0], p1[1], p1[2]).endVertex()
                            pos(p2[0], p2[1], p2[2]).endVertex()

                            pos(p2[0], p2[1], p2[2]).endVertex()
                            pos(p3[0], p3[1], p3[2]).endVertex()

                            pos(p3[0], p3[1], p3[2]).endVertex()
                            pos(p4[0], p4[1], p4[2]).endVertex()

                            pos(p4[0], p4[1], p4[2]).endVertex()
                            pos(p1[0], p1[1], p1[2]).endVertex()
                        }
                    }
                }
            }
        }

        glEnable(GL_CULL_FACE)
        glEnable(GL_DEPTH_TEST)
        glDisable(GL_ALPHA_TEST)
        glDisable(GL_LINE_SMOOTH)
        glDisable(GL_BLEND)
        glEnable(GL_TEXTURE_2D)

        glPopMatrix()
        glPopAttrib()
    }

    private fun calculateDomeVertex(
        entityX: Double,
        entityY: Double,
        entityZ: Double,
        theta: Double,
        phi: Double,
        horizontalRadius: Double,
        verticalRadius: Double
    ): DoubleArray {
        return doubleArrayOf(
            entityX + horizontalRadius * sin(theta) * cos(phi),
            entityY + verticalRadius * cos(theta),
            entityZ + horizontalRadius * sin(theta) * sin(phi)
        )
    }

    fun drawConesForEntities(f: () -> Unit) {
        pushAttrib()
        pushMatrix()

        disableTexture2D()
        disableCull()

        enableBlend()
        glEnable(GL_DEPTH_TEST)
        depthMask(false)
        blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        f()

        resetColor()

        enableTexture2D()
        depthMask(true)
        enableCull()

        disableBlend()
        glDisable(GL_DEPTH_TEST)

        popMatrix()
        popAttrib()
    }

    fun drawCone(width: Float, height: Float, useTexture: Boolean = false) {
        if (useTexture) {
            mc.textureManager.bindTexture(ResourceLocation("liquidbounce/textures/hat.png"))
            enableTexture2D()
            depthMask(true)
        }

        drawWithTessellatorWorldRenderer {
            begin(GL_TRIANGLE_FAN, if (useTexture) DefaultVertexFormats.POSITION_TEX else DefaultVertexFormats.POSITION)

            if (useTexture) {
                pos(0.0, height.toDouble(), 0.0).tex(0.5, 0.5).endVertex()
            } else {
                pos(0.0, height.toDouble(), 0.0).endVertex()
            }

            for (point in circlePoints) {
                if (useTexture) {
                    val u = 0.5 + 0.5 * point.x
                    val v = 0.5 + 0.5 * point.z
                    pos(point.x * width, 0.0, point.z * width).tex(u, v).endVertex()
                } else {
                    pos(point.x * width, 0.0, point.z * width).endVertex()
                }
            }
        }
    }

    fun drawEntityBox(entity: Entity, color: Color, outline: Boolean) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        enableGlCap(GL_BLEND)
        disableGlCap(GL_TEXTURE_2D, GL_DEPTH_TEST)
        glDepthMask(false)

        val (x, y, z) = entity.interpolatedPosition(entity.lastTickPos) - mc.renderManager.renderPos
        val entityBox = entity.hitBox

        val axisAlignedBB = AxisAlignedBB.fromBounds(
            entityBox.minX - entity.posX + x - 0.05,
            entityBox.minY - entity.posY + y,
            entityBox.minZ - entity.posZ + z - 0.05,
            entityBox.maxX - entity.posX + x + 0.05,
            entityBox.maxY - entity.posY + y + 0.15,
            entityBox.maxZ - entity.posZ + z + 0.05
        )

        if (outline) {
            glLineWidth(1f)
            enableGlCap(GL_LINE_SMOOTH)
            glColor(color.red, color.green, color.blue, 95)
            drawSelectionBoundingBox(axisAlignedBB)
        }

        glColor(color.red, color.green, color.blue, if (outline) 26 else 35)
        drawFilledBox(axisAlignedBB)
        resetColor()
        glDepthMask(true)
        resetCaps()
    }

    fun drawPosBox(x: Double, y: Double, z: Double, width: Float, height: Float, color: Color, outline: Boolean) {
        val (adjustedX, adjustedY, adjustedZ) = Vec3(x, y, z) - mc.renderManager.renderPos

        val axisAlignedBB = AxisAlignedBB.fromBounds(
            adjustedX - width / 2,
            adjustedY,
            adjustedZ - width / 2,
            adjustedX + width / 2,
            adjustedY + height,
            adjustedZ + width / 2
        )

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        enableGlCap(GL_BLEND)
        disableGlCap(GL_TEXTURE_2D, GL_DEPTH_TEST)

        glDepthMask(false)

        if (outline) {
            glLineWidth(1f)
            enableGlCap(GL_LINE_SMOOTH)
            glColor(color.red, color.green, color.blue, 95)
            drawSelectionBoundingBox(axisAlignedBB)
        }

        glColor(color.red, color.green, color.blue, if (outline) 26 else 35)
        drawFilledBox(axisAlignedBB)

        resetColor()
        glDepthMask(true)
        resetCaps()
    }

    fun drawBacktrackBox(axisAlignedBB: AxisAlignedBB, color: Color) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_BLEND)
        glLineWidth(2f)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glColor(color.red, color.green, color.blue, 90)
        drawFilledBox(axisAlignedBB)
        resetColor()
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }

    fun drawAxisAlignedBB(axisAlignedBB: AxisAlignedBB, color: Color) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_BLEND)
        glLineWidth(2f)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)
        glColor(color)
        drawFilledBox(axisAlignedBB)
        resetColor()
        glEnable(GL_TEXTURE_2D)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDisable(GL_BLEND)
    }

    fun drawPlatform(y: Double, color: Color, size: Double) {
        val renderY = y - mc.renderManager.renderPosY
        drawAxisAlignedBB(AxisAlignedBB.fromBounds(size, renderY + 0.02, size, -size, renderY, -size), color)
    }

    fun drawPlatform(entity: Entity, color: Color) {
        val deltaPos = entity.interpolatedPosition(entity.lastTickPos) - mc.renderManager.renderPos
        val axisAlignedBB = entity.entityBoundingBox.offset(-entity.currPos + deltaPos)

        drawAxisAlignedBB(
            AxisAlignedBB.fromBounds(
                axisAlignedBB.minX,
                axisAlignedBB.maxY + 0.2,
                axisAlignedBB.minZ,
                axisAlignedBB.maxX,
                axisAlignedBB.maxY + 0.26,
                axisAlignedBB.maxZ
            ), color
        )
    }

    fun drawFilledBox(axisAlignedBB: AxisAlignedBB) = drawWithTessellatorWorldRenderer {
        begin(7, DefaultVertexFormats.POSITION)
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).endVertex()
        pos(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).endVertex()
    }

    fun drawRect(x: Float, y: Float, x2: Float, y2: Float, color: Color) = drawRect(x, y, x2, y2, color.rgb)

    fun drawBorderedRect(x: Float, y: Float, x2: Float, y2: Float, width: Float, borderColor: Int, rectColor: Int) {
        drawRect(x, y, x2, y2, rectColor)
        drawBorder(x, y, x2, y2, width, borderColor)
    }

    fun drawBorderedRect(x: Int, y: Int, x2: Int, y2: Int, width: Number, borderColor: Int, rectColor: Int) {
        drawRect(x, y, x2, y2, rectColor)
        drawBorder(x, y, x2, y2, width, borderColor)
    }

    fun drawRoundedBorderRect(
        x: Float, y: Float, x2: Float, y2: Float, width: Float, color1: Int, color2: Int, radius: Float
    ) {
        drawRoundedRect(x, y, x2, y2, color1, radius)
        drawRoundedBorder(x, y, x2, y2, width, color2, radius)
    }

    fun drawRoundedBorderRectInt(
        x: Int, y: Int, x2: Int, y2: Int, width: Int, color1: Int, color2: Int, radius: Float
    ) {
        drawRoundedRectInt(x, y, x2, y2, color1, radius)
        drawRoundedBorderInt(x, y, x2, y2, width.toFloat(), color2, radius)
    }

    fun drawBorder(x: Float, y: Float, x2: Float, y2: Float, width: Float, color: Int) {
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glColor(color)
        glLineWidth(width)

        drawWithTessellatorWorldRenderer {
            begin(GL_LINE_LOOP, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }

        glColor(Color.WHITE)
        glDisable(GL_LINE_SMOOTH)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
    }

    fun drawBorder(x: Int, y: Int, x2: Int, y2: Int, width: Number, color: Int) {
        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glColor(color)
        glLineWidth(width.toFloat())

        drawWithTessellatorWorldRenderer {
            begin(GL_LINE_LOOP, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }

        glColor(Color.WHITE)
        glDisable(GL_LINE_SMOOTH)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    fun drawRoundedBorder(x: Float, y: Float, x2: Float, y2: Float, width: Float, color: Int, radius: Float) {
        renderRoundedBorder(x, y, x2, y2, color, width, radius)
    }

    fun drawRoundedBorderInt(x: Int, y: Int, x2: Int, y2: Int, width: Float, color: Int, radius: Float) {
        renderRoundedBorder(x.toFloat(), y.toFloat(), x2.toFloat(), y2.toFloat(), color, width, radius)
    }

    private fun renderRoundedBorder(
        x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float, radius: Float, bottom: Boolean = true
    ) {
        val (alpha, red, green, blue) = ColorUtils.unpackARGBFloatValue(color)
        val (newX1, newY1, newX2, newY2) = orderPoints(x1, y1, x2, y2)

        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glLineWidth(width)

        glColor4f(red, green, blue, alpha)

        val radiusD = min(radius.toDouble(), min(newX2 - newX1, newY2 - newY1) / 2.0)

        val corners = arrayOf(
            doubleArrayOf(newX2 - radiusD, newY2 - radiusD, 0.0),
            doubleArrayOf(newX2 - radiusD, newY1 + radiusD, 90.0),
            doubleArrayOf(newX1 + radiusD, newY1 + radiusD, 180.0),
            doubleArrayOf(newX1 + radiusD, newY2 - radiusD, 270.0)
        )

        drawWithTessellatorWorldRenderer {
            begin(if (bottom) GL_LINE_LOOP else GL_LINE_STRIP, DefaultVertexFormats.POSITION)

            for ((cx, cy, startAngle) in corners) {
                for (i in 0..90 step 10) {
                    val angle = Math.toRadians(startAngle + i)
                    val x = cx + radiusD * sin(angle)
                    val y = cy + radiusD * cos(angle)
                    pos(x, y, 0.0).endVertex()
                }
            }
        }

        glColor(Color.WHITE)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_LINE_SMOOTH)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    fun drawRoundedBorderedWithoutBottom(
        x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float, radius: Float
    ) = renderRoundedBorder(x1, y1, x2, y2, color, width, radius, false)

    fun quickDrawRect(x: Float, y: Float, x2: Float, y2: Float) {
        drawWithTessellatorWorldRenderer {
            begin(GL_QUADS, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }
    }

    fun drawRect(x: Float, y: Float, x2: Float, y2: Float, color: Int) {
        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glColor(color)
        drawWithTessellatorWorldRenderer {
            begin(GL_QUADS, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }
        glColor(Color.WHITE)

        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_LINE_SMOOTH)
        glPopMatrix()
    }

    fun drawRect(x: Int, y: Int, x2: Int, y2: Int, color: Int) {
        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glColor(color)

        drawWithTessellatorWorldRenderer {
            begin(GL_QUADS, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }

        glColor(Color.WHITE)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_LINE_SMOOTH)
        glPopMatrix()
    }

    fun quickDrawRect(x: Float, y: Float, x2: Float, y2: Float, color: Int) {
        glPushMatrix()
        glColor(color)

        drawWithTessellatorWorldRenderer {
            begin(GL_QUADS, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }
        glColor(Color.WHITE)
        glPopMatrix()
    }

    fun quickDrawBorderedRect(x: Float, y: Float, x2: Float, y2: Float, width: Float, color1: Int, color2: Int) {
        quickDrawRect(x, y, x2, y2, color2)
        glColor(color1)
        glLineWidth(width)

        drawWithTessellatorWorldRenderer {
            begin(GL_LINE_LOOP, DefaultVertexFormats.POSITION)
            pos(x2.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y.toDouble(), 0.0).endVertex()
            pos(x.toDouble(), y2.toDouble(), 0.0).endVertex()
            pos(x2.toDouble(), y2.toDouble(), 0.0).endVertex()
        }
    }

    /**
     * Draw gradient rect.
     *
     * @param left       the left
     * @param top        the top
     * @param right      the right
     * @param bottom     the bottom
     * @param startColor the start color
     * @param endColor   the end color
     */
    fun drawGradientRect(
        left: Number, top: Number, right: Number, bottom: Number, startColor: Int, endColor: Int, zLevel: Float
    ) {
        val a1 = (startColor shr 24 and 255) / 255f
        val r1 = (startColor shr 16 and 255) / 255f
        val g1 = (startColor shr 8 and 255) / 255f
        val b1 = (startColor and 255) / 255f
        val a2 = (endColor shr 24 and 255) / 255f
        val r2 = (endColor shr 16 and 255) / 255f
        val g2 = (endColor shr 8 and 255) / 255f
        val b2 = (endColor and 255) / 255f

        pushMatrix()
        disableTexture2D()
        enableBlend()
        disableAlpha()
        tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, 1, 0)
        shadeModel(GL_SMOOTH)

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.worldRenderer

        buffer.begin(GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        buffer.pos(right.toDouble(), top.toDouble(), zLevel.toDouble()).color(r2, g2, b2, a2).endVertex()
        buffer.pos(left.toDouble(), top.toDouble(), zLevel.toDouble()).color(r1, g1, b1, a1).endVertex()
        buffer.pos(left.toDouble(), bottom.toDouble(), zLevel.toDouble()).color(r1, g1, b1, a1).endVertex()
        buffer.pos(right.toDouble(), bottom.toDouble(), zLevel.toDouble()).color(r2, g2, b2, a2).endVertex()
        tessellator.draw()

        shadeModel(GL_FLAT)
        disableBlend()
        enableAlpha()
        enableTexture2D()
        popMatrix()
    }

    fun drawLoadingCircle(x: Float, y: Float) {
        for (i in 0..3) {
            val rot = (System.nanoTime() / 5000000 * i % 360).toInt()
            drawCircle(x, y, (i * 10).toFloat(), rot - 180, rot)
        }
    }

    fun drawRoundedRect(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        radius: Float,
        cornersToRound: RoundedCorners = RoundedCorners.ALL
    ) {
        val (alpha, red, green, blue) = ColorUtils.unpackARGBFloatValue(color)

        val (newX1, newY1, newX2, newY2) = orderPoints(x1, y1, x2, y2)

        drawRoundedRectangle(newX1, newY1, newX2, newY2, red, green, blue, alpha, radius, cornersToRound)
    }

    fun drawRoundedRect2(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Color,
        radius: Float,
        cornersToRound: RoundedCorners = RoundedCorners.ALL
    ) {
        val alpha = color.alpha / 255.0f
        val red = color.red / 255.0f
        val green = color.green / 255.0f
        val blue = color.blue / 255.0f

        val (newX1, newY1, newX2, newY2) = orderPoints(x1, y1, x2, y2)

        drawRoundedRectangle(newX1, newY1, newX2, newY2, red, green, blue, alpha, radius, cornersToRound)
    }

    fun drawRoundedRect3(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        rgba: Int,
        radius: Float,
        cornersToRound: RoundedCorners = RoundedCorners.ALL
    ) {
        val alpha = (rgba ushr 24 and 0xFF) / 255.0f
        val red = (rgba ushr 16 and 0xFF) / 255.0f
        val green = (rgba ushr 8 and 0xFF) / 255.0f
        val blue = (rgba and 0xFF) / 255.0f

        val (newX1, newY1, newX2, newY2) = orderPoints(x1, y1, x2, y2)

        drawRoundedRectangle(newX1, newY1, newX2, newY2, red, green, blue, alpha, radius, cornersToRound)
    }

    fun drawRoundedRectInt(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        radius: Float,
        cornersToRound: RoundedCorners = RoundedCorners.ALL
    ) {
        val (alpha, red, green, blue) = ColorUtils.unpackARGBFloatValue(color)

        val (newX1, newY1, newX2, newY2) = orderPoints(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat())

        drawRoundedRectangle(newX1, newY1, newX2, newY2, red, green, blue, alpha, radius, cornersToRound)
    }

    enum class Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    enum class RoundedCorners(val corners: Set<Corner>, val displayName: String) {
        NONE(emptySet(), "None"), TOP_LEFT_ONLY(
            setOf(Corner.TOP_LEFT),
            "Top-Left-Only"
        ),
        TOP_RIGHT_ONLY(setOf(Corner.TOP_RIGHT), "Top-Right-Only"), BOTTOM_LEFT_ONLY(
            setOf(Corner.BOTTOM_LEFT),
            "Bottom-Left-Only"
        ),
        BOTTOM_RIGHT_ONLY(setOf(Corner.BOTTOM_RIGHT), "Bottom-Right-Only"), TOP_ONLY(
            setOf(
                Corner.TOP_LEFT,
                Corner.TOP_RIGHT
            ), "Top-Only"
        ),
        BOTTOM_ONLY(setOf(Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT), "Bottom-Only"), LEFT_ONLY(
            setOf(
                Corner.TOP_LEFT,
                Corner.BOTTOM_LEFT
            ), "Left-Only"
        ),
        RIGHT_ONLY(setOf(Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT), "Right-Only"), ALL(
            setOf(
                Corner.TOP_LEFT,
                Corner.TOP_RIGHT,
                Corner.BOTTOM_LEFT,
                Corner.BOTTOM_RIGHT
            ), "All"
        )
    }

    private fun drawRoundedRectangle(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        radius: Float,
        cornersToRound: RoundedCorners = RoundedCorners.ALL
    ) {
        val (newX1, newY1, newX2, newY2) = orderPoints(x1, y1, x2, y2)

        glPushMatrix()
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)

        glColor4f(red, green, blue, alpha)

        val radiusD = min(radius.toDouble(), min(newX2 - newX1, newY2 - newY1) / 2.0)

        val corners = arrayOf(
            Corner.BOTTOM_RIGHT to doubleArrayOf(
                newX2 - radiusD, newY2 - radiusD, 0.0, newX2.toDouble(), newY2.toDouble()
            ), Corner.TOP_RIGHT to doubleArrayOf(
                newX2 - radiusD, newY1 + radiusD, 90.0, newX2.toDouble(), newY1.toDouble()
            ), Corner.TOP_LEFT to doubleArrayOf(
                newX1 + radiusD, newY1 + radiusD, 180.0, newX1.toDouble(), newY1.toDouble()
            ), Corner.BOTTOM_LEFT to doubleArrayOf(
                newX1 + radiusD, newY2 - radiusD, 270.0, newX1.toDouble(), newY2.toDouble()
            )
        )

        drawWithTessellatorWorldRenderer {
            begin(GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION)

            for ((corner, directionData) in corners) {
                val (cx, cy, startAngle, ox, oy) = directionData

                if (corner in cornersToRound.corners) {
                    for (i in 0..90 step 10) {
                        val angle = Math.toRadians(startAngle + i)
                        val x = cx + radiusD * sin(angle)
                        val y = cy + radiusD * cos(angle)
                        pos(x, y, 0.0).endVertex()
                    }
                } else {
                    pos(ox, oy, 0.0).endVertex()
                }
            }
        }

        glColor(Color.WHITE)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_LINE_SMOOTH)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    private fun orderPoints(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray {
        val newX1 = min(x1, x2)
        val newY1 = min(y1, y2)
        val newX2 = max(x1, x2)
        val newY2 = max(y1, y2)
        return floatArrayOf(newX1, newY1, newX2, newY2)
    }

    fun drawCircle(x: Float, y: Float, radius: Float, start: Int, end: Int) {
        enableBlend()
        disableTexture2D()
        tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        glColor(Color.WHITE)
        glEnable(GL_LINE_SMOOTH)
        glLineWidth(2f)
        glBegin(GL_LINE_STRIP)
        var i = end.toFloat()
        while (i >= start) {
            val rad = i.toRadians()
            glVertex2f(
                x + cos(rad) * (radius * 1.001f), y + sin(rad) * (radius * 1.001f)
            )
            i -= 360 / 90f
        }
        glEnd()
        glDisable(GL_LINE_SMOOTH)
        enableTexture2D()
        disableBlend()
    }

    fun drawFilledCircle(xx: Int, yy: Int, radius: Float, color: Color) {
        val sections = 50
        val dAngle = 2 * Math.PI / sections
        var x: Float
        var y: Float
        glPushAttrib(GL_ENABLE_BIT)
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_LINE_SMOOTH)
        glBegin(GL_TRIANGLE_FAN)
        for (i in 0 until sections) {
            x = (radius * sin(i * dAngle)).toFloat()
            y = (radius * cos(i * dAngle)).toFloat()
            glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)
            glVertex2f(xx + x, yy + y)
        }
        resetColor()
        glEnd()
        glPopAttrib()
    }

    fun drawHead(
        skin: ResourceLocation?,
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        uWidth: Int,
        vHeight: Int,
        width: Int,
        height: Int,
        tileWidth: Float,
        tileHeight: Float,
        color: Color
    ) {
        glPushMatrix()
        val texture: ResourceLocation = skin ?: mc.thePlayer.locationSkin

        glColor(color)
        mc.textureManager.bindTexture(texture)
        drawScaledCustomSizeModalRect(x, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight)
        glColor(Color.WHITE)
        glPopMatrix()
    }

    fun drawImage(
        image: ResourceLocation?,
        x: Number,
        y: Number,
        width: Int,
        height: Int,
        color: Color = Color.WHITE,
        radius: Float = 0f
    ) {
        glPushMatrix()
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glDepthMask(false)
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        glColor(color)

        mc.textureManager.bindTexture(image)

        if (radius > 0) {
            val x1 = x.toFloat()
            val y1 = y.toFloat()
            val x2 = x1 + width
            val y2 = y1 + height
            val radiusD = min(radius.toDouble(), min(width, height) / 2.0)

            drawWithTessellatorWorldRenderer {
                begin(GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_TEX)

                val corners = arrayOf(
                    doubleArrayOf(x2 - radiusD, y2 - radiusD, 0.0),
                    doubleArrayOf(x2 - radiusD, y1 + radiusD, 90.0),
                    doubleArrayOf(x1 + radiusD, y1 + radiusD, 180.0),
                    doubleArrayOf(x1 + radiusD, y2 - radiusD, 270.0),
                )

                for (corner in corners) {
                    val (cx, cy, startAngle) = corner
                    for (i in 0..90 step 10) {
                        val angle = Math.toRadians(startAngle + i)
                        val px = cx + radiusD * sin(angle)
                        val py = cy + radiusD * cos(angle)
                        val texX = (px - x1) / width
                        val texY = (py - y1) / height
                        pos(px, py, 0.0).tex(texX, texY).endVertex()
                    }
                }
            }
        } else {
            drawModalRectWithCustomSizedTexture(
                x.toFloat(), y.toFloat(), 0f, 0f, width.toFloat(), height.toFloat(), width.toFloat(), height.toFloat()
            )
        }
        glColor(Color.WHITE)
        glDepthMask(true)
        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
        glPopMatrix()
    }

    /**
     * Draws a textured rectangle at z = 0. Args: x, y, u, v, width, height, textureWidth, textureHeight
     */
    fun drawModalRectWithCustomSizedTexture(
        x: Float, y: Float, u: Float, v: Float, width: Float, height: Float, textureWidth: Float, textureHeight: Float
    ) = drawWithTessellatorWorldRenderer {
        val f = 1f / textureWidth
        val f1 = 1f / textureHeight
        begin(7, DefaultVertexFormats.POSITION_TEX)
        pos(x.toDouble(), (y + height).toDouble(), 0.0).tex((u * f).toDouble(), ((v + height) * f1).toDouble())
            .endVertex()
        pos((x + width).toDouble(), (y + height).toDouble(), 0.0).tex(
            ((u + width) * f).toDouble(), ((v + height) * f1).toDouble()
        ).endVertex()
        pos((x + width).toDouble(), y.toDouble(), 0.0).tex(((u + width) * f).toDouble(), (v * f1).toDouble())
            .endVertex()
        pos(x.toDouble(), y.toDouble(), 0.0).tex((u * f).toDouble(), (v * f1).toDouble()).endVertex()
    }

    /**
     * Draws a textured rectangle at the stored z-value. Args: x, y, u, v, width, height.
     */
    fun drawTexturedModalRect(
        x: Int, y: Int, textureX: Int, textureY: Int, width: Int, height: Int, zLevel: Float
    ) = drawWithTessellatorWorldRenderer {
        val f = 0.00390625f
        val f1 = 0.00390625f
        begin(7, DefaultVertexFormats.POSITION_TEX)
        pos(x.toDouble(), (y + height).toDouble(), zLevel.toDouble()).tex(
            (textureX.toFloat() * f).toDouble(), ((textureY + height).toFloat() * f1).toDouble()
        ).endVertex()
        pos(
            (x + width).toDouble(), (y + height).toDouble(), zLevel.toDouble()
        ).tex(((textureX + width).toFloat() * f).toDouble(), ((textureY + height).toFloat() * f1).toDouble())
            .endVertex()
        pos((x + width).toDouble(), y.toDouble(), zLevel.toDouble()).tex(
            ((textureX + width).toFloat() * f).toDouble(), (textureY.toFloat() * f1).toDouble()
        ).endVertex()
        pos(x.toDouble(), y.toDouble(), zLevel.toDouble()).tex(
            (textureX.toFloat() * f).toDouble(), (textureY.toFloat() * f1).toDouble()
        ).endVertex()
    }

    fun glColor(red: Int, green: Int, blue: Int, alpha: Int) =
        glColor4f(red / 255f, green / 255f, blue / 255f, alpha / 255f)

    fun glColor(color: Color) = glColor(color.red, color.green, color.blue, color.alpha)

    fun glStateManagerColor(color: Color) =
        color(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f)

    private fun glColor(hex: Int) = glColor(hex shr 16 and 0xFF, hex shr 8 and 0xFF, hex and 0xFF, hex shr 24 and 0xFF)

    fun draw2D(entity: EntityLivingBase, posX: Double, posY: Double, posZ: Double, color: Int, backgroundColor: Int) {
        glPushMatrix()
        glTranslated(posX, posY, posZ)
        glRotated(-mc.renderManager.playerViewY.toDouble(), 0.0, 1.0, 0.0)
        glScaled(-0.1, -0.1, 0.1)
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDepthMask(true)
        glColor(color)
        glCallList(DISPLAY_LISTS_2D[0])
        glColor(backgroundColor)
        glCallList(DISPLAY_LISTS_2D[1])
        glTranslated(0.0, 21 + -(entity.entityBoundingBox.maxY - entity.entityBoundingBox.minY) * 12, 0.0)
        glColor(color)
        glCallList(DISPLAY_LISTS_2D[2])
        glColor(backgroundColor)
        glCallList(DISPLAY_LISTS_2D[3])

        // Stop render
        glColor4f(1f, 1f, 1f, 1f)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    fun draw2D(blockPos: BlockPos, color: Int, backgroundColor: Int) {
        val renderManager = mc.renderManager
        val (x, y, z) = blockPos.center.offset(EnumFacing.DOWN, 0.5) - renderManager.renderPos
        glPushMatrix()
        glTranslated(x, y, z)
        glRotated(-renderManager.playerViewY.toDouble(), 0.0, 1.0, 0.0)
        glScaled(-0.1, -0.1, 0.1)
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDepthMask(true)
        glColor(color)
        glCallList(DISPLAY_LISTS_2D[0])
        glColor(backgroundColor)
        glCallList(DISPLAY_LISTS_2D[1])
        glTranslated(0.0, 9.0, 0.0)
        glColor(color)
        glCallList(DISPLAY_LISTS_2D[2])
        glColor(backgroundColor)
        glCallList(DISPLAY_LISTS_2D[3])

        // Stop render
        glColor4f(1f, 1f, 1f, 1f)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glPopMatrix()
    }

    // Used to draw a WIFI-like icon.
    fun drawQuarterCircle(x: Int, y: Int, radius: Float, color: Color) {
        glPushMatrix()
        glDisable(GL_TEXTURE_2D)
        glColor(color)
        glBegin(GL_TRIANGLE_FAN)

        glVertex2f(x.toFloat(), y.toFloat())

        for (i in CIRCLE_STEPS / 8..CIRCLE_STEPS * 3 / 8) {
            val point = circlePoints[i]
            glVertex2f(x + radius * point.z.toFloat(), y + radius * point.x.toFloat())
        }

        glColor4f(1f, 1f, 1f, 1f)
        glEnd()
        glEnable(GL_TEXTURE_2D)
        glPopMatrix()
    }

    fun renderNameTag(string: String, x: Double, y: Double, z: Double) {
        val renderManager = mc.renderManager
        val (x1, y1, z1) = Vec3(x, y, z) - renderManager.renderPos

        glPushMatrix()
        glTranslated(x1, y1, z1)
        glNormal3f(0f, 1f, 0f)
        glRotatef(-renderManager.playerViewY, 0f, 1f, 0f)
        glRotatef(renderManager.playerViewX, 1f, 0f, 0f)
        glScalef(-0.05f, -0.05f, 0.05f)
        setGlCap(GL_LIGHTING, false)
        setGlCap(GL_DEPTH_TEST, false)
        setGlCap(GL_BLEND, true)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        val width = Fonts.fontSemibold35.getStringWidth(string) / 2
        drawRect(-width - 1, -1, width + 1, Fonts.fontSemibold35.FONT_HEIGHT, Int.MIN_VALUE)
        Fonts.fontSemibold35.drawString(string, -width.toFloat(), 1.5f, Color.WHITE.rgb, true)
        resetCaps()
        resetColor()
        glPopMatrix()
    }

    fun drawLine(x: Double, y: Double, x1: Double, y1: Double, width: Float) {
        glDisable(GL_TEXTURE_2D)
        glLineWidth(width)
        glBegin(GL_LINES)
        glVertex2d(x, y)
        glVertex2d(x1, y1)
        glEnd()
        glEnable(GL_TEXTURE_2D)
    }

    fun makeScissorBox(x: Float, y: Float, x2: Float, y2: Float) {
        val scaledResolution = ScaledResolution(mc)
        val factor = scaledResolution.scaleFactor
        glScissor(
            (x * factor).toInt(),
            ((scaledResolution.scaledHeight - y2) * factor).toInt(),
            ((x2 - x) * factor).toInt(),
            ((y2 - y) * factor).toInt()
        )
    }

    /**
     * GL CAP MANAGER
     *
     *
     * TODO: Remove gl cap manager and replace by something better
     */

    fun resetCaps() = glCapMap.forEach { (cap, state) -> setGlState(cap, state) }

    fun enableGlCap(cap: Int) = setGlCap(cap, true)

    fun enableGlCap(vararg caps: Int) {
        for (cap in caps) setGlCap(cap, true)
    }

    fun disableGlCap(cap: Int) = setGlCap(cap, true)

    fun disableGlCap(vararg caps: Int) {
        for (cap in caps) setGlCap(cap, false)
    }

    fun setGlCap(cap: Int, state: Boolean) {
        glCapMap[cap] = glGetBoolean(cap)
        setGlState(cap, state)
    }

    fun setGlState(cap: Int, state: Boolean) = if (state) glEnable(cap) else glDisable(cap)

    fun drawScaledCustomSizeModalRect(
        x: Int,
        y: Int,
        u: Float,
        v: Float,
        uWidth: Int,
        vHeight: Int,
        width: Int,
        height: Int,
        tileWidth: Float,
        tileHeight: Float
    ) = drawWithTessellatorWorldRenderer {
        val f = 1f / tileWidth
        val f1 = 1f / tileHeight
        begin(7, DefaultVertexFormats.POSITION_TEX)
        pos(x.toDouble(), (y + height).toDouble(), 0.0).tex(
            (u * f).toDouble(), ((v + vHeight.toFloat()) * f1).toDouble()
        ).endVertex()
        pos((x + width).toDouble(), (y + height).toDouble(), 0.0).tex(
            ((u + uWidth.toFloat()) * f).toDouble(), ((v + vHeight.toFloat()) * f1).toDouble()
        ).endVertex()
        pos((x + width).toDouble(), y.toDouble(), 0.0).tex(((u + uWidth.toFloat()) * f).toDouble(), (v * f1).toDouble())
            .endVertex()
        pos(x.toDouble(), y.toDouble(), 0.0).tex((u * f).toDouble(), (v * f1).toDouble()).endVertex()
    }

    data class ColorValueCache(val lastHue: Float, val cachedTextureID: Int)

    private val colorValueCache: MutableMap<ColorValue, MutableMap<Int, ColorValueCache>> = mutableMapOf()

    fun ColorValue.updateTextureCache(
        id: Int,
        hue: Float,
        width: Int,
        height: Int,
        generateImage: (BufferedImage, Graphics2D) -> Unit,
        drawAt: (Int) -> Unit
    ) {
        val cached = colorValueCache[this]?.get(id)
        val lastHue = cached?.lastHue

        if (lastHue == null || lastHue != hue) {
            val image = createRGBImageDrawing(width, height) { img, graphics -> generateImage(img, graphics) }
            val texture = convertImageToTexture(image)
            colorValueCache.getOrPut(this, ::mutableMapOf)[id] = ColorValueCache(hue, texture)
        }

        colorValueCache[this]?.get(id)?.cachedTextureID?.let(drawAt)
    }

    private fun createRGBImageDrawing(width: Int, height: Int, f: (BufferedImage, Graphics2D) -> Unit): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        f(image, g)

        g.dispose()
        return image
    }

    private fun convertImageToTexture(image: BufferedImage): Int {
        val width = image.width
        val height = image.height

        val pixels = IntArray(width * height)

        image.getRGB(0, 0, width, height, pixels, 0, width)

        val buffer = ByteBuffer.allocateDirect(width * height * 4)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            buffer.put(((pixel shr 16) and 0xFF).toByte())
            buffer.put(((pixel shr 8) and 0xFF).toByte())
            buffer.put(((pixel shr 0) and 0xFF).toByte())
            buffer.put(((pixel shr 24) and 0xFF).toByte())
        }

        buffer.flipSafely()

        val textureID = glGenTextures()

        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        glBindTexture(GL_TEXTURE_2D, textureID)

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer)

        glPopMatrix()
        glPopAttrib()

        return textureID
    }

    fun drawTexture(textureID: Int, x: Int, y: Int, width: Int, height: Int) {
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        glEnable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_ALPHA_TEST)
        glAlphaFunc(GL_GREATER, 0f)

        glBindTexture(GL_TEXTURE_2D, textureID)

        glTranslatef(x.toFloat(), y.toFloat(), 0.0f)

        glBegin(GL_QUADS)
        glTexCoord2f(0.0f, 0.0f); glVertex2f(0.0f, 0.0f) // Bottom-left corner
        glTexCoord2f(1.0f, 0.0f); glVertex2f(width.toFloat(), 0.0f) // Bottom-right corner
        glTexCoord2f(1.0f, 1.0f); glVertex2f(width.toFloat(), height.toFloat()) // Top-right corner
        glTexCoord2f(0.0f, 1.0f); glVertex2f(0.0f, height.toFloat()) // Top-left corner
        glEnd()

        glDisable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_ALPHA_TEST)
        glEnable(GL_CULL_FACE)

        glPopMatrix()
        glPopAttrib()
    }
}
