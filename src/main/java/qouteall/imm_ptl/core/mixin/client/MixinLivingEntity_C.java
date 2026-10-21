package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * 26.x: LivingEntity.lerpTo and the lerpX/Y/Z fields are gone,
 * position interpolation goes through {@link Entity#moveOrInterpolateTo} and {@link InterpolationHandler}.
 * The target is Entity (where moveOrInterpolateTo is declared), the logic only applies to living entities.
 */
@Mixin(Entity.class)
public class MixinLivingEntity_C {
    // avoid entity position interpolate when crossing portal to the same dimension
    @Inject(
        //? if >=26.3 {
        method = "moveOrInterpolateTo(Lnet/minecraft/world/entity/PositionPath;FFZ)V",
        //?} else {
        /*method = "moveOrInterpolateTo(Ljava/util/Optional;Ljava/util/Optional;Ljava/util/Optional;)V",
        *///?}
        at = @At("RETURN")
    )
    private void onMoveOrInterpolateTo(CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity this_)) {
            return;
        }

        InterpolationHandler handler = this_.getInterpolation();
        if (handler == null || !handler.hasActiveInterpolation()) {
            return;
        }

        //? if >=26.3 {
        net.minecraft.core.PositionAndRotation target = handler.target();
        if (target == null) {
            return;
        }
        Vec3 targetPos = target.position();
        //?} else {
        /*Vec3 targetPos = handler.position();
        *///?}

        if (!IPGlobal.allowClientEntityPosInterpolation) {
            handler.cancel();
            this_.setPos(targetPos);
            return;
        }

        Portal collidingPortal = ((IEEntity) this).ip_getCollidingPortal();
        if (collidingPortal != null) {
            if (this_.position().distanceToSqr(targetPos) > 4) {
                handler.cancel();
                McHelper.setPosAndLastTickPos(
                    this_,
                    targetPos,
                    targetPos.subtract(McHelper.getWorldVelocity(this_))
                );
                McHelper.updateBoundingBox(this_);
            }
        }
    }
}
