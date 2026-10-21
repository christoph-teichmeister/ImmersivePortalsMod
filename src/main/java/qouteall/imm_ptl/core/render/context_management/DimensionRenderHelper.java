package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRenderer_Accessor;
import qouteall.q_misc_util.Helper;

/**
 * Per-dimension rendering resources.
 * 26.x: the lightmap is a GPU texture rendered from a {@link LightmapRenderState} each frame.
 */
public class DimensionRenderHelper {
    private static final Minecraft client = Minecraft.getInstance();
    public final Level world;

    public final Lightmap lightmapTexture;

    public final LightmapRenderState lightmapRenderState = new LightmapRenderState();

    public DimensionRenderHelper(Level world) {
        this.world = world;

        if (client.level == world) {
            lightmapTexture = getMainLightmap();
        }
        else {
            lightmapTexture = new Lightmap();
            Helper.log("Created lightmap texture for " + world.dimension().identifier());
        }
    }

    private static Lightmap getMainLightmap() {
        return ((IEGameRenderer_Accessor) client.gameRenderer).ip_getLightmap();
    }

    /**
     * 26.2+: the level extractor that receives the block updates of this level
     * (the one passed to the ClientLevel constructor).
     */
    //? if >=26.2 {
    @Nullable
    public net.minecraft.client.renderer.extract.LevelExtractor getLevelExtractor() {
        if (world instanceof ClientLevel clientLevel) {
            return ((qouteall.imm_ptl.core.mixin.client.accessor.IEClientLevel_Accessor) clientLevel)
                .ip_getLevelExtractor();
        }
        return null;
    }
    //?}

    /**
     * Extracts the lightmap state of this dimension (client.level must be switched to it)
     * and renders it into this dimension's lightmap.
     */
    public void updateLightmap() {
        ((IEGameRenderer_Accessor) client.gameRenderer).ip_getLightmapRenderStateExtractor()
            .extract(lightmapRenderState, 1.0F);
        lightmapTexture.render(lightmapRenderState);
    }

    public void tick() {
        // 26.x: the lightmap flicker is ticked by the vanilla LightmapRenderStateExtractor
    }

    public void cleanUp() {
        if (lightmapTexture != getMainLightmap()) {
            lightmapTexture.close();
        }
    }

}
