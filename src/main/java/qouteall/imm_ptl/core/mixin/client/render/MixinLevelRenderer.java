package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.GpuHelper;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
//? if >=26.3 {
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.renderer.SectionOcclusionGraph;
*///?} else {
/*import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.IPGlobal;
import net.minecraft.util.profiling.Profiler;
*///?}

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The portal rendering hooks of the level rendering.
 * <p>
 * 26.x renders the level in frame graph passes. The portals are rendered inside the main pass,
 * after the solid things and before the translucent things, outside any open render pass:
 * <ul>
 *     <li>26.1.x / 26.2: before the translucent features are drawn</li>
 *     <li>26.3: the classic transparency shares the render pass with the solid things,
 *     so it is deferred to a separate pass after the portals</li>
 * </ul>
 * The entities that are halfway through a portal are drawn clipped right before the portals,
 * see {@link CrossPortalEntityRenderer}.
 * The 1.21.1 hooks for per-layer clipping, sky rendering, weather,
 * glowing and the fabulous translucent target are gone with the immediate mode rendering,
 * see the notes in {@link qouteall.imm_ptl.core.render.FrontClipping}.
 */
@Mixin(value = LevelRenderer.class)
public abstract class MixinLevelRenderer implements IEWorldRenderer {

    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Shadow
    private @Nullable ViewArea viewArea;

    // swapped per portal view (IELevelRenderer_Accessor), so not final
    @Shadow
    @Final
    @org.spongepowered.asm.mixin.Mutable
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Shadow
    private @Nullable SectionRenderDispatcher sectionRenderDispatcher;

    //? if >=26.3 {
    @Shadow
    protected abstract void executeClassicTransparency(
        ChunkSectionsToRender chunkSectionsToRender,
        FeatureRenderDispatcher.PreparedFrame featureFrame,
        RenderPass renderPass
    );

    @Shadow
    @Final
    private net.minecraft.client.renderer.GameRenderer gameRenderer;
    
    @Shadow
    protected abstract void prepareTranslucents();

    @Nullable
    private ChunkSectionsToRender ip_deferredTranslucentSections;
    @Nullable
    private FeatureRenderDispatcher.PreparedFrame ip_deferredTranslucentFrame;
    //?} else if <26.2 {
    /*@Shadow
    private @Nullable ClientLevel level;
    *///?}

    private static Matrix4f ip_getCurrentModelView() {
        return new Matrix4f(RenderSystem.getModelViewStack());
    }

    // ---- portal rendering inside the main pass

