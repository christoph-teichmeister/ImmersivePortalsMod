package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRenderer {
    //avoid rendering suffocating when colliding with portal
    //? if >=26.2 {
    @Inject(
        method = "submitBlockSprite",
        at = @At("HEAD"),
        cancellable = true
    )
    //?} else {
    /*@Inject(
        method = "renderTex",
        at = @At("HEAD"),
        cancellable = true
    )
    *///?}
    private static void onRenderInWallOverlay(
        CallbackInfo ci
    ) {
        if (PortalRendering.isRendering()) {
            ci.cancel();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (((IEEntity) player).ip_getCollidingPortal() != null) {
                ci.cancel();
            }
        }
        if (ClientTeleportationManager.isTeleportingFrequently()) {
            ci.cancel();
        }
    }
}
