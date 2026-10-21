package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.ArrayList;
import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay {
    // 26.x: getSystemInformation() is gone, the debug screen is built from debug entries.
    // Append IP's debug text to the right column (the second extractLines call).
    @ModifyArg(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            //? if >=26.3 {
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;ZI)V",
            //?} else {
            /*target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;extractLines(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Ljava/util/List;Z)V",
            *///?}
            ordinal = 1
        ),
        index = 1
    )
    private List<String> onExtractRightLines(List<String> lines) {
        List<String> debugText = RenderStates.collectDebugText();
        List<String> result = new ArrayList<>(lines);

        if (IPGlobal.moveDebugTextToTop) {
            result.addAll(0, debugText);
        }
        else {
            result.addAll(debugText);
        }
        return result;
    }

}
