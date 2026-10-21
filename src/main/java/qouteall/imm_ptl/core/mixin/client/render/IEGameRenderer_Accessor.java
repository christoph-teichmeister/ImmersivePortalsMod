package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessors used by the portal world rendering ({@link qouteall.imm_ptl.core.render.MyGameRenderer}).
 */
@Mixin(GameRenderer.class)
public interface IEGameRenderer_Accessor {
    @Accessor("gameRenderState")
    GameRenderState ip_getGameRenderState();
    
    @Accessor("resourcePool")
    CrossFrameResourcePool ip_getResourcePool();
    
    @Accessor("fogRenderer")
    FogRenderer ip_getFogRenderer();
    
    @Accessor("lightmapRenderStateExtractor")
    LightmapRenderStateExtractor ip_getLightmapRenderStateExtractor();
    
    @Accessor("lightmap")
    Lightmap ip_getLightmap();
    
    @Mutable
    @Accessor("lightmap")
    void ip_setLightmap(Lightmap lightmap);
    
    @Accessor("mainCamera")
    Camera ip_getMainCamera();
    
    @Mutable
    @Accessor("mainCamera")
    void ip_setMainCamera(Camera camera);
    
    @Invoker("bobHurt")
    void ip_bobHurt(CameraRenderState cameraState, PoseStack poseStack);
    
    @Invoker("bobView")
    void ip_bobView(CameraRenderState cameraState, PoseStack poseStack);
    
    //? if >=26.3 {
    @Invoker("extractCamera")
    void ip_extractCamera(DeltaTracker deltaTracker, float worldPartialTicks);
    //?} else {
    /*@Invoker("extractCamera")
    void ip_extractCamera(DeltaTracker deltaTracker, float worldPartialTicks, float cameraEntityPartialTicks);
    *///?}
    
    @Accessor("hudProjection")
    net.minecraft.client.renderer.Projection ip_getHudProjection();
    
    @Accessor("hud3dProjectionMatrixBuffer")
    net.minecraft.client.renderer.ProjectionMatrixBuffer ip_getHud3dProjectionMatrixBuffer();
    
    //? if >=26.3 {
    @Invoker("renderItemInHand")
    void ip_renderItemInHand(
        CameraRenderState cameraState,
        net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
        com.mojang.renderpearl.api.textures.GpuTextureView depthTextureView
    );
    //?} else {
    /*@Invoker("renderItemInHand")
    void ip_renderItemInHand(CameraRenderState cameraState, float deltaPartialTick, org.joml.Matrix4fc modelViewMatrix);
    *///?}
    
    //? if >=26.2 {
    @Mutable
    @Accessor("mainRenderTarget")
    void ip_setMainRenderTarget(com.mojang.blaze3d.pipeline.RenderTarget target);
    //?}
}
