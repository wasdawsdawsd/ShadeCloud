/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
@file:Suppress("unused")

package net.ccbluex.liquidbounce.features.module.modules.world

import kotlinx.coroutines.delay
import net.ccbluex.liquidbounce.LiquidBounce.hud
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.AutoArmor
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner.canBeSortedTo
import net.ccbluex.liquidbounce.features.module.modules.player.InventoryCleaner.isStackUseful
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.extensions.component1
import net.ccbluex.liquidbounce.utils.extensions.component2
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.canClickInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.chestStealerCurrentSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryManager.chestStealerLastSlot
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.countSpaceInInventory
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.hasSpaceInInventory
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.timing.TickedActions.awaitTicked
import net.ccbluex.liquidbounce.utils.timing.TickedActions.clickNextTick
import net.ccbluex.liquidbounce.utils.timing.TickedActions.isTicked
import net.ccbluex.liquidbounce.utils.timing.TickedActions.nextTick
import net.ccbluex.liquidbounce.utils.timing.TimeUtils.randomDelay
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.entity.EntityLiving.getArmorPosition
import net.minecraft.init.Blocks
import net.minecraft.item.ItemArmor
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraft.network.play.server.S2EPacketCloseWindow
import net.minecraft.network.play.server.S30PacketWindowItems
import java.awt.Color
import kotlin.math.sqrt

object ChestStealer : Module("ChestStealer", Category.WORLD) {

    private val smartDelay by boolean("SmartDelay", false)
    private val multiplier by int("DelayMultiplier", 120, 0..500) { smartDelay }
    private val smartOrder by boolean("SmartOrder", true) { smartDelay }

    private val simulateShortStop by boolean("SimulateShortStop", false)

    private val delay by intRange("Delay", 50..50, 0..500) { !smartDelay }
    private val startDelay by intRange("StartDelay", 50..100, 0..500)
    private val closeDelay by intRange("CloseDelay", 50..100, 0..500)

    private val noMove by +InventoryManager.noMoveValue
    private val noMoveAir by +InventoryManager.noMoveAirValue
    private val noMoveGround by +InventoryManager.noMoveGroundValue

    private val chestTitle by boolean("ChestTitle", true)

    private val randomSlot by boolean("RandomSlot", true)

    private val progressBar by boolean("ProgressBar", true).subjective()

    val silentGUI by boolean("SilentGUI", false).subjective()

    val highlightSlot by boolean("Highlight-Slot", false) { !silentGUI }.subjective()
    val backgroundColor =
        color("BackgroundColor", Color(128, 128, 128)) { highlightSlot && !silentGUI }.subjective()

    val borderStrength by int("Border-Strength", 3, 1..5) { highlightSlot && !silentGUI }.subjective()
    val borderColor = color("BorderColor", Color(128, 128, 128)) { highlightSlot && !silentGUI }.subjective()

    private val chestDebug by choices("Chest-Debug", arrayOf("Off", "Text", "Notification"), "Off").subjective()
    private val itemStolenDebug by boolean("ItemStolen-Debug", false) { chestDebug != "Off" }.subjective()

    private var progress: Float? = null
        set(value) {
            field = value?.coerceIn(0f, 1f)

            if (field == null)
                easingProgress = 0f
        }

    private var easingProgress = 0f

    private var receivedId: Int? = null

    private var stacks = emptyList<ItemStack?>()

    private suspend fun shouldOperate(): Boolean {
        while (true) {
            if (!handleEvents())
                return false

            if (mc.playerController?.currentGameType?.isSurvivalOrAdventure != true)
                return false

            if (mc.currentScreen !is GuiChest)
                return false

            if (mc.thePlayer?.openContainer?.windowId != receivedId)
                return false

            // Wait till NoMove check isn't violated
            if (canClickInventory())
                return true

            // If NoMove is violated, wait a tick and check again
            // If there is no delay, very weird things happen: https://www.guilded.gg/CCBlueX/groups/1dgpg8Jz/channels/034be45e-1b72-4d5a-bee7-d6ba52ba1657/chat?messageId=94d314cd-6dc4-41c7-84a7-212c8ea1cc2a
            delay(50)
        }
    }

