package qouteall.q_misc_util.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else {
/*import net.minecraft.client.gui.Gui;
*///?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.CustomTextOverlay;

// 26.x: Gui.render(GuiGraphics, DeltaTracker) became extractRenderState(GuiGraphicsExtractor, DeltaTracker);
// since 26.2 the HUD part lives in Hud (and Options.hideGui became Hud.isHidden()).
//? if >=26.2 {
@Mixin(Hud.class)
//?} else {
/*@Mixin(Gui.class)
*///?}
public class MixinGui_Overlay {
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("RETURN")
    )
    private void onRender(
        GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci
    ) {
        //? if >=26.2 {
        boolean hidden = ((Hud) (Object) this).isHidden();
        //?} else {
        /*boolean hidden = this.minecraft.options.hideGui;
        *///?}
        if (!hidden) {
            CustomTextOverlay.render(guiGraphics, deltaTracker);
        }
    }
}
