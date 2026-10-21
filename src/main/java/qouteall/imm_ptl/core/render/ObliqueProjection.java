package qouteall.imm_ptl.core.render;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Oblique near plane projection (Lengyel, "Oblique View Frustum Depth Projection and Clipping"):
 * replaces the near plane of a perspective projection with an arbitrary view space plane,
 * so everything on the negative side of the plane is clipped without shader support.
 * Pure math, see ObliqueProjectionTest.
 */
public final class ObliqueProjection {
    private ObliqueProjection() {}
    
    /**
     * @param projection     modified in place
     * @param viewSpacePlane {@code dot(plane.xyz, p) + plane.w >= 0} is the kept side
     * @param reversedZ      true: near -> 1 and far -> 0 or -1 (MC 26.2+), false: classic OpenGL near -> -1, far -> 1 (MC 26.1.x)
     * @param zeroToOne      the NDC depth range is [0,1] (glClipControl), otherwise [-1,1]. Only used with reversedZ.
     * @return false if the plane cannot be used as near plane (the matrix is unchanged then)
     */
    public static boolean apply(Matrix4f projection, Vector4f viewSpacePlane, boolean reversedZ, boolean zeroToOne) {
        Vector4f plane = new Vector4f(viewSpacePlane);
        
        Matrix4f inverse = new Matrix4f(projection).invert();
        
        // the plane in clip space: (P^-1)^T * plane
        Vector4f clipPlane = new Matrix4f(inverse).transpose().transform(new Vector4f(plane));
        
        float farNdcZ = reversedZ ? (zeroToOne ? 0 : -1) : 1;
        
        // the frustum corner on the far plane that is farthest on the kept side
        Vector4f farCorner = inverse.transform(new Vector4f(
            Math.signum(clipPlane.x), Math.signum(clipPlane.y), farNdcZ, 1
        ));
        
        Vector4f row4 = projection.getRow(3, new Vector4f());
        
        float planeDotCorner = plane.dot(farCorner);
        float row4DotCorner = row4.dot(farCorner);
        if (planeDotCorner <= 1e-6f || row4DotCorner <= 0) {
            return false;
        }
        
        Vector4f newRow3;
        if (reversedZ) {
            // near plane is z_clip = w_clip, keep w - z = a * plane; the far plane (z = 0 or z = -w)
            // passes the far corner
            float a = (zeroToOne ? 1 : 2) * row4DotCorner / planeDotCorner;
            newRow3 = new Vector4f(row4).sub(new Vector4f(plane).mul(a));
        }
        else {
            // near plane is z_clip = -w_clip, keep z + w = a * plane
            float a = 2 * row4DotCorner / planeDotCorner;
            newRow3 = new Vector4f(plane).mul(a).sub(row4);
        }
        
        projection.setRow(2, newRow3);
        return true;
    }
}
