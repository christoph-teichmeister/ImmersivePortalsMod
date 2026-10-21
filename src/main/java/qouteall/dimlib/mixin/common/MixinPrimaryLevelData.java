package qouteall.dimlib.mixin.common;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.dimlib.DimensionImpl;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

@Mixin(PrimaryLevelData.class)
public class MixinPrimaryLevelData {
    // disable the warning from the root
    @Inject(method = "worldGenSettingsLifecycle", at = @At("HEAD"), cancellable = true)
    private void onWorldGenSettingsLifecycle(CallbackInfoReturnable<Lifecycle> cir) {
        if (IPConfig.getConfig().suppressExperimentalWarning
            || DimensionImpl.suppressExperimentalWarning
        ) {
            cir.setReturnValue(Lifecycle.stable());
        }
    }
}
