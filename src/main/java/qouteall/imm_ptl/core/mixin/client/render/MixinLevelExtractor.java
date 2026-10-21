package qouteall.imm_ptl.core.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
//? if >=26.2 {
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
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

    /**
     * The dirty section tracker is a ring buffer around one camera position.
     * Moving it marks all sections that enter it dirty, so moving it back and forth
     * between several cameras in one frame would recompile everything every frame.
     * The player's dimension keeps it around the player camera,
     * the other dimensions move it once per frame.
     * TODO port: a second portal view into the same dimension in the same frame does not get its
     *  section updates tracked (1.21.1 kept separate render sections per column, 26.2+ tracks dirtiness separately)
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
