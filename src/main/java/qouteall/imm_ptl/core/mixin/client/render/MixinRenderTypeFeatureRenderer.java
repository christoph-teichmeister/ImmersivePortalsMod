package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.FrontClipping;

/**
 * Lets the geometry of entities that are halfway through a portal be clipped on the CPU,
 * see {@link FrontClipping#beginEntityClipping}.
 * 26.2+: the feature renderers get their vertex builders here.
 * 26.1.x: the feature renderers get them from the buffer source.
 */
//? if >=26.2 {
@Mixin(net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.MultiBufferSource.BufferSource.class)
*///?}
public class MixinRenderTypeFeatureRenderer {
    //? if >=26.2 {
    @Inject(method = "getVertexBuilder", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
    *///?}
    private void ip_onGetVertexBuilder(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer original = cir.getReturnValue();
        VertexConsumer wrapped = FrontClipping.wrapEntityVertexConsumer(original, renderType);
        if (wrapped != original) {
            cir.setReturnValue(wrapped);
        }
    }
}
