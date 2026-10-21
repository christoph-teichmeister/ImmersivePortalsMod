package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
public interface IEMinecraft_Accessor {
    // 26.2+: the level extractor belongs to the current level renderer,
    // it is switched together with it during portal rendering
    //? if >=26.2 {
    @org.spongepowered.asm.mixin.Mutable
    @org.spongepowered.asm.mixin.gen.Accessor("levelExtractor")
    void ip_setLevelExtractor(net.minecraft.client.renderer.extract.LevelExtractor extractor);
    //?}
}
