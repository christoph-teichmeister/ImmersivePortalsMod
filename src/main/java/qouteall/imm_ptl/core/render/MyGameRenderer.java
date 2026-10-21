package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.mixin.client.render.IECamera_Accessor;
import qouteall.imm_ptl.core.mixin.client.render.IECloudRenderer_Accessor;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRenderState_Accessor;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRenderer_Accessor;
import qouteall.imm_ptl.core.mixin.client.render.IELevelRenderer_Accessor;
import qouteall.imm_ptl.core.render.context_management.DimensionRenderHelper;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.my_util.LimitedLogger;
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
*///?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Renders the world again from another camera (the portal view).
 * <p>
 * 26.x splits rendering into "extract" (fill a LevelRenderState) and "render" (consume it).
 * Rendering a portal world switches the level, level renderer, (26.2+) level extractor, camera
 * and lightmap, extracts the portal world into a separate LevelRenderState and renders it.
 * The per-frame objects that the outer level rendering is still using (render state, submit node
 * storage, feature dispatcher with its vertex buffers, projection buffer) are swapped
 * with a separate set for each nesting depth, see {@link NestedContext}.
 * The objects whose GPU data lives in per-frame ring buffers (fog, clouds) exist once per view,
 * see {@link ViewResources}.
 */
@Environment(EnvType.CLIENT)
public class MyGameRenderer {
    public static final Minecraft client = Minecraft.getInstance();

    private static final LimitedLogger limitedLogger = new LimitedLogger(10);

    // the vanilla visibility sections discovery code is multithreaded
    // when the player teleports through a portal, on the first frame it will not work normally
    // so use IP's non-multi-threaded algorithm at the first frame
    public static int vanillaTerrainSetupOverride = 0;

    public static boolean enablePortalCaveCulling = true;

    /**
     * The objects that each nesting level of portal rendering needs its own instance of.
     * (Replaces the 1.21.1 secondary RenderBuffers.)
     */
    private static class NestedContext {
        final LevelRenderState levelRenderState = new LevelRenderState();
        // the frame graph resource handles, the outer frame still reads them after a portal view of the same renderer
        final LevelTargetBundle targets = new LevelTargetBundle();
        final RenderBuffers renderBuffers = new RenderBuffers(0);
        final SubmitNodeStorage submitNodeStorage;
        final FeatureRenderDispatcher featureRenderDispatcher;
        final ProjectionMatrixBuffer projectionMatrixBuffer =
            new ProjectionMatrixBuffer("imm_ptl portal level");
        // contains the camera position that the terrain shader uses
        final GlobalSettingsUniform globalSettingsUniform = new GlobalSettingsUniform();

        NestedContext() {
            GameRenderState gameRenderState =
                ((IEGameRenderer_Accessor) client.gameRenderer).ip_getGameRenderState();
            //? if >=26.2 {
            submitNodeStorage = new SubmitNodeStorage();
            featureRenderDispatcher = new FeatureRenderDispatcher(
                renderBuffers, client.getModelManager(), client.getAtlasManager(),
                client.font, gameRenderState
            );
            //?} else {
            /*submitNodeStorage = new SubmitNodeStorage();
            featureRenderDispatcher = new FeatureRenderDispatcher(
                submitNodeStorage, client.getModelManager(), renderBuffers.bufferSource(),
                client.getAtlasManager(), renderBuffers.outlineBufferSource(),
                renderBuffers.crumblingBufferSource(), client.font, gameRenderState
            );
            *///?}
        }

        void endFrame() {
            //? if >=26.2 {
            renderBuffers.endFrame();
            //?} else {
            /*submitNodeStorage.endFrame();
            featureRenderDispatcher.endFrame();
            *///?}
        }

        void close() {
            projectionMatrixBuffer.close();
            globalSettingsUniform.close();
            //? if >=26.2 {
            featureRenderDispatcher.close();
            renderBuffers.close();
            //?}
        }
    }

