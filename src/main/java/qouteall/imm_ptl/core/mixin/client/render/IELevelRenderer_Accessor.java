package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The per-frame state of a LevelRenderer that has to be swapped for nested (portal) rendering.
 */
@Mixin(LevelRenderer.class)
public interface IELevelRenderer_Accessor {
    @Accessor("levelRenderState")
    LevelRenderState ip_getLevelRenderState();
    
    @Mutable
    @Accessor("levelRenderState")
    void ip_setLevelRenderState(LevelRenderState arg);
    
    @Accessor("submitNodeStorage")
    SubmitNodeStorage ip_getSubmitNodeStorage();
    
    @Mutable
    @Accessor("submitNodeStorage")
    void ip_setSubmitNodeStorage(SubmitNodeStorage arg);
    
    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher ip_getFeatureRenderDispatcher();
    
    @Mutable
    @Accessor("featureRenderDispatcher")
    void ip_setFeatureRenderDispatcher(FeatureRenderDispatcher arg);
    
    @Accessor("renderBuffers")
    RenderBuffers ip_getRenderBuffers();
    
    @Mutable
    @Accessor("renderBuffers")
    void ip_setRenderBuffers(RenderBuffers arg);
}
