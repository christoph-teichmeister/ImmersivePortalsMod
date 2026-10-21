package qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
public interface IEChunkAccess_AlternateDim {
    // ChunkAccess.noiseChunk no longer exists in 26.3 (the NoiseChunk lives only inside buildTerrain)
    //? if <26.3 {
    /*@Accessor("noiseChunk")
    void ip_setNoiseChunk(NoiseChunk noiseChunk);
    *///?}
}
