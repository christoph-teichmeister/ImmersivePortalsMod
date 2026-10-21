package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionEntry;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.mixin.client.render.IEGameRenderer_Accessor;
import qouteall.imm_ptl.core.mixin.client.render.IELevelRenderer_Accessor;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;
//? if >=26.3 {
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Optional;
import java.util.OptionalDouble;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Rendering entities that are halfway through a portal.
 * <p>
 * Such an entity is drawn twice: in its own world, clipped by the portal plane (the part that has not gone through),
 * and in the destination world, transformed by the portal and clipped by the destination plane
 * (the part that has gone through, the "projection").
 * <p>
 * 1.21.1 drew them with a clip plane in the shaders. 26.x draws entities from extracted render states
 * through the feature render dispatcher, with no per-draw clip state and no clip plane in the 26.x pipelines.
 * So these entities are taken out of the normal entity rendering ({@link #shouldRenderEntityNow})
 * and each is submitted to a separate feature dispatcher whose vertices are cut by the planes on the CPU
 * ({@link ClippingVertexConsumer}), then drawn into the main target with the depth of the level,
 * right after the solid things of the level and before the portals ({@link #renderCrossPortalEntities}).
 * Inside a portal view the oblique near plane clips additionally.
 */
@Environment(EnvType.CLIENT)
public class CrossPortalEntityRenderer {
    private static final Minecraft client = Minecraft.getInstance();
    
    //there is no weak hash set
    private static final WeakHashMap<Entity, Object> collidedEntities = new WeakHashMap<>();
    
    // the dispatcher (and buffers) that draws the clipped entities, one entity at a time
    private static @Nullable SubmitNodeStorage submitNodeStorage;
    private static @Nullable FeatureRenderDispatcher featureRenderDispatcher;
    private static @Nullable RenderBuffers renderBuffers;
    private static int lastFrameIndex = -1;
    
    public static void init() {
        IPGlobal.POST_CLIENT_TICK_EVENT.register(CrossPortalEntityRenderer::onClientTick);
        
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(CrossPortalEntityRenderer::cleanUp);
        
        ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanUp());
    }
    
    private static void cleanUp() {
        collidedEntities.clear();
        
        if (featureRenderDispatcher != null) {
            //? if >=26.2 {
            featureRenderDispatcher.close();
            //?}
            featureRenderDispatcher = null;
        }
        if (renderBuffers != null) {
            //? if >=26.2 {
            renderBuffers.close();
            //?}
            renderBuffers = null;
        }
        submitNodeStorage = null;
    }
    
    private static void onClientTick() {
        collidedEntities.entrySet().removeIf(entry -> {
            Entity entity = entry.getKey();
            return entity.isRemoved() || !((IEEntity) entity).ip_isCollidingWithPortal();
        });
    }
    
    public static void onEntityTickClient(Entity entity) {
        if (entity instanceof Portal) {
            return;
        }
        
        if (((IEEntity) entity).ip_isCollidingWithPortal()) {
            collidedEntities.put(entity, null);
        }
    }
    
    private static boolean isCrossPortalRenderingEnabled() {
        if (IrisInterface.invoker.isIrisPresent()) {
            return false;
        }
        return IPGlobal.correctCrossPortalEntityRendering;
    }
    
    private static @Nullable List<PortalCollisionEntry> getPortalCollisions(Entity entity) {
        if (!collidedEntities.containsKey(entity)) {
            return null;
        }
        PortalCollisionHandler collisionHandler = ((IEEntity) entity).ip_getPortalCollisionHandler();
        if (collisionHandler == null || collisionHandler.portalCollisions.isEmpty()) {
            return null;
        }
        return collisionHandler.portalCollisions;
    }
    
    /**
     * Whether the entity is left out of the normal entity rendering and drawn clipped by {@link #renderCrossPortalEntities}.
     * (A vehicle of the player is always rendered by vanilla, see LevelExtractor.isEntityVisible.)
     */
    private static boolean isRenderedSeparately(Entity entity) {
        return isCrossPortalRenderingEnabled() && getPortalCollisions(entity) != null &&
            !(client.player != null && entity.hasIndirectPassenger(client.player));
    }
    
    /**
     * Draws the entities that are halfway through a portal into the level that is being rendered:
     * the entities of this level clipped by the portals they collide with,
     * and the entities of other levels (or this level) that go through a portal into this level.
     * Called in the main pass of the level rendering, after the solid things and before the portals.
     */
    public static void renderCrossPortalEntities() {
        if (!isCrossPortalRenderingEnabled() || collidedEntities.isEmpty() || client.level == null) {
            return;
        }
        
        Camera camera = MyGameRenderer.getMainCamera();
        CameraRenderState cameraState =
            ((IELevelRenderer_Accessor) client.levelRenderer).ip_getLevelRenderState().cameraRenderState;
        
        for (Entity entity : new ArrayList<>(collidedEntities.keySet())) {
            List<PortalCollisionEntry> collisions = getPortalCollisions(entity);
            if (collisions == null || entity.isRemoved() ||
                (client.player != null && entity.hasIndirectPassenger(client.player))
            ) {
                continue;
            }
            
            if (entity.level() == client.level && shouldRenderClippedEntity(entity, camera, cameraState.pos)) {
                List<Plane> outerClippings = new ArrayList<>();
                for (PortalCollisionEntry e : collisions) {
                    Plane outerClipping = e.portal.getPortalShape().getOuterClipping(e.portal.getThisSideState());
                    if (outerClipping != null) {
                        outerClippings.add(outerClipping);
                    }
                }
                renderClipped(entity, null, outerClippings, cameraState);
            }
            
            for (PortalCollisionEntry e : collisions) {
                Portal collidingPortal = e.portal;
                if (!(collidingPortal instanceof Mirror) &&
                    collidingPortal.getDestDim() == client.level.dimension() &&
                    shouldRenderProjection(entity, collidingPortal, cameraState.pos)
                ) {
                    Plane innerClipping = collidingPortal.getInnerClipping();
                    renderClipped(
                        entity, collidingPortal,
                        innerClipping == null ? List.of() : List.of(innerClipping),
                        cameraState
                    );
                }
            }
        }
    }
    
    /**
     * The conditions of LevelExtractor.extractVisibleEntities (except the frustum culling)
     * and of {@link #shouldRenderEntityNow}, for an entity in its own level.
     */
    private static boolean shouldRenderClippedEntity(Entity entity, Camera camera, Vec3 cameraPos) {
        Entity cameraEntity = camera.entity();
        if (entity == cameraEntity && !camera.isDetached() &&
            !(cameraEntity instanceof LivingEntity livingEntity && livingEntity.isSleeping())
        ) {
            return false;
        }
        if (entity instanceof LocalPlayer && cameraEntity != entity) {
            return false;
        }
        if (!entity.shouldRender(cameraPos.x, cameraPos.y, cameraPos.z)) {
            return false;
        }
        return shouldRenderEntityNormallyNow(entity);
    }
    
    //if an entity is in overworld but halfway through a nether portal
    //then it has a projection in nether
    private static boolean shouldRenderProjection(Entity entity, Portal collidingPortal, Vec3 cameraPos) {
        Vec3 entityEyePos = McHelper.getEyePos(entity);
        Vec3 newEyePos = collidingPortal.transformPoint(entityEyePos);
        
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            //correctly rendering it needs two culling planes
            //use some rough check to work around
            if (Portal.isFlippedPortal(renderingPortal, collidingPortal)
                || Portal.isReversePortal(renderingPortal, collidingPortal)
            ) {
                return false;
            }
            
            Vec3 outerCameraPos = MyGameRenderer.getMainCamera().position();
            Plane innerClipping = collidingPortal.getInnerClipping();
            boolean isHidden = innerClipping != null &&
                !innerClipping.isPointOnPositiveSide(outerCameraPos);
            if (renderingPortal != collidingPortal && isHidden) {
                return false;
            }
            
            Vec3 transformedEntityPos = newEyePos.subtract(McHelper.getEyeOffset(entity));
            AABB transformedBoundingBox = McHelper.getBoundingBoxWithMovedPosition(entity, transformedEntityPos);
            if (!PortalManipulation.isOtherSideBoxInside(transformedBoundingBox, renderingPortal)) {
                return false;
            }
        }
        
        if (entity instanceof LocalPlayer) {
            if (!IPGlobal.renderYourselfInPortal) {
                return false;
            }
            
            if (!collidingPortal.getDoRenderPlayer()) {
                return false;
            }
            
            if (client.options.getCameraType().isFirstPerson()) {
                //avoid rendering player too near and block view
                double dis = newEyePos.distanceTo(cameraPos);
                double valve = 0.5 + McHelper.lastTickPosOf(entity).distanceTo(entity.position());
                if (collidingPortal.getScaling() > 1) {
                    valve *= collidingPortal.getScaling();
                }
                if (dis < valve) {
                    return false;
                }
                
                AABB transformedBoundingBox =
                    Helper.transformBox(RenderStates.originalPlayerBoundingBox, collidingPortal::transformPoint);
                if (transformedBoundingBox.contains(CHelper.getCurrentCameraPos())) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Draws the entity, moved by the portal if transformingPortal is not null,
     * with its geometry clipped by the planes (in the coordinates of the level being rendered).
     */
    private static void renderClipped(
        Entity entity, @Nullable Portal transformingPortal, List<Plane> clippingPlanes,
        CameraRenderState cameraState
    ) {
        EntityRenderDispatcher entityRenderDispatcher;
        //? if >=26.2 {
        entityRenderDispatcher = client.levelRenderer.entityRenderDispatcher();
        //?} else {
        /*entityRenderDispatcher = client.getEntityRenderDispatcher();
        *///?}
        
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(
            !entity.level().tickRateManager().isEntityFrozen(entity)
        );
        EntityRenderState state = entityRenderDispatcher.extractEntity(entity, partialTick);
        // the glowing outline is drawn by the level rendering into its outline target
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        
        Vec3 cameraPos = cameraState.pos;
        PoseStack poseStack = new PoseStack();
        Vec3 renderedPos = new Vec3(state.x, state.y, state.z);
        if (transformingPortal != null) {
            renderedPos = transformingPortal.transformPoint(renderedPos);
            // the shadow and the leash are made of positions in the entity's own world
            state.shadowPieces.clear();
            state.leashStates = null;
        }
        poseStack.translate(renderedPos.subtract(cameraPos));
        if (transformingPortal != null) {
            float scaling = (float) transformingPortal.getScaling();
            poseStack.scale(scaling, scaling, scaling);
            if (transformingPortal.getRotation() != null) {
                poseStack.mulPose(new Matrix4f().rotation(transformingPortal.getRotation().toMcQuaternion()));
            }
        }
        
        ensureDispatcher();
        
        FrontClipping.beginEntityClipping(clippingPlanes, cameraPos);
        try {
            entityRenderDispatcher.submit(state, cameraState, 0, 0, 0, poseStack, submitNodeStorage);
            drawSubmittedFeatures();
        }
        finally {
            FrontClipping.endEntityClipping();
        }
    }
    
    private static void ensureDispatcher() {
        if (featureRenderDispatcher == null) {
            renderBuffers = new RenderBuffers(0);
            submitNodeStorage = new SubmitNodeStorage();
            var gameRenderState = ((IEGameRenderer_Accessor) client.gameRenderer).ip_getGameRenderState();
            //? if >=26.2 {
            featureRenderDispatcher = new FeatureRenderDispatcher(
                renderBuffers, client.getModelManager(), client.getAtlasManager(),
                client.font, gameRenderState
            );
            //?} else {
            /*featureRenderDispatcher = new FeatureRenderDispatcher(
                submitNodeStorage, client.getModelManager(), renderBuffers.bufferSource(),
                client.getAtlasManager(), renderBuffers.outlineBufferSource(),
                renderBuffers.crumblingBufferSource(), client.font, gameRenderState
            );
            *///?}
        }
        
        //? if >=26.2 {
        if (lastFrameIndex != RenderStates.frameIndex) {
            // recycle the vertex buffers of the previous frames
            lastFrameIndex = RenderStates.frameIndex;
            renderBuffers.endFrame();
        }
        //?}
    }
    
    /**
     * Prepares the submitted entity (this writes its vertices, through the clipping)
     * and draws it into the main target with the depth of the level.
     */
    private static void drawSubmittedFeatures() {
        //? if >=26.3 {
        try (FeatureRenderDispatcher.PreparedFrame frame = featureRenderDispatcher.prepareFrame(submitNodeStorage)) {
            if (frame.isEmpty()) {
                return;
            }
            RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
            try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                    () -> "Cross portal entity (imm_ptl)",
                    mainTarget.getColorTextureView(),
                    Optional.empty(),
                    mainTarget.getDepthTextureView(),
                    OptionalDouble.empty()
                )) {
                RenderSystem.bindDefaultUniforms(renderPass);
                FeatureRenderDispatcher.renderAllFeatures(renderPass, frame);
            }
        }
        //?} else if >=26.2 {
        /*featureRenderDispatcher.renderAllFeatures(submitNodeStorage);
        *///?} else {
        /*featureRenderDispatcher.renderAllFeatures();
        renderBuffers.bufferSource().endBatch();
        *///?}
    }
    
    public static boolean shouldRenderPlayerDefault() {
        if (!IPGlobal.renderYourselfInPortal) {
            return false;
        }
        if (!WorldRenderInfo.isRendering()) {
            return false;
        }
        LocalPlayer player = client.player;
        assert player != null;
        
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            if (renderingPortal instanceof Mirror) {
                // if the camera pos is too close to the mirror,
                // it will show the inside of the player head.
                // avoid rendering player in this case.
                float width = player.getBbWidth();
                if (renderingPortal.getDistanceToNearestPointInPortal(player.getEyePosition()) < width * 0.8) {
                    return false;
                }
            }
        }
        
        if (client.level == player.level()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Whether the vanilla entity rendering renders the entity.
     */
    public static boolean shouldRenderEntityNow(Entity entity) {
        Validate.notNull(entity);
        if (IrisInterface.invoker.isRenderingShadowMap()) {
            return true;
        }
        if (isRenderedSeparately(entity)) {
            return false;
        }
        return shouldRenderEntityNormallyNow(entity);
    }
    
    private static boolean shouldRenderEntityNormallyNow(Entity entity) {
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            Portal collidingPortal = ((IEEntity) entity).ip_getCollidingPortal();
            
            if (entity instanceof Player && !renderingPortal.getDoRenderPlayer()) {
                return false;
            }
            
            // client colliding portal update is not immediate
            if (collidingPortal != null && !(entity instanceof LocalPlayer)) {
                if (renderingPortal instanceof Portal) {
                    if (!Portal.isReversePortal(collidingPortal, ((Portal) renderingPortal))) {
                        Vec3 cameraPos = MyGameRenderer.getMainCamera().position();
                        
                        boolean isHidden = cameraPos.subtract(collidingPortal.getOriginPos())
                            .dot(collidingPortal.getNormal()) < 0;
                        if (isHidden) {
                            return false;
                        }
                    }
                }
            }
            
            return renderingPortal.isOnDestinationSide(
                getRenderingCameraPos(entity), -0.01
            );
        }
        return true;
    }
    
    public static boolean shouldRenderPlayerNormally(Entity entity) {
        if (!client.options.getCameraType().isFirstPerson()) {
            return true;
        }
        
        if (RenderStates.originalPlayerBoundingBox.contains(CHelper.getCurrentCameraPos())) {
            return false;
        }
        
        double distanceToCamera =
            getRenderingCameraPos(entity)
                .distanceTo(MyGameRenderer.getMainCamera().position());
        //avoid rendering player too near and block view except mirror
        return distanceToCamera > 1 || PortalRendering.isRenderingOddNumberOfMirrors();
    }
    
    public static Vec3 getRenderingCameraPos(Entity entity) {
        if (entity instanceof LocalPlayer) {
            return RenderStates.originalPlayerPos.add(
                McHelper.getEyeOffset(entity)
            );
        }
        return entity.getEyePosition(RenderStates.getPartialTick());
    }
}
