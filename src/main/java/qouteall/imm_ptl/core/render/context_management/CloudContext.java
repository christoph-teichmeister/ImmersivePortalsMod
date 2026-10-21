package qouteall.imm_ptl.core.render.context_management;

/**
 * 1.21.1 reused the cloud vertex buffer between portal views (MixinLevelRenderer_Clouds).
 * TODO port: the 26.x CloudRenderer builds its mesh per LevelRenderer, the optimization is not ported.
 */
@Deprecated
public class CloudContext {
    public static void init() {
    }
}
