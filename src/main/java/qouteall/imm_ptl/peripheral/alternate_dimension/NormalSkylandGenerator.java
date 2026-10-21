package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
//? if >=26.3 {
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import org.jetbrains.annotations.Nullable;
import java.util.Set;
//?} else {
/*import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IEChunkAccess_AlternateDim;
*///?}
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.NotNull;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IEChunkGenerator_AlternateDim;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IENoiseRouterData;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * It extends NoiseBasedChunkGenerator, because in the ChunkMap constructor
 * it uses instanceof to initialize random source.
 */
public class NormalSkylandGenerator extends NoiseBasedChunkGenerator {
    
    public static final MapCodec<NormalSkylandGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RegistryOps.retrieveGetter(Registries.BIOME),
                RegistryOps.retrieveGetter(Registries.DENSITY_FUNCTION),
                RegistryOps.retrieveGetter(Registries.NOISE),
                RegistryOps.retrieveGetter(Registries.NOISE_SETTINGS),
                RegistryOps.retrieveGetter(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
                Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed)
            )
            .apply(instance, NormalSkylandGenerator::create)
    );
    
    private RandomState delegatedRandomState;
    
    private final HolderGetter<Biome> biomeHolderGetter;
    private final HolderGetter<DensityFunction> densityFunctionHolderGetter;
    // the element type of Registries.NOISE is NormalNoise since 26.3
    //? if >=26.3 {
    private final HolderGetter<NormalNoise> noiseParametersHolderGetter;
    //?} else {
    /*private final HolderGetter<NormalNoise.NoiseParameters> noiseParametersHolderGetter;
    *///?}
    private final long seed;
    
    public NormalSkylandGenerator(
        BiomeSource biomeSource,
        Holder<NoiseGeneratorSettings> noiseGeneratorSettings,
        
        NoiseBasedChunkGenerator delegate,
        
        HolderGetter<Biome> biomeHolderGetter,
        HolderGetter<DensityFunction> densityFunctionHolderGetter,
        //? if >=26.3 {
        HolderGetter<NormalNoise> noiseParametersHolderGetter,
        //?} else {
        /*HolderGetter<NormalNoise.NoiseParameters> noiseParametersHolderGetter,
        *///?}
        long seed
    ) {
        super(biomeSource, noiseGeneratorSettings);
        
        this.delegate = delegate;
        this.biomeHolderGetter = biomeHolderGetter;
        this.densityFunctionHolderGetter = densityFunctionHolderGetter;
        this.noiseParametersHolderGetter = noiseParametersHolderGetter;
        this.seed = seed;
        
        //? if >=26.3 {
        this.delegatedRandomState = RandomState.create(
            noiseParametersHolderGetter, seed, delegate.generatorSettings().value()
        );
        //?} else {
        /*this.delegatedRandomState = RandomState.create(
            (delegate.generatorSettings().value()),
            noiseParametersHolderGetter,
            seed
        );
        *///?}
    }
    
    public static NormalSkylandGenerator create(
        HolderGetter<Biome> biomeHolderGetter,
        HolderGetter<DensityFunction> densityFunctionHolderGetter,
        //? if >=26.3 {
        HolderGetter<NormalNoise> noiseParametersHolderGetter,
        //?} else {
        /*HolderGetter<NormalNoise.NoiseParameters> noiseParametersHolderGetter,
        *///?}
        HolderGetter<NoiseGeneratorSettings> noiseGeneratorSettingsHolderGetter,
        HolderGetter<MultiNoiseBiomeSourceParameterList> biomeParamListLookup,
        long seed
    ) {
        Holder.Reference<MultiNoiseBiomeSourceParameterList> overworldBiomeParamList =
            biomeParamListLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        
        MultiNoiseBiomeSource overworldBiomeSource =
            MultiNoiseBiomeSource.createFromPreset(overworldBiomeParamList);
        
        NoiseGeneratorSettings overworldNGS = noiseGeneratorSettingsHolderGetter
            .getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
        
        NoiseGeneratorSettings intrinsicSkylandNGS = noiseGeneratorSettingsHolderGetter
            .getOrThrow(NoiseGeneratorSettings.FLOATING_ISLANDS).value();

//        NoiseGeneratorSettings endNGS = IENoiseGeneratorSettings.ip_end();
        
        DensityFunction slide = IENoiseRouterData.ip_slideEndLike(IENoiseRouterData.ip_getFunction(
            densityFunctionHolderGetter, IENoiseRouterData.get_BASE_3D_NOISE_END()
        ), 0, 128);
        
        //? if >=26.3 {
        NoiseGeneratorSettings usedSkylandNGS = new NoiseGeneratorSettings(
            intrinsicSkylandNGS.noiseSettings(),
            intrinsicSkylandNGS.defaultBlock(),
            intrinsicSkylandNGS.defaultFluid(),
            noNewCaves(densityFunctionHolderGetter, noiseParametersHolderGetter, slide),
            intrinsicSkylandNGS.materialRule(),
            intrinsicSkylandNGS.spawnTarget(),
            0, // overwrite seaLevel
            intrinsicSkylandNGS.disableMobGeneration(),
            intrinsicSkylandNGS.aquifers(),
            intrinsicSkylandNGS.useLegacyRandomSource(),
            intrinsicSkylandNGS.debugFunctions()
        );
        //?} else {
        /*NoiseGeneratorSettings usedSkylandNGS = new NoiseGeneratorSettings(
            intrinsicSkylandNGS.noiseSettings(),
            intrinsicSkylandNGS.defaultBlock(),
            intrinsicSkylandNGS.defaultFluid(),
            noNewCaves(densityFunctionHolderGetter, noiseParametersHolderGetter, slide),
            intrinsicSkylandNGS.surfaceRule(),
            intrinsicSkylandNGS.spawnTarget(),
            0, // overwrite seaLevel
            intrinsicSkylandNGS.disableMobGeneration(),
            intrinsicSkylandNGS.aquifersEnabled(),
            intrinsicSkylandNGS.oreVeinsEnabled(),
            intrinsicSkylandNGS.useLegacyRandomSource()
        );
        *///?}
        
        NoiseBasedChunkGenerator skylandGenerator = new NoiseBasedChunkGenerator(
            overworldBiomeSource, Holder.direct(usedSkylandNGS)
        );
        
        NormalSkylandGenerator result = new NormalSkylandGenerator(
            overworldBiomeSource,
            Holder.direct(overworldNGS),
            skylandGenerator,
            biomeHolderGetter,
            densityFunctionHolderGetter,
            noiseParametersHolderGetter,
            seed
        );
        
        ((IEChunkGenerator_AlternateDim) result).ip_setFeaturesPerStep(
            Suppliers.memoize(
                () -> FeatureSorter.buildFeaturesPerStep(
                    List.copyOf(overworldBiomeSource.possibleBiomes()),
                    holder -> {
                        Biome biome = holder.value();
                        BiomeGenerationSettings bgs = biome.getGenerationSettings();
                        List<HolderSet<PlacedFeature>> features = bgs.features();
                        // TODO modify feature
                        return features;
                    },
                    true
                )
            )
        );
        
        return result;
    }
    
    /**
     * Re-implementation of vanilla NoiseRouterData.noNewCaves (removed after 1.21.1):
     * only temperature, vegetation and the final density are set.
     */
    //? if >=26.3 {
    private static NoiseRouter noNewCaves(
        HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises, DensityFunction slide
    ) {
        DensityFunction shiftX = NoiseRouterData.getFunction(functions, NoiseRouterData.SHIFT_X);
        DensityFunction shiftZ = NoiseRouterData.getFunction(functions, NoiseRouterData.SHIFT_Z);
        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.VEGETATION));
        // cell size 8x4 like vanilla floating islands; the beardifier was implicit in NoiseChunk before 26.3
        DensityFunction finalDensity = DensityFunctions.add(
            NoiseRouterData.postProcess(slide, 8, 4), DensityFunctions.beardifier()
        );
        return new NoiseRouter(
            temperature, vegetation,
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            DensityFunctions.zero(),
            finalDensity
        );
    }
    //?} else {
    /*private static NoiseRouter noNewCaves(
        HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise.NoiseParameters> noises, DensityFunction slide
    ) {
        DensityFunction shiftX = NoiseRouterData.getFunction(functions, NoiseRouterData.SHIFT_X);
        DensityFunction shiftZ = NoiseRouterData.getFunction(functions, NoiseRouterData.SHIFT_Z);
        DensityFunction temperature = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.TEMPERATURE));
        DensityFunction vegetation = DensityFunctions.shiftedNoise2d(shiftX, shiftZ, 0.25, noises.getOrThrow(Noises.VEGETATION));
        DensityFunction finalDensity = NoiseRouterData.postProcess(slide);
        return new NoiseRouter(
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            temperature, vegetation,
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero(),
            DensityFunctions.zero(),
            finalDensity,
            DensityFunctions.zero(), DensityFunctions.zero(), DensityFunctions.zero()
        );
    }
    *///?}
    
    private final NoiseBasedChunkGenerator delegate;
    
    @Override
    protected @NotNull MapCodec<? extends ChunkGenerator> codec() {
        return MAP_CODEC;
    }
    
    //? if >=26.3 {
    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(
        ChunkAccess chunk, Blender blender, RandomState randomState,
        StructureManager structureManager, BiomeManager biomeManager,
        @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes
    ) {
        // TODO port: since 26.3 noise, surface and carvers run together in buildTerrain,
        //  so surface and carvers now use the skyland settings and random state
        //  instead of the overworld ones (1.21.1 only delegated fillFromNoise).
        return delegate.buildTerrain(
            chunk, blender, delegatedRandomState, structureManager, biomeManager,
            carverBiomeRegion, possibleBiomes
        );
    }
    //?} else {
    /*@Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
        Blender blender, RandomState pRandomState,
        StructureManager structureManager, ChunkAccess chunkAccess
    ) {
        ((IEChunkAccess_AlternateDim) chunkAccess).ip_setNoiseChunk(null);
        
        return delegate.fillFromNoise(
            blender, delegatedRandomState, structureManager, chunkAccess
        ).thenApply(c -> {
            ((IEChunkAccess_AlternateDim) c).ip_setNoiseChunk(null);
            return c;
        });
    }
    *///?}
    
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        // filter the mineshaft out
        // cannot use HolderLookup.filterElements because it does not provide id in predicate
        HolderLookup<StructureSet> structureSetLookupDelegate = new HolderLookup<StructureSet>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return structureSetLookup.listElements().filter(
                    holder -> !holder.key().identifier().getPath().equals("mineshafts")
                );
            }
            
            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() {
                return structureSetLookup.listTags();
            }
            
            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> resourceKey) {
                return structureSetLookup.get(resourceKey);
            }
            
            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tagKey) {
                return structureSetLookup.get(tagKey);
            }
        };
        
        return super.createState(structureSetLookupDelegate, randomState, seed);
    }
}
