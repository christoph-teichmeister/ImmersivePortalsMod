package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

/**
 * 26.x: the 1.21.1 hooks of this mixin (translucent sort in renderSectionLayer, camera position in setupRender,
 * clipping uniform update, view area position) target removed methods. What is left is avoiding the translucent
 * resort with the portal camera (the camera position of the section dispatcher is handled in MixinLevelRenderer).
 */
// avoid crashing with sodium
@Mixin(value = LevelRenderer.class, priority = 1100)
public class MixinLevelRenderer_Optional {
    //avoid translucent sort while rendering portal
    @Inject(
        method = "scheduleTranslucentSectionResort",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void onScheduleTranslucentResort(Vec3 cameraPos, CallbackInfo ci) {
        if (PortalRendering.isRendering()) {
            ci.cancel();
        }
    }
}
