package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.Plane;

/**
 * Clipping the content behind the portal plane.
 * <p>
 * 1.21.1 injected gl_ClipDistance into the vanilla shaders and enabled GL_CLIP_PLANE0 around draws.
 * The 26.x shaders are compiled from pipelines (and in 26.3 through SPIR-V), so that is gone.
 * The inner clipping (content seen through a portal) is now done with an oblique near plane
 * in the projection matrix, see {@link #applyObliqueClipping}. It is applied to the whole
 * portal world rendering (terrain, entities, particles, weather, sky).
 * <p>
 * TODO port: per-draw clipping is not available any more. The outer clipping of entities that are
 *  halfway through a portal ({@link #setupOuterClipping}) and per-layer toggling have no effect.
 */
public class FrontClipping {
    private static final Minecraft client = Minecraft.getInstance();
    private static double[] activeClipPlaneEquationBeforeModelView;
    private static double[] activeClipPlaneAfterModelView;

    public static boolean isClippingEnabled = false;

    public static final double ADJUSTMENT = 0.01;

    public static void disableClipping() {
        isClippingEnabled = false;
    }

    private static void enableClipping() {
        if (IPGlobal.enableClippingMechanism) {
            isClippingEnabled = true;
        }
    }

    public static void updateInnerClipping(PoseStack matrixStack) {
        Matrix4f modelView = matrixStack.last().pose();
        updateInnerClipping(modelView);
    }

    public static void updateInnerClipping(Matrix4fc modelView) {
        if (PortalRendering.isRendering()) {
            setupInnerClipping(
                PortalRendering.getActiveClippingPlane(),
                modelView, 0
            );
        }
        else {
            disableClipping();
        }
    }

    // NOTE the actual clipping plane is related to current model view matrix
    public static void setupInnerClipping(
        Plane clipping, Matrix4fc modelView, double adjustment
    ) {
        if (!IPCGlobal.useFrontClipping) {
            return;
        }

        // Note: the normal of plane points to the non-clipped side

        if (clipping != null) {
            activeClipPlaneEquationBeforeModelView =
                getClipEquationInner(clipping.pos(), clipping.normal(), adjustment);
            activeClipPlaneAfterModelView =
                transformClipEquation(activeClipPlaneEquationBeforeModelView, modelView);

            enableClipping();
        }
        else {
            activeClipPlaneEquationBeforeModelView = null;
            disableClipping();
        }
    }

    private static double[] transformClipEquation(
        double[] equation, Matrix4fc modelView
    ) {
        Vector4f eq =
            new Vector4f((float) equation[0], (float) equation[1], (float) equation[2], (float) equation[3]);
        Matrix4f m = new Matrix4f(modelView);
        m.invert();
        m.transpose();
        m.transform(eq);
        return new double[]{eq.x(), eq.y(), eq.z(), eq.w()};
    }

    private static double[] getClipEquationInner(
        Vec3 clippingPoint, Vec3 clippingDirection, double correction
    ) {
        Vec3 cameraPos = CHelper.getCurrentCameraPos();

        Vec3 planeNormal = clippingDirection;

        Vec3 portalPos = clippingPoint
            .add(planeNormal.scale(correction))
            .subtract(cameraPos);

        //equation: planeNormal * p + c > 0
        //-planeNormal * portalCenter = c
        double c = planeNormal.scale(-1).dot(portalPos);

        return new double[]{
            planeNormal.x, planeNormal.y, planeNormal.z, c
        };
    }

    public static void setupOuterClipping(PoseStack matrixStack, Portal portal) {
        if (!IPCGlobal.useFrontClipping) {
            return;
        }

        double[] clipEquationOuter = getClipEquationOuter(portal);

        if (clipEquationOuter != null) {
            activeClipPlaneEquationBeforeModelView = clipEquationOuter;
            activeClipPlaneAfterModelView = transformClipEquation(
                activeClipPlaneEquationBeforeModelView, matrixStack.last().pose()
            );
            enableClipping();
        }
        else {
            activeClipPlaneEquationBeforeModelView = null;
            disableClipping();
        }
    }

