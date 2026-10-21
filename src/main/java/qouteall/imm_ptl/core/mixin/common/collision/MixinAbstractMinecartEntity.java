package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;

@Mixin(AbstractMinecart.class)
public class MixinAbstractMinecartEntity {
    // for debugging
    // 26.x: lerpTo is gone (InterpolationHandler); hook the per-step interpolation instead,
    // jumping straight to the target keeps the 1.21.1 "no interpolation" behaviour
    @Inject(
        method = "lerpPositionAndRotationStep",
        at = @At("RETURN")
    )
    private void onUpdateTracketPositionAndAngles(
        int stepsToTarget, double x, double y, double z, double yaw, double pitch, CallbackInfo ci
    ) {
        AbstractMinecart this_ = (AbstractMinecart) ((Object) this);
        if (!IPGlobal.allowClientEntityPosInterpolation) {
            this_.setPos(x, y, z);
        }
    }
}
