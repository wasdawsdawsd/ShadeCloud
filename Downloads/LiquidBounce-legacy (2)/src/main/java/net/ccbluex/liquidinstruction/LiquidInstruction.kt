/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidinstruction

import com.formdev.flatlaf.themes.FlatMacLightLaf
import net.ccbluex.liquidbounce.LiquidBounce
import java.awt.BorderLayout
import java.awt.Desktop
import javax.swing.JEditorPane
import javax.swing.JFrame
import javax.swing.WindowConstants
import javax.swing.event.HyperlinkEvent

fun main() {
    FlatMacLightLaf.setup()

    // Setup instruction frame
    val frame = JFrame("ShadeClient | Installation")
    frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
    frame.layout = BorderLayout()
    frame.isResizable = false
    frame.isAlwaysOnTop = true

    // Add instruction as editor pane (uneditable)
    val editorPane = JEditorPane().apply {
        contentType = "text/html"
        text = with(LiquidBounce::class.java) {
            getResourceAsStream("/instructions.html")!!.bufferedReader().readText()
                .replace("{assets}", classLoader.getResource("assets")!!.toString())
        }
        isEditable = false
        addHyperlinkListener { event ->
            if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                Desktop.getDesktop().browse(event.url.toURI())
            }
        }
    }

    frame.add(editorPane, BorderLayout.CENTER)

    // Pack frame
    frame.pack()

    // Set location to center of screen
    frame.setLocationRelativeTo(null)

    // Display instruction frame
    frame.isVisible = true
}