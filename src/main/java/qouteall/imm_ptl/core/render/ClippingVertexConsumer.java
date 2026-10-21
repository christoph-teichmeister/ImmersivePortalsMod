package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Clips quads against planes on the CPU before they reach the wrapped vertex consumer.
 * <p>
 * Used for entities that are halfway through a portal (see {@link CrossPortalEntityRenderer}).
 * 26.x has no per-draw clip plane any more, so the geometry itself is cut:
 * a quad on the kept side of every plane passes through unchanged, a quad on the removed side
 * of a plane is dropped, and a quad crossing a plane is cut (Sutherland-Hodgman) and written
 * as one or more quads (a triangle is written as a quad with a repeated vertex).
 * This does not depend on the shaders or the camera position and keeps the depth of the
 * clipped geometry consistent with the rest of the scene.
 * <p>
 * A vertex is complete once every element of the vertex format is set, like in BufferBuilder,
 * (later setters for the same vertex are ignored there too), so no explicit flush is needed.
 * Only for the QUADS topology.
 */
@Environment(EnvType.CLIENT)
public final class ClippingVertexConsumer implements VertexConsumer {
    // the element bits, in the order of the BufferBuilder elements
    public static final int POSITION = 1;
    public static final int COLOR = 1 << 1;
    public static final int UV0 = 1 << 2;
    public static final int UV1 = 1 << 3;
    public static final int UV2 = 1 << 4;
    public static final int UV3 = 1 << 5;
    public static final int NORMAL = 1 << 6;
    public static final int LINE_WIDTH = 1 << 7;

    private final VertexConsumer delegate;
    private final int requiredMask;
    // camera relative plane equations {a, b, c, d}, the kept side is a*x + b*y + c*z + d >= 0
    private final double[][] planes;

    private final Vertex[] quad = {new Vertex(), new Vertex(), new Vertex(), new Vertex()};
    private int completedVertices = 0;
    private Vertex current = null;

    public ClippingVertexConsumer(VertexConsumer delegate, int requiredMask, double[][] planes) {
        this.delegate = delegate;
        this.requiredMask = requiredMask | POSITION;
        this.planes = planes;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (current != null) {
            // the previous vertex misses elements, BufferBuilder would throw, pass it on anyway
            completeVertex();
        }
        current = quad[completedVertices];
        current.mask = POSITION;
        current.x = x;
        current.y = y;
        current.z = z;
        checkComplete();
        return this;
    }

