/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot.isBot
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.getHealth
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isLookingOnEntities
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.client.EntityLookup
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils.disableGlCap
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawTexturedModalRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.enableGlCap
import net.ccbluex.liquidbounce.utils.render.RenderUtils.quickDrawBorderedRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.quickDrawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.resetCaps
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.isEntityHeightVisible
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.potion.Potion
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

object NameTags : Module("NameTags", Category.RENDER) {
    private val renderSelf by boolean("RenderSelf", false)
    private val health by boolean("Health", true)
    private val healthFromScoreboard by boolean("HealthFromScoreboard", false) { health }
    private val absorption by boolean("Absorption", false) { health || healthBar }
    private val roundedHealth by boolean("RoundedHealth", true) { health }

    private val healthPrefix by boolean("HealthPrefix", false) { health }
    private val healthPrefixText by text("HealthPrefixText", "") { health && healthPrefix }

    private val healthSuffix by boolean("HealthSuffix", true) { health }
    private val healthSuffixText by text("HealthSuffixText", " HP") { health && healthSuffix }

    private val ping by boolean("Ping", false)
    private val healthBar by boolean("Bar", true)
    private val distance by boolean("Distance", false)
    private val armor by boolean("Armor", true)
    private val bot by boolean("Bots", true)
    private val potion by boolean("Potions", true)
    private val clearNames by boolean("ClearNames", false)
    private val font by font("Font", Fonts.fontRegular40)
    private val scale by float("Scale", 1F, 1F..4F)
    private val fontShadow by boolean("Shadow", true)

    private val background by boolean("Background", true)
    private val backgroundColor by color("BackgroundColor", Color.BLACK.withAlpha(70)) { background }

    private val border by boolean("Border", true)
    private val borderColor by color("BorderColor", Color.BLACK.withAlpha(100)) { border }

    private val maxRenderDistance by int("MaxRenderDistance", 50, 1..200).onChanged { value ->
        maxRenderDistanceSq = value.toDouble().pow(2)
    }

