package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.Map;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.my_util.SignalArged;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Vanilla use a 2D array to store the chunk references on client and cannot store the chunks that are far from player.
 * This use map to store the chunk references, to eliminate such limitation.
 * (Two maps, one for main thread and one for other threads)
 */
@Environment(EnvType.CLIENT)
@IPVanillaCopy
public class ImmPtlClientChunkMap extends ClientChunkCache {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // the most chunk accesses are from the main thread,
    // so we use two maps to reduce synchronization.
    // the main thread accesses this map, without synchronization
    protected final Long2ObjectOpenHashMap<LevelChunk> chunkMapForMainThread =
        new Long2ObjectOpenHashMap<>();
    // other threads read this map, with synchronization
    protected final Long2ObjectOpenHashMap<LevelChunk> chunkMapForOtherThreads =
        new Long2ObjectOpenHashMap<>();
    
    public final Thread mainThread;
    
    public static final SignalArged<LevelChunk> clientChunkLoadSignal = new SignalArged<>();
    public static final SignalArged<LevelChunk> clientChunkUnloadSignal = new SignalArged<>();
    
    public ImmPtlClientChunkMap(ClientLevel clientWorld, int loadDistance) {
        super(clientWorld, 1);
        // the chunk array is unused. make it small by passing 1 as load distance to super constructor
        
        mainThread = ((IEMinecraftClient) Minecraft.getInstance()).ip_getRunningThread();
    }
    
    @Override
    public void drop(ChunkPos chunkPos) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        
//        LOGGER.info("unload {} {}", level, chunkPos);
        
