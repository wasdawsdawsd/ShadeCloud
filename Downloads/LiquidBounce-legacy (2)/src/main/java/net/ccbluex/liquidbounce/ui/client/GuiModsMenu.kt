/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.ui.client

import kotlinx.coroutines.launch
import net.ccbluex.liquidbounce.LiquidBounce.clientRichPresence
import net.ccbluex.liquidbounce.file.FileManager.saveConfig
import net.ccbluex.liquidbounce.file.FileManager.valuesConfig
import net.ccbluex.liquidbounce.lang.translationMenu
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.kotlin.SharedScopes
import net.ccbluex.liquidbounce.utils.ui.AbstractScreen
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiTextField
import net.minecraftforge.fml.client.GuiModList
import org.lwjgl.input.Keyboard

class GuiModsMenu(private val prevGui: GuiScreen) : AbstractScreen() {

    private lateinit var customTextField: GuiTextField

    override fun initGui() {
        +GuiButton(0, width / 2 - 100, height / 4 + 48, "Forge Mods")
        +GuiButton(1, width / 2 - 100, height / 4 + 48 + 25, "Scripts")
        +GuiButton(
            2,
            width / 2 - 100,
            height / 4 + 48 + 85,
            "Toggle: ${if (clientRichPresence.showRPCValue) "§aON" else "§cOFF"}"
        )
        +GuiButton(
            3,
            width / 2 - 100,
            height / 4 + 48 + 110,
            "Show IP: ${if (clientRichPresence.showRPCServerIP) "§aON" else "§cOFF"}"
        )
        +GuiButton(
            4,
            width / 2 - 100,
            height / 4 + 48 + 135,
            "Show Modules Count: ${if (clientRichPresence.showRPCModulesCount) "§aON" else "§cOFF"}"
        )
        +GuiButton(5, width / 2 - 100, height / 4 + 48 + 255, "Back")

        customTextField = GuiTextField(2, Fonts.fontSemibold35, width / 2 - 100, height / 4 + 48 + 190, 200, 20)
        customTextField.maxStringLength = Int.MAX_VALUE
    }

    override fun actionPerformed(button: GuiButton) {
        when (val id = button.id) {
            // Forge Mods
            0 -> mc.displayGuiScreen(GuiModList(this))

            // Scripts
            1 -> mc.displayGuiScreen(GuiScripts(this))

            // Toggle
            2 -> {
                val rpc = clientRichPresence

                rpc.showRPCValue = if (rpc.showRPCValue) {
                    rpc.shutdown()
                    changeDisplayState(id, false)
                    false
                } else {
                    var value = true
                    SharedScopes.IO.launch {
                        value = try {
                            rpc.setup()
                            true
                        } catch (throwable: Throwable) {
                            LOGGER.error("Failed to setup Discord RPC.", throwable)
                            false
                        }
                    }
                    changeDisplayState(id, value)
                    value
                }
            }

            // Show IP
            3 -> {
                val rpc = clientRichPresence
                rpc.showRPCServerIP = if (rpc.showRPCServerIP) {
                    changeDisplayState(id, false)
                    false
                } else {
                    var value = true
                    SharedScopes.IO.launch {
                        value = try {
                            rpc.update()
                            true
                        } catch (throwable: Throwable) {
                            LOGGER.error("Failed to update Discord RPC.", throwable)
                            false
                        }
                    }
                    changeDisplayState(id, value)
                    value
                }
            }

            // Show Modules Count
            4 -> {
                val rpc = clientRichPresence
                rpc.showRPCModulesCount = if (rpc.showRPCModulesCount) {
                    rpc.shutdown()
                    changeDisplayState(id, false)
                    false
                } else {
                    var value = true
                    SharedScopes.IO.launch {
                        value = try {
                            rpc.update()
                            true
                        } catch (throwable: Throwable) {
                            LOGGER.error("Failed to update Discord RPC.", throwable)
                            false
                        }
                    }
                    changeDisplayState(id, value)
                    value
                }
            }

            // Back
            5 -> mc.displayGuiScreen(prevGui)
        }
    }

    private fun changeDisplayState(buttonId: Int, state: Boolean) {
        val button = buttonList[buttonId]
        val displayName = button.displayString
        button.displayString = when (state) {
            false -> displayName.replace("§aON", "§cOFF")
            true -> displayName.replace("§cOFF", "§aON")
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawBackground(0)

        Fonts.fontBold180.drawCenteredString(translationMenu("mods"), width / 2F, height / 8F + 5F, 4673984, true)

        Fonts.fontSemibold40.drawCenteredString("Rich Presence Settings:", width / 2F, height / 4 + 48 + 70F, 0xffffff, true)
        Fonts.fontSemibold40.drawCenteredString("Rich Presence Text:", width / 2F, height / 4 + 48 + 175F, 0xffffff, true)

        customTextField.drawTextBox()
        if (customTextField.text.isEmpty() && !customTextField.isFocused) {
            Fonts.fontSemibold35.drawStringWithShadow(
                clientRichPresence.customRPCText.ifEmpty { translationMenu("discordRPC.typeBox") },
                customTextField.xPosition + 4f,
                customTextField.yPosition + (customTextField.height - Fonts.fontSemibold35.FONT_HEIGHT) / 2F,
                0xffffff
            )
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        customTextField.mouseClicked(mouseX, mouseY, mouseButton)
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (Keyboard.KEY_ESCAPE == keyCode) {
            mc.displayGuiScreen(prevGui)
            return
        }

        if (customTextField.isFocused) {
            customTextField.textboxKeyTyped(typedChar, keyCode)
            clientRichPresence.customRPCText = customTextField.text
            saveConfig(valuesConfig)
        }

        super.keyTyped(typedChar, keyCode)
    }
}