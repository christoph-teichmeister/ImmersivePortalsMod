package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=26.2 {
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.textures.TextureFormat;
*///?}
//? if >=26.3 {
import com.mojang.renderpearl.api.GpuFormat;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///?}

/**
 * Give every depth texture of the OpenGL backend a stencil aspect (D32F + S8).
 * IP's stencil portal renderer needs a stencil buffer on the main target, and converting all depth textures
 * keeps depth copies between render targets format-compatible.
 * 26.2+: GpuFormat.D32_FLOAT -> D32_FLOAT_S8_UINT when the GL device creates the texture.
 * 26.1.x: TextureFormat has no stencil format, the GL format of DEPTH32 is changed instead.
 * The framebuffer attachment point is changed in MixinDirectStateAccess_Stencil.
 */
//? if >=26.3 {
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice", remap = false)
//?} else if >=26.2 {
/*@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice", remap = false)
*///?} else {
/*@Mixin(targets = "com.mojang.blaze3d.opengl.GlConst", remap = false)
*///?}
public class MixinGlDevice_Stencil {
    
    //? if >=26.3 {
    @ModifyVariable(
        method = "createTexture(Ljava/lang/String;ILcom/mojang/renderpearl/api/GpuFormat;IIII)Lcom/mojang/renderpearl/api/textures/GpuTexture;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private GpuFormat modifyDepthFormat(GpuFormat format) {
        return format == GpuFormat.D32_FLOAT ? GpuFormat.D32_FLOAT_S8_UINT : format;
    }
    //?} else if >=26.2 {
    /*@ModifyVariable(
        method = "createTexture(Ljava/lang/String;ILcom/mojang/blaze3d/GpuFormat;IIII)Lcom/mojang/blaze3d/textures/GpuTexture;",
        at = @At("HEAD"),
        argsOnly = true
    )
    private GpuFormat modifyDepthFormat(GpuFormat format) {
        return format == GpuFormat.D32_FLOAT ? GpuFormat.D32_FLOAT_S8_UINT : format;
    }
    *///?} else {
    /*// GL_DEPTH32F_STENCIL8
    @Inject(method = "toGlInternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("HEAD"), cancellable = true)
    private static void onToGlInternalId(TextureFormat format, CallbackInfoReturnable<Integer> cir) {
        if (format == TextureFormat.DEPTH32) {
            cir.setReturnValue(36013);
        }
    }
    
    // GL_DEPTH_STENCIL
    @Inject(method = "toGlExternalId(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("HEAD"), cancellable = true)
    private static void onToGlExternalId(TextureFormat format, CallbackInfoReturnable<Integer> cir) {
        if (format == TextureFormat.DEPTH32) {
            cir.setReturnValue(34041);
        }
    }
    
    // GL_FLOAT_32_UNSIGNED_INT_24_8_REV
    @Inject(method = "toGlType(Lcom/mojang/blaze3d/textures/TextureFormat;)I", at = @At("HEAD"), cancellable = true)
    private static void onToGlType(TextureFormat format, CallbackInfoReturnable<Integer> cir) {
        if (format == TextureFormat.DEPTH32) {
            cir.setReturnValue(36269);
        }
    }
    *///?}
}
