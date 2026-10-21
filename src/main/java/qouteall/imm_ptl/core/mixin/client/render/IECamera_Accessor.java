package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.x: the FOV modifier lives in the Camera (it is ticked per client tick),
 * a new portal camera copies it from the outer camera.
 */
@Mixin(Camera.class)
public interface IECamera_Accessor {
    @Accessor("fovModifier")
    float ip_getFovModifier();
    
    @Accessor("fovModifier")
    void ip_setFovModifier(float value);
    
    @Accessor("oldFovModifier")
    float ip_getOldFovModifier();
    
    @Accessor("oldFovModifier")
    void ip_setOldFovModifier(float value);
}
