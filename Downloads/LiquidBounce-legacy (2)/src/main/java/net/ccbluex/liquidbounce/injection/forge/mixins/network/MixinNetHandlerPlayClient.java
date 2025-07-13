/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.network;

import io.netty.buffer.Unpooled;
import net.ccbluex.liquidbounce.event.EntityMovementEvent;
import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.features.module.modules.exploit.AntiExploit;
import net.ccbluex.liquidbounce.features.module.modules.misc.NoRotateSet;
import net.ccbluex.liquidbounce.features.module.modules.player.Blink;
import net.ccbluex.liquidbounce.features.special.ClientFixes;
import net.ccbluex.liquidbounce.ui.client.hud.HUD;
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification;
import net.ccbluex.liquidbounce.utils.client.ClientUtils;
import net.ccbluex.liquidbounce.utils.client.PacketUtils;
import net.ccbluex.liquidbounce.utils.rotation.Rotation;
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils;
import net.ccbluex.liquidbounce.utils.extensions.PlayerExtensionKt;
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.*;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;
import java.net.URISyntaxException;

import static net.ccbluex.liquidbounce.utils.client.ClientUtilsKt.chat;
import static net.ccbluex.liquidbounce.utils.client.MinecraftInstance.mc;
import static net.minecraft.network.play.client.C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD;

