package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.glCullFace;

public class MyRenderHelper {

    public static final Minecraft client = Minecraft.getInstance();

    public static void init() {
        // 26.x: the shaders are referenced by the pipelines in GpuHelper and loaded by the vanilla ShaderManager.
        // The 1.21.1 ShaderInstance loading signal is gone.
    }

    /**
     * Draws the portal area mesh, textured with the framebuffer content at the same screen position.
     * Used by the framebuffer-based (compatibility) renderer.
     */
    public static void drawPortalAreaWithFramebuffer(
        Portal portal,
        RenderTarget textureProvider,
        Matrix4fc modelViewMatrix
    ) {
        GpuHelper.drawTrianglesWithFramebuffer(
            GpuHelper.getMainTarget(), textureProvider, modelViewMatrix,
            vertexConsumer -> ViewAreaRenderer.buildPortalViewAreaTriangles(
                vertexConsumer, Vec3.ZERO, portal, CHelper.getCurrentCameraPos()
            )
        );
    }

    public static void renderScreenTriangle() {
        renderScreenTriangle(255, 255, 255, 255);
    }

    public static void renderScreenTriangle(Vec3 color) {
        renderScreenTriangle(
            (int) (color.x * 255),
            (int) (color.y * 255),
            (int) (color.z * 255),
            255
        );
    }

    /**
     * Fills the screen with the color, without writing depth.
     * (In 1.21.1 the depth/color masks were set by the caller through GL state;
     * in 26.x they are pipeline state, see {@link #renderScreenTriangle(int, int, int, int, int, boolean, GpuHelper.DepthMode)}.)
     */
    public static void renderScreenTriangle(int r, int g, int b, int a) {
        renderScreenTriangle(r, g, b, a, GpuHelper.WRITE_ALL, false, GpuHelper.DepthMode.NONE);
    }

    /**
     * Draws two triangles covering the whole screen at the far plane depth.
     */
    public static void renderScreenTriangle(
        int r, int g, int b, int a,
        int colorWriteMask, boolean writeDepth, GpuHelper.DepthMode depthMode
    ) {
        GpuHelper.drawScreenTriangles(
            GpuHelper.getMainTarget(),
            GpuHelper.getPositionColorPipeline(false, colorWriteMask, writeDepth, depthMode),
            GpuHelper.getFarNdcZ(),
            r, g, b, a
        );
    }

    /**
     * Draws the framebuffer onto the current main render target.
     * 26.x: the blit always covers the whole target; the viewport arguments of the 1.21.1 variants are ignored.
     */
    public static void drawScreenFrameBuffer(
        RenderTarget textureProvider,
        boolean doUseAlphaBlend,
        boolean doEnableModifyAlpha
    ) {
        // this is used for rendering a FB onto screen when the FB contains translucent things
        // the FB should initialize with zero color and zero alpha
        // so the FB content is premultiplied: color = fbColor * 1 + dstColor * (1-contentAlpha)
        GpuHelper.blitToTarget(
            textureProvider, GpuHelper.getMainTarget(), doUseAlphaBlend, doEnableModifyAlpha
        );
    }

    public static void drawFramebuffer(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        float xMin, float xMax, float yMin, float yMax
    ) {
        drawScreenFrameBuffer(textureProvider, doUseAlphaBlend, doEnableModifyAlpha);
    }

    public static void drawFramebufferWithViewport(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        float left, float right, float bottom, float up,
        int viewportWidth, int viewportHeight
    ) {
        drawScreenFrameBuffer(textureProvider, doUseAlphaBlend, doEnableModifyAlpha);
    }

    // TODO port: 26.x render passes always use the full attachment as viewport,
    //  drawing the framebuffer into a sub-rectangle (used by GUI portal rendering) needs a
    //  custom pipeline with a position transform. Draws onto the whole target for now.
    public static void drawFramebufferWithBounds(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int xMin, int xMax, int yMin, int yMax
    ) {
        drawScreenFrameBuffer(textureProvider, doUseAlphaBlend, doEnableModifyAlpha);
    }

    public static void drawFramebufferWithCoordinatesAndDimensions(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int x, int y, int viewportWidth, int viewportHeight
    ) {
        drawScreenFrameBuffer(textureProvider, doUseAlphaBlend, doEnableModifyAlpha);
    }

    // it will remove the light sections that are marked to be removed
    // if not, light data will cause minor memory leak
    // and wrongly remove the light data when the chunks get reloaded to client
    // this should not run before world rendering or the smooth lighting may become abnormal in section edge
    public static void lateUpdateLight() {
        if (!ClientWorldLoader.getIsInitialized()) {
            return;
        }

        ClientWorldLoader.getClientWorlds().forEach(world -> {
            if (!RenderStates.isDimensionRendered(world.dimension())) {
                world.getChunkSource().getLightEngine().runLightUpdates();
            }
        });
    }

    /**
     * Upload the compiled sections of the dimensions that are not rendered this frame.
     * (In 1.21.1 the upload futures of non-rendered dimensions may never complete.)
     */
    public static void earlyRemoteUpload() {
        if (!ClientWorldLoader.getIsInitialized()) {
            return;
        }

        ClientWorldLoader.WORLD_RENDERER_MAP.forEach((dim, worldRenderer) -> {
            if (client.level.dimension() != dim) {
                uploadPendingSections(worldRenderer);
            }
        });
    }

    private static void uploadPendingSections(LevelRenderer worldRenderer) {
        //? if >=26.2 {
        SectionRenderDispatcher dispatcher = worldRenderer.sectionRenderDispatcher();
        if (dispatcher != null) {
            dispatcher.lock();
            try {
                dispatcher.uploadTerrainBuffersToGpu();
            }
            finally {
                dispatcher.unlock();
            }
        }
        //?} else {
        /*SectionRenderDispatcher dispatcher = worldRenderer.getSectionRenderDispatcher();
        if (dispatcher != null) {
            dispatcher.lock();
            try {
                dispatcher.uploadGlobalGeomBuffersToGPU();
            }
            finally {
                dispatcher.unlock();
            }
        }
        *///?}
    }

    /**
     * The cull face is not part of the 26.x pipeline state (only cull on/off is),
     * so the raw GL cull face persists through MC's draws on the OpenGL backend.
     */
    public static void applyMirrorFaceCulling() {
        glCullFace(GL_FRONT);
    }

    public static void recoverFaceCulling() {
        glCullFace(GL_BACK);
    }

    public static void clearAlphaTo1(RenderTarget mcFrameBuffer) {
        GpuHelper.drawScreenTriangles(
            mcFrameBuffer,
            GpuHelper.getPositionColorPipeline(false, 8 /*alpha only*/, false, GpuHelper.DepthMode.NONE),
            0, 0, 0, 0, 255
        );
    }

    /**
     * 26.x: every render pass sets the viewport to its attachment size, nothing to restore.
     */
    public static void restoreViewPort() {
    }

    public static float transformFogDistance(float value) {
        if (!WorldRenderInfo.isFogEnabled()) {
            return value * 23333;
        }

        // just disable fog for fuse-view portals for now
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();

            if (renderingPortal.isFuseView()) {
                return value * 23333;
            }
        }

        // as non-fuse-view portals does not apply scale transformation to modelview,
        // there is no need to transform fog distance (both with and without sodium)

        return value;
    }
}
