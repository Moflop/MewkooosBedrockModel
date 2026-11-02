package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.core.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ServerModelResourcesTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "server_model");
    private static final String MODEL = """
            {"format_version":"1.12.0","minecraft:geometry":[{
              "description":{"texture_width":16,"texture_height":16},
              "bones":[{"name":"root","pivot":[0,0,0],
                "cubes":[{"origin":[0,0,0],"size":[1,1,1],"uv":[0,0]}]}]
            }]}
            """;
    private static final String ANIMATION = """
            {"animations":{"animation.test":{"animation_length":1,
              "bones":{"root":{"position":[0,2,0]}}}}}
            """;

    @AfterEach
    void clearViews() {
        BedrockResources.clear(LogicalSide.CLIENT);
        BedrockResources.clear(LogicalSide.SERVER);
    }

    @Test
    void serverRepositoryLoadsSkeletonAndAssociatedAnimation() {
        for (ModelType kind : List.of(ModelType.TREE, ModelType.BAKED)) {
            var loader = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER);
            var animationSources = new BedrockAnimationSourceRepository(Map.of(ID,
                    new BedrockAnimationEntry(loader,
                            (file, model) -> new LinkedHashMap<>(BedrockAnimation.createAnimationsByName(file, model)), false)));
            var repository = new BedrockModelRepositoryImpl(LogicalSide.SERVER,
                    Map.of(ID, new BedrockModelEntry(loader, ID, kind,
                            BedrockModelBakeContext::optionsFromAnimations, List.of(ID), false)),
                    List.of(), animationSources, null);
            ResourceManager manager = new MemoryResources(Map.of(
                    path("models/bedrock/"), MODEL, path("animations/"), ANIMATION));

            animationSources.apply(animationSources.prepare(manager, InactiveProfiler.INSTANCE),
                    manager, InactiveProfiler.INSTANCE);
            repository.apply(repository.prepare(manager, InactiveProfiler.INSTANCE),
                    manager, InactiveProfiler.INSTANCE);

            var resource = BedrockResources.models(LogicalSide.SERVER).getResource(ID);
            assertNotNull(resource, kind + " server resource");
            assertTrue(resource.model().getIndex("root") >= 0, kind + " must keep the animated bone");
            assertEquals(1, resource.getAnimations(ID).size());
            assertNotNull(resource.getAnimations(ID).getFirst().evaluate(0));
            assertSame(resource.getAnimations(ID).getFirst(), resource.getAnimation(ID, "animation.test"));
            assertSame(resource.getAnimation(ID, "animation.test"),
                    BedrockResources.models(LogicalSide.SERVER).getAnimation(ID, ID, "animation.test"));
            assertNull(resource.getAnimation(ID, "animation.missing"));
            assertNull(BedrockResources.models(LogicalSide.SERVER).getAnimation(ID, ID, "animation.missing"));
        }
    }

    @Test
    void serverParsingKeepsBonesAndSkipsClientGeometryWhileClientParsingKeepsCubes() {
        var server = parse(LogicalSide.SERVER);
        var client = parse(LogicalSide.CLIENT);
        assertEquals("root", server.getGeometryModelNew().getBones()[0].getName());
        assertNull(server.getGeometryModelNew().getBones()[0].getCubes());
        assertEquals(1, client.getGeometryModelNew().getBones()[0].getCubes().length);
    }

    @Test
    void listenerFailureLeavesThePreviousGenerationPublished() {
        AtomicBoolean failListener = new AtomicBoolean();
        var loader = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER);
        var animationSources = new BedrockAnimationSourceRepository(Map.of());
        var repository = new BedrockModelRepositoryImpl(LogicalSide.SERVER,
                Map.of(ID, new BedrockModelEntry(loader, ID, ModelType.TREE,
                        BedrockModelBakeContext::optionsFromAnimations, List.of(), false)),
                List.of(resources -> {
                    if (failListener.get()) throw new IllegalStateException("listener failure");
                }), animationSources, null);
        ResourceManager manager = new MemoryResources(Map.of(path("models/bedrock/"), MODEL));
        var prepared = repository.prepare(manager, InactiveProfiler.INSTANCE);
        repository.apply(prepared, manager, InactiveProfiler.INSTANCE);
        var previous = repository.getResource(ID);
        assertNotNull(previous);

        failListener.set(true);
        assertThrows(IllegalStateException.class,
                () -> repository.apply(Map.of(ID, Optional.empty()), manager, InactiveProfiler.INSTANCE));

        assertSame(previous, repository.getResource(ID));
        assertEquals(Map.of(ID, previous), repository.getAllResources());
    }

    @Test
    void successfulReloadWithMissingAssetRemovesTheModel() {
        var loader = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER);
        var repository = new BedrockModelRepositoryImpl(LogicalSide.SERVER,
                Map.of(ID, new BedrockModelEntry(loader, ID, ModelType.TREE,
                        BedrockModelBakeContext::optionsFromAnimations, List.of(), false)),
                List.of(), new BedrockAnimationSourceRepository(Map.of()), null);
        ResourceManager manager = new MemoryResources(Map.of(path("models/bedrock/"), MODEL));
        repository.apply(repository.prepare(manager, InactiveProfiler.INSTANCE), manager, InactiveProfiler.INSTANCE);
        assertNotNull(repository.getResource(ID));

        ResourceManager emptyManager = new MemoryResources(Map.of());
        repository.apply(repository.prepare(emptyManager, InactiveProfiler.INSTANCE), emptyManager, InactiveProfiler.INSTANCE);

        assertNull(repository.getResource(ID));
        assertTrue(repository.getAllResources().isEmpty());
    }

    @Test
    void concurrentReloadReadersObserveACompletePreviousGeneration() throws Exception {
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "second_model");
        AtomicBoolean pauseSecondBake = new AtomicBoolean();
        AtomicInteger bakeCount = new AtomicInteger();
        CountDownLatch secondBakeStarted = new CountDownLatch(1);
        CountDownLatch finishSecondBake = new CountDownLatch(1);
        var loader = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER);
        var options = (Function<BedrockModelBakeContext, BakerOptions>) context -> {
            if (pauseSecondBake.get() && bakeCount.incrementAndGet() == 2) {
                secondBakeStarted.countDown();
                try {
                    if (!finishSecondBake.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to finish the second model bake");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return BakerOptions.defaults();
        };
        var entries = Map.of(
                ID, new BedrockModelEntry(loader, ID, ModelType.BAKED, options, List.of(), false),
                secondId, new BedrockModelEntry(loader, ID, ModelType.BAKED, options, List.of(), false));
        var repository = new BedrockModelRepositoryImpl(LogicalSide.SERVER, entries, List.of(),
                new BedrockAnimationSourceRepository(Map.of()), null);
        ResourceManager manager = new MemoryResources(Map.of());
        var pojo = Optional.of(parse(LogicalSide.SERVER));
        Map<ResourceLocation, Optional<BedrockModelPOJO>> prepared = Map.of(ID, pojo, secondId, pojo);
        repository.apply(prepared, manager, InactiveProfiler.INSTANCE);
        Map<ResourceLocation, BedrockModelResource> previous = repository.getAllResources();
        assertEquals(2, previous.size());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            pauseSecondBake.set(true);
            var reload = executor.submit(() -> repository.apply(prepared, manager, InactiveProfiler.INSTANCE));
            assertTrue(secondBakeStarted.await(5, TimeUnit.SECONDS));
            Map<ResourceLocation, BedrockModelResource> snapshot = repository.getAllResources();
            assertEquals(2, snapshot.size());
            assertSame(previous.get(ID), snapshot.get(ID));
            assertSame(previous.get(secondId), snapshot.get(secondId));
            finishSecondBake.countDown();
            reload.get(5, TimeUnit.SECONDS);
            assertEquals(2, repository.getAllResources().size());
        } finally {
            finishSecondBake.countDown();
            executor.shutdownNow();
        }
    }

    private static BedrockModelPOJO parse(LogicalSide side) {
        return RawResourceLoaders.forLogicalSide(side).load(
                new ByteArrayInputStream(MODEL.getBytes(StandardCharsets.UTF_8)), BedrockModelPOJO.class);
    }

    private static ResourceLocation path(String prefix) {
        return ResourceLocation.fromNamespaceAndPath(ID.getNamespace(), prefix + ID.getPath() + ".json");
    }

    private record MemoryResources(Map<ResourceLocation, String> files) implements ResourceManager {
        @Override public Set<String> getNamespaces() { return Set.of("test"); }
        @Override public Optional<Resource> getResource(ResourceLocation id) {
            return Optional.ofNullable(files.get(id)).map(json -> new Resource(null,
                    () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
        }
        @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
        @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) { return Map.of(); }
        @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) { return Map.of(); }
        @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
    }
}
