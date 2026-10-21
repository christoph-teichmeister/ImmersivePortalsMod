package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.server.level.DistanceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Executor;

// 26.x: the "tickets" map moved to TicketStorage (see ducks.IEDistanceManager#ip_getTicketStorage)
// and "ticketThrottler" (ChunkTaskPriorityQueueSorter) became ThrottlingChunkTaskDispatcher.
// Both accessors had no users and were removed.
@Mixin(DistanceManager.class)
public interface IEDistanceManager {
    @Accessor("mainThreadExecutor")
    Executor ip_getMainThreadExecutor();
}
