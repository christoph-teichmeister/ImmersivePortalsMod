package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public class ViewAreaRenderer {

    /**
     * Draws the portal view area mesh into the main render target with the current projection.
     * <p>
     * 26.x: cull/color mask/depth mask are pipeline state, chosen from the flags.
     * The inner clipping plane is applied through the projection matrix while rendering inside a portal
     * (see {@link FrontClipping}), so {@code doClip} has no separate effect anymore.
     */
    public static void renderPortalArea(
        Portal portal, Vec3 fogColor,
        Matrix4fc modelViewMatrix,
        boolean doFaceCulling, boolean doModifyColor,
        boolean doModifyDepth, boolean doClip
    ) {
        renderPortalArea(
            portal, fogColor, modelViewMatrix,
            doFaceCulling, doModifyColor, doModifyDepth, doClip,
            GpuHelper.DepthMode.TEST
        );
    }
    
    /**
     * @param depthMode 1.21.1 changed the GL depth func around this call, in 26.x it is pipeline state
     */
    public static void renderPortalArea(
        Portal portal, Vec3 fogColor,
        Matrix4fc modelViewMatrix,
        boolean doFaceCulling, boolean doModifyColor,
        boolean doModifyDepth, boolean doClip,
        GpuHelper.DepthMode depthMode
    ) {
        int colorMask;
        if (portal.isFuseView() && IPGlobal.maxPortalLayer != 0) {
            colorMask = GpuHelper.WRITE_NONE;
        }
        else {
            colorMask = doModifyColor ? GpuHelper.WRITE_ALL : GpuHelper.WRITE_NONE;
        }

        boolean writeDepth = doModifyDepth && !portal.isFuseView();

        boolean shouldReverseCull = PortalRendering.isRenderingOddNumberOfMirrors();
        if (shouldReverseCull) {
            MyRenderHelper.applyMirrorFaceCulling();
        }

        // depth clamp keeps the portal area when the camera touches the portal,
        // but inside a portal view it would disable the oblique near plane clipping (see FrontClipping)
        boolean depthClamp = !PortalRendering.isRendering();
        if (depthClamp) {
            CHelper.enableDepthClamp();
        }

        GpuHelper.drawPositionColorTriangles(
            GpuHelper.getMainTarget(),
            GpuHelper.getPositionColorPipeline(
                doFaceCulling, colorMask, writeDepth, depthMode
            ),
            modelViewMatrix,
            null,
            vertexConsumer -> buildPortalViewAreaTriangles(
                vertexConsumer, fogColor, portal, CHelper.getCurrentCameraPos()
            )
        );

        if (depthClamp) {
            CHelper.disableDepthClamp();
        }

        if (shouldReverseCull) {
            MyRenderHelper.recoverFaceCulling();
        }

        CHelper.checkGlError();
    }

    public static void buildPortalViewAreaTriangles(
        VertexConsumer bufferBuilder, Vec3 fogColor, Portal portal, Vec3 cameraPos
    ) {
        Vec3 originRelativeToCamera = portal.getOriginPos().subtract(cameraPos);

        float r = (float) fogColor.x;
        float g = (float) fogColor.y;
        float b = (float) fogColor.z;

        TriangleConsumer vertexOutput = (p0x, p0y, p0z, p1x, p1y, p1z, p2x, p2y, p2z) -> {
            bufferBuilder.addVertex((float) p0x, (float) p0y, (float) p0z).setColor(r, g, b, 1.0f);
            bufferBuilder.addVertex((float) p1x, (float) p1y, (float) p1z).setColor(r, g, b, 1.0f);
            bufferBuilder.addVertex((float) p2x, (float) p2y, (float) p2z).setColor(r, g, b, 1.0f);
        };

        portal.renderViewAreaMesh(originRelativeToCamera, vertexOutput);
    }

    public static void outputTriangle(
        TriangleConsumer vertexOutput, Vec3 center,
        Vec3 localXAxis, Vec3 localYAxis,
        double p0x, double p0y, double p1x, double p1y, double p2x, double p2y
    ) {
        vertexOutput.accept(
            center.x + p0x * localXAxis.x() + p0y * localYAxis.x(),
            center.y + p0x * localXAxis.y() + p0y * localYAxis.y(),
            center.z + p0x * localXAxis.z() + p0y * localYAxis.z(),
            center.x + p1x * localXAxis.x() + p1y * localYAxis.x(),
            center.y + p1x * localXAxis.y() + p1y * localYAxis.y(),
            center.z + p1x * localXAxis.z() + p1y * localYAxis.z(),
            center.x + p2x * localXAxis.x() + p2y * localYAxis.x(),
            center.y + p2x * localXAxis.y() + p2y * localYAxis.y(),
            center.z + p2x * localXAxis.z() + p2y * localYAxis.z()
        );
    }

    @Deprecated
    private static void generateTriangleForNormalShape(
        TriangleConsumer vertexOutput,
        Portal portal,
        Vec3 posInPlayerCoordinate
    ) {
        //avoid floating point error for converted global portal
        final double w = Math.min(portal.getWidth(), 23333);
        final double h = Math.min(portal.getHeight(), 23333);

        Vec3 localXAxis = portal.getAxisW().scale(w / 2);
        Vec3 localYAxis = portal.getAxisH().scale(h / 2);

        outputFullQuad(vertexOutput, posInPlayerCoordinate, localXAxis, localYAxis);

    }

    @Deprecated
    private static void generateTriangleForGlobalPortal(
        TriangleConsumer vertexOutput,
        Portal portal,
        Vec3 portalOriginLocal
    ) {
        Vec3 cameraPosFromPortalOrigin = portalOriginLocal.scale(-1);

        Vec3 cameraPosFromPortalOriginProjected =
            portal.getLocalVecProjectedToPlane(cameraPosFromPortalOrigin);

        Vec3 localCenter = portalOriginLocal.add(cameraPosFromPortalOriginProjected);

        double r = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 - 16;
        if (TransformationManager.isIsometricView) {
            r *= 2;
        }

        double distance = Math.abs(cameraPosFromPortalOrigin.dot(portal.getNormal()));
        if (distance > 200) {
            r = r * 200 / distance;
        }

        Vec3 localXAxis = portal.getAxisW().scale(r);
        Vec3 localYAxis = portal.getAxisH().scale(r);

        outputFullQuad(vertexOutput, localCenter, localXAxis, localYAxis);
    }

    public static void outputFullQuad(
        TriangleConsumer vertexOutput, Vec3 posInPlayerCoordinate,
        Vec3 localXAxis, Vec3 localYAxis
    ) {
        outputTriangle(
            vertexOutput, posInPlayerCoordinate,
            localXAxis, localYAxis, 1, 1, -1, 1, 1, -1
        );
        outputTriangle(
            vertexOutput, posInPlayerCoordinate,
            localXAxis, localYAxis, -1, 1, -1, -1, 1, -1
        );
    }
}
