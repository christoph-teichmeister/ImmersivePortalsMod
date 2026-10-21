package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * 1.21.1 kept the fog color in static fields of FogRenderer and swapped them per dimension.
 * 26.x computes the fog color from the environment attributes of the camera each frame
 * (FogRenderer.setupFog), so there is no per-dimension static state to swap anymore.
 * This only remembers the fog color of the level that is being rendered
 * (taken from the frame clear color, see MixinLevelRenderer).
 */
@SuppressWarnings("SpellCheckingInspection")
public class FogRendererContext {
    private static Vec3 currentFogColor = Vec3.ZERO;

    public static final Supplier<Vec3> getCurrentFogColor = () -> currentFogColor;

    public static void init() {
    }

    public static void setCurrentFogColor(float r, float g, float b) {
        currentFogColor = new Vec3(r, g, b);
    }

    public static void update() {
    }

    public static void onPlayerTeleport(ResourceKey<Level> from, ResourceKey<Level> to) {
    }

}
