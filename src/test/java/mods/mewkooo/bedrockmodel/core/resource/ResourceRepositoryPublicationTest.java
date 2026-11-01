package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceRepositoryPublicationTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "publication");
    private static final String MODEL = """
            {"format_version":"1.12.0","minecraft:geometry":[{
              "description":{"texture_width":16,"texture_height":16},
              "bones":[{"name":"root","pivot":[0,0,0]}]}]}
            """;

    @AfterEach
    void clearView() {
        BedrockResources.clear(LogicalSide.SERVER);
    }

    @Test
    void replacementReloadPublishesOnlyAfterItsCompleteGenerationIsApplied() {
        BedrockModelRepositoryImpl previous = repository(List.of());
        apply(previous);
        BedrockModelResource previousResource = BedrockResources.models(LogicalSide.SERVER).getResource(ID);
        assertNotNull(previousResource);

        BedrockModelRepositoryImpl replacement = repository(List.of());
        assertSame(previous, BedrockResources.models(LogicalSide.SERVER),
                "Constructing the new listeners for server /reload must keep the old published repository");
        assertSame(previousResource, BedrockResources.models(LogicalSide.SERVER).getResource(ID));

        apply(replacement);
        assertSame(replacement, BedrockResources.models(LogicalSide.SERVER));
        assertNotNull(BedrockResources.models(LogicalSide.SERVER).getResource(ID));
        assertNotSame(previousResource, BedrockResources.models(LogicalSide.SERVER).getResource(ID));
    }

    @Test
    void failureInReplacementReloadKeepsThePreviousRepositoryPublished() {
        BedrockModelRepositoryImpl previous = repository(List.of());
        apply(previous);
        BedrockModelResource previousResource = BedrockResources.models(LogicalSide.SERVER).getResource(ID);
        BedrockModelRepositoryImpl replacement = repository(List.of(resources -> {
            throw new IllegalStateException("replacement listener failed");
        }));

        assertThrows(IllegalStateException.class, () -> apply(replacement));
        assertSame(previous, BedrockResources.models(LogicalSide.SERVER));
        assertSame(previousResource, BedrockResources.models(LogicalSide.SERVER).getResource(ID));
    }

    private static BedrockModelRepositoryImpl repository(List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners) {
        var loader = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER);
        return new BedrockModelRepositoryImpl(LogicalSide.SERVER,
                Map.of(ID, new BedrockModelEntry(loader, ID, ModelType.TREE,
                        BedrockModelBakeContext::optionsFromAnimations, List.of(), false)),
                listeners, new BedrockAnimationSourceRepository(Map.of()), null);
    }

    private static void apply(BedrockModelRepositoryImpl repository) {
        BedrockModelPOJO pojo = RawResourceLoaders.forLogicalSide(LogicalSide.SERVER).load(
                new ByteArrayInputStream(MODEL.getBytes(StandardCharsets.UTF_8)), BedrockModelPOJO.class);
        repository.apply(Map.of(ID, Optional.of(pojo)), null, InactiveProfiler.INSTANCE);
    }
}
