package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.util.profiling.Profiler;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.CrossPortalViewRendering;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import net.minecraft.client.DeltaTracker;

/**
 * 26.x: a frame is update (camera) -> extract (render states) -> render.
 * The per-frame portal logic (teleportation, animation) runs before the camera update,
 * the portal renderer is prepared around the level rendering.
 * The 1.21.1 projection matrix and camera rotation hooks moved to the portal world rendering
 * (MyGameRenderer) and MixinCamera.
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IEGameRenderer {
    @Shadow
    @Final
    @Mutable
    private Lightmap lightmap;

    @Shadow
    @Final
    @Mutable
    private Camera mainCamera;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    //? if >=26.2 {
    @Inject(method = "update", at = @At("HEAD"))
    private void onFarBeforeRendering(DeltaTracker deltaTracker, CallbackInfo ci) {
        ip_onFrameBegin(deltaTracker);
    }
    //?} else {
    /*@Inject(method = "update", at = @At("HEAD"))
    private void onFarBeforeRendering(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ip_onFrameBegin(deltaTracker);
    }
    *///?}

    /**
     * Runs before the camera update and the render state extraction of the frame
     * (in 1.21.1 it ran at the beginning of GameRenderer.render).
     */
    private void ip_onFrameBegin(DeltaTracker deltaTracker) {
        Profiler.get().push("ip_pre_total_render");
        IPGlobal.PRE_TOTAL_RENDER_TASK_LIST.processTasks();
        Profiler.get().pop();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (!minecraft.isGameLoadFinished()) {
            return;
        }
        Profiler.get().push("ip_pre_render");
        // Note do not use delta tick. use partial tick.
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        RenderStates.updatePreRenderInfo(partialTick);
        StableClientTimer.update(
            minecraft.level.getGameTime(), partialTick
        );
        ClientPortalAnimationManagement.update(); // must update before teleportation
        ClientTeleportationManager.manageTeleportation(false);
        IPGlobal.PRE_GAME_RENDER_EVENT.invoker().run();
        if (IPCGlobal.earlyRemoteUpload) {
            MyRenderHelper.earlyRemoteUpload();
        }
        Profiler.get().pop();

        RenderStates.frameIndex++;
    }

    //before rendering world (not triggered when rendering portal)
    //? if >=26.3 {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V"
        )
    )
    //?} else {
    /*@Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    *///?}
    private void onBeforeRenderingCenter(CallbackInfo ci) {
        PortalRenderer.switchToCorrectRenderer();

        IPCGlobal.renderer.prepareRendering();
    }

    //after rendering world (not triggered when rendering portal)
    //? if >=26.3 {
    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V",
            shift = At.Shift.AFTER
        )
    )
    //?} else {
    /*@Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER
        )
    )
    *///?}
    private void onAfterRenderingCenter(CallbackInfo ci) {
        IPCGlobal.renderer.finishRendering();

        RenderStates.onTotalRenderEnd();

        GuiPortalRendering._onGameRenderEnd();

        if (IPCGlobal.lateClientLightUpdate) {
            Profiler.get().push("ip_late_update_light");
            MyRenderHelper.lateUpdateLight();
            Profiler.get().pop();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(CallbackInfo ci) {
        MyGameRenderer.onFrameEnd();
    }

    //special rendering in third person view
    //? if >=26.3 {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel()V"
        )
    )
    private void wrapRenderingWorld(GameRenderer gameRenderer, Operation<Void> original) {
        if (CrossPortalViewRendering.renderCrossPortalView()) {
            return;
        }

        original.call(gameRenderer);
    }
    //?} else {
    /*@WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"
        )
    )
    private void wrapRenderingWorld(GameRenderer gameRenderer, DeltaTracker deltaTracker, Operation<Void> original) {
        if (CrossPortalViewRendering.renderCrossPortalView()) {
            return;
        }

        original.call(gameRenderer, deltaTracker);
    }
    *///?}

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRenderCenterEnded(CallbackInfo ci) {
        IPCGlobal.renderer.onHandRenderingEnded();
    }

    //? if >=26.3 {
    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
            shift = At.Shift.AFTER
        )
    )
    //?} else if >=26.2 {
    /*@Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER
        )
    )
    *///?} else {
    /*@Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            shift = At.Shift.AFTER
        )
    )
    *///?}
    private void onAfterLevelRendering(CallbackInfo ci) {
        IPCGlobal.renderer.onBeforeHandRendering(
            new Matrix4f(gameRenderState.levelRenderState.cameraRenderState.viewRotationMatrix)
        );
    }

    //resize all world renderers when resizing window
    @Inject(method = "resize(II)V", at = @At("RETURN"))
    private void onOnResized(int int_1, int int_2, CallbackInfo ci) {
        if (ClientWorldLoader.getIsInitialized()) {
            ClientWorldLoader.WORLD_RENDERER_MAP.values().stream()
                .filter(
                    worldRenderer -> worldRenderer != minecraft.levelRenderer
                )
                .forEach(
                    worldRenderer -> worldRenderer.resize(int_1, int_2)
                );
        }
    }

    //? if >=26.3 {
    // avoid the improved transparency (OIT) render targets in portal rendering, like fabulous in 1.21.1
    @Inject(method = "useImprovedTransparency", at = @At("HEAD"), cancellable = true)
    private void onUseImprovedTransparency(CallbackInfoReturnable<Boolean> cir) {
        if (WorldRenderInfo.isRendering()) {
            cir.setReturnValue(false);
        }
    }
    //?}

    private static boolean portal_isRenderingHand = false;

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void onRenderHandBegins(CallbackInfo ci) {
        portal_isRenderingHand = true;
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void onRenderHandEnds(CallbackInfo ci) {
        portal_isRenderingHand = false;
    }

    // not using ModifyArgs because ModifyArgs seems broken on Forge
    @ModifyArg(
        method = "bobView",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        index = 0
    )
    private float modifyBobViewTranslateX(float f) {
        if (portal_isRenderingHand) {
            return f;
        }
        else {
            return (float) (f * RenderStates.getViewBobbingOffsetMultiplier());
        }
    }

    @ModifyArg(
        method = "bobView",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        index = 1
    )
    private float modifyBobViewTranslateY(float f) {
        if (portal_isRenderingHand) {
            return f;
        }
        else {
            return (float) (f * RenderStates.getViewBobbingOffsetMultiplier());
        }
    }

    @ModifyArg(
        method = "bobView",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
        index = 2
    )
    private float modifyBobViewTranslateZ(float f) {
        if (portal_isRenderingHand) {
            return f;
        }
        else {
            return (float) (f * RenderStates.getViewBobbingOffsetMultiplier());
        }
    }

    @Override
    public void ip_setLightmapTextureManager(Lightmap manager) {
        lightmap = manager;
    }

    @Override
    public void ip_setCamera(Camera camera_) {
        mainCamera = camera_;
    }

}
