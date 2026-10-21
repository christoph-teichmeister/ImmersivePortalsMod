package qouteall.imm_ptl.peripheral.wand;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Since 1.21.9 debug rendering is gizmo-based and MultiBufferSource/RenderType.lines() are gone.
 * This consumer takes the camera-relative line vertices that the wand code writes
 * (through WireRenderingHelper) and turns them into world-space {@link Gizmos#line} calls.
 * A vertex is complete once its color is set.
 * Must be used while a gizmo collector is active (DebugRenderer.emitGizmos).
 */
@Environment(EnvType.CLIENT)
public final class WandGizmoLines implements VertexConsumer {
    // 1.21.1 widths: RenderType.lines() used 2.5px, RenderType.debugLineStrip(1) used 1px
    private static final float LINES_WIDTH = 2.5F;
    private static final float STRIP_WIDTH = 1.0F;

    private final Vec3 cameraPos;
    private final boolean strip;
    private final float width;

    private @Nullable Vec3 pos;
    private @Nullable Vec3 prevPos;
    private int prevColor;

    private WandGizmoLines(Vec3 cameraPos, boolean strip, float width) {
        this.cameraPos = cameraPos;
        this.strip = strip;
        this.width = width;
    }

    /** Vertex pairs form separate lines (like RenderType.lines()). */
    public static WandGizmoLines lines(Vec3 cameraPos) {
        return new WandGizmoLines(cameraPos, false, LINES_WIDTH);
    }

    /** Consecutive vertices form a strip; a transparent vertex breaks it (like debugLineStrip). */
    public static WandGizmoLines strip(Vec3 cameraPos) {
        return new WandGizmoLines(cameraPos, true, STRIP_WIDTH);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        pos = new Vec3(x, y, z).add(cameraPos);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor(ARGB.color(a, r, g, b));
    }

    @Override
    public VertexConsumer setColor(int color) {
        if (pos == null) {
            return this;
        }
        if (prevPos != null && ARGB.alpha(prevColor) != 0 && ARGB.alpha(color) != 0) {
            Gizmos.line(prevPos, pos, color, width);
        }
        if (strip || prevPos == null) {
            prevPos = pos;
            prevColor = color;
        }
        else {
            prevPos = null;
        }
        pos = null;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    // abstract since 26.3, plain extra method before
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }
}
