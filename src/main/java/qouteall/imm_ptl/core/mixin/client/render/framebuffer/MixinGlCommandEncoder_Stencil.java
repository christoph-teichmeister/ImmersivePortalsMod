package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Detach the depth-stencil texture completely after clearing a depth texture
 * (vanilla only detaches GL_DEPTH_ATTACHMENT).
 */
//? if >=26.3 {
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder", remap = false)
//?} else {
/*@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder", remap = false)
*///?}
public class MixinGlCommandEncoder_Stencil {
    @ModifyConstant(
        method = "clearDepthTexture",
        constant = @Constant(intValue = 36096)
    )
    private int modifyDepthAttachment(int original) {
        return qouteall.imm_ptl.core.render.GpuHelper.STENCIL_ENABLED ? 33306 : original;
    }
}
