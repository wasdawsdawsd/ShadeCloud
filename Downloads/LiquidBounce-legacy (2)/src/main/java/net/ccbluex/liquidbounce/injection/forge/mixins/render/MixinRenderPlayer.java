/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.render;

import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura;
import net.ccbluex.liquidbounce.features.module.modules.movement.NoSlow;
import net.ccbluex.liquidbounce.features.module.modules.render.SilentHotbarModule;
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer {


    @Shadow
    public abstract ModelPlayer getMainModel();

    /**
     * @author CCBlueX
     */
    @Overwrite
    private void setModelVisibilities(AbstractClientPlayer entity) {
        ModelPlayer modelplayer = this.getMainModel();
        if (entity.isSpectator()) {
            modelplayer.setInvisible(false);
            modelplayer.bipedHead.showModel = true;
            modelplayer.bipedHeadwear.showModel = true;
        } else {
            SilentHotbarModule module = SilentHotbarModule.INSTANCE;

            int slot = SilentHotbar.INSTANCE.renderSlot(module.handleEvents() && module.getKeepItemInHandInThirdPerson());

            ItemStack itemstack = entity instanceof EntityPlayerSP ? entity.inventory.getStackInSlot(slot) : entity.getHeldItem();

            modelplayer.setInvisible(true);
            modelplayer.bipedHeadwear.showModel = entity.isWearing(EnumPlayerModelParts.HAT);
            modelplayer.bipedBodyWear.showModel = entity.isWearing(EnumPlayerModelParts.JACKET);
            modelplayer.bipedLeftLegwear.showModel = entity.isWearing(EnumPlayerModelParts.LEFT_PANTS_LEG);
            modelplayer.bipedRightLegwear.showModel = entity.isWearing(EnumPlayerModelParts.RIGHT_PANTS_LEG);
            modelplayer.bipedLeftArmwear.showModel = entity.isWearing(EnumPlayerModelParts.LEFT_SLEEVE);
            modelplayer.bipedRightArmwear.showModel = entity.isWearing(EnumPlayerModelParts.RIGHT_SLEEVE);
            modelplayer.heldItemLeft = 0;
            modelplayer.aimedBow = false;
            modelplayer.isSneak = entity.isSneaking();
            if (itemstack == null) {
                modelplayer.heldItemRight = 0;
            } else {
                modelplayer.heldItemRight = 1;
                boolean isForceBlocking = entity instanceof EntityPlayerSP && ((itemstack.getItem() instanceof ItemSword && KillAura.INSTANCE.getRenderBlocking()) || NoSlow.INSTANCE.isUNCPBlocking());
                if (entity.getItemInUseCount() > 0 || isForceBlocking) {
                    EnumAction enumaction = isForceBlocking? EnumAction.BLOCK : itemstack.getItemUseAction();
                    if (enumaction == EnumAction.BLOCK) {
                        modelplayer.heldItemRight = 3;
                    } else if (enumaction == EnumAction.BOW) {
                        modelplayer.aimedBow = true;
                    }
                }
            }
        }

    }
}
