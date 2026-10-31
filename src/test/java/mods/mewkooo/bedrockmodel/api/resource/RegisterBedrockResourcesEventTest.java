package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterBedrockResourcesEventTest {
    @Test
    void exposesLogicalServerWhenRunningInsidePhysicalClient() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(
                Dist.CLIENT, LogicalSide.SERVER);

        assertEquals(Dist.CLIENT, event.getDist());
        assertEquals(LogicalSide.SERVER, event.getLogicalSide());
    }

    @Test
    void exposesClientLogicalSideOnClientRepository() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(
                Dist.CLIENT, LogicalSide.CLIENT);

        assertEquals(Dist.CLIENT, event.getDist());
        assertEquals(LogicalSide.CLIENT, event.getLogicalSide());
    }

    @Test
    void exposesDedicatedServerLogicalSideAndKeepsRuntimeLoader() {
        RawResourceLoader loader = new RawResourceLoader() {
            @Override
            public <T> T load(InputStream input, Class<T> type) {
                throw new UnsupportedOperationException("Registration test does not read resources");
            }
        };
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(
                Dist.DEDICATED_SERVER, LogicalSide.SERVER, loader);
        ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        event.treeModel(modelId).animation(modelId).register();

        assertEquals(Dist.DEDICATED_SERVER, event.getDist());
        assertEquals(LogicalSide.SERVER, event.getLogicalSide());
        assertSame(loader, event.getModelRegistry().get(modelId).rawLoader());
        assertSame(loader, event.getAnimationRegistry().get(modelId).rawLoader());
    }

    @Test
    void clientSubscriberCanSkipIntegratedServerAssetsAndReloadCallbacks() {
        ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        Consumer<RegisterBedrockResourcesEvent> subscriber = event -> {
            if (event.getLogicalSide() != LogicalSide.CLIENT) {
                return;
            }
            event.treeModel(modelId).animation(modelId).register();
            event.onReload(resources -> {});
        };
        RegisterBedrockResourcesEvent client = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        RegisterBedrockResourcesEvent integratedServer = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.SERVER);

        subscriber.accept(client);
        subscriber.accept(integratedServer);

        assertEquals(1, client.getModelRegistry().size());
        assertEquals(1, client.getAnimationRegistry().size());
        assertEquals(1, client.getReloadListeners().size());
        assertTrue(integratedServer.getModelRegistry().isEmpty());
        assertTrue(integratedServer.getAnimationRegistry().isEmpty());
        assertTrue(integratedServer.getReloadListeners().isEmpty());
    }

    @Test
    void rejectsNullLogicalSide() {
        assertThrows(NullPointerException.class,
                () -> new RegisterBedrockResourcesEvent(Dist.CLIENT, null));
    }

    @Test
    void registersModelAndAnimationFromOneBuilder() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
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
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        event.treeModel(id).register();

        assertThrows(IllegalArgumentException.class, () -> event.treeModel(id).register());
    }
}

