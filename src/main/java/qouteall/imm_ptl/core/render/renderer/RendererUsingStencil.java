package qouteall.imm_ptl.core.render.renderer;

import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.GpuHelper;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_ALWAYS;
import static org.lwjgl.opengl.GL11.GL_EQUAL;
import static org.lwjgl.opengl.GL11.GL_INCR;
import static org.lwjgl.opengl.GL11.GL_KEEP;
import static org.lwjgl.opengl.GL11.GL_LESS;
import static org.lwjgl.opengl.GL11.GL_REPLACE;
import static org.lwjgl.opengl.GL11.GL_STENCIL_TEST;

/**
 * The default portal renderer: marks the portal area in the stencil buffer
 * and renders the portal world inside it.
 * <p>
 * 26.x: the depth test, depth write, color mask and cull of the draws are pipeline state
 * (chosen in {@link ViewAreaRenderer} and {@link MyRenderHelper}). The stencil state is not part
 * of the 26.x pipelines and the OpenGL backend never touches it, so it is still set with raw GL calls
 * and applies to all draws of the portal world rendering. The main target's depth texture must have
 * a stencil aspect, see MixinRenderTarget.
 */
public class RendererUsingStencil extends PortalRenderer {

    @Override
    public boolean replaceFrameBufferClearing() {
        boolean skipClearing = WorldRenderInfo.isRendering();
        if (skipClearing) {
            if (WorldRenderInfo.getTopRenderInfo().doRenderSky) {
                // fill the portal area (limited by stencil) with the fog color, without touching the depth
                MyRenderHelper.renderScreenTriangle(FogRendererContext.getCurrentFogColor.get());
            }
        }
        return skipClearing;
    }

    @Override
    public void onBeforeTranslucentRendering(Matrix4f modelView) {
        doPortalRendering(modelView);
    }

    protected void doPortalRendering(Matrix4f modelView) {
        Profiler.get().popPush("render_portal_total");
        renderPortals(modelView);
        if (PortalRendering.isRendering()) {
            setStencilStateForWorldRendering();
        }
        else {
            // don't do it in finishRendering()
            // as it will render outer world's transparent things later
            myFinishRendering();
        }
    }

    protected void renderPortals(Matrix4f modelView) {
        List<Portal> portalsToRender = getPortalsToRender(modelView);

        for (Portal portal : portalsToRender) {
            doRenderPortal(portal, modelView);
        }
    }

    @Override
    public void onAfterTranslucentRendering(Matrix4f modelView) {

    }

    @Override
    public void onHandRenderingEnded() {
        //nothing
    }

    @Override
    public void prepareRendering() {
        // 26.x: the depth textures always have a stencil aspect when IP is loaded (MixinRenderTarget)
        GpuHelper.clearStencil(GpuHelper.getMainTarget());

        GL11.glStencilFunc(GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
        GL11.glEnable(GL_STENCIL_TEST);
    }

    @Override
    public void finishRendering() {
        //nothing
    }

    private void myFinishRendering() {
        GL11.glStencilFunc(GL_ALWAYS, 2333, 0xFF);
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

        GL11.glDisable(GL_STENCIL_TEST);
    }

    protected void doRenderPortal(
        Portal portal,
        Matrix4f modelView
    ) {
        if (shouldSkipRenderingInsideFuseViewPortal(portal)) {
            return;
        }

        int outerPortalStencilValue = PortalRendering.getPortalLayer();

        Profiler.get().push("render_view_area");

        boolean anySamplePassed = PortalRenderInfo.renderAndDecideVisibility(portal, () -> {
            renderPortalViewAreaToStencil(portal, modelView);
        });

        Profiler.get().pop();

        if (!anySamplePassed) {
            setStencilStateForWorldRendering();
            return;
        }

        PortalRendering.pushPortalLayer(portal);

        int thisPortalStencilValue = outerPortalStencilValue + 1;

        if (!portal.isFuseView()) {
            Profiler.get().push("clear_depth_of_view_area");
            clearDepthOfThePortalViewArea(portal);
            Profiler.get().pop();
        }

        setStencilStateForWorldRendering();

        renderPortalContent(portal);

        PortalRendering.popPortalLayer();
        // pop portal layer before restoring depth, for clipping, see ViewAreaRenderer

        if (!portal.isFuseView()) {
            restoreDepthOfPortalViewArea(portal, modelView, thisPortalStencilValue);
        }

        clampStencilValue(outerPortalStencilValue);
    }

    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        //nothing
    }

