package qouteall.imm_ptl.core.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.optimization.GLResourceCache;
//? if >=26.3 {
import com.mojang.renderpearl.backend.opengl.GlStateManager;
//?} else {
/*import com.mojang.blaze3d.opengl.GlStateManager;
*///?}

/**
 * 26.x: the forced cull disabling for mirrors is gone, mirrors flip the cull face for the whole
 * portal world rendering instead (see MyGameRenderer).
 */
@Mixin(value = GlStateManager.class, remap = false)
public abstract class MixinGlStateManager {
    
    @Inject(
        method = "_glGenBuffers()I",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onGenBuffers(CallbackInfoReturnable<Integer> cir) {
        if (IPGlobal.cacheGlBuffer) {
            cir.setReturnValue(GLResourceCache.bufferCache.getNewResourceId());
            cir.cancel();
        }
    }
    
    @Inject(
        method = "_glGenVertexArrays()I",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onGenVertexArrays(CallbackInfoReturnable<Integer> cir) {
        if (IPGlobal.cacheGlBuffer) {
            cir.setReturnValue(GLResourceCache.vertexArrayCache.getNewResourceId());
            cir.cancel();
        }
    }
}
