package qouteall.imm_ptl.core.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.2+: creates the dirty states that {@link qouteall.imm_ptl.core.render.ImmPtlViewArea} keeps per section.
 * 26.1.x keeps the dirty flag in the RenderSection itself, this accessor is empty there.
 */
//? if >=26.2 {
@Mixin(net.minecraft.client.SectionUpdateTracker.SectionDirtyState.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
*///?}
public interface IESectionDirtyState_Invoker {
    //? if >=26.2 {
    @org.spongepowered.asm.mixin.gen.Invoker("<init>")
    static net.minecraft.client.SectionUpdateTracker.SectionDirtyState ip_create(
        boolean isDirty, boolean isDirtyFromPlayer, long sectionNode
    ) {
        throw new AssertionError();
    }
    //?}
}
