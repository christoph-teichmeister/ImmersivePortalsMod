package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.2 {
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.miscellaneous.IPortalInitialScreen;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

import java.util.List;
import java.util.function.Function;
//?}

/**
 * 26.2+: the initial screens moved from Minecraft to Gui.
 * In 26.1.x this hook is in MixinMinecraft and this mixin is empty.
 */
@Mixin(Gui.class)
public class MixinGui_InitialScreens {
    //? if >=26.2 {
    @Inject(
        method = "addInitialScreens",
        at = @At("RETURN")
    )
    private void onAddInitialScreens(List<Function<Runnable, Screen>> output, CallbackInfoReturnable<Boolean> cir) {
        IPConfig config = IPConfig.getConfig();
        if (!config.initialScreenShown) {
            output.add(IPortalInitialScreen::new);
        }
    }
    //?}
}
