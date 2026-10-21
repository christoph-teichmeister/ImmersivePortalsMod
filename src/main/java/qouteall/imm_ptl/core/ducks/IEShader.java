package qouteall.imm_ptl.core.ducks;

import com.mojang.renderpearl.backend.opengl.Uniform;
import org.jetbrains.annotations.Nullable;

public interface IEShader {
    @Nullable
    Uniform ip_getClippingEquationUniform();
}
