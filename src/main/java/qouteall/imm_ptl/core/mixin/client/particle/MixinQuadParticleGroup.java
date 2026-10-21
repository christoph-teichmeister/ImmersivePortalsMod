package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(QuadParticleGroup.class)
public class MixinQuadParticleGroup {
    // only extract the particles of the rendering dimension that are on the visible side of the portal
    // maybe incompatible with sodium and iris
    @WrapWithCondition(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/SingleQuadParticle;extract(Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"
        )
    )
    private boolean shouldExtractParticle(
        SingleQuadParticle instance, QuadParticleRenderState state, Camera camera, float partialTick
    ) {
        return RenderStates.shouldRenderParticle(instance);
    }
}