    private val onLook by boolean("OnLook", false)
    private val maxAngleDifference by float("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by boolean("ThruBlocks", true)

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private val inventoryBackground = ResourceLocation("textures/gui/container/inventory.png")
    private val decimalFormat = DecimalFormat("##0.00", DecimalFormatSymbols(Locale.ENGLISH))

    private val entities by EntityLookup<EntityLivingBase>()
        .filter { bot || !isBot(it) }
        .filter { !onLook || isLookingOnEntities(it, maxAngleDifference.toDouble()) }
        .filter { thruBlocks || isEntityHeightVisible(it) }

    val onRender3D = handler<Render3DEvent> {
        if (mc.theWorld == null || mc.thePlayer == null) return@handler

        glPushAttrib(GL_ENABLE_BIT)
        glPushMatrix()

        // Disable lightning and depth test
        glDisable(GL_LIGHTING)
        glDisable(GL_DEPTH_TEST)

        glEnable(GL_LINE_SMOOTH)

        // Enable blend
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        for (entity in entities) {
            val isRenderingSelf =
                entity is EntityPlayerSP && (mc.gameSettings.thirdPersonView != 0 || FreeCam.handleEvents())

            if (!isRenderingSelf || !renderSelf) {
                if (!isSelected(entity, false)) continue
            }

            val name = entity.displayName.unformattedText ?: continue

            val distanceSquared = mc.thePlayer.getDistanceSqToEntity(entity)

            // In case user has FreeCam enabled, we restore the position back to normal,
            // so it renders the name-tag at the player's body position instead of the FreeCam position.
            if (isRenderingSelf) {
                FreeCam.restoreOriginalPosition()
            }

            if (distanceSquared <= maxRenderDistanceSq) {
                renderNameTag(entity, isRenderingSelf, if (clearNames) ColorUtils.stripColor(name) else name)
            }

            if (isRenderingSelf) {
                FreeCam.useModifiedPosition()
            }
        }

        glDisable(GL_BLEND)
        glDisable(GL_LINE_SMOOTH)

        glPopMatrix()
        glPopAttrib()

        // Reset color
        glColor4f(1F, 1F, 1F, 1F)
    }

    private fun renderNameTag(entity: EntityLivingBase, isRenderingSelf: Boolean, name: String) {
        val thePlayer = mc.thePlayer ?: return

        // Set fontrenderer local
        val fontRenderer = font

        // Push
        glPushMatrix()

        // Translate to player position
        val renderManager = mc.renderManager
        val rotateX = if (mc.gameSettings.thirdPersonView == 2) -1.0f else 1.0f

        val (x, y, z) = entity.interpolatedPosition(entity.lastTickPos) - renderManager.renderPos

        glTranslated(x, y + entity.eyeHeight.toDouble() + 0.55, z)

        glRotatef(-renderManager.playerViewY, 0F, 1F, 0F)
        glRotatef(renderManager.playerViewX * rotateX, 1F, 0F, 0F)

        // Disable lightning and depth test
        disableGlCap(GL_LIGHTING, GL_DEPTH_TEST)

        // Enable blend
        enableGlCap(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Modify tag
        val bot = isBot(entity)
        val nameColor = if (bot) "§3" else if (entity.isInvisible) "§6" else if (entity.isSneaking) "§4" else "§7"
        val playerPing = if (entity is EntityPlayer) entity.getPing() else 0
        val playerDistance = thePlayer.getDistanceToEntity(entity)

        val distanceText = if (distance && !isRenderingSelf) "§7${playerDistance.roundToInt()} m " else ""
        val pingText =
            if (ping && entity is EntityPlayer) "§7[" + (if (playerPing > 200) "§c" else if (playerPing > 100) "§e" else "§a") + playerPing + "ms§7] " else ""
        val healthText = if (health) " " + getHealthString(entity) else ""
        val botText = if (bot) " §c§lBot" else ""

        val text = "$distanceText$pingText$nameColor$name$healthText$botText"

        // Calculate health color based on entity's health
        val healthColor = when {
            entity.health <= 0 -> Color(255, 0, 0)
            else -> {
                val healthRatio = (getHealth(entity, healthFromScoreboard) / entity.maxHealth).coerceIn(0.0F, 1.0F)
                val red = (255 * (1 - healthRatio)).toInt()
                val green = (255 * healthRatio).toInt()
                Color(red, green, 0)
            }
        }

        // Scale
        val scale = ((playerDistance / 4F).coerceAtLeast(1F) / 150F) * scale

        glScalef(-scale, -scale, scale)

        val width = fontRenderer.getStringWidth(text) * 0.5f
        fontRenderer.drawString(
            text, 1F + -width, if (fontRenderer == Fonts.minecraftFont) 1F else 1.5F, 0xFFFFFF, fontShadow
        )

        val dist = width + 4F - (-width - 2F)

        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)

        val bgColor = if (background) {
            // Background
            backgroundColor
        } else {
            // Transparent
            Color(0, 0, 0, 0)
        }

        if (border) quickDrawBorderedRect(
            -width - 2F,
            -2F,
            width + 4F,
            fontRenderer.FONT_HEIGHT + 2F + if (healthBar) 2F else 0F,
            2F,
            borderColor.rgb,
            bgColor.rgb
        )
        else quickDrawRect(
            -width - 2F, -2F, width + 4F, fontRenderer.FONT_HEIGHT + 2F + if (healthBar) 2F else 0F, bgColor.rgb
        )

        if (healthBar) {
            quickDrawRect(
                -width - 2F,
                fontRenderer.FONT_HEIGHT + 3F,
                -width - 2F + dist,
                fontRenderer.FONT_HEIGHT + 4F,
                Color(50, 50, 50).rgb
            )
            quickDrawRect(
                -width - 2F,
                fontRenderer.FONT_HEIGHT + 3F,
                -width - 2F + (dist * (getHealth(entity, healthFromScoreboard) / entity.maxHealth).coerceIn(0F, 1F)),
                fontRenderer.FONT_HEIGHT + 4F,
                healthColor.rgb
            )
        }

        glEnable(GL_TEXTURE_2D)

        fontRenderer.drawString(
            text, 1F + -width, if (fontRenderer == Fonts.minecraftFont) 1F else 1.5F, Color.white.rgb, fontShadow
        )

        var foundPotion = false

        if (potion && entity is EntityPlayer) {
            val potions =
                entity.activePotionEffects.map { Potion.potionTypes[it.potionID] }.filter { it.hasStatusIcon() }
            if (potions.isNotEmpty()) {
                foundPotion = true

                color(1.0F, 1.0F, 1.0F, 1.0F)
                disableLighting()
                enableTexture2D()

                val minX = (potions.size * -20) / 2

                glPushMatrix()
                enableRescaleNormal()
                for ((index, potion) in potions.withIndex()) {
                    color(1.0F, 1.0F, 1.0F, 1.0F)
                    mc.textureManager.bindTexture(inventoryBackground)
                    val i1 = potion.statusIconIndex
                    drawTexturedModalRect(minX + index * 20, -22, 0 + i1 % 8 * 18, 198 + i1 / 8 * 18, 18, 18, 0F)
                }
                disableRescaleNormal()
                glPopMatrix()

                enableAlpha()
                disableBlend()
                enableTexture2D()
            }
        }

        if (armor && entity is EntityPlayer) {
            RenderHelper.enableGUIStandardItemLighting()
            for (index in 0..4) {
                val itemStack = entity.getEquipmentInSlot(index) ?: continue

                mc.renderItem.zLevel = -147F
                mc.renderItem.renderItemAndEffectIntoGUI(
                    itemStack, -50 + index * 20, if (potion && foundPotion) -42 else -22
                )
            }
            RenderHelper.disableStandardItemLighting()

            enableAlpha()
            disableBlend()
            enableTexture2D()
        }

        // Reset caps
        resetCaps()

        // Reset color
        resetColor()
        glColor4f(1F, 1F, 1F, 1F)

        // Pop
        glPopMatrix()
    }

    private fun getHealthString(entity: EntityLivingBase): String {
        val prefix = if (healthPrefix) healthPrefixText else ""
        val suffix = if (healthSuffix) healthSuffixText else ""

        val result = getHealth(entity, healthFromScoreboard, absorption)

        val healthPercentage = (getHealth(entity, healthFromScoreboard) / entity.maxHealth).coerceIn(0.0F, 1.0F)
        val healthColor = when {
            entity.health <= 0 -> "§4"
            healthPercentage >= 0.75 -> "§a"
            healthPercentage >= 0.5 -> "§e"
            healthPercentage >= 0.25 -> "§6"
            else -> "§c"
        }

        return "$healthColor$prefix${if (roundedHealth) result.roundToInt() else decimalFormat.format(result)}$suffix"
    }

    fun shouldRenderNameTags(entity: Entity) =
        handleEvents() && entity is EntityLivingBase && (ESP.handleEvents() && ESP.renderNameTags || isSelected(
            entity,
            false
        ) && (bot || !isBot(entity)))
}
