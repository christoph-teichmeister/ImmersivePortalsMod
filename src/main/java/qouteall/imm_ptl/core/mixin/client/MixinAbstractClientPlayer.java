package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.ducks.IEAbstractClientPlayer;

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayer implements IEAbstractClientPlayer {
    // 26.x: AbstractClientPlayer no longer has its own clientLevel field,
    // the level lives in Entity.level which IEEntity.ip_setWorld already switches.
    @Override
    public void ip_setClientLevel(ClientLevel clientWorld) {
    }
}
