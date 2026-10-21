package qouteall.imm_ptl.core.ducks;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;

/**
 * 26.x: the render buffers, transparency shader, frustum and visible section list accessors
 * moved to qouteall.imm_ptl.core.mixin.client.render.IELevelRenderer_Accessor,
 * ip_myRenderEntity is gone with MultiBufferSource.
 */
public interface IEWorldRenderer {
    EntityRenderDispatcher ip_getEntityRenderDispatcher();
    
    ViewArea ip_getBuiltChunkStorage();
    
    void portal_fullyDispose();
}