    private static final List<NestedContext> nestedContexts = new ArrayList<>();
    private static int nestingDepth = 0;

    /**
     * FogRenderer and CloudRenderer write their uniforms (and the cloud mesh) into ring buffers that rotate
     * once per frame. Two views using the same instance in one frame overwrite the data of the first view
     * before the GPU reads it, and the 26.3 CloudRenderer throws when it rebuilds its mesh three times
     * in a frame. So each view has its own instances, kept across frames, which also keeps the cloud mesh
     * of each view instead of rebuilding it every frame (the 1.21.1 CloudContext optimization).
     */
    private static class ViewResources {
        final FogRenderer fogRenderer = new FogRenderer();
        final CloudRenderer cloudRenderer = new CloudRenderer();
        int lastUsedFrame;

        void close() {
            fogRenderer.close();
            cloudRenderer.close();
        }
    }

    // the views are identified by the portal chain, the occurrence separates views with the same chain in a frame
    private record ViewKey(List<UUID> description, int occurrence) {}

    private static final Map<ViewKey, ViewResources> viewResources = new HashMap<>();
    private static final Map<List<UUID>, Integer> viewOccurrencesThisFrame = new HashMap<>();
    
    // the level renderers of other dimensions rendered in this frame (vanilla only ends the frame of the main one)
    private static final Set<LevelRenderer> renderedLevelRenderers = new HashSet<>();

