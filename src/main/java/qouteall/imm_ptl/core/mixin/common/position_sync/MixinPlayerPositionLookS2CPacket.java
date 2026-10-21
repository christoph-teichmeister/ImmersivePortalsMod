package qouteall.imm_ptl.core.mixin.common.position_sync;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;

@Mixin(ClientboundPlayerPositionPacket.class)
public class MixinPlayerPositionLookS2CPacket implements IEPlayerPositionLookS2CPacket {
    @Unique
    private ResourceKey<Level> playerDimension;
    
    @Override
    public ResourceKey<Level> ip_getPlayerDimension() {
        return playerDimension;
    }
    
    @Override
    public void ip_setPlayerDimension(ResourceKey<Level> dimension) {
        playerDimension = dimension;
    }
    
    /**
     * 26.x: the packet is a record encoded by STREAM_CODEC (no write(FriendlyByteBuf) / buf constructor).
     * Wrap the codec to append the player dimension.
     * This replaces the 1.21.1 write() inject here and the read in client MixinClientboundPlayerPositionPacket.
     */
    @ModifyExpressionValue(
        method = "<clinit>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/codec/StreamCodec;composite(Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lnet/minecraft/network/codec/StreamCodec;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function3;)Lnet/minecraft/network/codec/StreamCodec;"
        )
    )
    private static StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> ip_wrapStreamCodec(
        StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> original
    ) {
        return StreamCodec.of(
            (buf, packet) -> {
                original.encode(buf, packet);
                buf.writeResourceKey(((IEPlayerPositionLookS2CPacket) (Object) packet).ip_getPlayerDimension());
            },
            buf -> {
                ClientboundPlayerPositionPacket packet = original.decode(buf);
                // decoding only happens on client
                if (ImmPtlNetworkConfig.doesServerHaveImmPtl()) {
                    ((IEPlayerPositionLookS2CPacket) (Object) packet)
                        .ip_setPlayerDimension(buf.readResourceKey(Registries.DIMENSION));
                }
                return packet;
            }
        );
    }
}
