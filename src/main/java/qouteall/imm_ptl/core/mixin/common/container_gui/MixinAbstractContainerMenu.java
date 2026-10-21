package qouteall.imm_ptl.core.mixin.common.container_gui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@Mixin(AbstractContainerMenu.class)
public class MixinAbstractContainerMenu {
    /**
     * Allow using containers through portals.
     * 1.21.1 wrapped Player.canInteractWithBlock inside the stillValid lambda (method_17696).
     * 26.x uses isWithinBlockInteractionRange inside an unnamed lambda, so this re-evaluates on a failed
     * check instead: same result as "inRange || validateReach" with the block check kept.
     */
    @Inject(
        method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void onStillValid(
        ContainerLevelAccess access, Player player, Block block, CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            return;
        }
        
        boolean reachableThroughPortal = access.evaluate(
            (world, blockPos) -> world.getBlockState(blockPos).is(block)
                && BlockManipulationServer.validateReach(player, world, blockPos),
            false
        );
        if (reachableThroughPortal) {
            cir.setReturnValue(true);
        }
    }
}