        LevelChunk chunk = chunkMapForMainThread.get(chunkPos.pack());
        if (chunk != null) {
            modifyChunkMap(chunkMap -> {
                chunkMap.remove(chunkPos.pack());
            });
            
            //? if >=26.2 {
            onChunkRemoved(chunk);
            //?}
            O_O.postClientChunkUnloadEvent(chunk);
            this.level.unload(chunk);
            SodiumInterface.invoker.onClientChunkUnloaded(level, chunkPos.x(), chunkPos.z());
            clientChunkUnloadSignal.emit(chunk);
        }
    }
    
    public <T> T readChunkMap(Function<Long2ObjectOpenHashMap<LevelChunk>, T> func) {
        if (Thread.currentThread() == mainThread) {
            return func.apply(chunkMapForMainThread);
        }
        else {
            synchronized (chunkMapForOtherThreads) {
                return func.apply(chunkMapForOtherThreads);
            }
        }
    }
    
    public void modifyChunkMap(Consumer<Long2ObjectOpenHashMap<LevelChunk>> func) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        func.accept(chunkMapForMainThread);
        synchronized (chunkMapForOtherThreads) {
            func.accept(chunkMapForOtherThreads);
        }
    }
    
    @Override
    public LevelChunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean create) {
        return readChunkMap(chunkMap -> {
            LevelChunk chunk = chunkMap.get(ChunkPos.pack(x, z));
            if (chunk != null) {
                return chunk;
            }
            
            return create ? this.emptyChunk : null;
        });
    }
    
    public boolean isChunkLoaded(int x, int z) {
        return readChunkMap(chunkMap -> {
            return chunkMap.containsKey(ChunkPos.pack(x, z));
        });
    }
    
    @Override
    public void replaceBiomes(int x, int z, FriendlyByteBuf friendlyByteBuf) {
        Validate.isTrue(Thread.currentThread() == mainThread);
        
        long chunkPosLong = ChunkPos.pack(x, z);
        
        LevelChunk worldChunk = chunkMapForMainThread.get(chunkPosLong);
        ChunkPos chunkPos = new ChunkPos(x, z);
        if (worldChunk == null) {
            LOGGER.error("Trying to replace biomes for missing chunk {} {}", x, z);
        }
        else {
            worldChunk.replaceBiomes(friendlyByteBuf);
        }
    }
    
    @Override
    //? if >=26.3 {
    public LevelChunk replaceWithPacketData(
        int x, int z, ClientboundLevelChunkPacketData chunkData
    ) {
        Consumer<LevelChunk> loader = c -> c.replaceWithPacketData(x, z, chunkData);
    //?} else {
    /*public LevelChunk replaceWithPacketData(
        int x, int z,
        FriendlyByteBuf buf, Map<Heightmap.Types, long[]> heightmaps,
        Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> consumer
    ) {
        Consumer<LevelChunk> loader = c -> c.replaceWithPacketData(buf, heightmaps, consumer);
    *///?}
        Validate.isTrue(Thread.currentThread() == mainThread);
        
        long chunkPosLong = ChunkPos.pack(x, z);
        LevelChunk worldChunk = chunkMapForMainThread.get(chunkPosLong);
        if (worldChunk == null) {
            worldChunk = new LevelChunk(this.level, new ChunkPos(x, z));
            loadChunkDataFromPacket(worldChunk, loader);
            
            LevelChunk worldChunkToPut = worldChunk; // lambda can only capture effectively final variables
            modifyChunkMap(chunkMap -> {
                chunkMap.put(chunkPosLong, worldChunkToPut);
            });
            //? if >=26.2 {
            onChunkAdded(worldChunk);
            //?}
        }
        else {
            loadChunkDataFromPacket(worldChunk, loader);
            //? if >=26.2 {
            refreshEmptySections(worldChunk);
            //?}
        }
        
        this.level.onChunkLoaded(new ChunkPos(x, z));
        O_O.postClientChunkLoadEvent(worldChunk);
        SodiumInterface.invoker.onClientChunkLoaded(level, x, z);
        clientChunkLoadSignal.emit(worldChunk);
        
//        LOGGER.info("load {} {} {}", level, x, z);
        
        return worldChunk;
    }
    
    /**
     * {@link net.minecraft.core.IdMap#byIdOrThrow(int)}
     * {@link net.minecraft.world.level.chunk.LinearPalette#read(FriendlyByteBuf)}
     */
    private void loadChunkDataFromPacket(
        LevelChunk worldChunk,
        Consumer<LevelChunk> loader
    ) {
        try {
            loader.accept(worldChunk);
        }
        catch (Exception e) {
            LOGGER.error(
                "Error deserializing chunk packet {} {}",
                worldChunk.getLevel().dimension().identifier(),
                worldChunk.getPos(),
                e
            );
            CHelper.printChat(
                Component
                    .literal("Failed to deserialize chunk packet. %s %s %s".formatted(
                        worldChunk.getLevel().dimension().identifier(),
                        worldChunk.getPos().x(), worldChunk.getPos().z()
                    ))
                    .append(Component.literal(" Report issue:"))
                    .append(McHelper.getLinkText(O_O.getIssueLink()))
                    .withStyle(ChatFormatting.RED)
            );
            
            throw new RuntimeException(e);
        }
    }
    
    public List<LevelChunk> getCopiedChunkList() {
        return readChunkMap((chunkMap) -> {
            return Arrays.asList(chunkMap.values().toArray(new LevelChunk[0]));
        });
    }
    
    @Override
    public void updateViewCenter(int x, int z) {
        // do nothing
    }
    
    @Override
    public void updateViewRadius(int r) {
        // do nothing
    }
    
    @Override
    public String gatherStats() {
        return "Client Chunks (ImmPtl) " + getLoadedChunksCount();
    }
    
    @Override
    public int getLoadedChunksCount() {
        return readChunkMap(chunkMap -> {
            return chunkMap.size();
        });
    }
    
    @Override
    public void onLightUpdate(LightLayer lightType, SectionPos chunkSectionPos) {
        //? if >=26.2 {
        ClientWorldLoader.getWorld(level.dimension()); // make sure the per-dimension objects exist
        ClientWorldLoader.LEVEL_EXTRACTOR_MAP.get(level.dimension())
            .setSectionDirty(chunkSectionPos.x(), chunkSectionPos.y(), chunkSectionPos.z());
        //?} else {
        /*ClientWorldLoader.getWorldRenderer(level.dimension())
            .setSectionDirty(chunkSectionPos.x(), chunkSectionPos.y(), chunkSectionPos.z());
        *///?}
    }
    

    //? if >=26.2 {
    // 26.2+: the vanilla occlusion graph only sees the chunks and empty sections reported in these sets
    // (the vanilla storage of this class is unused). Double buffered like ClientChunkCache.Storage,
    // the LevelExtractor reads them and flips them once per frame.
    private final LongOpenHashSet[] addedEmptySections = {new LongOpenHashSet(), new LongOpenHashSet()};
    private final LongOpenHashSet[] removedEmptySections = {new LongOpenHashSet(), new LongOpenHashSet()};
    private final LongOpenHashSet[] addedLoadedChunks = {new LongOpenHashSet(), new LongOpenHashSet()};
    private final LongOpenHashSet[] removedLoadedChunks = {new LongOpenHashSet(), new LongOpenHashSet()};
    private int updatingSetsIndex = 0;
    
    @Override
    public LongOpenHashSet addedEmptySections() {
        return addedEmptySections[updatingSetsIndex];
    }
    
    @Override
    public LongOpenHashSet removedEmptySections() {
        return removedEmptySections[updatingSetsIndex];
    }
    
    @Override
    public LongOpenHashSet addedLoadedChunks() {
        return addedLoadedChunks[updatingSetsIndex];
    }
    
    @Override
    public LongOpenHashSet removedLoadedChunks() {
        return removedLoadedChunks[updatingSetsIndex];
    }
    
    @Override
    public void flipUpdateTrackingSets() {
        updatingSetsIndex = (updatingSetsIndex + 1) % 2;
        addedEmptySections[updatingSetsIndex].clear();
        removedEmptySections[updatingSetsIndex].clear();
        addedLoadedChunks[updatingSetsIndex].clear();
        removedLoadedChunks[updatingSetsIndex].clear();
    }
    
    @Override
    public void onSectionEmptinessChanged(int sectionX, int sectionY, int sectionZ, boolean empty) {
        markSectionEmpty(SectionPos.asLong(sectionX, sectionY, sectionZ), empty);
    }
    
    private void onChunkAdded(LevelChunk chunk) {
        long chunkNode = chunk.getPos().pack();
        removedLoadedChunks[updatingSetsIndex].remove(chunkNode);
        addedLoadedChunks[updatingSetsIndex].add(chunkNode);
        refreshEmptySections(chunk);
    }
    
    private void onChunkRemoved(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkNode = chunkPos.pack();
        addedLoadedChunks[updatingSetsIndex].remove(chunkNode);
        removedLoadedChunks[updatingSetsIndex].add(chunkNode);
        for (int i = 0; i < chunk.getSections().length; i++) {
            long sectionNode = SectionPos.asLong(chunkPos.x(), chunk.getSectionYFromSectionIndex(i), chunkPos.z());
            //? if >=26.3 {
            markSectionEmpty(sectionNode, true);
            //?} else {
            /*markSectionEmpty(sectionNode, false); // 26.2 drops the sections of unloaded chunks from the empty set
            *///?}
        }
    }
    
    private void refreshEmptySections(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            long sectionNode = SectionPos.asLong(chunkPos.x(), chunk.getSectionYFromSectionIndex(i), chunkPos.z());
            markSectionEmpty(sectionNode, sections[i].hasOnlyAir());
        }
    }
    
    private void markSectionEmpty(long sectionNode, boolean empty) {
        (empty ? removedEmptySections : addedEmptySections)[updatingSetsIndex].remove(sectionNode);
        (empty ? addedEmptySections : removedEmptySections)[updatingSetsIndex].add(sectionNode);
    }
    //?}
}