@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    @Shadow
    public int currentServerMaxPlayers;
    @Shadow
    @Final
    private NetworkManager netManager;
    @Shadow
    private Minecraft gameController;
    @Shadow
    private WorldClient clientWorldController;

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void cancelExplosionMotion(S27PacketExplosion packetExplosion, CallbackInfo ci) {
        AntiExploit module = AntiExploit.INSTANCE;

        double motionX = packetExplosion.field_149159_h;
        double motionY = packetExplosion.func_149144_d();
        double motionZ = packetExplosion.func_149147_e();

        if (module.handleEvents() && module.getCancelExplosionMotion()) {
            double x = MathHelper.clamp_double(motionX, -50.0, 50.0);
            double y = MathHelper.clamp_double(motionY, -50.0, 50.0);
            double z = MathHelper.clamp_double(motionZ, -50.0, 50.0);

            if (x != motionX || y != motionY || z != motionZ) {
                if (module.getWarn().equals("Chat")) {
                    chat("Cancelled too strong TNT explosion motion");
                } else if (module.getWarn().equals("Notification")) {
                    HUD.INSTANCE.addNotification(Notification.Companion.informative(module,"Cancelled too strong TNT explosion motion", 1000L));
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void cancelExplosionStrength(S27PacketExplosion packetExplosion, CallbackInfo ci) {
        AntiExploit module = AntiExploit.INSTANCE;

        if (module.handleEvents() && module.getCancelExplosionStrength()) {
            float originalStrength = packetExplosion.getStrength();
            float strength = MathHelper.clamp_float(originalStrength, -100f, 100f);

            if (strength != originalStrength) {
                if (module.getWarn().equals("Chat")) {
                    chat("Cancelled too strong TNT explosion strength");
                } else if (module.getWarn().equals("Notification")) {
                    HUD.INSTANCE.addNotification(Notification.Companion.informative(module, "Cancelled too strong TNT explosion strength", 1000L));
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void cancelExplosionRadius(S27PacketExplosion packetExplosion, CallbackInfo ci) {
        AntiExploit module = AntiExploit.INSTANCE;

        if (module.handleEvents() && module.getCancelExplosionRadius()) {
            float originalRadius = packetExplosion.func_149149_c();
            float radius = MathHelper.clamp_float(originalRadius, -100f, 100f);

            if (radius != originalRadius) {
                if (module.getWarn().equals("Chat")) {
                    chat("Cancelled too big TNT explosion radius");
                } else if (module.getWarn().equals("Notification")) {
                    HUD.INSTANCE.addNotification(Notification.Companion.informative(module, "Cancelled too big TNT explosion radius", 1000L));
                }
                ci.cancel();
            }
        }
    }

    @Redirect(method = "handleParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2APacketParticles;getParticleCount()I", ordinal = 1))
    private int onParticleAmount(S2APacketParticles packetParticles) {
        AntiExploit module = AntiExploit.INSTANCE;

        if (module.handleEvents() && module.getLimitParticlesAmount() && packetParticles.getParticleCount() >= 500) {
            if (module.getWarn().equals("Chat")) {
                chat("Limited too many particles");
            } else if (module.getWarn().equals("Notification")) {
                HUD.INSTANCE.addNotification(Notification.Companion.informative(module, "Limited too many particles", 1000L));
            }
            return 100;
        }
        return packetParticles.getParticleCount();
    }

    @Redirect(method = "handleParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2APacketParticles;getParticleSpeed()F"))
    private float onParticleSpeed(S2APacketParticles packetParticles) {
        AntiExploit module = AntiExploit.INSTANCE;

        if (module.handleEvents() && module.getLimitParticlesSpeed() && packetParticles.getParticleSpeed() >= 10f) {
            if (module.getWarn().equals("Chat")) {
                chat("Limited too fast particles speed");
            } else if (module.getWarn().equals("Notification")) {
                HUD.INSTANCE.addNotification(Notification.Companion.informative(module, "Limited too fast particles speed", 1000L));
            }
            return 5f;
        }
        return packetParticles.getParticleSpeed();
    }

    @Redirect(method = "handleSpawnObject", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S0EPacketSpawnObject;getType()I"))
    private int onSpawnObjectType(S0EPacketSpawnObject packet) {
        AntiExploit module = AntiExploit.INSTANCE;
        
        if (module.handleEvents() && module.getLimitedEntitySpawn()) {
            if (packet.getType() == 60) {
                int arrows = module.getArrowMax();
                module.setArrowMax(arrows + 1);

                if (arrows >= module.getMaxArrowsSpawned()) {
                    if (module.getWarn().equals("Chat")) {
                        chat("Limited too many arrows spawned");
                    } else if (module.getWarn().equals("Notification")) {
                        HUD.INSTANCE.addNotification(Notification.Companion.informative(module, "Limited too many arrows spawned", 1000L));
                    }
                    return -1;
                }
            }
            if (packet.getType() == 2) {
                int items = module.getItemMax();
                module.setItemMax(items + 1);

                if (items >= module.getMaxItemDropped()) {
                    if (module.getWarn().equals("Chat")) {
                        chat("Limited too many items dropped");
                    } else if (module.getWarn().equals("Notification")) {
                        HUD.INSTANCE.addNotification(Notification.Companion.informative(module,"Limited too many items dropped", 1000L));
                    }
                    return -1;
                }
            }
        }
        return packet.getType();
    }

    @Redirect(method = "handleChangeGameState", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S2BPacketChangeGameState;getGameState()I"))
    private int onChangeGameState(S2BPacketChangeGameState packet) {
        if (AntiExploit.INSTANCE.handleEvents() && AntiExploit.INSTANCE.getCancelDemo() && packet.getGameState() == 5) {
            chat("Cancelled Demo GameState packet");
            return -1; // Cancel demo
        }

        return packet.getGameState();
    }

    @Inject(method = "handleResourcePack", at = @At("HEAD"), cancellable = true)
    private void handleResourcePack(final S48PacketResourcePackSend p_handleResourcePack_1_, final CallbackInfo callbackInfo) {
        final String url = p_handleResourcePack_1_.getURL();
        final String hash = p_handleResourcePack_1_.getHash();

        if (ClientFixes.INSTANCE.getBlockResourcePackExploit()) {
            try {
                final String scheme = new URI(url).getScheme();
                final boolean isLevelProtocol = "level".equals(scheme);

                if (!"http".equals(scheme) && !"https".equals(scheme) && !isLevelProtocol)
                    throw new URISyntaxException(url, "Wrong protocol");

                if (isLevelProtocol && (url.contains("..") || !url.endsWith("/resources.zip")))
                    throw new URISyntaxException(url, "Invalid levelstorage resourcepack path");
            } catch (final URISyntaxException e) {
                ClientUtils.INSTANCE.getLOGGER().error("Failed to handle resource pack", e);

                // We fail of course.
                netManager.sendPacket(new C19PacketResourcePackStatus(hash, FAILED_DOWNLOAD));

                callbackInfo.cancel();
            }
        }
    }

    @Inject(method = "handleJoinGame", at = @At("HEAD"), cancellable = true)
    private void handleJoinGameWithAntiForge(S01PacketJoinGame packetIn, final CallbackInfo callbackInfo) {
        if (!ClientFixes.INSTANCE.getFmlFixesEnabled() || !ClientFixes.INSTANCE.getBlockFML() || mc.isIntegratedServerRunning())
            return;

        PacketThreadUtil.checkThreadAndEnqueue(packetIn, (NetHandlerPlayClient) (Object) this, gameController);
        gameController.playerController = new PlayerControllerMP(gameController, (NetHandlerPlayClient) (Object) this);
        clientWorldController = new WorldClient((NetHandlerPlayClient) (Object) this, new WorldSettings(0L, packetIn.getGameType(), false, packetIn.isHardcoreMode(), packetIn.getWorldType()), packetIn.getDimension(), packetIn.getDifficulty(), gameController.mcProfiler);
        gameController.gameSettings.difficulty = packetIn.getDifficulty();
        gameController.loadWorld(clientWorldController);
        gameController.thePlayer.dimension = packetIn.getDimension();
        gameController.displayGuiScreen(new GuiDownloadTerrain((NetHandlerPlayClient) (Object) this));
        gameController.thePlayer.setEntityId(packetIn.getEntityId());
        currentServerMaxPlayers = packetIn.getMaxPlayers();
        gameController.thePlayer.setReducedDebug(packetIn.isReducedDebugInfo());
        gameController.playerController.setGameType(packetIn.getGameType());
        gameController.gameSettings.sendSettingsToServer();
        netManager.sendPacket(new C17PacketCustomPayload("MC|Brand", (new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));
        callbackInfo.cancel();
    }

    @Inject(method = "handleEntityMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;onGround:Z"))
    private void handleEntityMovementEvent(S14PacketEntity packetIn, final CallbackInfo callbackInfo) {
        final Entity entity = packetIn.getEntity(clientWorldController);

        if (entity != null)
            EventManager.INSTANCE.call(new EntityMovementEvent(entity));
    }

    @Inject(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setPositionAndRotation(DDDFF)V", shift = At.Shift.BEFORE))
    private void injectNoRotateSetPositionOnly(S08PacketPlayerPosLook p_handlePlayerPosLook_1_, CallbackInfo ci) {
        NoRotateSet module = NoRotateSet.INSTANCE;

        // Save the server's requested rotation before it resets the rotations
        module.setSavedRotation(PlayerExtensionKt.getRotation(Minecraft.getMinecraft().thePlayer));
    }

    @Redirect(method = "handlePlayerPosLook", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void injectNoRotateSetAndAntiServerRotationOverride(NetworkManager instance, Packet p_sendPacket_1_) {
        Blink module2 = Blink.INSTANCE;
        boolean shouldTrigger = module2.blinkingSend();
        PacketUtils.sendPacket(p_sendPacket_1_, shouldTrigger);

        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        NoRotateSet module = NoRotateSet.INSTANCE;

        if (player == null || !module.shouldModify(player)) {
            return;
        }

        int sign = RandomUtils.INSTANCE.nextBoolean() ? 1 : -1;

        Rotation rotation = player.ticksExisted == 0 ? RotationUtils.INSTANCE.getServerRotation() : module.getSavedRotation();

        if (module.getAffectRotation()) {
            NoRotateSet.INSTANCE.rotateBackToPlayerRotation();
        }

        // Slightly modify the client-side rotations, so they pass the rotation difference check in onUpdateWalkingPlayer, EntityPlayerSP.
        player.rotationYaw = (rotation.getYaw() + 0.000001f * sign) % 360.0F;
        player.rotationPitch = (rotation.getPitch() + 0.000001f * sign) % 360.0F;
        RotationUtils.INSTANCE.syncRotations();
    }
}
