package qouteall.imm_ptl.core.mixin.client.render.isometric;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.TransformationManager;

/**
 * 26.x: the level projection matrix comes from the camera render state
 * (1.21.1 hooked GameRenderer.getProjectionMatrix).
 */
@Mixin(Camera.class)
public class MixinCamera_Isometric {
    //? if >=26.3 {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(
        CameraRenderState cameraState, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        ip_applyIsometric(cameraState);
    }
    //?} else {
    /*@Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(
        CameraRenderState cameraState, float cameraEntityPartialTicks, CallbackInfo ci
    ) {
        ip_applyIsometric(cameraState);
    }
    *///?}
    
    private static void ip_applyIsometric(CameraRenderState cameraState) {
        if (TransformationManager.isIsometricView) {
            cameraState.projectionMatrix.set(TransformationManager.getIsometricProjection());
        }
    }
}
