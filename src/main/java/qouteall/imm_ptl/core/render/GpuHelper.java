package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.McHelper;
//? if >=26.3 {
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.BindGroupLayouts;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.BindGroupLayouts;
*///?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
*///?}

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * The 26.x replacement of the immediate-mode drawing (Tesselator, BufferUploader, ShaderInstance) used in 1.21.1.
 * All GPU-API version differences of IP's own drawing are concentrated here.
 * <p>
 * Stencil state is not part of the 26.x pipeline state. The OpenGL backend does not touch it,
 * so IP keeps setting it with raw GL calls (see {@link qouteall.imm_ptl.core.render.renderer.RendererUsingStencil}).
 * That only works on the OpenGL backend, see {@link #isOpenGlBackend()}.
 */
public final class GpuHelper {

    /**
     * The depth value of the far plane.
     * 26.1.x uses the classic depth range, 26.2+ uses reversed-Z with a [0,1] clip range.
     */
    //? if >=26.2 {
    public static final double FAR_DEPTH = 0.0;
    public static final CompareOp DEPTH_TEST_OP = CompareOp.GREATER_THAN_OR_EQUAL;
    //?} else {
    /*public static final double FAR_DEPTH = 1.0;
    public static final CompareOp DEPTH_TEST_OP = CompareOp.LESS_THAN_OR_EQUAL;
    *///?}

    /**
     * NDC z that maps to {@link #FAR_DEPTH}. In 26.1.x the clip range is [-1,1].
     */
    public static final float FAR_NDC_Z = (float) FAR_DEPTH;

    /**
     * The depth textures of the OpenGL backend get a stencil aspect, see MixinGlDevice_Stencil.
     */
    public static final boolean STENCIL_ENABLED = true;
    
    public static final int WRITE_ALL = 15;
    public static final int WRITE_NONE = 0;
    public static final int WRITE_COLOR = 7;

    public enum DepthMode {
        /** no depth test, no depth write */
        NONE,
        /** the normal depth test of the world */
        TEST,
        /** always pass */
        ALWAYS
    }

    private static final Map<Integer, RenderPipeline> positionColorPipelines = new HashMap<>();
    private static final Map<Integer, RenderPipeline> blitPipelines = new HashMap<>();
    @Nullable
    private static RenderPipeline fbInAreaPipeline;

    @Nullable
    private static ProjectionMatrixBuffer identityProjectionBuffer;

    private GpuHelper() {}

    public static boolean isOpenGlBackend() {
        //? if >=26.2 {
        String name = RenderSystem.getDevice().getDeviceInfo().backendName();
        return name != null && name.toLowerCase().contains("gl");
        //?} else {
        /*return true;
        *///?}
    }

    public static RenderTarget getMainTarget() {
        //? if >=26.2 {
        return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        //?} else {
        /*return Minecraft.getInstance().getMainRenderTarget();
        *///?}
    }

    private static Identifier id(String path) {
        return McHelper.newResourceLocation("immersive_portals", path);
    }

    /**
     * A position-color pipeline using the vanilla position_color shader.
     */
    public static RenderPipeline getPositionColorPipeline(
        boolean cull, int colorWriteMask, boolean depthWrite, DepthMode depthMode
    ) {
        int key = (cull ? 1 : 0) | (colorWriteMask << 1) | ((depthWrite ? 1 : 0) << 5) | (depthMode.ordinal() << 6);
        return positionColorPipelines.computeIfAbsent(key, k -> {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(id("pipeline/position_color_" + k))
                .withCull(cull)
                .withColorTargetState(colorTargetState(Optional.empty(), colorWriteMask))
                .withDepthStencilState(depthState(depthMode, depthWrite));
            //? if >=26.2 {
            builder.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
            //?} else {
            /*builder.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES);
            *///?}
            return builder.build();
        });
    }

    /**
     * Full screen triangle that copies a texture.
     */
    private static RenderPipeline getBlitPipeline(boolean blend, boolean writeAlpha) {
        int key = (blend ? 1 : 0) | (writeAlpha ? 2 : 0);
        return blitPipelines.computeIfAbsent(key, k -> {
            Optional<BlendFunction> blendFunction = blend ?
                // the framebuffer content is premultiplied, see MyRenderHelper.drawScreenFrameBuffer
                // ponytail: alpha channel uses the premultiplied func too (1.21.1 kept dst alpha); matters only if the alpha is read back
                Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA) : Optional.empty();
            RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/blit_" + k))
                .withVertexShader("core/screenquad")
                .withFragmentShader("core/blit_screen")
                .withColorTargetState(colorTargetState(blendFunction, writeAlpha ? WRITE_ALL : WRITE_COLOR))
                .withDepthStencilState(Optional.empty());
            //? if >=26.2 {
            builder.withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
            //?} else {
            /*builder.withSampler("InSampler")
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES);
            *///?}
            return builder.build();
        });
    }

    /**
     * Position-color triangles whose fragments sample InSampler at the screen position.
     * Replaces the 1.21.1 portal_draw_fb_in_area shader.
     */
    private static RenderPipeline getFbInAreaPipeline() {
        if (fbInAreaPipeline == null) {
            RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(id("pipeline/draw_fb_in_area"))
                //? if >=26.3 {
                .withFragmentShader(id("core/draw_fb_in_area_spv"))
                //?} else {
                /*.withFragmentShader(id("core/draw_fb_in_area"))
                *///?}
                .withCull(false)
                .withColorTargetState(colorTargetState(Optional.empty(), WRITE_ALL))
                .withDepthStencilState(depthState(DepthMode.TEST, true));
            //? if >=26.2 {
            builder.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
            //?} else {
            /*builder.withUniform("Globals", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)
                .withSampler("InSampler")
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES);
            *///?}
            fbInAreaPipeline = builder.build();
        }
        return fbInAreaPipeline;
    }

    private static ColorTargetState colorTargetState(Optional<BlendFunction> blend, int writeMask) {
        //? if >=26.2 {
        return new ColorTargetState(blend, GpuFormat.RGBA8_UNORM, writeMask);
        //?} else {
        /*return new ColorTargetState(blend, writeMask);
        *///?}
    }

    private static Optional<DepthStencilState> depthState(DepthMode depthMode, boolean depthWrite) {
        return switch (depthMode) {
            case NONE -> Optional.empty();
            case TEST -> Optional.of(new DepthStencilState(DEPTH_TEST_OP, depthWrite));
            case ALWAYS -> Optional.of(new DepthStencilState(CompareOp.ALWAYS_PASS, depthWrite));
        };
    }

    /**
     * A projection buffer containing the identity matrix, for drawing in normalized device coordinates.
     */
    public static GpuBufferSlice getIdentityProjection() {
        if (identityProjectionBuffer == null) {
            identityProjectionBuffer = new ProjectionMatrixBuffer("imm_ptl identity projection");
        }
        return identityProjectionBuffer.getBuffer(new Matrix4f());
    }

    private static GpuBufferSlice writeTransform(Matrix4fc modelView) {
        //? if >=26.2 {
        return RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView));
        //?} else {
        /*return RenderSystem.getDynamicUniforms().writeTransform(
            modelView, new org.joml.Vector4f(1, 1, 1, 1), new org.joml.Vector3f(), new Matrix4f()
        );
        *///?}
    }

    private static RenderPass openPass(
        String label, RenderTarget target, boolean withDepth
    ) {
        GpuTextureView depthView = withDepth ? target.getDepthTextureView() : null;
        //? if >=26.2 {
        return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> label, target.getColorTextureView(), Optional.empty(), depthView, OptionalDouble.empty()
        );
        //?} else {
        /*return RenderSystem.getDevice().createCommandEncoder().createRenderPass(
            () -> label, target.getColorTextureView(), OptionalInt.empty(), depthView, OptionalDouble.empty()
        );
        *///?}
    }

    private static void setPipeline(RenderPass pass, RenderPipeline pipeline) {
        //? if >=26.3 {
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
        //?} else {
        /*pass.setPipeline(pipeline);
        *///?}
    }

    private static void bindSampler(RenderPass pass, String name, GpuTextureView textureView) {
        //? if >=26.3 {
        pass.setUniform(name, textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        //?} else {
        /*pass.bindTexture(name, textureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        *///?}
    }

    private static void drawNonIndexed(RenderPass pass, int vertexCount) {
        //? if >=26.2 {
        pass.draw(vertexCount, 1, 0, 0);
        //?} else {
        /*pass.draw(0, vertexCount);
        *///?}
    }

    /**
     * Opens a render pass on the target (which binds its framebuffer in the OpenGL backend)
     * and runs raw GL code inside it. Used for stencil manipulation.
     */
    public static void withTargetBound(RenderTarget target, Runnable glCode) {
        try (RenderPass pass = openPass("imm_ptl raw gl", target, target.getDepthTextureView() != null)) {
            glCode.run();
        }
    }

    /**
     * Builds and draws a position-color triangle list into the target.
     *
     * @param projectionOverride if not null, used instead of the current RenderSystem projection
     */
    public static void drawPositionColorTriangles(
        RenderTarget target,
        RenderPipeline pipeline,
        Matrix4fc modelView,
        @Nullable GpuBufferSlice projectionOverride,
        Consumer<VertexConsumer> vertexProducer
    ) {
        drawTriangles(target, pipeline, modelView, projectionOverride, null, vertexProducer);
    }

    private static void drawTriangles(
        RenderTarget target,
        RenderPipeline pipeline,
        Matrix4fc modelView,
        @Nullable GpuBufferSlice projectionOverride,
        @Nullable GpuTextureView sampled,
        Consumer<VertexConsumer> vertexProducer
    ) {
        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(256)) {
            //? if >=26.2 {
            BufferBuilder bufferBuilder = new BufferBuilder(
                byteBufferBuilder, PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR
            );
            //?} else {
            /*BufferBuilder bufferBuilder = new BufferBuilder(
                byteBufferBuilder, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR
            );
            *///?}
            vertexProducer.accept(bufferBuilder);

            try (MeshData mesh = bufferBuilder.build()) {
                if (mesh == null) {
                    return;
                }

                int vertexCount = mesh.drawState().vertexCount();

                // ponytail: one GPU buffer per draw, portal area meshes are tiny and few per frame.
                // Use a ring buffer if this shows up in profiling.
                GpuBuffer vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "imm_ptl immediate vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer()
                );

                GpuBufferSlice transform = writeTransform(modelView);

                try (RenderPass pass = openPass("imm_ptl draw", target, target.getDepthTextureView() != null)) {
                    setPipeline(pass, pipeline);
                    RenderSystem.bindDefaultUniforms(pass);
                    if (projectionOverride != null) {
                        pass.setUniform("Projection", projectionOverride);
                    }
                    pass.setUniform("DynamicTransforms", transform);
                    if (sampled != null) {
                        bindSampler(pass, "InSampler", sampled);
                    }
                    //? if >=26.2 {
                    pass.setVertexBuffer(0, vertexBuffer.slice());
                    //?} else {
                    /*pass.setVertexBuffer(0, vertexBuffer);
                    *///?}
                    drawNonIndexed(pass, vertexCount);
                }

                vertexBuffer.close();
            }
        }
    }

    /**
     * Draws two triangles covering the whole screen at the given NDC depth with the given color.
     */
    public static void drawScreenTriangles(
        RenderTarget target, RenderPipeline pipeline, float ndcZ, int r, int g, int b, int a
    ) {
        drawPositionColorTriangles(
            target, pipeline, new Matrix4f(), getIdentityProjection(),
            bufferBuilder -> {
                bufferBuilder.addVertex(1, -1, ndcZ).setColor(r, g, b, a);
                bufferBuilder.addVertex(1, 1, ndcZ).setColor(r, g, b, a);
                bufferBuilder.addVertex(-1, 1, ndcZ).setColor(r, g, b, a);

                bufferBuilder.addVertex(-1, 1, ndcZ).setColor(r, g, b, a);
                bufferBuilder.addVertex(-1, -1, ndcZ).setColor(r, g, b, a);
                bufferBuilder.addVertex(1, -1, ndcZ).setColor(r, g, b, a);
            }
        );
    }

    /**
     * Copies the color of the source onto the target, covering the whole target.
     */
    public static void blitToTarget(
        RenderTarget source, RenderTarget target, boolean blend, boolean writeAlpha
    ) {
        GpuTextureView sourceView = source.getColorTextureView();
        if (sourceView == null) {
            return;
        }
        try (RenderPass pass = openPass("imm_ptl blit", target, false)) {
            setPipeline(pass, getBlitPipeline(blend, writeAlpha));
            RenderSystem.bindDefaultUniforms(pass);
            bindSampler(pass, "InSampler", sourceView);
            drawNonIndexed(pass, 3);
        }
    }

    /**
     * Draws the triangles textured with the source framebuffer at the same screen position.
     */
    public static void drawTrianglesWithFramebuffer(
        RenderTarget target, RenderTarget source, Matrix4fc modelView,
        Consumer<VertexConsumer> vertexProducer
    ) {
        GpuTextureView sourceView = source.getColorTextureView();
        if (sourceView == null) {
            return;
        }
        drawTriangles(target, getFbInAreaPipeline(), modelView, null, sourceView, vertexProducer);
    }

    /**
     * Clears color and depth (to the far plane) of the target.
     */
    public static void clearColorAndDepth(RenderTarget target, float r, float g, float b, float a) {
        if (target.getColorTexture() == null || target.getDepthTexture() == null) {
            return;
        }
        //? if >=26.2 {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            target.getColorTexture(), new org.joml.Vector4f(r, g, b, a), target.getDepthTexture(), FAR_DEPTH
        );
        //?} else {
        /*RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
            target.getColorTexture(), net.minecraft.util.ARGB.colorFromFloat(a, r, g, b),
            target.getDepthTexture(), FAR_DEPTH
        );
        *///?}
    }
    
    public static void clearStencil(RenderTarget target) {
        withTargetBound(target, () -> {
            GL11.glStencilMask(0xFF);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        });
    }

    public static void cleanup() {
        if (identityProjectionBuffer != null) {
            identityProjectionBuffer.close();
            identityProjectionBuffer = null;
        }
    }
}
