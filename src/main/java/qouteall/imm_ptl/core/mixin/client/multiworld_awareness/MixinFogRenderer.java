package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.MyRenderHelper;

/**
 * 26.x: the fog color has no static state anymore (it comes from the environment attributes of the camera),
 * so the 1.21.1 static field swapping is gone. The fog distances are transformed for portal rendering
 * (replaces the 1.21.1 RenderSystem.setShaderFogStart/End hooks).
 */
@Mixin(value = FogRenderer.class, priority = 1100)
public class MixinFogRenderer {
    @ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
    private FogData onSetupFog(FogData fog) {
        fog.environmentalStart = MyRenderHelper.transformFogDistance(fog.environmentalStart);
        fog.environmentalEnd = MyRenderHelper.transformFogDistance(fog.environmentalEnd);
        fog.renderDistanceStart = MyRenderHelper.transformFogDistance(fog.renderDistanceStart);
        fog.renderDistanceEnd = MyRenderHelper.transformFogDistance(fog.renderDistanceEnd);
        fog.skyEnd = MyRenderHelper.transformFogDistance(fog.skyEnd);
        fog.cloudEnd = MyRenderHelper.transformFogDistance(fog.cloudEnd);
        return fog;
    }
}
