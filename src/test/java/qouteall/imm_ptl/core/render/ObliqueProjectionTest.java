package qouteall.imm_ptl.core.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ObliqueProjectionTest {
    private static final float NEAR = 0.05f;
    private static final float FAR = 512;
    
    // the camera looks to -z, the kept side is beyond the tilted plane through (0, 0, -3)
    private static Vector4f tiltedPlane() {
        Vector4f n = new Vector4f(0.3f, 0, -1, 0);
        float len = (float) Math.sqrt(n.x * n.x + n.z * n.z);
        n.mul(1 / len);
        // dot(n, p) + w = 0 at p = (0, 0, -3)
        n.w = -(n.z * -3);
        return n;
    }
    
    // the near plane condition: >= 0 means not clipped by the near plane
    private static float nearCondition(Vector4f clip, boolean reversedZ) {
        return reversedZ ? clip.w - clip.z : clip.z + clip.w;
    }
    
    private static void check(boolean reversedZ) {
        Matrix4f projection = reversedZ ?
            new Matrix4f().setPerspective((float) Math.toRadians(70), 16f / 9, FAR, NEAR, true) :
            new Matrix4f().setPerspective((float) Math.toRadians(70), 16f / 9, NEAR, FAR, false);
        Vector4f plane = tiltedPlane();
        
        assertTrue(ObliqueProjection.apply(projection, plane, reversedZ));
        
        Vector4f behindPlane = projection.transform(new Vector4f(0, 0, -2, 1));
        assertTrue(nearCondition(behindPlane, reversedZ) < 0, "between camera and plane is clipped");
        
        Vector4f beyondPlane = projection.transform(new Vector4f(0.5f, 0.2f, -20, 1));
        assertTrue(nearCondition(beyondPlane, reversedZ) > 0, "beyond the plane is kept");
        float farCondition = reversedZ ? beyondPlane.z : beyondPlane.w - beyondPlane.z;
        assertTrue(farCondition > 0, "beyond the plane is not clipped by the far plane");
        
        Vector4f onPlane = projection.transform(new Vector4f(0, 0, -3, 1));
        assertEquals(0, nearCondition(onPlane, reversedZ), 1e-3f, "the plane is the near plane");
    }
    
    @Test
    void reversedZ() {
        check(true);
    }
    
    @Test
    void classicDepth() {
        check(false);
    }
    
    @Test
    void planeBehindCameraIsRejected() {
        Matrix4f projection = new Matrix4f().setPerspective(1.2f, 1, FAR, NEAR, true);
        Matrix4f original = new Matrix4f(projection);
        // kept side is behind the camera, the frustum has no far corner on it
        assertTrue(!ObliqueProjection.apply(projection, new Vector4f(0, 0, 1, -1), true));
        assertEquals(original, projection);
    }
}