    //? if >=26.3 {
    @Redirect(
        method = "lambda$addMainPass$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
        )
    )
    private void ip_deferClassicTransparency(
        LevelRenderer instance,
        ChunkSectionsToRender chunkSectionsToRender,
        FeatureRenderDispatcher.PreparedFrame featureFrame,
        RenderPass renderPass
    ) {
        ip_deferredTranslucentSections = chunkSectionsToRender;
        ip_deferredTranslucentFrame = featureFrame;
    }

    @Inject(
        method = "lambda$addMainPass$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/RenderPass;close()V",
            ordinal = 0,
            shift = At.Shift.AFTER
        )
    )
    private void ip_onAfterSolidPass(CallbackInfo ci) {
        // read the deferred state before rendering the portals,
        // a portal view of the same level renderer (mirror) sets and consumes these fields too
        ChunkSectionsToRender sections = ip_deferredTranslucentSections;
        FeatureRenderDispatcher.PreparedFrame frame = ip_deferredTranslucentFrame;
        ip_deferredTranslucentSections = null;
        ip_deferredTranslucentFrame = null;
        
        CrossPortalEntityRenderer.renderCrossPortalEntities();
        IPCGlobal.renderer.onBeforeTranslucentRendering(ip_getCurrentModelView());
        
        if (sections != null && frame != null) {
            // a portal view of the same level renderer prepared the clouds, weather and world border for its camera
            prepareTranslucents();
            
            RenderTarget mainTarget = gameRenderer.mainRenderTarget();
            try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    () -> "Translucent (imm_ptl)",
                    mainTarget.getColorTextureView(),
                    Optional.empty(),
                    mainTarget.getDepthTextureView(),
                    OptionalDouble.empty()
                )) {
                RenderSystem.bindDefaultUniforms(renderPass);
                executeClassicTransparency(sections, frame, renderPass);
            }
        }
    }
    //?} else if >=26.2 {
    /*@Inject(
        method = "lambda$addMainPass$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucent()V"
        )
    )
    private void ip_onBeforeTranslucent(CallbackInfo ci) {
        CrossPortalEntityRenderer.renderCrossPortalEntities();
        IPCGlobal.renderer.onBeforeTranslucentRendering(ip_getCurrentModelView());
    }
    *///?} else {
    /*@Inject(
        method = "lambda$addMainPass$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V"
        )
    )
    private void ip_onBeforeTranslucent(CallbackInfo ci) {
        CrossPortalEntityRenderer.renderCrossPortalEntities();
        IPCGlobal.renderer.onBeforeTranslucentRendering(ip_getCurrentModelView());
    }
    *///?}

    //? if >=26.2 {
    @Inject(method = "render", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "renderLevel", at = @At("RETURN"))
    *///?}
    private void onAfterTranslucentRendering(CallbackInfo ci) {
        IPCGlobal.renderer.onAfterTranslucentRendering(ip_getCurrentModelView());
    }

    // ---- frame buffer clearing

    /**
     * When rendering a portal, the main target must not be cleared.
     * (The portal renderer clears the depth of the portal area itself.)
     */
    //? if >=26.3 {
    @WrapOperation(
        method = "lambda$render$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/renderpearl/api/textures/GpuTexture;D)V"
        )
    )
    private void wrapClearing(
        CommandEncoder instance, GpuTexture colorTexture, Vector4fc clearColor,
        GpuTexture depthTexture, double clearDepth, Operation<Void> original
    ) {
        FogRendererContext.setCurrentFogColor(clearColor.x(), clearColor.y(), clearColor.z());
        if (!IPCGlobal.renderer.replaceFrameBufferClearing()) {
            original.call(instance, colorTexture, clearColor, depthTexture, clearDepth);
        }
    }
    //?} else if >=26.2 {
    /*@WrapOperation(
        method = "lambda$render$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;Lorg/joml/Vector4fc;Lcom/mojang/blaze3d/textures/GpuTexture;D)V"
        )
    )
    private void wrapClearing(
        CommandEncoder instance, GpuTexture colorTexture, Vector4fc clearColor,
        GpuTexture depthTexture, double clearDepth, Operation<Void> original
    ) {
        FogRendererContext.setCurrentFogColor(clearColor.x(), clearColor.y(), clearColor.z());
        if (!IPCGlobal.renderer.replaceFrameBufferClearing()) {
            original.call(instance, colorTexture, clearColor, depthTexture, clearDepth);
        }
    }
    *///?} else {
    /*@WrapOperation(
        method = "lambda$renderLevel$0",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V"
        )
    )
    private void wrapClearing(
        CommandEncoder instance, GpuTexture colorTexture, int clearColor,
        GpuTexture depthTexture, double clearDepth, Operation<Void> original
    ) {
        FogRendererContext.setCurrentFogColor(
            net.minecraft.util.ARGB.redFloat(clearColor),
            net.minecraft.util.ARGB.greenFloat(clearColor),
            net.minecraft.util.ARGB.blueFloat(clearColor)
        );
        if (!IPCGlobal.renderer.replaceFrameBufferClearing()) {
            original.call(instance, colorTexture, clearColor, depthTexture, clearDepth);
        }
    }
    *///?}

    // ---- fabulous / improved transparency is disabled while rendering portals

    //? if <26.3 {
    /*@Inject(method = "getTransparencyChain", at = @At("HEAD"), cancellable = true)
    private void onGetTransparencyChain(CallbackInfoReturnable<PostChain> cir) {
        if (WorldRenderInfo.isRendering()) {
            cir.setReturnValue(null);
        }
    }
    *///?}

    // ---- view area

    //? if >=26.2 {
    @Redirect(
        method = "invalidateCompiledGeometry",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;IIIIILnet/minecraft/client/renderer/SectionOcclusionGraph;)Lnet/minecraft/client/renderer/ViewArea;"
        )
    )
    private ViewArea redirectConstructingViewArea(
        SectionRenderDispatcher sectionRenderDispatcher,
        int minY, int maxY, int minSectionY, int maxSectionY,
        int renderDistance,
        SectionOcclusionGraph sectionOcclusionGraph
    ) {
        if (IPCGlobal.useHackedChunkRenderDispatcher) {
            return new ImmPtlViewArea(
                sectionRenderDispatcher, minY, maxY, minSectionY, maxSectionY,
                renderDistance, sectionOcclusionGraph
            );
        }
        else {
            return new ViewArea(
                sectionRenderDispatcher, minY, maxY, minSectionY, maxSectionY,
                renderDistance, sectionOcclusionGraph
            );
        }
    }

    /**
     * The vanilla occlusion graph of the player's dimension serves the outer view.
     * Updating it from a portal camera (mirrors, same-dimension portals) would rebuild it every frame.
     */
    @com.llamalad7.mixinextras.injector.WrapWithCondition(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;update(Lnet/minecraft/client/renderer/state/level/CameraRenderState;ILnet/minecraft/client/renderer/state/level/ChunkLoadingRenderState;)V"
        )
    )
    private boolean shouldUpdateOcclusionGraph(
        SectionOcclusionGraph instance, CameraRenderState camera, int fov,
        net.minecraft.client.renderer.state.level.ChunkLoadingRenderState chunkLoading
    ) {
        return !ip_isRenderingPortalOfPlayerDimension();
    }
    
    /**
     * The outer view of the same dimension (mirrors) still draws from the terrain buffers later in this frame,
     * do not change them in the portal view. The outer rendering uploads the results afterwards.
     */
    @com.llamalad7.mixinextras.injector.WrapWithCondition(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadTerrainBuffersToGpu()V"
        )
    )
    private boolean shouldUploadTerrain(SectionRenderDispatcher instance) {
        return !ip_isRenderingPortalOfPlayerDimension();
    }
    
    private static boolean ip_isRenderingPortalOfPlayerDimension() {
        Minecraft client = Minecraft.getInstance();
        return WorldRenderInfo.isRendering() && client.level != null &&
            client.level.dimension() == qouteall.imm_ptl.core.render.context_management.RenderStates.originalPlayerDimension;
    }
    
    /**
     * The view area and the section dispatcher follow the outer camera.
     * (In 1.21.1 the portal rendering did not reposition the view area either.)
     */
    @Inject(
        method = "repositionCamera",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRepositionCamera(CameraRenderState camera, CallbackInfo ci) {
        if (WorldRenderInfo.isRendering()) {
            // the camera position is used for translucent sort, keep the outer one in the player's dimension
            if (sectionRenderDispatcher != null && !ip_isRenderingPortalOfPlayerDimension()) {
                sectionRenderDispatcher.setCameraPosition(camera.pos);
            }
            ci.cancel();
        }
    }
    //?} else {
    /*@Redirect(
        method = "allChanged",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;Lnet/minecraft/world/level/Level;ILnet/minecraft/client/renderer/LevelRenderer;)Lnet/minecraft/client/renderer/ViewArea;"
        )
    )
    private ViewArea redirectConstructingViewArea(
        SectionRenderDispatcher sectionRenderDispatcher,
        Level world,
        int renderDistance,
        LevelRenderer levelRenderer
    ) {
        if (IPCGlobal.useHackedChunkRenderDispatcher) {
            return new ImmPtlViewArea(
                sectionRenderDispatcher, world, renderDistance, levelRenderer
            );
        }
        else {
            return new ViewArea(
                sectionRenderDispatcher, world, renderDistance, levelRenderer
            );
        }
    }

    // the outer view of the same dimension (mirrors) still draws from the terrain buffers later in this frame
    @com.llamalad7.mixinextras.injector.WrapWithCondition(
        method = "prepareChunkRenders",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;uploadGlobalGeomBuffersToGPU()V"
        )
    )
    private boolean shouldUploadTerrain(SectionRenderDispatcher instance) {
        return !(WorldRenderInfo.isRendering() && level != null &&
            level.dimension() == RenderStates.originalPlayerDimension);
    }
    
    // sometimes we change renderDistance but we don't want to reload it
    @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
    private void onReloadStarted(CallbackInfo ci) {
        if (WorldRenderInfo.isRendering()) {
            Helper.log("world renderer reloading cancelled during portal rendering");
            ci.cancel();
        }
    }

    //reload other world renderers when the main world renderer is reloaded
    @Inject(method = "allChanged", at = @At("TAIL"))
    private void onReloadFinished(CallbackInfo ci) {
        LevelRenderer this_ = (LevelRenderer) (Object) this;

        if (ClientWorldLoader.getIsCreatingClientWorld()) {
            return;
        }

        Validate.isTrue(Minecraft.getInstance().levelRenderer == this_);

        ClientWorldLoader._onWorldRendererReloaded();
    }

    // ---- terrain setup (26.1.x: LevelRenderer.cullTerrain, 26.2+: see MixinLevelExtractor)

    @Inject(
        method = "cullTerrain",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onCullTerrainBegin(
        Camera camera, Frustum frustum, boolean spectator,
        CallbackInfo ci
    ) {
        if (WorldRenderInfo.isRendering()) {
            if (level.dimension() != RenderStates.originalPlayerDimension) {
                sectionRenderDispatcher.setCameraPosition(camera.position());
            }
        }

        if (ip_allowOverrideTerrainSetup()) {
            if (WorldRenderInfo.isRendering()) {
                if (viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                    Profiler.get().push("ip_terrain_setup");
                    VisibleSectionDiscovery.discoverVisibleSections(
                        level, immPtlViewArea,
                        camera,
                        new Frustum(frustum).offsetToFullyIncludeCameraCube(8),
                        visibleSections
                    );
                    Profiler.get().pop();

                    ci.cancel();
                }
            }
        }
    }

    private boolean ip_allowOverrideTerrainSetup() {
        return !SodiumInterface.invoker.isSodiumPresent()
            && !IrisInterface.invoker.isRenderingShadowMap();
    }

    @Inject(
        method = "cullTerrain",
        at = @At("RETURN")
    )
    private void onCullTerrainEnd(
        Camera camera, Frustum frustum, boolean spectator,
        CallbackInfo ci
    ) {
        if (!WorldRenderInfo.isRendering()) {
            if (ip_allowOverrideTerrainSetup() && viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                if (MyGameRenderer.vanillaTerrainSetupOverride > 0) {
                    MyGameRenderer.vanillaTerrainSetupOverride--;

                    Profiler.get().push("ip_terrain_setup");
                    VisibleSectionDiscovery.discoverVisibleSections(
                        level, immPtlViewArea,
                        camera,
                        new Frustum(frustum).offsetToFullyIncludeCameraCube(8),
                        visibleSections
                    );
                    Profiler.get().pop();
                }
                else if (IPGlobal.alwaysOverrideTerrainSetup) {
                    // debug
                    Profiler.get().push("ip_terrain_setup_debug");
                    VisibleSectionDiscovery.discoverVisibleSections(
                        level, immPtlViewArea,
                        camera,
                        new Frustum(frustum).offsetToFullyIncludeCameraCube(8),
                        visibleSections
                    );
                    Profiler.get().pop();
                }
            }
        }
    }
    *///?}

    // ---- IEWorldRenderer

    @Override
    public EntityRenderDispatcher ip_getEntityRenderDispatcher() {
        return entityRenderDispatcher;
    }

    @Override
    public ViewArea ip_getBuiltChunkStorage() {
        return viewArea;
    }

    @Override
    public void portal_fullyDispose() {
        if (viewArea != null) {
            viewArea.releaseAllBuffers();
        }
        //? if <26.2 {
        /*level = null;
        *///?}
    }
}
