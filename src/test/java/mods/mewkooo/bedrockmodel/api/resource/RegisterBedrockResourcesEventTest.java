package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void distinctModelsShareOneDefaultAnimationSourceAndKeepTheirAssociations() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        ResourceLocation first = id("first_model");
        ResourceLocation second = id("second_model");
        ResourceLocation animation = id("common.animation");

        event.treeModel(first, id("first.geo")).animation(animation).register();
        RegisterBedrockResourcesEvent.AnimationRegistration source = event.getAnimationRegistry().get(animation);
        event.treeModel(second, id("second.geo")).animation(animation).register();

        assertEquals(2, event.getModelRegistry().size());
        assertEquals(1, event.getAnimationRegistry().size());
        assertSame(source, event.getAnimationRegistry().get(animation));
        assertEquals(List.of(animation), event.getModelRegistry().get(first).animationSourceIds());
        assertEquals(List.of(animation), event.getModelRegistry().get(second).animationSourceIds());
        assertEquals(id("first.geo"), event.getModelRegistry().get(first).sourceId());
        assertEquals(id("second.geo"), event.getModelRegistry().get(second).sourceId());
    }

    @Test
    void reusesExplicitAnimationLoaderRegisteredByAnotherConsumer() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        RawResourceLoader sharedLoader = loader();
        ResourceLocation animation = id("common.animation");
        ResourceLocation otherAnimation = id("other.animation");

        event.legacyModel(id("other_consumer")).animation(animation, sharedLoader).register();
        event.bakedModel(id("this_consumer"))
                .animation(otherAnimation)
                .animation(animation, sharedLoader)
                .register();

        assertEquals(2, event.getAnimationRegistry().size());
        assertSame(sharedLoader, event.getAnimationRegistry().get(animation).rawLoader());
        assertEquals(List.of(otherAnimation, animation),
                event.getModelRegistry().get(id("this_consumer")).animationSourceIds());
    }

    @Test
    void sharedAnimationIsLazyOnlyWhenAllModelsAreLazyRegardlessOfRegistrationOrder() {
        for (boolean firstLazy : new boolean[]{false, true}) {
            for (boolean secondLazy : new boolean[]{false, true}) {
                RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
                ResourceLocation animation = id("common.animation");
                RegisterBedrockResourcesEvent.ModelBuilder first = event.treeModel(id("first")).animation(animation);
                RegisterBedrockResourcesEvent.ModelBuilder second = event.treeModel(id("second")).animation(animation);
                if (firstLazy) first.lazy();
                if (secondLazy) second.lazy();

                first.register();
                second.register();

                assertEquals(firstLazy && secondLazy, event.getAnimationRegistry().get(animation).lazy());
                assertEquals(firstLazy, event.getModelRegistry().get(id("first")).lazy());
                assertEquals(secondLazy, event.getModelRegistry().get(id("second")).lazy());
            }
        }
    }

    @Test
    void incompatibleAnimationLoaderRejectsEntireRegistrationBeforeAnyChanges() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        RawResourceLoader sharedLoader = loader();
        ResourceLocation sharedAnimation = id("shared.animation");
        ResourceLocation conflict = id("conflict.animation");
        event.treeModel(id("existing"))
                .animation(sharedAnimation, sharedLoader)
                .animation(conflict, sharedLoader)
                .lazy()
                .register();
        Map<ResourceLocation, RegisterBedrockResourcesEvent.ModelRegistration> models = Map.copyOf(event.getModelRegistry());
        Map<ResourceLocation, RegisterBedrockResourcesEvent.AnimationRegistration> animations = Map.copyOf(event.getAnimationRegistry());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> event.treeModel(id("rejected"))
                        .animation(id("new.animation"))
                        .animation(sharedAnimation, sharedLoader)
                        .animation(conflict, loader())
                        .register());

        assertTrue(exception.getMessage().contains(conflict.toString()));
        assertTrue(exception.getMessage().contains("different loader instance"));
        assertEquals(models, event.getModelRegistry());
        assertEquals(animations, event.getAnimationRegistry());
        assertFalse(event.getModelRegistry().containsKey(id("rejected")));
        assertFalse(event.getAnimationRegistry().containsKey(id("new.animation")));
        assertTrue(event.getAnimationRegistry().get(sharedAnimation).lazy());
    }

    @Test
    void duplicateModelDoesNotPromoteSharedAnimationOrAddNewSources() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        ResourceLocation model = id("model");
        ResourceLocation animation = id("shared.animation");
        event.treeModel(model).animation(animation).lazy().register();
        Map<ResourceLocation, RegisterBedrockResourcesEvent.ModelRegistration> models = Map.copyOf(event.getModelRegistry());
        Map<ResourceLocation, RegisterBedrockResourcesEvent.AnimationRegistration> animations = Map.copyOf(event.getAnimationRegistry());

        assertThrows(IllegalArgumentException.class, () -> event.treeModel(model)
                .animation(animation).animation(id("new.animation")).register());

        assertEquals(models, event.getModelRegistry());
        assertEquals(animations, event.getAnimationRegistry());
    }

    @Test
    void rejectsDuplicateIds() {
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(Dist.CLIENT, LogicalSide.CLIENT);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        event.treeModel(id).register();

        assertThrows(IllegalArgumentException.class, () -> event.treeModel(id).register());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("examplemod", path);
    }

    private static RawResourceLoader loader() {
        return new RawResourceLoader() {
            @Override
            public <T> T load(InputStream input, Class<T> type) {
                throw new UnsupportedOperationException("Registration test does not read resources");
            }
        };
    }
}

