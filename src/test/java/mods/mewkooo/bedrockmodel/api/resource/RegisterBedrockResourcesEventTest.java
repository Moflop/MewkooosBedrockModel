package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterBedrockResourcesEventTest {
    @Test
    void registersModelAndAnimationFromOneBuilder() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT);
        ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        ResourceLocation animationId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");

        event.treeModel(modelId).animation(animationId).register();

        assertEquals(ModelType.TREE, event.getModelRegistry().get(modelId).kind());
        assertEquals(modelId, event.getModelRegistry().get(modelId).sourceId());
        assertEquals(1, event.getModelRegistry().get(modelId).animationSourceIds().size());
        assertEquals(modelId, event.getModelRegistry().get(modelId).animationSourceIds().get(0));
        assertEquals(animationId, event.getAnimationRegistry().keySet().iterator().next());
    }

    @Test
    void rejectsDuplicateIds() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        event.treeModel(id).register();

        assertThrows(IllegalArgumentException.class, () -> event.treeModel(id).register());
    }
}

