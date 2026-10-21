package qouteall.imm_ptl.core.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.2+: LevelExtractor fills the LevelRenderState that LevelRenderer renders.
 * 26.1.x has no LevelExtractor (the LevelRenderer extracts itself), this accessor is empty there.
 */
//? if >=26.2 {
@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
*///?}
public interface IELevelExtractor_Accessor {
    //? if >=26.2 {
    @org.spongepowered.asm.mixin.gen.Accessor("levelRenderState")
    net.minecraft.client.renderer.state.level.LevelRenderState ip_getLevelRenderState();
    
    @org.spongepowered.asm.mixin.Mutable
    @org.spongepowered.asm.mixin.gen.Accessor("levelRenderState")
    void ip_setLevelRenderState(net.minecraft.client.renderer.state.level.LevelRenderState arg);
    
    @org.spongepowered.asm.mixin.gen.Accessor("levelRenderer")
    net.minecraft.client.renderer.LevelRenderer ip_getLevelRenderer();
    //?}
}
