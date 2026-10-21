package qouteall.imm_ptl.core.platform_specific.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;

@Mixin(ServerPlayer.class)
public class MixinServerPlayerEntity_MA {
    // 26.x: changeDimension(DimensionTransition) and teleportTo(ServerLevel, DDDFF) both became
    // teleport(TeleportTransition) (teleportTo delegates to it), which also handles same-dimension teleports.
    @Inject(
        method = "Lnet/minecraft/server/level/ServerPlayer;teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
        at = @At("HEAD")
    )
    private void onTeleportByVanilla(
        TeleportTransition transition, CallbackInfoReturnable<ServerPlayer> cir
    ) {
        ServerPlayer this_ = (ServerPlayer) (Object) this;
        
        if (!this_.isRemoved() && this_.level() != transition.newLevel()) {
            onBeforeDimensionTravel(this_);
        }
    }
    
    private static void onBeforeDimensionTravel(ServerPlayer player) {
        CustomPortalGenManager customPortalGenManager =
            IPPerServerInfo.of(player.level().getServer()).customPortalGenManager;
        
        if (customPortalGenManager != null) {
            customPortalGenManager.onBeforeConventionalDimensionChange(player);
            ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(player);
            
            ServerTaskList.of(player.level().getServer()).addTask(() -> {
                customPortalGenManager.onAfterConventionalDimensionChange(player);
                return true;
            });
        }
    }
}