    // "double @Nullable []" is weird...
    private static double @Nullable [] getClipEquationOuter(Portal portal) {
        @Nullable Plane outerClipping = portal.getPortalShape()
            .getOuterClipping(portal.getThisSideState());

        if (outerClipping == null) {
            return null;
        }

        Vec3 planeNormal = outerClipping.normal();

        Vec3 cameraPos = CHelper.getCurrentCameraPos();

        Vec3 portalPos = outerClipping.pos()
            .subtract(cameraPos);

        //equation: planeNormal * p + c > 0
        //-planeNormal * portalCenter = c
        double c = planeNormal.scale(-1).dot(portalPos);

        return new double[]{
            planeNormal.x, planeNormal.y, planeNormal.z, c
        };
    }

    public static double[] getActiveClipPlaneEquationBeforeModelView() {
        return activeClipPlaneEquationBeforeModelView;
    }

    public static double[] getActiveClipPlaneEquationAfterModelView() {
        return activeClipPlaneAfterModelView;
    }

    /**
     * Sets up the inner clipping of the currently rendering portal for the given view matrix
     * and returns the projection with an oblique near plane on the clipping plane.
     * Returns the projection unchanged if not rendering a portal or clipping is disabled.
     */
    public static Matrix4f getProjectionWithInnerClipping(Matrix4fc projection, Matrix4fc viewMatrix) {
        Matrix4f result = new Matrix4f(projection);
        if (!PortalRendering.isRendering()) {
            disableClipping();
            return result;
        }

        setupInnerClipping(
            PortalRendering.getActiveClippingPlane(), viewMatrix,
            // move the clipping plane a little back, to make world wrapping portal not z-fight
            -ADJUSTMENT
        );

        if (!isClippingEnabled || activeClipPlaneEquationBeforeModelView == null) {
            return result;
        }

        applyObliqueClipping(result, activeClipPlaneAfterModelView);
        return result;
    }

    /**
     * Replaces the near plane of the perspective projection with the view space plane
     * (Lengyel, "Oblique View Frustum Depth Projection and Clipping").
     * The plane equation is {@code dot(plane.xyz, p) + plane.w >= 0} for the kept side.
     * Handles both the classic [-1,1] depth range (26.1.x) and reversed-Z [0,1] (26.2+).
     * Leaves the matrix unchanged if the plane cannot be used as near plane.
     */
    public static void applyObliqueClipping(Matrix4f projection, double[] viewSpacePlane) {
        Vector4f plane = new Vector4f(
            (float) viewSpacePlane[0], (float) viewSpacePlane[1],
            (float) viewSpacePlane[2], (float) viewSpacePlane[3]
        );

        Matrix4f inverse = new Matrix4f(projection).invert();

        // the plane in clip space: (P^-1)^T * plane
        Vector4f clipPlane = new Matrix4f(inverse).transpose().transform(new Vector4f(plane));

        // the frustum corner on the far plane that is farthest on the kept side
        Vector4f farCorner = inverse.transform(new Vector4f(
            Math.signum(clipPlane.x), Math.signum(clipPlane.y), (float) GpuHelper.FAR_DEPTH, 1
        ));

        Vector4f row4 = new Vector4f();
        projection.getRow(3, row4);

        float planeDotCorner = plane.dot(farCorner);
        float row4DotCorner = row4.dot(farCorner);
        if (planeDotCorner <= 1e-6f || row4DotCorner <= 0) {
            return;
        }

        Vector4f newRow3;
        //? if >=26.2 {
        // reversed-Z: near plane is z_clip = w_clip, keep w - z = a * plane
        float a = row4DotCorner / planeDotCorner;
        newRow3 = new Vector4f(row4).sub(new Vector4f(plane).mul(a));
        //?} else {
        /*// classic: near plane is z_clip = -w_clip, keep z + w = a * plane
        float a = 2 * row4DotCorner / planeDotCorner;
        newRow3 = new Vector4f(plane).mul(a).sub(row4);
        *///?}

        projection.setRow(2, newRow3);
    }
}
