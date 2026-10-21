package qouteall.imm_ptl.core.ducks;

import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.TicketStorage;

import java.util.List;

public interface IEDistanceManager {
    
    /**
     * 26.x: tickets are no longer generic and live in {@link TicketStorage}.
     * (1.21.1 returned {@code SortedArraySet<Ticket<?>>})
     */
    List<Ticket> portal_getTicketSet(long chunkPos);
    
    TicketStorage ip_getTicketStorage();
}
