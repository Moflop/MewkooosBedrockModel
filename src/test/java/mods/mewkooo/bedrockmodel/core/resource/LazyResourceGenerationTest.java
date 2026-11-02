package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.core.animation.BedrockAnimation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LazyResourceGenerationTest {
    private static final ResourceLocation MODEL_ID = id("lazy_model");
    private static final ResourceLocation ANIMATION_ID = id("lazy_animation");
    @TempDir Path directory;

    @AfterEach
    void clearView() {
        BedrockResources.clear(LogicalSide.SERVER);
    }

    @ParameterizedTest
    @CsvSource({"TREE,true", "TREE,false", "BAKED,true", "BAKED,false"})
    void firstLazyLookupBetweenAnimationAndModelApplyUsesOneCompleteGeneration(ModelType kind, boolean lazyAnimation) {
        Fixture fixture = fixture(kind, lazyAnimation);
        ResourceManager oldResources = resources("old_root", 1);
        ResourceManager newResources = resources("new_root", 9);
        applyBoth(fixture, oldResources);

        // A first lookup here must use both old model bytes and old animation bytes.
        // Only the independent animation reload listener has applied the next generation.
        fixture.animations.apply(fixture.animations.prepare(newResources, InactiveProfiler.INSTANCE),
                newResources, InactiveProfiler.INSTANCE);
        var oldResource = fixture.models.getResource(MODEL_ID);
        assertGeneration(oldResource, "old_root", 1);

        fixture.models.apply(fixture.models.prepare(newResources, InactiveProfiler.INSTANCE),
                newResources, InactiveProfiler.INSTANCE);
        var newResource = fixture.models.getResource(MODEL_ID);
        assertGeneration(newResource, "new_root", 9);
        assertNotSame(oldResource.getAnimation(ANIMATION_ID, "animation.generation"),
                newResource.getAnimation(ANIMATION_ID, "animation.generation"));
        assertGeneration(oldResource, "old_root", 1);
    }

    @ParameterizedTest
    @CsvSource({"TREE,true", "TREE,false", "BAKED,true", "BAKED,false"})
    void firstLazyLookupAfterFailedModelApplyKeepsOldModelAndAnimationGeneration(ModelType kind, boolean lazyAnimation) {
        Fixture fixture = fixture(kind, lazyAnimation);
        ResourceManager oldResources = resources("old_root", 1);
        ResourceManager newResources = resources("new_root", 9);
        applyBoth(fixture, oldResources);
        fixture.failModels.set(true);
        fixture.animations.apply(fixture.animations.prepare(newResources, InactiveProfiler.INSTANCE),
                newResources, InactiveProfiler.INSTANCE);
        assertThrows(IllegalStateException.class,
                () -> fixture.models.apply(fixture.models.prepare(newResources, InactiveProfiler.INSTANCE),
                        newResources, InactiveProfiler.INSTANCE));

        assertGeneration(BedrockResources.models(LogicalSide.SERVER).getResource(MODEL_ID), "old_root", 1);
    }

    @ParameterizedTest
    @CsvSource({"TREE,true", "TREE,false", "BAKED,true", "BAKED,false"})
    void firstLazyLookupAfterOldZipPackClosesReopensItsMatchingGeneration(ModelType kind, boolean lazyAnimation)
            throws IOException {
        Fixture fixture = fixture(kind, lazyAnimation);
        try (MultiPackResourceManager oldResources = zipResources("old", "old_root", 1);
             MultiPackResourceManager newResources = zipResources("new", "new_root", 9)) {
            applyBoth(fixture, oldResources);
            // ReloadableResourceManager closes the preceding MultiPackResourceManager
            // before it starts the next reload. FilePackResources reopens on a later lookup.
            oldResources.close();
            fixture.animations.apply(fixture.animations.prepare(newResources, InactiveProfiler.INSTANCE),
                    newResources, InactiveProfiler.INSTANCE);
            assertGeneration(fixture.models.getResource(MODEL_ID), "old_root", 1);

            fixture.models.apply(fixture.models.prepare(newResources, InactiveProfiler.INSTANCE),
                    newResources, InactiveProfiler.INSTANCE);
            assertGeneration(fixture.models.getResource(MODEL_ID), "new_root", 9);
        }
    }

    private MultiPackResourceManager zipResources(String name, String bone, int duration) throws IOException {
        Path path = directory.resolve(name + ".zip");
        MemoryResources memory = (MemoryResources) resources(bone, duration);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<ResourceLocation, String> entry : memory.files.entrySet()) {
                zip.putNextEntry(new ZipEntry("data/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        PackLocationInfo location = new PackLocationInfo(name, Component.literal(name), PackSource.DEFAULT, Optional.empty());
        var pack = new FilePackResources(location, new FilePackResources.SharedZipFileAccess(path.toFile()), "");
        return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack));
    }

    private static Fixture fixture(ModelType kind, boolean lazyAnimation) {
        var loader = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER);
        var animations = new BedrockAnimationSourceRepository(Map.of(ANIMATION_ID,
                new BedrockAnimationEntry(loader,
                        (file, model) -> new LinkedHashMap<>(BedrockAnimation.createAnimationsByName(file, model)), lazyAnimation)));
        AtomicBoolean failModels = new AtomicBoolean();
        var models = new BedrockModelRepositoryImpl(LogicalSide.SERVER,
                Map.of(MODEL_ID, new BedrockModelEntry(loader, MODEL_ID, kind,
                        BedrockModelBakeContext::optionsFromAnimations, List.of(ANIMATION_ID), true)),
                List.of(resources -> {
                    if (failModels.get()) throw new IllegalStateException("model callback failure");
                }), animations, null);
        return new Fixture(models, animations, failModels);
    }

    private static void applyBoth(Fixture fixture, ResourceManager manager) {
        fixture.animations.apply(fixture.animations.prepare(manager, InactiveProfiler.INSTANCE),
                manager, InactiveProfiler.INSTANCE);
        fixture.models.apply(fixture.models.prepare(manager, InactiveProfiler.INSTANCE),
                manager, InactiveProfiler.INSTANCE);
        assertTrue(fixture.models.getAllResources().isEmpty(), "the model must remain unloaded until the critical first lookup");
    }

    private static void assertGeneration(BedrockModelResource resource, String bone, float duration) {
        assertNotNull(resource);
        var clips = resource.getAnimations(ANIMATION_ID);
        assertNotNull(clips);
        assertEquals(1, clips.size());
        var namedClip = resource.getAnimation(ANIMATION_ID, "animation.generation");
        assertNotNull(namedClip);
        assertSame(clips.getFirst(), namedClip);
        assertNull(resource.getAnimation(ANIMATION_ID, "animation.missing"));
        assertEquals(duration, namedClip.getSpecifiedEndTimeS(),
                "lazy animation bytes must belong to the same generation as lazy model bytes");
        assertTrue(resource.model().getIndex(bone) >= 0, "the matching animated model bone must remain available");
        assertTrue(clips.getFirst().evaluate(0).getBoneTransforms().iterator().hasNext(),
                "the animation must bind to its matching model generation");
    }

    private static ResourceManager resources(String bone, int duration) {
        String model = """
                {"format_version":"1.12.0","minecraft:geometry":[{
                  "description":{"texture_width":16,"texture_height":16},
                  "bones":[{"name":"%s","pivot":[0,0,0]}]}]}
                """.formatted(bone);
        String animation = """
                {"animations":{"animation.generation":{"animation_length":%d,
                  "bones":{"%s":{"position":[0,2,0]}}}}}
                """.formatted(duration, bone);
        return new MemoryResources(Map.of(id("models/bedrock/lazy_model.json"), model,
                id("animations/lazy_animation.json"), animation));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private record Fixture(BedrockModelRepositoryImpl models, BedrockAnimationSourceRepository animations,
                           AtomicBoolean failModels) {}

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
