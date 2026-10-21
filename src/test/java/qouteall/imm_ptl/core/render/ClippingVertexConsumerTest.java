package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClippingVertexConsumerTest {
    private static final int MASK = ClippingVertexConsumer.POSITION | ClippingVertexConsumer.COLOR |
        ClippingVertexConsumer.UV0 | ClippingVertexConsumer.NORMAL;

    // records x, y, z, u of the written vertices
    private static class Recorder implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[]{x, y, z, Float.NaN});
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {return this;}

        @Override
        public VertexConsumer setColor(int color) {return this;}

        @Override
        public VertexConsumer setUv(float u, float v) {
            vertices.get(vertices.size() - 1)[3] = u;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {return this;}

        @Override
        public VertexConsumer setUv2(int u, int v) {return this;}

        //? if >=26.3 {
        @Override
        public VertexConsumer setUv3(float u, float v) {return this;}
        //?}

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {return this;}

        @Override
        public VertexConsumer setLineWidth(float width) {return this;}
    }

    // a unit quad in the xy plane from x = 0 to x = 1, u = x
    private static void putQuad(VertexConsumer consumer, float offsetX) {
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (float[] c : corners) {
            consumer.addVertex(c[0] + offsetX, c[1], 0);
            consumer.setColor(-1);
            consumer.setUv(c[0] + offsetX, c[1]);
            consumer.setNormal(0, 0, 1);
        }
    }

    @Test
    public void clipsQuadCrossingPlane() {
        Recorder recorder = new Recorder();
        // keep x <= 0.5
        ClippingVertexConsumer clipping =
            new ClippingVertexConsumer(recorder, MASK, new double[][]{{-1, 0, 0, 0.5}});

        putQuad(clipping, 0);

        assertEquals(4, recorder.vertices.size());
        for (float[] v : recorder.vertices) {
            assertTrue(v[0] <= 0.5f + 1e-6f, "x " + v[0]);
            // the attributes are interpolated with the position
            assertEquals(v[0], v[3], 1e-6f);
        }
    }

    @Test
    public void keepsAndDropsWholeQuads() {
        Recorder recorder = new Recorder();
        ClippingVertexConsumer clipping =
            new ClippingVertexConsumer(recorder, MASK, new double[][]{{-1, 0, 0, 0.5}});

        putQuad(clipping, -2); // fully kept
        putQuad(clipping, 2); // fully removed

        assertEquals(4, recorder.vertices.size());
        assertEquals(-2, recorder.vertices.get(0)[0], 1e-6f);
    }

    @Test
    public void cornerCutBecomesQuadAndTriangle() {
        Recorder recorder = new Recorder();
        // cut off the corner (1, 1): keep x + y <= 1.5, a pentagon remains
        ClippingVertexConsumer clipping =
            new ClippingVertexConsumer(recorder, MASK, new double[][]{{-1, -1, 0, 1.5}});

        putQuad(clipping, 0);

        assertEquals(8, recorder.vertices.size());
        for (float[] v : recorder.vertices) {
            assertTrue(v[0] + v[1] <= 1.5f + 1e-6f);
        }
    }
}
