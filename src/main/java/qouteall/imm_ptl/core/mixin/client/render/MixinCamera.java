package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(Camera.class)
public abstract class MixinCamera implements IECamera {

    @Shadow
    private Level level;
    @Shadow
    private Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Shadow
    protected abstract void setPosition(Vec3 vec3d_1);

    /**
     * 26.x: Camera.update replaced Camera.setup. Adjust the position right after it follows the entity,
     * before the culling frustum is computed from it.
     */
    @Inject(
        method = "update",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
            shift = At.Shift.AFTER
        )
    )
    private void onAlignedWithEntity(CallbackInfo ci) {
        Camera this_ = (Camera) (Object) this;
        WorldRenderInfo.adjustCameraPos(this_);
    }

    /**
     * Apply the portal camera transformation and the rotation animation to the view matrix.
     * (In 1.21.1 this wrapped the camera rotation in GameRenderer.renderLevel.)
     * The view matrix is cached until the rotation changes, which happens every Camera.update.
     */
    @WrapOperation(
        method = "getViewRotationMatrix",
        at = @At(
            value = "INVOKE",
            target = "Lorg/joml/Matrix4f;rotation(Lorg/joml/Quaternionfc;)Lorg/joml/Matrix4f;",
            remap = false
        )
    )
    private Matrix4f wrapCameraTransformation(
        Matrix4f instance, Quaternionfc quat, Operation<Matrix4f> original
    ) {
        Matrix4f r = original.call(instance, quat);
        return instance.set(TransformationManager.processTransformation((Camera) (Object) this, r));
    }

    @Inject(
        method = "getFluidInCamera",
        at = @At("HEAD"),
        cancellable = true
    )
    private void getSubmergedFluidState(CallbackInfoReturnable<FogType> cir) {
        if (PortalRendering.isRendering()) {
            cir.setReturnValue(FogType.NONE);
            cir.cancel();
        }
    }

    // to let the player be rendered when rendering portal
    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void onIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (CrossPortalEntityRenderer.shouldRenderPlayerDefault()) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public void ip_resetState(Vec3 pos, ClientLevel currWorld) {
        setPosition(pos);
        level = currWorld;
    }

    @Override
    public float ip_getCameraY() {
        return eyeHeight;
    }

    @Override
    public float ip_getLastCameraY() {
        return eyeHeightOld;
    }

    @Override
    public void ip_setCameraY(float cameraY_, float lastCameraY_) {
        eyeHeight = cameraY_;
        eyeHeightOld = lastCameraY_;
    }

    @Override
    public void portal_setPos(Vec3 pos) {
        setPosition(pos);
    }

    @Override
    public void portal_setFocusedEntity(Entity arg) {
        entity = arg;
    }
}
