package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * 26.x: entity rendering is split into extract (render state) and submit.
 */
@Environment(EnvType.CLIENT)
public class PortalEntityRenderer extends EntityRenderer<Portal, PortalEntityRenderer.PortalRenderState> {
    
    public static class PortalRenderState extends EntityRenderState {
        @Nullable
        public Portal portal;
    }
    
    public PortalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public PortalRenderState createRenderState() {
        return new PortalRenderState();
    }
    
    @Override
    public void extractRenderState(Portal portal, PortalRenderState state, float partialTicks) {
        super.extractRenderState(portal, state, partialTicks);
        state.portal = portal;
    }
    
    @Override
    public void submit(
        PortalRenderState state, PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector, CameraRenderState camera
    ) {
        Portal portal = state.portal;
        if (portal != null) {
            IPCGlobal.renderer.renderPortalInEntityRenderer(portal);
            
            if (OverlayRendering.shouldRenderOverlay(portal)) {
                OverlayRendering.onRenderPortalEntity(portal, poseStack, submitNodeCollector);
            }
            
            if (IPGlobal.debugRenderPortalShapeMesh && !PortalRendering.isRendering()) {
                submitNodeCollector.submitCustomGeometry(
                    poseStack, RenderTypes.lines(),
                    (pose, vertexConsumer) -> {
                        PoseStack wireStack = new PoseStack();
                        wireStack.last().set(pose);
                        WireRenderingHelper.renderPortalShapeMeshDebug(wireStack, vertexConsumer, portal);
                    }
                );
            }
        }
        
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
