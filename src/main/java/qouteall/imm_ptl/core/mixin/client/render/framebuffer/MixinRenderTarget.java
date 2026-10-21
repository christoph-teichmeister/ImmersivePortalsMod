package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEFrameBuffer;
//? if >=26.3 {
import com.mojang.renderpearl.api.textures.GpuTexture;
//?} else {
/*import com.mojang.blaze3d.textures.GpuTexture;
*///?}

/**
 * 26.x: the depth textures of all render targets get a stencil aspect on the OpenGL backend
 * (see MixinGlDevice_Stencil and MixinDirectStateAccess_Stencil), so the stencil buffer is always enabled.
 * Switching it per render target (1.21.1 re-created the depth attachment) is not needed anymore.
 */
@Mixin(RenderTarget.class)
public abstract class MixinRenderTarget implements IEFrameBuffer {
    
    @Shadow
    @Nullable
    protected GpuTexture depthTexture;
    
    @Override
    public boolean ip_getIsStencilBufferEnabled() {
        if (depthTexture == null) {
            return false;
        }
        //? if >=26.2 {
        return depthTexture.getFormat().hasStencilAspect();
        //?} else {
        /*return qouteall.imm_ptl.core.render.GpuHelper.STENCIL_ENABLED;
        *///?}
    }
    
    @Override
    public void ip_setIsStencilBufferEnabledAndReload(boolean cond) {
        // always enabled
    }
}
