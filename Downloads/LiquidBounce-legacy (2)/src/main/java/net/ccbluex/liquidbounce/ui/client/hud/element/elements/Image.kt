/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements

import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.utils.io.FileFilters
import net.ccbluex.liquidbounce.utils.io.MiscUtils
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.randomNumber
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawImage
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import java.awt.Color
import java.io.File
import java.util.*
import javax.imageio.ImageIO

/**
 * CustomHUD image element
 *
 * Draw custom image
 */
@ElementInfo(name = "Image")
class Image : Element("Image") {

    private val color by color("Color", Color.WHITE)
    private val shadow by boolean("Shadow", true)
    private val xDistance by float("ShadowXDistance", 1.0F, -2F..2F) { shadow }
    private val yDistance by float("ShadowYDistance", 1.0F, -2F..2F) { shadow }
    private val shadowColor by color("ShadowColor", Color.BLACK.withAlpha(128)) { shadow }

    companion object {

        /**
         * Create default element
         */
        fun default(): Image {
            val image = Image()

            image.x = 1.0
            image.y = 1.0

            return image
        }

    }

    private val image = text("Image", "").onChanged { value ->
        if (value.isBlank()) return@onChanged

        setImage(value)
    }

    private val resourceLocation = ResourceLocation(randomNumber(128))
    private var width = 64
    private var height = 64

    /**
     * Draw element
     */
    override fun drawElement(): Border {
        if (shadow) {
            drawImage(resourceLocation, xDistance, yDistance, width / 2, height / 2, shadowColor)
        }

        drawImage(resourceLocation, 0, 0, width / 2, height / 2, color)

        return Border(0F, 0F, width / 2F, height / 2F)
    }

    override fun createElement(): Boolean {
        val file = MiscUtils.openFileChooser(FileFilters.ALL_IMAGES, acceptAll = false) ?: return false

        if (!file.exists()) {
            MiscUtils.showMessageDialog("Error", "The file does not exist.")
            return false
        }

        if (file.isDirectory) {
            MiscUtils.showMessageDialog("Error", "The file is a directory.")
            return false
        }

        return try {
            setImage(file)
            true
        } catch (e: Exception) {
            MiscUtils.showMessageDialog("Error", "Exception occurred while opening the image: ${e.message}")
            false
        }
    }

    private fun setImage(b64image: String): Image {
        this.image.changeValue(b64image)

        val bufferedImage = Base64.getDecoder().decode(b64image).inputStream().use(ImageIO::read)

        width = bufferedImage.width
        height = bufferedImage.height

        mc.textureManager.loadTexture(resourceLocation, DynamicTexture(bufferedImage))

        return this
    }

    private fun setImage(image: File): Image {
        setImage(Base64.getEncoder().encodeToString(image.readBytes()))
        return this
    }

}