    private boolean begin(int bit) {
        // like BufferBuilder, an element that is already set (or a finished vertex) ignores the setter
        return current != null && (current.mask & bit) == 0;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor(((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255));
    }

    @Override
    public VertexConsumer setColor(int color) {
        if (begin(COLOR)) {
            current.color = color;
            setDone(COLOR);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (begin(UV0)) {
            current.u0 = u;
            current.v0 = v;
            setDone(UV0);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        if (begin(UV1)) {
            current.u1 = u;
            current.v1 = v;
            setDone(UV1);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if (begin(UV2)) {
            current.u2 = u;
            current.v2 = v;
            setDone(UV2);
        }
        return this;
    }

    //? if >=26.3 {
    @Override
    public VertexConsumer setUv3(float u, float v) {
        if (begin(UV3)) {
            current.u3 = u;
            current.v3 = v;
            setDone(UV3);
        }
        return this;
    }
    //?}

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (begin(NORMAL)) {
            current.nx = x;
            current.ny = y;
            current.nz = z;
            setDone(NORMAL);
        }
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        if (begin(LINE_WIDTH)) {
            current.lineWidth = width;
            setDone(LINE_WIDTH);
        }
        return this;
    }

    private void setDone(int bit) {
        current.mask |= bit;
        checkComplete();
    }

    private void checkComplete() {
        if ((current.mask & requiredMask) == requiredMask) {
            completeVertex();
        }
    }

    private void completeVertex() {
        current = null;
        completedVertices++;
        if (completedVertices == 4) {
            completedVertices = 0;
            clipQuad();
        }
    }

    private void clipQuad() {
        boolean allKept = true;
        for (double[] plane : planes) {
            int kept = 0;
            for (Vertex vertex : quad) {
                if (vertex.distanceTo(plane) >= 0) {
                    kept++;
                }
            }
            if (kept == 0) {
                return;
            }
            if (kept != 4) {
                allKept = false;
            }
        }

        if (allKept) {
            for (Vertex vertex : quad) {
                emit(vertex);
            }
            return;
        }

        List<Vertex> polygon = List.of(quad);
        for (double[] plane : planes) {
            polygon = clipPolygon(polygon, plane);
            if (polygon.size() < 3) {
                return;
            }
        }

        // the clipped quad is convex, write it as a fan of quads (and a last triangle)
        int n = polygon.size();
        Vertex first = polygon.get(0);
        int i = 1;
        while (i + 2 < n) {
            emit(first);
            emit(polygon.get(i));
            emit(polygon.get(i + 1));
            emit(polygon.get(i + 2));
            i += 2;
        }
        if (i + 1 < n) {
            emit(first);
            emit(polygon.get(i));
            emit(polygon.get(i + 1));
            emit(polygon.get(i + 1));
        }
    }

    // Sutherland-Hodgman against one plane
    private static List<Vertex> clipPolygon(List<Vertex> polygon, double[] plane) {
        List<Vertex> result = new ArrayList<>(polygon.size() + 1);
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            Vertex a = polygon.get(i);
            Vertex b = polygon.get((i + 1) % n);
            double da = a.distanceTo(plane);
            double db = b.distanceTo(plane);
            if (da >= 0) {
                result.add(a);
            }
            if ((da >= 0) != (db >= 0)) {
                result.add(Vertex.lerp(a, b, (float) (da / (da - db))));
            }
        }
        return result;
    }

    private void emit(Vertex vertex) {
        delegate.addVertex(vertex.x, vertex.y, vertex.z);
        int mask = vertex.mask;
        if ((mask & COLOR) != 0) {
            delegate.setColor(vertex.color);
        }
        if ((mask & UV0) != 0) {
            delegate.setUv(vertex.u0, vertex.v0);
        }
        if ((mask & UV1) != 0) {
            delegate.setUv1(vertex.u1, vertex.v1);
        }
        if ((mask & UV2) != 0) {
            delegate.setUv2(vertex.u2, vertex.v2);
        }
        //? if >=26.3 {
        if ((mask & UV3) != 0) {
            delegate.setUv3(vertex.u3, vertex.v3);
        }
        //?}
        if ((mask & NORMAL) != 0) {
            delegate.setNormal(vertex.nx, vertex.ny, vertex.nz);
        }
        if ((mask & LINE_WIDTH) != 0) {
            delegate.setLineWidth(vertex.lineWidth);
        }
    }

    private static final class Vertex {
        int mask;
        float x, y, z;
        int color;
        float u0, v0;
        int u1, v1;
        int u2, v2;
        float u3, v3;
        float nx, ny, nz;
        float lineWidth;

        double distanceTo(double[] plane) {
            return plane[0] * x + plane[1] * y + plane[2] * z + plane[3];
        }

        static Vertex lerp(Vertex a, Vertex b, float t) {
            Vertex r = new Vertex();
            r.mask = a.mask & b.mask;
            r.x = lerp(a.x, b.x, t);
            r.y = lerp(a.y, b.y, t);
            r.z = lerp(a.z, b.z, t);
            r.color = lerpColor(a.color, b.color, t);
            r.u0 = lerp(a.u0, b.u0, t);
            r.v0 = lerp(a.v0, b.v0, t);
            r.u1 = Math.round(lerp(a.u1, b.u1, t));
            r.v1 = Math.round(lerp(a.v1, b.v1, t));
            r.u2 = Math.round(lerp(a.u2, b.u2, t));
            r.v2 = Math.round(lerp(a.v2, b.v2, t));
            r.u3 = lerp(a.u3, b.u3, t);
            r.v3 = lerp(a.v3, b.v3, t);
            r.nx = lerp(a.nx, b.nx, t);
            r.ny = lerp(a.ny, b.ny, t);
            r.nz = lerp(a.nz, b.nz, t);
            r.lineWidth = lerp(a.lineWidth, b.lineWidth, t);
            return r;
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static int lerpColor(int a, int b, float t) {
            int result = 0;
            for (int shift = 0; shift < 32; shift += 8) {
                int ca = (a >>> shift) & 255;
                int cb = (b >>> shift) & 255;
                result |= (Math.round(lerp(ca, cb, t)) & 255) << shift;
            }
            return result;
        }
    }
}
