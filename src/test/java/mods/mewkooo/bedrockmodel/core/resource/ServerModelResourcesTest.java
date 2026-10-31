package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
                            (file, model) -> new ArrayList<>(BedrockAnimation.createAnimation(file, model)), false)));
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
