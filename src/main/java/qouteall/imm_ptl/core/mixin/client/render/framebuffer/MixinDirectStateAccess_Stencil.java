package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The depth textures have a stencil aspect (MixinGlDevice_Stencil),
 * attach them as GL_DEPTH_STENCIL_ATTACHMENT instead of GL_DEPTH_ATTACHMENT.
 */
//? if >=26.3 {
@Mixin(
    targets = {
        "com.mojang.renderpearl.backend.opengl.DirectStateAccess$Core",
        "com.mojang.renderpearl.backend.opengl.DirectStateAccess$Emulated"
    },
    remap = false
)
//?} else {
/*@Mixin(
    targets = {
        "com.mojang.blaze3d.opengl.DirectStateAccess$Core",
        "com.mojang.blaze3d.opengl.DirectStateAccess$Emulated"
    },
    remap = false
)
*///?}
public class MixinDirectStateAccess_Stencil {
    // GL_DEPTH_ATTACHMENT -> GL_DEPTH_STENCIL_ATTACHMENT
    @ModifyConstant(
        method = "bindFrameBufferTextures",
        constant = @Constant(intValue = 36096)
    )
    private int modifyDepthAttachment(int original) {
        return qouteall.imm_ptl.core.render.GpuHelper.STENCIL_ENABLED ? 33306 : original;
    }
}
