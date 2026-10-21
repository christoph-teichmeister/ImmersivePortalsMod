package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

/**
 * 26.x: particles are extracted into render states (ParticleEngine.extract) instead of rendered,
 * the per-particle filtering moved to MixinQuadParticleGroup, the tick filtering to MixinParticleGroup.
 */
@SuppressWarnings("resource")
@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
    @Shadow
    protected ClientLevel level;
    
    // skip particle rendering for far portals
    @Inject(
        method = "extract",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBeginExtractParticles(CallbackInfo ci) {
        if (PortalRendering.isRendering()) {
            if (RenderStates.getRenderedPortalNum() > 4) {
                ci.cancel();
            }
        }
    }
    
    @Override
    public void ip_setWorld(ClientLevel world_) {
        level = world_;
    }
    
}
