package qouteall.imm_ptl.core.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
//? if >=26.2 {
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.Profiler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
//?}

/**
 * 26.2+: the visible section discovery and the dirty section tracking moved from LevelRenderer to LevelExtractor.
 * 26.1.x has no LevelExtractor, the equivalent hooks are in {@link MixinLevelRenderer} and this mixin is empty.
 */
//? if >=26.2 {
@Mixin(LevelExtractor.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
*///?}
public abstract class MixinLevelExtractor {
    //? if >=26.2 {
    @Shadow
    @Final
    private LevelRenderer levelRenderer;

    @Shadow
    private @Nullable ClientLevel level;

    private int ip_lastTrackerRepositionFrame = -1;

    @Shadow
    private double prevCamRotX;

    @Shadow
    private double prevCamRotY;

    private double ip_outerPrevCamRotX;
    private double ip_outerPrevCamRotY;

    /**
     * The dirty section tracker is a ring buffer around one camera position.
     * Moving it marks all sections that enter it dirty, so moving it back and forth
     * between several cameras in one frame would recompile everything every frame.
     * The player's dimension keeps it around the player camera,
     * the other dimensions move it once per frame.
     * With an {@link ImmPtlViewArea} the tracker is not used, see {@link #wrapGetDirtyState}.
     */
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;repositionCamera(Lnet/minecraft/core/SectionPos;)V"
        )
    )
    private void wrapRepositionTracker(
        SectionUpdateTracker tracker, SectionPos pos, Operation<Void> original
    ) {
        if (WorldRenderInfo.isRendering()) {
            if (level != null && level.dimension() == RenderStates.originalPlayerDimension) {
                return;
            }
            if (ip_lastTrackerRepositionFrame == RenderStates.frameIndex) {
                return;
            }
        }
        ip_lastTrackerRepositionFrame = RenderStates.frameIndex;
        original.call(tracker, pos);
    }

    /**
     * The tracker only covers the sections around one camera, the sections that another portal view
     * of this dimension sees in the same frame would never be compiled or updated.
     * An {@link ImmPtlViewArea} keeps a dirty state in each of its sections instead
     * (like the per-section dirty flag of 1.21.1 and 26.1.x).
     */
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;getDirtyState(J)Lnet/minecraft/client/SectionUpdateTracker$SectionDirtyState;"
        )
    )
    private SectionUpdateTracker.SectionDirtyState wrapGetDirtyState(
        SectionUpdateTracker tracker, long sectionNode, Operation<SectionUpdateTracker.SectionDirtyState> original,
        @Local(argsOnly = true) Camera camera
    ) {
        if (levelRenderer.viewArea() instanceof ImmPtlViewArea viewArea) {
            return viewArea.getDirtyState(sectionNode, SectionPos.of(camera.position()));
        }
        return original.call(tracker, sectionNode);
    }

    @WrapOperation(
        method = "setSectionDirty(IIIZ)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/SectionUpdateTracker;setDirty(IIIZ)V"
        )
    )
    private void wrapSetDirty(
        SectionUpdateTracker tracker, int x, int y, int z, boolean playerChanged, Operation<Void> original
    ) {
        original.call(tracker, x, y, z, playerChanged);
        if (levelRenderer.viewArea() instanceof ImmPtlViewArea viewArea) {
            viewArea.setDirty(x, y, z, playerChanged);
        }
    }

    /**
     * The occlusion graph of the player's dimension serves the outer view,
     * a portal view of that dimension must not consume its frustum update (the portal view does not apply it).
     */
    @WrapOperation(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SectionOcclusionGraph;consumeFrustumUpdate()Z"
        )
    )
    private boolean wrapConsumeFrustumUpdate(
        net.minecraft.client.renderer.SectionOcclusionGraph graph, Operation<Boolean> original
    ) {
        if (WorldRenderInfo.isRendering() && level != null &&
            level.dimension() == RenderStates.originalPlayerDimension) {
            return false;
        }
        return original.call(graph);
    }

    // the outer view compares its camera rotation with the last one to decide whether to apply the frustum
    @Inject(method = "extract", at = @At("HEAD"))
    private void onExtractBegin(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
        ip_outerPrevCamRotX = prevCamRotX;
        ip_outerPrevCamRotY = prevCamRotY;
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void onExtractEnd(DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci) {
        if (WorldRenderInfo.isRendering()) {
            prevCamRotX = ip_outerPrevCamRotX;
            prevCamRotY = ip_outerPrevCamRotY;
        }
    }

    /**
     * The chunk loading events of the player's dimension are consumed by the outer view's occlusion graph,
     * do not consume them in a portal view (the graph update is skipped there, see MixinLevelRenderer).
     */
    @WrapWithCondition(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;flipUpdateTrackingSets()V"
        )
    )
    private boolean shouldFlipChunkTracking(net.minecraft.client.multiplayer.ClientChunkCache instance) {
        return !(WorldRenderInfo.isRendering() && level != null &&
            level.dimension() == RenderStates.originalPlayerDimension);
    }
    
    private boolean ip_allowOverrideTerrainSetup() {
        return !SodiumInterface.invoker.isSodiumPresent()
            && !IrisInterface.invoker.isRenderingShadowMap()
            && levelRenderer.viewArea() instanceof ImmPtlViewArea;
    }

    // the vanilla occlusion graph is camera-dependent and multithreaded, it does not work for portal views
    @WrapWithCondition(
        method = "extract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/extract/LevelExtractor;applyFrustum(Lnet/minecraft/client/renderer/culling/Frustum;)V"
        )
    )
    private boolean shouldApplyVanillaFrustum(LevelExtractor instance, Frustum frustum) {
        return !(WorldRenderInfo.isRendering() && ip_allowOverrideTerrainSetup());
    }

    @Inject(
        method = "extract",
        at = @At(
            value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiling/ProfilerFiller;popPush(Ljava/lang/String;)V",
            args = "ldc=sectionUpdates"
        )
    )
    private void onBeforeSectionUpdates(
        DeltaTracker deltaTracker, Camera camera, float worldPartialTicks, CallbackInfo ci
    ) {
        if (!ip_allowOverrideTerrainSetup() || level == null) {
            return;
        }

        boolean doDiscover;
        if (WorldRenderInfo.isRendering()) {
            doDiscover = true;
        }
        else if (MyGameRenderer.vanillaTerrainSetupOverride > 0) {
            MyGameRenderer.vanillaTerrainSetupOverride--;
            doDiscover = true;
        }
        else {
            // debug
            doDiscover = IPGlobal.alwaysOverrideTerrainSetup;
        }

        if (doDiscover) {
            Profiler.get().push("ip_terrain_setup");
            levelRenderer.clearVisibleSections();
            VisibleSectionDiscovery.discoverVisibleSections(
                level, (ImmPtlViewArea) levelRenderer.viewArea(),
                camera,
                new Frustum(camera.getCullFrustum()).offsetToFullyIncludeCameraCube(8),
                levelRenderer.visibleSections()
            );
            Profiler.get().pop();
        }
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
        if (ClientWorldLoader.getIsCreatingClientWorld()) {
            return;
        }

        if (Minecraft.getInstance().levelExtractor != (Object) this) {
            return;
        }

        ClientWorldLoader._onWorldRendererReloaded();
    }
    //?}
}
