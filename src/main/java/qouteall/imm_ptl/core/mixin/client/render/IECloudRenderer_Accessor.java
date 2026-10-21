package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The cloud texture is loaded by the reload listener of the main LevelRenderer's CloudRenderer,
 * the per-view CloudRenderers of {@link qouteall.imm_ptl.core.render.MyGameRenderer} copy it.
 */
@Mixin(CloudRenderer.class)
public interface IECloudRenderer_Accessor {
    @Accessor("texture")
    CloudRenderer.TextureData ip_getTexture();
    
    @Accessor("texture")
    void ip_setTexture(CloudRenderer.TextureData texture);
}
