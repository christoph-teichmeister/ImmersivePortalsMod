package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.Lightmap;

public interface IEGameRenderer {
    // 26.x: LightTexture became Lightmap (render port implements this in MixinGameRenderer)
    void ip_setLightmapTextureManager(Lightmap manager);
    
    boolean ip_getDoRenderHand();
    
    void ip_setCamera(Camera camera);
    
    void ip_setIsRenderingPanorama(boolean cond);
}
