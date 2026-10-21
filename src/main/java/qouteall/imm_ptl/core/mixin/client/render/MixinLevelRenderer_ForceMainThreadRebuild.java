package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.core.render.ForceMainThreadRebuild;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_ForceMainThreadRebuild {
    // the second boolean local of compileSections is "rebuildSync" (the first is "isNearby"),
    // it is only loaded once, in "if (rebuildSync)"
    @ModifyVariable(
        method = "compileSections",
        at = @At("LOAD"),
        ordinal = 1
    )
    private boolean modifyShouldImmediatelyRebuild(boolean originalValue) {
        if (ForceMainThreadRebuild.isCurrentFrameForceMainThreadRebuild()) {
            return true;
        }
        else {
            return originalValue;
        }
    }
}
