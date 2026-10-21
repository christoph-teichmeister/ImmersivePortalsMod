package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

@Mixin(ThrownEnderpearl.class)
public class MixinThrownEnderPearl {
    /**
     * 26.x: vanilla onHit teleports the owner with ServerPlayer.teleport(TeleportTransition), which also
     * changes dimension conventionally. 1.21.1 ImmPtl injected before discard() and did the cross-dimension
     * teleport itself; here the vanilla teleport is replaced by the ImmPtl one when the dimension differs.
     */
    @WrapOperation(
        method = "onHit",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;"
        )
    )
    private ServerPlayer wrapOwnerTeleport(
        ServerPlayer serverPlayer, TeleportTransition transition, Operation<ServerPlayer> original
    ) {
        if (serverPlayer.level() != transition.newLevel() && !serverPlayer.isSleeping()) {
            Helper.log("Doing cross-dimensional ender pearl teleportation");
            ServerTeleportationManager.teleportEntityGeneral(
                serverPlayer,
                transition.position(),
                transition.newLevel()
            );
            return serverPlayer;
        }
        return original.call(serverPlayer, transition);
    }
}
