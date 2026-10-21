package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderState.class)
public interface IEGameRenderState_Accessor {
    @Mutable
    @Accessor("levelRenderState")
    void ip_setLevelRenderState(LevelRenderState levelRenderState);
}