    suspend fun stealFromChest() {
        if (!handleEvents())
            return

        val thePlayer = mc.thePlayer ?: return

        val screen = mc.currentScreen ?: return

        if (screen !is GuiChest || !shouldOperate())
            return

        // Check if chest isn't a custom gui
        if (chestTitle && Blocks.chest.localizedName !in (screen.lowerChestInventory ?: return).name)
            return

        progress = 0f

        delay(startDelay.random().toLong())

        debug("Stealing items..")

        // Go through the chest multiple times, till there are no useful items anymore
        while (true) {
            if (!shouldOperate())
                return

            if (!hasSpaceInInventory())
                return

            var hasTaken = false

            val itemsToSteal = getItemsToSteal()

            run scheduler@{
                itemsToSteal.forEachIndexed { index, (slot, stack, sortableTo) ->
                    // Wait for NoMove or cancel click
                    if (!shouldOperate()) {
                        nextTick { SilentHotbar.resetSlot() }
                        chestStealerCurrentSlot = -1
                        chestStealerLastSlot = -1
                        return
                    }

                    if (!hasSpaceInInventory()) {
                        chestStealerCurrentSlot = -1
                        chestStealerLastSlot = -1
                        return@scheduler
                    }

                    hasTaken = true

                    // Set current slot being stolen for highlighting
                    chestStealerCurrentSlot = slot

                    val stealingDelay = if (smartDelay && index + 1 < itemsToSteal.size) {
                        val dist = squaredDistanceOfSlots(slot, itemsToSteal[index + 1].index)
                        val trueDelay = sqrt(dist.toDouble()) * multiplier
                        randomDelay(trueDelay.toInt(), trueDelay.toInt() + 20)
                    } else {
                        delay.random()
                    }

                    if (itemStolenDebug) debug("item: ${stack.displayName.lowercase()} | slot: $slot | delay: ${stealingDelay}ms")

                    // If target is sortable to a hotbar slot, steal and sort it at the same time, else shift + left-click
                    clickNextTick(slot, sortableTo ?: 0, if (sortableTo != null) 2 else 1) {
                        progress = (index + 1) / itemsToSteal.size.toFloat()

                        if (!AutoArmor.canEquipFromChest())
                            return@clickNextTick

                        val item = stack.item

                        if (item !is ItemArmor || thePlayer.inventory.armorInventory[getArmorPosition(stack) - 1] != null)
                            return@clickNextTick

                        // TODO: should the stealing be suspended until the armor gets equipped and some delay on top of that, maybe toggleable?
                        // Try to equip armor piece from hotbar 1 tick after stealing it
                        nextTick {
                            val hotbarStacks = thePlayer.inventory.mainInventory.take(9)

                            // Can't get index of stack instance, because it is different even from the one returned from windowClick()
                            val newIndex = hotbarStacks.indexOfFirst { it?.getIsItemStackEqual(stack) == true }

                            if (newIndex != -1)
                                AutoArmor.equipFromHotbarInChest(newIndex, stack)
                        }
                    }

                    delay(stealingDelay.toLong())

                    if (simulateShortStop && Math.random() > 0.75) {
                        val minDelays = randomDelay(150, 300)
                        val maxDelays = randomDelay(minDelays, 500)
                        val randomDelay = randomDelay(minDelays, maxDelays).toLong()

                        delay(randomDelay)
                    }
                }
            }

            // If no clicks were sent in the last loop stop searching
            if (!hasTaken) {
                progress = 1f
                delay(closeDelay.random().toLong())

                nextTick { SilentHotbar.resetSlot() }
                break
            }

            // Wait till all scheduled clicks were sent
            awaitTicked()

            // Before closing the chest, check all items once more, whether server hadn't cancelled some of the actions.
            stacks = thePlayer.openContainer.inventory
        }

        // Wait before the chest gets closed (if it gets closed out of tick loop it could throw npe)
        nextTick {
            chestStealerCurrentSlot = -1
            chestStealerLastSlot = -1
            thePlayer.closeScreen()
            progress = null

            debug("Chest closed")
        }

        awaitTicked()
    }