    public static void init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> {
            for (NestedContext context : nestedContexts) {
                context.close();
            }
            nestedContexts.clear();
            nestingDepth = 0;
            renderedLevelRenderers.clear();
            viewResources.values().forEach(ViewResources::close);
            viewResources.clear();
            viewOccurrencesThisFrame.clear();
        });
    }

    private static NestedContext acquireContext() {
        if (nestingDepth >= nestedContexts.size()) {
            nestedContexts.add(new NestedContext());
        }
        NestedContext result = nestedContexts.get(nestingDepth);
        nestingDepth++;
        return result;
    }

    private static void releaseContext() {
        nestingDepth--;
    }

    private static ViewResources acquireViewResources(LevelRenderer worldRenderer) {
        List<UUID> description = WorldRenderInfo.getRenderingDescription();
        int occurrence = viewOccurrencesThisFrame.merge(description, 1, Integer::sum) - 1;
        ViewResources result = viewResources.computeIfAbsent(
            new ViewKey(description, occurrence), k -> new ViewResources()
        );
        result.lastUsedFrame = RenderStates.frameIndex;

        // only the CloudRenderer of the first LevelRenderer is registered as reload listener
        IECloudRenderer_Accessor clouds = (IECloudRenderer_Accessor) result.cloudRenderer;
        CloudRenderer.TextureData texture = getCloudTexture(worldRenderer);
        if (clouds.ip_getTexture() != texture) {
            clouds.ip_setTexture(texture);
            result.cloudRenderer.markForRebuild();
        }
        return result;
    }

    @Nullable
    private static CloudRenderer.TextureData getCloudTexture(LevelRenderer preferred) {
        CloudRenderer.TextureData texture =
            ((IECloudRenderer_Accessor) ((IELevelRenderer_Accessor) preferred).ip_getCloudRenderer()).ip_getTexture();
        if (texture != null) {
            return texture;
        }
        for (LevelRenderer levelRenderer : ClientWorldLoader.WORLD_RENDERER_MAP.values()) {
            texture = ((IECloudRenderer_Accessor) ((IELevelRenderer_Accessor) levelRenderer).ip_getCloudRenderer()).ip_getTexture();
            if (texture != null) {
                return texture;
            }
        }
        return null;
    }

    /**
     * Called once per frame after the whole frame is rendered.
     */
    public static void onFrameEnd() {
        for (NestedContext context : nestedContexts) {
            context.endFrame();
        }
        viewOccurrencesThisFrame.clear();
        // ponytail: a view unused for 600 frames releases its buffers, the next use rebuilds its cloud mesh once
        viewResources.values().removeIf(view -> {
            if (view.lastUsedFrame == RenderStates.frameIndex) {
                view.fogRenderer.endFrame();
                view.cloudRenderer.endFrame();
                return false;
            }
            if (RenderStates.frameIndex - view.lastUsedFrame > 600) {
                view.close();
                return true;
            }
            return false;
        });
        //? if >=26.2 {
        for (LevelRenderer levelRenderer : renderedLevelRenderers) {
            if (levelRenderer != client.levelRenderer) {
                levelRenderer.endFrame();
            }
        }
        //?}
        renderedLevelRenderers.clear();
    }

    public static void renderWorldNew(
        WorldRenderInfo worldRenderInfo,
        Consumer<Runnable> invokeWrapper
    ) {
        WorldRenderInfo.pushRenderInfo(worldRenderInfo);

        switchAndRenderTheWorld(
            worldRenderInfo.world,
            invokeWrapper,
            worldRenderInfo.renderDistance
        );

        WorldRenderInfo.popRenderInfo();
    }

    private static void switchAndRenderTheWorld(
        ClientLevel newWorld,
        Consumer<Runnable> invokeWrapper,
        int renderDistance
    ) {
        if (!enablePortalCaveCulling) {
            client.smartCull = false;
        }

        if (!PortalRendering.shouldEnableSodiumCaveCulling()) {
            client.smartCull = false;
        }

        ResourceKey<Level> newDimension = newWorld.dimension();

        LevelRenderer worldRenderer = ClientWorldLoader.getWorldRenderer(newDimension);

        CHelper.checkGlError();

        IEGameRenderer_Accessor gameRendererAccess = (IEGameRenderer_Accessor) client.gameRenderer;
        DimensionRenderHelper helper =
            ClientWorldLoader.getDimensionRenderHelper(newDimension);

        //? if >=26.2 {
        net.minecraft.client.renderer.extract.LevelExtractor newExtractor = helper.getLevelExtractor();
        if (newExtractor == null) {
            limitedLogger.err("Missing level extractor of " + newDimension.identifier());
            return;
        }
        //?}

        Camera newCamera = new Camera();

        GameRenderState gameRenderState = gameRendererAccess.ip_getGameRenderState();
        IELevelRenderer_Accessor levelRendererAccess = (IELevelRenderer_Accessor) worldRenderer;

        // store old state
        ClientLevel oldWorld = client.level;
        LevelRenderer oldWorldRenderer = client.levelRenderer;
        Lightmap oldLightmap = gameRendererAccess.ip_getLightmap();
        boolean oldNoClip = client.player.noPhysics;
        HitResult oldCrosshairTarget = client.hitResult;
        Camera oldCamera = gameRendererAccess.ip_getMainCamera();
        LevelRenderState oldGameLevelRenderState = gameRenderState.levelRenderState;
        LevelRenderState oldRendererLevelRenderState = levelRendererAccess.ip_getLevelRenderState();
        SubmitNodeStorage oldSubmitNodeStorage = levelRendererAccess.ip_getSubmitNodeStorage();
        FeatureRenderDispatcher oldFeatureRenderDispatcher = levelRendererAccess.ip_getFeatureRenderDispatcher();
        RenderBuffers oldRenderBuffers = levelRendererAccess.ip_getRenderBuffers();
        LevelTargetBundle oldTargets = levelRendererAccess.ip_getTargets();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> oldVisibleSections =
            levelRendererAccess.ip_getVisibleSections();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> oldNearbyVisibleSections =
            levelRendererAccess.ip_getNearbyVisibleSections();
        GpuBufferSlice oldProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType oldProjectionType = RenderSystem.getProjectionType();
        GpuBufferSlice oldFog = RenderSystem.getShaderFog();
        GpuBuffer oldGlobalSettings = RenderSystem.getGlobalSettingsUniform();
        //? if >=26.3 {
        boolean oldIsRenderingLevel = RenderSystem.isRenderingLevel;
        //?}
        //? if >=26.2 {
        net.minecraft.client.renderer.extract.LevelExtractor oldExtractor = client.levelExtractor;
        LevelRenderState oldExtractorLevelRenderState =
            ((qouteall.imm_ptl.core.mixin.client.render.IELevelExtractor_Accessor) newExtractor).ip_getLevelRenderState();
        //?}

        Object irisPipeline = IrisInterface.invoker.getPipeline(worldRenderer);

        NestedContext context = acquireContext();
        ViewResources view = acquireViewResources(worldRenderer);
        CloudRenderer oldCloudRenderer = levelRendererAccess.ip_getCloudRenderer();
        renderedLevelRenderers.add(worldRenderer);

        // switch (note: it will no longer switch the world that client player is in )
        ((IEMinecraftClient) client).ip_setWorldRenderer(worldRenderer);
        client.level = newWorld;
        gameRendererAccess.ip_setLightmap(helper.lightmapTexture);
        gameRendererAccess.ip_setMainCamera(newCamera);
        newCamera.setLevel(newWorld);
        copyCameraState(oldCamera, newCamera);

        ((IEGameRenderState_Accessor) gameRenderState).ip_setLevelRenderState(context.levelRenderState);
        levelRendererAccess.ip_setLevelRenderState(context.levelRenderState);
        levelRendererAccess.ip_setSubmitNodeStorage(context.submitNodeStorage);
        levelRendererAccess.ip_setFeatureRenderDispatcher(context.featureRenderDispatcher);
        levelRendererAccess.ip_setRenderBuffers(context.renderBuffers);
        levelRendererAccess.ip_setTargets(context.targets);
        levelRendererAccess.ip_setCloudRenderer(view.cloudRenderer);
        // the outer rendering of the same level renderer (mirrors) keeps its visible sections
        ObjectArrayList<SectionRenderDispatcher.RenderSection> newVisibleSections = VisibleSectionDiscovery.takeList();
        ObjectArrayList<SectionRenderDispatcher.RenderSection> newNearbyVisibleSections = VisibleSectionDiscovery.takeList();
        levelRendererAccess.ip_setVisibleSections(newVisibleSections);
        levelRendererAccess.ip_setNearbyVisibleSections(newNearbyVisibleSections);
        //? if >=26.2 {
        ((qouteall.imm_ptl.core.mixin.client.accessor.IEMinecraft_Accessor) client).ip_setLevelExtractor(newExtractor);
        ((qouteall.imm_ptl.core.mixin.client.render.IELevelExtractor_Accessor) newExtractor)
            .ip_setLevelRenderState(context.levelRenderState);
        //?}

        client.player.noPhysics = true;

        ((IEParticleManager) client.particleEngine).ip_setWorld(newWorld);
        if (BlockManipulationClient.remotePointedDim == newDimension) {
            client.hitResult = BlockManipulationClient.remoteHitResult;
        }
        if (!PortalRendering.shouldRenderHitResult()) {
            client.hitResult = null;
        }

        Object newSodiumContext = SodiumInterface.invoker.createNewContext(renderDistance);
        SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);

        IrisInterface.invoker.setPipeline(worldRenderer, null);

        boolean updateLightmap = !RenderStates.isDimensionRendered(newDimension);

        // the outer level rendering still has its view rotation on the model view stack
        // (1.21.1 gave each portal view a fresh stack)
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix().identity();

        //invoke rendering
        try {
            invokeWrapper.accept(() -> {
                Profiler.get().push("render_portal_content");
                extractAndRenderLevel(context, view, newCamera, helper, updateLightmap);
                Profiler.get().pop();
            });
        }
        finally {
            modelViewStack.popMatrix();
            SodiumInterface.invoker.switchContextWithCurrentWorldRenderer(newSodiumContext);

            //recover

            ((IEMinecraftClient) client).ip_setWorldRenderer(oldWorldRenderer);
            client.level = oldWorld;
            gameRendererAccess.ip_setLightmap(oldLightmap);
            gameRendererAccess.ip_setMainCamera(oldCamera);
            client.player.noPhysics = oldNoClip;

            ((IEGameRenderState_Accessor) gameRenderState).ip_setLevelRenderState(oldGameLevelRenderState);
            levelRendererAccess.ip_setLevelRenderState(oldRendererLevelRenderState);
            levelRendererAccess.ip_setSubmitNodeStorage(oldSubmitNodeStorage);
            levelRendererAccess.ip_setFeatureRenderDispatcher(oldFeatureRenderDispatcher);
            levelRendererAccess.ip_setRenderBuffers(oldRenderBuffers);
            levelRendererAccess.ip_setTargets(oldTargets);
            levelRendererAccess.ip_setCloudRenderer(oldCloudRenderer);
            levelRendererAccess.ip_setVisibleSections(oldVisibleSections);
            levelRendererAccess.ip_setNearbyVisibleSections(oldNearbyVisibleSections);
            VisibleSectionDiscovery.returnList(newVisibleSections);
            VisibleSectionDiscovery.returnList(newNearbyVisibleSections);
            //? if >=26.2 {
            ((qouteall.imm_ptl.core.mixin.client.render.IELevelExtractor_Accessor) newExtractor)
                .ip_setLevelRenderState(oldExtractorLevelRenderState);
            ((qouteall.imm_ptl.core.mixin.client.accessor.IEMinecraft_Accessor) client).ip_setLevelExtractor(oldExtractor);
            //?}

            ((IEParticleManager) client.particleEngine).ip_setWorld(oldWorld);
            client.hitResult = oldCrosshairTarget;
            
            // the portal world extraction prepared the shared entity dispatcher with the portal camera
            //? if >=26.2 {
            oldWorldRenderer.entityRenderDispatcher().prepare(oldCamera, client.crosshairPickEntity);
            //?} else {
            /*client.getEntityRenderDispatcher().prepare(oldCamera, client.crosshairPickEntity);
            *///?}
            //? if >=26.3 {
            RenderSystem.isRenderingLevel = oldIsRenderingLevel;
            //?}

            if (oldProjection != null) {
                RenderSystem.setProjectionMatrix(oldProjection, oldProjectionType);
            }
            if (oldFog != null) {
                RenderSystem.setShaderFog(oldFog);
            }
            if (oldGlobalSettings != null) {
                RenderSystem.setGlobalSettingsUniform(oldGlobalSettings);
            }

            IrisInterface.invoker.setPipeline(worldRenderer, irisPipeline);

            releaseContext();

            CHelper.checkGlError();

            client.smartCull = true;
        }
    }

    /**
     * The portal version of GameRenderer.update + extract + renderLevel,
     * without the screen effects and the post effects (and the hand unless {@link WorldRenderInfo#doRenderHand}).
     */
    private static void extractAndRenderLevel(
        NestedContext context, ViewResources view, Camera camera, DimensionRenderHelper helper, boolean updateLightmap
    ) {
        IEGameRenderer_Accessor gameRendererAccess = (IEGameRenderer_Accessor) client.gameRenderer;
        DeltaTracker deltaTracker = client.getDeltaTracker();
        float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);

        // the camera position is adjusted by WorldRenderInfo.adjustCameraPos in MixinCamera
        camera.update(deltaTracker);
        // a new camera has no environment attribute samples yet (sky, fog and light colors come from them)
        camera.attributeProbe().tick(client.level, camera.position());
        
        //update lightmap
        if (updateLightmap) {
            helper.updateLightmap();
        }

        //? if >=26.3 {
        gameRendererAccess.ip_extractCamera(deltaTracker, worldPartialTicks);
        client.levelExtractor.extract(deltaTracker, camera, worldPartialTicks);
        //?} else if >=26.2 {
        /*gameRendererAccess.ip_extractCamera(
            deltaTracker, worldPartialTicks, camera.getCameraEntityPartialTicks(deltaTracker)
        );
        client.levelExtractor.extract(deltaTracker, camera, worldPartialTicks);
        *///?} else {
        /*client.levelRenderer.update(camera);
        gameRendererAccess.ip_extractCamera(
            deltaTracker, worldPartialTicks, camera.getCameraEntityPartialTicks(deltaTracker)
        );
        client.levelRenderer.extractLevel(deltaTracker, camera, worldPartialTicks);
        *///?}

        GameRenderState gameRenderState = gameRendererAccess.ip_getGameRenderState();
        CameraRenderState cameraState = gameRenderState.levelRenderState.cameraRenderState;
        
        // the terrain shader positions the sections relative to the camera position in the global settings
        updateGlobalSettings(context, gameRenderState, cameraState, deltaTracker);

        // the projection with view bobbing, see GameRenderer.renderLevel
        // (the nausea/portal spinning effect is not applied to portal views)
        Matrix4f projectionMatrix = new Matrix4f(cameraState.projectionMatrix);
        PoseStack bobStack = new PoseStack();
        gameRendererAccess.ip_bobHurt(cameraState, bobStack);
        if (gameRenderState.optionsRenderState.bobView) {
            gameRendererAccess.ip_bobView(cameraState, bobStack);
        }
        projectionMatrix.mul(bobStack.last().pose());

        // clip the content behind the portal with an oblique near plane
        projectionMatrix = FrontClipping.getProjectionWithInnerClipping(
            projectionMatrix, cameraState.viewRotationMatrix
        );

        RenderSystem.setProjectionMatrix(
            context.projectionMatrixBuffer.getBuffer(projectionMatrix), ProjectionType.PERSPECTIVE
        );

        view.fogRenderer.updateBuffer(cameraState.fogData);
        GpuBufferSlice terrainFog = view.fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);

        boolean shouldRenderSky = WorldRenderInfo.getTopRenderInfo().doRenderSky;

        boolean isMirror = PortalRendering.isRenderingOddNumberOfMirrors();
        if (isMirror) {
            MyRenderHelper.applyMirrorFaceCulling();
        }

        // no depth clamp (1.21.1 option useDepthClampForPortalRendering): it would disable the oblique near plane

        //? if >=26.3 {
        client.levelRenderer.render(
            gameRendererAccess.ip_getResourcePool(), false, cameraState,
            terrainFog, cameraState.fogData.color, shouldRenderSky, false
        );
        //?} else if >=26.2 {
        /*client.levelRenderer.render(
            gameRendererAccess.ip_getResourcePool(), client.getDeltaTracker(), false, cameraState,
            cameraState.viewRotationMatrix, terrainFog, cameraState.fogData.color, shouldRenderSky
        );
        *///?} else {
        /*client.levelRenderer.renderLevel(
            gameRendererAccess.ip_getResourcePool(), client.getDeltaTracker(), false, cameraState,
            cameraState.viewRotationMatrix, terrainFog, cameraState.fogData.color, shouldRenderSky,
            gameRenderState.levelRenderState.chunkSectionsToRender
        );
        *///?}


        if (isMirror) {
            MyRenderHelper.recoverFaceCulling();
        }

        FrontClipping.disableClipping();

        if (WorldRenderInfo.getTopRenderInfo().doRenderHand) {
            renderHand(gameRendererAccess, gameRenderState, cameraState, camera, deltaTracker);
        }
    }

    /**
     * The hand part of GameRenderer.renderLevel (26.3: render3dHud without the screen effects).
     * Like 1.21.1 it clears the whole depth buffer first, so only API views that cover the target use it.
     */
    private static void renderHand(
        IEGameRenderer_Accessor gameRendererAccess, GameRenderState gameRenderState,
        CameraRenderState cameraState, Camera camera, DeltaTracker deltaTracker
    ) {
        net.minecraft.client.renderer.Projection hudProjection = gameRendererAccess.ip_getHudProjection();
        //? if >=26.3 {
        float hudFar = cameraState.depthFar;
        //?} else {
        /*float hudFar = 100.0F;
        *///?}
        hudProjection.setupPerspective(
            0.05F, hudFar, cameraState.hudFov,
            gameRenderState.windowRenderState.width, gameRenderState.windowRenderState.height
        );
        RenderSystem.setProjectionMatrix(
            gameRendererAccess.ip_getHud3dProjectionMatrixBuffer().getBuffer(hudProjection), ProjectionType.PERSPECTIVE
        );
        com.mojang.blaze3d.pipeline.RenderTarget mainTarget = GpuHelper.getMainTarget();
        RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainTarget.getDepthTexture(), GpuHelper.FAR_DEPTH);
        //? if >=26.3 {
        gameRendererAccess.ip_renderItemInHand(
            cameraState, gameRenderState.levelRenderState.playerRenderState, mainTarget.getDepthTextureView()
        );
        //?} else {
        /*gameRendererAccess.ip_renderItemInHand(
            cameraState, camera.getCameraEntityPartialTicks(deltaTracker), cameraState.viewRotationMatrix
        );
        *///?}
    }

    /**
     * A new Camera has no ticked state, take it from the outer camera.
     */
    private static void copyCameraState(Camera from, Camera to) {
        IECamera_Accessor fromAccess = (IECamera_Accessor) from;
        IECamera_Accessor toAccess = (IECamera_Accessor) to;
        toAccess.ip_setFovModifier(fromAccess.ip_getFovModifier());
        toAccess.ip_setOldFovModifier(fromAccess.ip_getOldFovModifier());
        ((IECamera) to).ip_setCameraY(((IECamera) from).ip_getCameraY(), ((IECamera) from).ip_getLastCameraY());
        if (from.isPanoramicMode()) {
            to.enablePanoramicMode();
        }
        if (from.entity() != null) {
            to.setEntity(from.entity());
        }
    }
    
    /**
     * See GameRenderer.render, which updates it with the outer camera position.
     */
    private static void updateGlobalSettings(
        NestedContext context, GameRenderState gameRenderState, CameraRenderState cameraState,
        DeltaTracker deltaTracker
    ) {
        boolean rgss = gameRenderState.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS;
        //? if >=26.3 {
        context.globalSettingsUniform.update(
            gameRenderState.windowRenderState.width,
            gameRenderState.windowRenderState.height,
            gameRenderState.optionsRenderState.glintStrength,
            gameRenderState.levelRenderState.gameTime,
            gameRenderState.levelRenderState.worldPartialTicks,
            gameRenderState.optionsRenderState.menuBackgroundBlurriness,
            cameraState.pos,
            rgss
        );
        //?} else {
        /*context.globalSettingsUniform.update(
            gameRenderState.windowRenderState.width,
            gameRenderState.windowRenderState.height,
            gameRenderState.optionsRenderState.glintStrength,
            client.level.getGameTime(),
            deltaTracker,
            gameRenderState.optionsRenderState.menuBackgroundBlurriness,
            cameraState.pos,
            rgss
        );
        *///?}
    }
    
    /**
     * The current main camera. 26.1.x calls it getMainCamera(), 26.2+ mainCamera().
     */
    public static Camera getMainCamera() {
        //? if >=26.2 {
        return client.gameRenderer.mainCamera();
        //?} else {
        /*return client.gameRenderer.getMainCamera();
        *///?}
    }

    public static Vec3 getMainCameraPos() {
        return getMainCamera().position();
    }
}
