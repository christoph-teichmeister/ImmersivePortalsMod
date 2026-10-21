package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import qouteall.q_misc_util.Helper;
//? if >=26.3 {
import com.mojang.renderpearl.api.GpuFormat;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///?}

//it will always be the same size as the main frame buffer
public class SecondaryFrameBuffer {
    public TextureTarget fb;
    
    public void prepare() {
        RenderTarget mainFrameBuffer = GpuHelper.getMainTarget();
        int width = mainFrameBuffer.width;
        int height = mainFrameBuffer.height;
        prepare(width, height);
    }
    
    public void prepare(int width, int height) {
        if (fb == null) {
            //? if >=26.3 {
            fb = new TextureTarget(
                "imm_ptl secondary", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT
            );
            //?} else if >=26.2 {
            /*fb = new TextureTarget(
                "imm_ptl secondary", width, height, true, GpuFormat.RGBA8_UNORM
            );
            *///?} else {
            /*fb = new TextureTarget("imm_ptl secondary", width, height, true);
            *///?}
            Helper.log("Secondary Framebuffer init");
        }
        if (width != fb.width ||
            height != fb.height
        ) {
            fb.resize(width, height);
            Helper.log("Secondary Framebuffer resized");
        }
    }
    
    
}
