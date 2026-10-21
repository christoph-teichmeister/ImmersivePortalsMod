package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=26.3 {
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexFormat;
*///?} else {
/*import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
*///?}
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.Plane;

import java.util.List;

/**
 * Clipping the content behind the portal plane.
 * <p>
 * 1.21.1 injected gl_ClipDistance into the vanilla shaders and enabled GL_CLIP_PLANE0 around draws.
 * The 26.x shaders are compiled from pipelines (and in 26.3 through SPIR-V), so that is gone.
 * The inner clipping (content seen through a portal) is now done with an oblique near plane
 * in the projection matrix, see {@link #applyObliqueClipping}. It is applied to the whole
 * portal world rendering (terrain, entities, particles, weather, sky).
 * <p>
 * Per-draw clipping is not available any more. The entities that are halfway through a portal
 * are clipped on the CPU instead, see {@link #beginEntityClipping}.
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

    // the planes that clip the entity geometry on the CPU, see ClippingVertexConsumer
    private static double @Nullable [][] entityClipPlanes;

    /**
     * Clips the entity geometry that is prepared until {@link #endEntityClipping()}.
     * 26.x has no per-draw clip planes, the entities that are halfway through a portal
     * are cut on the CPU (see {@link ClippingVertexConsumer} and {@link CrossPortalEntityRenderer}).
     *
     * @param planes    the kept side is the positive side of each plane
     * @param cameraPos the vertex positions are relative to this camera position
     */
    public static void beginEntityClipping(List<Plane> planes, Vec3 cameraPos) {
        double[][] equations = new double[planes.size()][];
        for (int i = 0; i < planes.size(); i++) {
            Plane plane = planes.get(i);
            Vec3 n = plane.normal();
            // n * (p + cameraPos - planePos) >= 0
            equations[i] = new double[]{
                n.x, n.y, n.z, n.dot(cameraPos.subtract(plane.pos()))
            };
        }
        entityClipPlanes = equations;
    }

    public static void endEntityClipping() {
        entityClipPlanes = null;
    }

    /**
     * Wraps the vertex consumer of an entity draw into a {@link ClippingVertexConsumer}
     * while entity clipping is active. Called by the feature renderers (26.2+) or the buffer source (26.1.x).
     */
    public static VertexConsumer wrapEntityVertexConsumer(VertexConsumer consumer, RenderType renderType) {
        double[][] planes = entityClipPlanes;
        if (planes == null || planes.length == 0) {
            return consumer;
        }
        //? if >=26.2 {
        if (renderType.primitiveTopology() != PrimitiveTopology.QUADS) {
            return consumer;
        }
        //?} else {
        /*if (renderType.mode() != VertexFormat.Mode.QUADS) {
            return consumer;
        }
        *///?}
        VertexFormat format = renderType.format();
        int mask = 0;
        //? if >=26.2 {
        String[] names = {"Position", "Color", "UV0", "UV1", "UV2", "UV3", "Normal", "LineWidth"};
        for (int i = 0; i < names.length; i++) {
            if (format.contains(names[i])) {
                mask |= 1 << i;
            }
        }
        //?} else {
        /*VertexFormatElement[] elements = {
            VertexFormatElement.POSITION, VertexFormatElement.COLOR, VertexFormatElement.UV0,
            VertexFormatElement.UV1, VertexFormatElement.UV2, null,
            VertexFormatElement.NORMAL, VertexFormatElement.LINE_WIDTH
        };
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] != null && format.contains(elements[i])) {
                mask |= 1 << i;
            }
        }
        *///?}
        if (Integer.bitCount(mask) != format.getElements().size()) {
            // an unknown vertex element, the vertex end could not be detected
            return consumer;
        }
        return new ClippingVertexConsumer(consumer, mask, planes);
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
     * Replaces the near plane of the perspective projection with the view space plane,
     * for the depth convention of this MC version. See {@link ObliqueProjection}.
     */
    public static void applyObliqueClipping(Matrix4f projection, double[] viewSpacePlane) {
        Vector4f plane = new Vector4f(
            (float) viewSpacePlane[0], (float) viewSpacePlane[1],
            (float) viewSpacePlane[2], (float) viewSpacePlane[3]
        );
        //? if >=26.2 {
        ObliqueProjection.apply(projection, plane, true, GpuHelper.isZeroToOneDepth());
        //?} else {
        /*ObliqueProjection.apply(projection, plane, false, false);
        *///?}
    }
}
