package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Lightmap;

/**
 * 26.x: the lightmap is a {@link Lightmap} (LightTexture is gone).
 * The hand rendering flag and the panorama flag are gone from GameRenderer.
 */
public interface IEGameRenderer {
    void ip_setLightmapTextureManager(Lightmap manager);
    
    void ip_setCamera(Camera camera);
}