    private fun squaredDistanceOfSlots(from: Int, to: Int): Int {
        fun getCoords(slot: Int): IntArray {
            val x = slot % 9
            val y = slot / 9
            return intArrayOf(x, y)
        }

        val (x1, y1) = getCoords(from)
        val (x2, y2) = getCoords(to)
        return (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
    }

    private data class ItemTakeRecord(
        val index: Int,
        val stack: ItemStack,
        val sortableToSlot: Int?
    )

    private fun getItemsToSteal(): List<ItemTakeRecord> {
        val sortBlacklist = BooleanArray(9)

        var spaceInInventory = countSpaceInInventory()

        val itemsToSteal = stacks.dropLast(36)
            .mapIndexedNotNullTo(ArrayList(32)) { index, stack ->
                stack ?: return@mapIndexedNotNullTo null

                if (isTicked(index)) return@mapIndexedNotNullTo null

                val mergeableCount = mc.thePlayer.inventory.mainInventory.sumOf { otherStack ->
                    otherStack ?: return@sumOf 0

                    if (otherStack.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(stack, otherStack))
                        otherStack.maxStackSize - otherStack.stackSize
                    else 0
                }

                val canMerge = mergeableCount > 0
                val canFullyMerge = mergeableCount >= stack.stackSize

                // Clicking this item wouldn't take it from chest or merge it
                if (!canMerge && spaceInInventory <= 0) return@mapIndexedNotNullTo null

                // If stack can be merged without occupying any additional slot, do not take stack limits into account
                // TODO: player could theoretically already have too many stacks in inventory before opening the chest so no more should even get merged
                // TODO: if it can get merged but would also need another slot, it could simulate 2 clicks, one which maxes out the stack in inventory and second that puts excess items back
                if (InventoryCleaner.handleEvents() && !isStackUseful(stack, stacks, noLimits = canFullyMerge))
                    return@mapIndexedNotNullTo null

                var sortableTo: Int? = null

                // If stack can get merged, do not try to sort it, normal shift + left-click will merge it
                if (!canMerge && InventoryCleaner.handleEvents() && InventoryCleaner.sort) {
                    for (hotbarIndex in 0..8) {
                        if (sortBlacklist[hotbarIndex])
                            continue

                        if (!canBeSortedTo(hotbarIndex, stack.item))
                            continue

                        val hotbarStack = stacks.getOrNull(stacks.size - 9 + hotbarIndex)

                        // If occupied hotbar slot isn't already sorted or isn't strictly best, sort to it
                        if (!canBeSortedTo(hotbarIndex, hotbarStack?.item) || !isStackUseful(
                                hotbarStack,
                                stacks,
                                strictlyBest = true
                            )
                        ) {
                            sortableTo = hotbarIndex
                            sortBlacklist[hotbarIndex] = true
                            break
                        }
                    }
                }

                // If stack gets fully merged, no slot in inventory gets occupied
                if (!canFullyMerge) spaceInInventory--

                ItemTakeRecord(index, stack, sortableTo)
            }.also { it ->
                if (randomSlot)
                    it.shuffle()

                // Prioritise armor pieces with lower priority, so that as many pieces can get equipped from hotbar after chest gets closed
                it.sortByDescending { it.stack.item is ItemArmor }

                // Prioritize items that can be sorted
                it.sortByDescending { it.sortableToSlot != null }

                // Fully prioritise armor pieces when it is possible to equip armor while in chest
                if (AutoArmor.canEquipFromChest())
                    it.sortByDescending { it.stack.item is ItemArmor }

                if (smartOrder) {
                    sortBasedOnOptimumPath(it)
                }
            }

        return itemsToSteal
    }

    private fun sortBasedOnOptimumPath(itemsToSteal: MutableList<ItemTakeRecord>) {
        for (i in itemsToSteal.indices) {
            var nextIndex = i
            var minDistance = Int.MAX_VALUE
            var next: ItemTakeRecord? = null
            for (j in i + 1 until itemsToSteal.size) {
                val distance = squaredDistanceOfSlots(itemsToSteal[i].index, itemsToSteal[j].index)
                if (distance < minDistance) {
                    minDistance = distance
                    next = itemsToSteal[j]
                    nextIndex = j
                }
            }
            if (next != null) {
                itemsToSteal[nextIndex] = itemsToSteal[i + 1]
                itemsToSteal[i + 1] = next
            }
        }
    }

    // Progress bar
    val onRender2D = handler<Render2DEvent> { event ->
        if (!progressBar || mc.currentScreen !is GuiChest)
            return@handler

        val progress = progress ?: return@handler

        val (scaledWidth, scaledHeight) = ScaledResolution(mc)

        val minX = scaledWidth * 0.3f
        val maxX = scaledWidth * 0.7f
        val minY = scaledHeight * 0.75f
        val maxY = minY + 10f

        easingProgress += (progress - easingProgress) / 6f * event.partialTicks

        drawRect(minX - 2, minY - 2, maxX + 2, maxY + 2, Color(200, 200, 200).rgb)
        drawRect(minX, minY, maxX, maxY, Color(50, 50, 50).rgb)
        drawRect(
            minX,
            minY,
            minX + (maxX - minX) * easingProgress,
            maxY,
            Color.HSBtoRGB(easingProgress / 5, 1f, 1f) or 0xFF0000
        )
    }

    val onPacket = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is C0DPacketCloseWindow, is S2DPacketOpenWindow, is S2EPacketCloseWindow -> {
                receivedId = null
                progress = null
            }

            is S30PacketWindowItems -> {
                // Chests never have windowId 0
                val packetWindowId = packet.func_148911_c()

                if (packetWindowId == 0)
                    return@handler

                if (receivedId != packetWindowId) {
                    debug("Chest opened with ${stacks.size} items")
                }

                receivedId = packetWindowId

                stacks = packet.itemStacks.toList()
            }
        }
    }

    private fun debug(message: String) {
        if (chestDebug == "Off") return

        when (chestDebug.lowercase()) {
            "text" -> chat(message)
            "notification" -> hud.addNotification(Notification.informative(this, message, 500L))
        }
    }
}