    private void renderPortalViewAreaToStencil(
        Portal portal, Matrix4f modelView
    ) {
        int outerPortalStencilValue = PortalRendering.getPortalLayer();

        //is the mask here different from the mask of glStencilMask?
        GL11.glStencilFunc(GL_EQUAL, outerPortalStencilValue, 0xFF);

        //if stencil and depth test pass, the data in stencil buffer will increase by 1
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
        //NOTE about GL_INCR:
        //if multiple triangles occupy the same pixel and passed stencil and depth tests,
        //its stencil value will still increase by one

        GL11.glStencilMask(0xFF);

        // update it before pushing
        FrontClipping.updateInnerClipping(modelView);

        ViewAreaRenderer.renderPortalArea(
            portal, Vec3.ZERO,
            modelView,
            true, true,
            true, true
        );
    }

    private void clearDepthOfThePortalViewArea(
        Portal portal
    ) {
        setStencilStateForWorldRendering();

        // write the furthest depth, do not manipulate color buffer
        // (1.21.1 used glDepthRange(1,1), 26.x draws at the far plane depth directly)
        MyRenderHelper.renderScreenTriangle(
            255, 255, 255, 255,
            GpuHelper.WRITE_NONE, true, GpuHelper.DepthMode.ALWAYS
        );
    }

    protected void restoreDepthOfPortalViewArea(
        Portal portal, Matrix4f modelView,
        int portalStencilValue
    ) {
        setStencilLimitation(portalStencilValue);

        ViewAreaRenderer.renderPortalArea(
            portal, Vec3.ZERO,
            modelView,
            false, false,
            true,
            true, // important: should clip, otherwise depth will be abnormal when viewing scale box from inside in portal
            GpuHelper.DepthMode.ALWAYS
        );
    }

    public static void clampStencilValue(
        int maximumValue
    ) {
        //NOTE GL_GREATER means ref > stencil
        //GL_LESS means ref < stencil

        //pass if the stencil value is greater than the maximum value
        GL11.glStencilFunc(GL_LESS, maximumValue, 0xFF);

        //if stencil test passed, encode the stencil value
        GL11.glStencilOp(GL_KEEP, GL_REPLACE, GL_REPLACE);

        // no depth test, do not manipulate the depth and color buffer
        MyRenderHelper.renderScreenTriangle(
            255, 255, 255, 255,
            GpuHelper.WRITE_NONE, false, GpuHelper.DepthMode.NONE
        );
    }

    private void setStencilStateForWorldRendering() {
        int thisPortalStencilValue = PortalRendering.getPortalLayer();

        setStencilLimitation(thisPortalStencilValue);
    }

    public static void setStencilLimitation(int stencilValue) {
        //draw content in the mask
        GL11.glStencilFunc(GL_EQUAL, stencilValue, 0xFF);

        //do not manipulate stencil buffer now
        GL11.glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }

    public static boolean shouldSkipRenderingInsideFuseViewPortal(Portal portal) {
        if (!PortalRendering.isRendering()) {
            return false;
        }

        Portal renderingPortal = PortalRendering.getRenderingPortal();

        if (!renderingPortal.isFuseView()) {
            return false;
        }

        Vec3 cameraPos = CHelper.getCurrentCameraPos();

        Vec3 transformedCameraPos = portal
            .transformPoint(renderingPortal.transformPoint(cameraPos));

        // roughly test whether they are reverse portals
        return cameraPos.distanceToSqr(transformedCameraPos) < 0.1;
    }
}
