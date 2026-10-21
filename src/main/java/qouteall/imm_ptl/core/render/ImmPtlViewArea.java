package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
//? if >=26.2 {
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.renderer.SectionOcclusionGraph;
//?}

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * A ViewArea whose render sections do not depend on the camera position.
 * The sections are stored by column (chunk x/z), so the sections around a portal view
 * stay alive and compiled while the player camera is elsewhere.
 * <p>
 * Vanilla code only sees the sections within the render distance around the (outer) camera,
 * like the vanilla ViewArea. While rendering a portal view ({@link WorldRenderInfo#isRendering()})
 * every section is reachable. The index of a section (used by the vanilla occlusion graph)
 * is computed from its position like in the vanilla ring buffer storage.
 * <p>
 * 26.x removed the per-camera section arrays (and 26.2 replaced ViewArea with a rotating storage),
 * so the 1.21.1 "preset" arrays are gone.
 */
@Environment(EnvType.CLIENT)
public class ImmPtlViewArea extends ViewArea {

    public static class Column {
        public long mark = 0;
        public RenderSection[] sections;

        public Column(RenderSection[] sections) {
            this.sections = sections;
        }
    }

    private final SectionRenderDispatcher factory;
    private final Long2ObjectOpenHashMap<Column> columnMap = new Long2ObjectOpenHashMap<>();

    public final int minSectionY;
    public final int endSectionY;

    private final int radius;

    private SectionPos center = SectionPos.of(Integer.MAX_VALUE / 2, 0, Integer.MAX_VALUE / 2);

    private boolean isAlive = true;

    //? if >=26.2 {
    private final SectionOcclusionGraph occlusionGraph;
    //?} else {
    /*private final LevelRenderer levelRendererRef;
    *///?}

    public static void init() {
        ImmPtlClientChunkMap.clientChunkUnloadSignal.connect(section -> {
            ResourceKey<Level> dimension = section.getLevel().dimension();

            LevelRenderer worldRenderer = ClientWorldLoader.WORLD_RENDERER_MAP.get(dimension);

            if (worldRenderer != null) {
                ViewArea viewArea = ((IEWorldRenderer) worldRenderer).ip_getBuiltChunkStorage();
                if (viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                    immPtlViewArea.onChunkUnload(section.getPos().x(), section.getPos().z());
                }
            }
        });

        IPGlobal.POST_CLIENT_TICK_EVENT.register(() -> {
            if (ClientWorldLoader.getIsInitialized()) {
                for (ClientLevel world : ClientWorldLoader.getClientWorlds()) {
                    LevelRenderer worldRenderer =
                        ClientWorldLoader.getWorldRenderer(world.dimension());
                    ViewArea viewArea = ((IEWorldRenderer) worldRenderer).ip_getBuiltChunkStorage();
                    if (viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                        immPtlViewArea.tick();
                    }
                }
            }
        });
    }

    //? if >=26.2 {
    public ImmPtlViewArea(
        SectionRenderDispatcher sectionRenderDispatcher,
        int minY, int maxY, int minSectionY, int maxSectionY,
        int renderDistance,
        SectionOcclusionGraph sectionOcclusionGraph
    ) {
        // radius 0: the vanilla storage only allocates one column, the sections are managed here
        super(sectionRenderDispatcher, minY, maxY, minSectionY, maxSectionY, 0, sectionOcclusionGraph);
        this.factory = sectionRenderDispatcher;
        this.occlusionGraph = sectionOcclusionGraph;
        this.minSectionY = minSectionY;
        this.endSectionY = maxSectionY + 1;
        this.radius = renderDistance;
    }
    //?} else {
    /*public ImmPtlViewArea(
        SectionRenderDispatcher sectionRenderDispatcher,
        Level level,
        int renderDistance,
        LevelRenderer levelRenderer
    ) {
        super(sectionRenderDispatcher, level, renderDistance, levelRenderer);
        this.factory = sectionRenderDispatcher;
        this.levelRendererRef = levelRenderer;
        this.minSectionY = level.getMinSectionY();
        this.endSectionY = level.getMaxSectionY() + 1;
        this.radius = renderDistance;
    }

    @Override
    protected void createSections(SectionRenderDispatcher sectionRenderDispatcher) {
        // the vanilla code reads the length of this array, the sections are managed in columns
        this.sections = new RenderSection[this.sectionGridSizeX * this.sectionGridSizeY * this.sectionGridSizeZ];
    }

    @Override
    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        RenderSection section = getRenderSection(SectionPos.asLong(sectionX, sectionY, sectionZ));
        if (section != null) {
            section.setDirty(playerChanged);
        }
    }
    *///?}

    private int gridSizeXZ() {
        return radius * 2 + 1;
    }

    private int height() {
        return endSectionY - minSectionY;
    }

    //? if >=26.2 {
    @Override
    public int size() {
        return gridSizeXZ() * gridSizeXZ() * height();
    }

    @Override
    public int getViewDistance() {
        return radius;
    }

    @Override
    public boolean repositionCamera(SectionPos cameraSectionPos) {
        if (cameraSectionPos.equals(center)) {
            return false;
        }
        center = cameraSectionPos;
        occlusionGraph.invalidate();
        return true;
    }

    @Override
    public SectionRenderDispatcher.@Nullable RenderSection getRenderSectionAt(BlockPos pos) {
        return getRenderSection(SectionPos.asLong(pos));
    }

    /**
     * Replaces the camera-centered SectionUpdateTracker, see MixinLevelExtractor.
     * Like the tracker, the outer view only gets the sections around its camera
     * ({@link #getRenderSection} only returns those when the section is compiled later in the frame).
     */
    public SectionUpdateTracker.@Nullable SectionDirtyState getDirtyState(long sectionNode, SectionPos cameraSectionPos) {
        int sx = SectionPos.x(sectionNode);
        int sz = SectionPos.z(sectionNode);
        if (!WorldRenderInfo.isRendering() &&
            (Math.abs(sx - cameraSectionPos.x()) > radius || Math.abs(sz - cameraSectionPos.z()) > radius)) {
            return null;
        }
        RenderSection section = rawGet(sx, SectionPos.y(sectionNode), sz);
        return section == null ? null : ((IERenderSection) section).ip_getDirtyState();
    }

    // a section that does not exist yet is dirty when it gets created
    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged) {
        RenderSection section = rawGet(sectionX, sectionY, sectionZ);
        if (section != null) {
            ((IERenderSection) section).ip_getDirtyState().setDirty(playerChanged);
        }
    }
    //?} else {
    /*@Override
    public void repositionCamera(SectionPos cameraSectionPos) {
        center = cameraSectionPos;
        levelRendererRef.getSectionOcclusionGraph().invalidate();
    }

    @Override
    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSectionAt(BlockPos pos) {
        return getRenderSection(SectionPos.asLong(pos));
    }
    *///?}

    @Override
    public SectionPos getCameraSectionPos() {
        return center;
    }

    /**
     * NOTE it may be accessed from another thread (the vanilla occlusion graph)
     */
    @Override
    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSection(long sectionNode) {
        int sx = SectionPos.x(sectionNode);
        int sy = SectionPos.y(sectionNode);
        int sz = SectionPos.z(sectionNode);

        if (sy < minSectionY || sy >= endSectionY) {
            return null;
        }

        if (!WorldRenderInfo.isRendering() || !Minecraft.getInstance().isSameThread()) {
            // like vanilla, only the sections around the camera are visible to vanilla code
            if (Math.abs(sx - center.x()) > radius || Math.abs(sz - center.z()) > radius) {
                return null;
            }
        }

        Column column;
        synchronized (columnMap) {
            column = provideColumn(ChunkPos.pack(sx, sz));
        }
        RenderSection result = column.sections[sy - minSectionY];

        ((IERenderSection) result).portal_setIndex(getIndex(sx, sy, sz));
        return result;
    }

    private int getIndex(int sx, int sy, int sz) {
        int size = gridSizeXZ();
        return (Math.floorMod(sz, size) * height() + (sy - minSectionY)) * size + Math.floorMod(sx, size);
    }

    @Override
    public void releaseAllBuffers() {
        synchronized (columnMap) {
            for (Column column : columnMap.values()) {
                for (RenderSection section : column.sections) {
                    section.reset();
                }
            }
            columnMap.clear();
        }

        isAlive = false;
    }

    public Column provideColumn(long chunkPos) {
        return columnMap.computeIfAbsent(chunkPos, this::createColumn);
    }

    private Column createColumn(long chunkPos) {
        RenderSection[] array = new RenderSection[height()];

        int sectionX = ChunkPos.getX(chunkPos);
        int sectionZ = ChunkPos.getZ(chunkPos);

        for (int offsetCY = 0; offsetCY < array.length; offsetCY++) {
            int sy = offsetCY + minSectionY;
            array[offsetCY] = factory.new RenderSection(
                getIndex(sectionX, sy, sectionZ),
                SectionPos.asLong(sectionX, sy, sectionZ)
            );
        }

        return new Column(array);
    }

    private void tick() {
        if (!isAlive) {
            return;
        }

        ClientLevel worldClient = Minecraft.getInstance().level;
        if (worldClient != null) {
            if (GcMonitor.isMemoryNotEnough()) {
                if (worldClient.getGameTime() % 3 == 0) {
                    purge();
                }
            }
            else {
                if (worldClient.getGameTime() % 213 == 66) {
                    purge();
                }
            }
        }
    }

    /**
     * Release the columns that are out of the camera range and not used by portal rendering recently.
     */
    private void purge() {
        Profiler.get().push("my_built_section_storage_purge");

        long timeThreshold = Helper.secondToNano(GcMonitor.isMemoryNotEnough() ? 3 : 20);

        long currentTime = System.nanoTime();

        List<RenderSection> toDelete = new ArrayList<>();

        synchronized (columnMap) {
            columnMap.long2ObjectEntrySet().removeIf(entry -> {
                Column column = entry.getValue();
                long chunkPos = entry.getLongKey();

                boolean inCameraRange =
                    Math.abs(ChunkPos.getX(chunkPos) - center.x()) <= radius &&
                        Math.abs(ChunkPos.getZ(chunkPos) - center.z()) <= radius;

                if (inCameraRange) {
                    column.mark = currentTime;
                    return false;
                }

                boolean shouldRemove = currentTime - column.mark > timeThreshold;
                if (shouldRemove) {
                    toDelete.addAll(List.of(column.sections));
                }

                return shouldRemove;
            });
        }

        if (!toDelete.isEmpty()) {
            ArrayDeque<RenderSection> queue = new ArrayDeque<>(toDelete);
            IPGlobal.PRE_GAME_RENDER_TASK_LIST.addTask(() -> {
                if (queue.isEmpty()) {
                    return true;
                }

                int num = 0;
                while (!queue.isEmpty() && num < 100) {
                    RenderSection builtChunk = queue.poll();
                    builtChunk.reset();
                    num++;
                }

                return false;
            });
        }

        Profiler.get().pop();
    }

    public int getManagedSectionNum() {
        return columnMap.size() * height();
    }

    public String getDebugString() {
        return String.format(
            "Built Section Storage Columns:%s",
            columnMap.size()
        );
    }

    public int getRadius() {
        return radius;
    }

    public boolean isRegionActive(int cxStart, int czStart, int cxEnd, int czEnd) {
        for (int cx = cxStart; cx <= cxEnd; cx++) {
            for (int cz = czStart; cz <= czEnd; cz++) {
                if (columnMap.containsKey(ChunkPos.pack(cx, cz))) {
                    return true;
                }
            }
        }

        return false;
    }

    public void onChunkUnload(int sectionX, int sectionZ) {
        long sectionPos = ChunkPos.pack(sectionX, sectionZ);
        Column column = columnMap.get(sectionPos);
        if (column != null) {
            for (RenderSection builtChunk : column.sections) {
                ((IERenderSection) builtChunk).portal_fullyReset();
            }
        }
    }

    /**
     * Get the section and mark the column as used (it won't be purged soon).
     */
    @Nullable
    public RenderSection rawFetch(int cx, int cy, int cz, long timeMark) {
        if (cy < minSectionY || cy >= endSectionY) {
            return null;
        }

        long l = ChunkPos.pack(cx, cz);
        Column column;
        synchronized (columnMap) {
            column = provideColumn(l);
        }

        column.mark = timeMark;

        int yOffset = cy - minSectionY;

        return column.sections[yOffset];
    }

    @Nullable
    public RenderSection rawGet(int cx, int cy, int cz) {
        if (cy < minSectionY || cy >= endSectionY) {
            return null;
        }

        long l = ChunkPos.pack(cx, cz);
        Column column;
        synchronized (columnMap) {
            column = columnMap.get(l);
        }

        if (column == null) {
            return null;
        }

        int yOffset = cy - minSectionY;

        return column.sections[yOffset];
    }
}
