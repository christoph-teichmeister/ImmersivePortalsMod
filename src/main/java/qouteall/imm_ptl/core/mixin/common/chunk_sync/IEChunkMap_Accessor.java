package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface IEChunkMap_Accessor {
    /**
     * 26.x: ChunkMap.getChunks() is gone (it returned visibleChunkMap.values()).
     * Was {@code @Invoker("getChunks") Iterable<ChunkHolder> ip_getChunks()};
     * callers use {@code ip_getVisibleChunkMap().values()}.
     */
    @Accessor("visibleChunkMap")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> ip_getVisibleChunkMap();
}
