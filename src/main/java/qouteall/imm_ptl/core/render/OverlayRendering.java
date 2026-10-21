package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.x: BakedModel is replaced by BlockStateModel/BlockStateModelPart, the overlay quads are submitted
 * as custom geometry of the entity rendering.
 */
@Environment(EnvType.CLIENT)
public class OverlayRendering {
    private static final RandomSource random = RandomSource.create();
    
    
    public static boolean shouldRenderOverlay(Portal portal) {
        if (portal instanceof BreakablePortalEntity breakablePortalEntity) {
            if (breakablePortalEntity.getActualOverlay() != null) {
                return breakablePortalEntity.isInFrontOfPortal(CHelper.getCurrentCameraPos());
            }
        }
        return false;
    }
    
    private static boolean shaderOverlayWarned = false;
    
    public static void onRenderPortalEntity(
        Portal portal,
        PoseStack matrixStack,
        SubmitNodeCollector submitNodeCollector
    ) {
        if (IrisInterface.invoker.isShaders()) {
            if (!shaderOverlayWarned) {
                shaderOverlayWarned = true;
                CHelper.printChat("[Immersive Portals] Portal overlay cannot be rendered with shaders");
            }
            
            return;
        }
        
        if (portal instanceof BreakablePortalEntity) {
            renderBreakablePortalOverlay(
                ((BreakablePortalEntity) portal),
                RenderStates.getPartialTick(),
                matrixStack,
                submitNodeCollector
            );
        }
    }
    
    public static List<BakedQuad> getQuads(BlockStateModel model, BlockState blockState, Vec3 portalNormal) {
        Direction facing = Direction.getApproximateNearest(portalNormal.x, portalNormal.y, portalNormal.z);
        
        List<BlockStateModelPart> parts = new ArrayList<>();
        random.setSeed(0);
        model.collectParts(random, parts);
        
        List<BakedQuad> result = new ArrayList<>();
        
        for (BlockStateModelPart part : parts) {
            result.addAll(part.getQuads(facing));
            result.addAll(part.getQuads(null));
        }
        
        if (result.isEmpty()) {
            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    result.addAll(part.getQuads(direction));
                }
            }
        }
        
        return result;
    }
    
    /**
     * {@link net.minecraft.client.renderer.entity.FallingBlockRenderer}
     */
    private static void renderBreakablePortalOverlay(
        BreakablePortalEntity portal,
        float partialTick,
        PoseStack matrixStack,
        SubmitNodeCollector submitNodeCollector
    ) {
        BreakablePortalEntity.OverlayInfo overlay = portal.getActualOverlay();
        
        if (overlay == null) {
            return;
        }
        
        BlockState blockState = overlay.blockState();
        
        if (blockState == null) {
            return;
        }
        
        BlockPortalShape blockPortalShape = portal.blockPortalShape;
        if (blockPortalShape == null) {
            return;
        }
        
        BlockStateModel model = Minecraft.getInstance().getModelManager()
            .getBlockStateModelSet().get(blockState);
        
        List<BakedQuad> quads = getQuads(model, blockState, portal.getNormal());
        
        for (BakedQuad quad : quads) {
            SodiumInterface.invoker.markSpriteActive(quad.materialInfo().sprite());
        }
        
        Vec3 offset = portal.getNormal().scale(overlay.offset());
        Vec3 pos = portal.position();
        
        QuadInstance quadInstance = new QuadInstance();
        quadInstance.setColor(((int) (overlay.opacity() * 255) << 24) | 0xFFFFFF);
        quadInstance.setLightCoords(14680304);//packed light value
        quadInstance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        
        submitNodeCollector.submitCustomGeometry(
            matrixStack, Sheets.translucentBlockItemSheet(),
            (pose, buffer) -> {
                PoseStack poseStack = new PoseStack();
                poseStack.last().set(pose);
                
                poseStack.translate(offset.x, offset.y, offset.z);
                
                for (BlockPos blockPos : blockPortalShape.area) {
                    poseStack.pushPose();
                    poseStack.translate(
                        blockPos.getX() - pos.x, blockPos.getY() - pos.y, blockPos.getZ() - pos.z
                    );
                    
                    if (overlay.rotation() != null) {
                        poseStack.mulPose(new org.joml.Matrix4f().rotation(overlay.rotation().toMcQuaternion()));
                    }
                    
                    for (BakedQuad quad : quads) {
                        buffer.putBakedQuad(poseStack.last(), quad, quadInstance);
                    }
                    
                    poseStack.popPose();
                }
            }
        );
    }
}
