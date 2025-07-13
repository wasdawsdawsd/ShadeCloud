/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPackets
import net.minecraft.network.play.client.C19PacketResourcePackStatus
import net.minecraft.network.play.client.C19PacketResourcePackStatus.Action.*
import net.minecraft.network.play.server.S48PacketResourcePackSend
import java.net.URI
import java.net.URISyntaxException

object ResourcePackSpoof : Module("ResourcePackSpoof", Category.MISC, gameDetecting = false) {

    val onPacket = handler<PacketEvent> { event ->
        val packet = event.packet as? S48PacketResourcePackSend ?: return@handler

        val url = packet.url
        val hash = packet.hash

        try {
            val scheme = URI(url).scheme
            val isLevelProtocol = "level" == scheme

            if ("http" != scheme && "https" != scheme && !isLevelProtocol)
                throw URISyntaxException(url, "Wrong protocol")

            if (isLevelProtocol && (".." in url || !url.endsWith("/resources.zip")))
                throw URISyntaxException(url, "Invalid levelstorage resourcepack path")

            sendPackets(
                C19PacketResourcePackStatus(packet.hash, ACCEPTED),
                C19PacketResourcePackStatus(packet.hash, SUCCESSFULLY_LOADED)
            )
        } catch (e: URISyntaxException) {
            LOGGER.error("Failed to handle resource pack", e)
            sendPacket(C19PacketResourcePackStatus(hash, FAILED_DOWNLOAD))
        }
    }

}