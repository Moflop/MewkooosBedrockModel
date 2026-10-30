package mods.mewkooo.bedrockmodel.resource.event;

import mods.mewkooo.bedrockmodel.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.model.baked.BakerOptions;
import mods.mewkooo.bedrockmodel.resource.BedrockAnimationEntry;
import mods.mewkooo.bedrockmodel.resource.BedrockAnimationFactory;
import mods.mewkooo.bedrockmodel.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.resource.BedrockModelEntry;
import mods.mewkooo.bedrockmodel.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.resource.ModelType;
import mods.mewkooo.bedrockmodel.resource.RawResourceLoader;
import mods.mewkooo.bedrockmodel.resource.RawResourceLoaders;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class RegisterBedrockResourcesEvent extends Event implements IModBusEvent {
    private final Map<ResourceLocation, BedrockModelEntry> modelRegistry = new LinkedHashMap<>();
    private final Map<ResourceLocation, BedrockAnimationEntry> animationRegistry = new LinkedHashMap<>();
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> reloadListeners;
    private final Dist dist;

    public RegisterBedrockResourcesEvent(Dist dist) {
        this.reloadListeners = new ArrayList<>();
        this.dist = Objects.requireNonNull(dist, "dist");
    }

    public ModelBuilder legacyModel(ResourceLocation modelId) {
        return legacyModel(modelId, modelId);
    }

    public ModelBuilder legacyModel(ResourceLocation modelId, ResourceLocation sourceId) {
        return legacyModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder legacyModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.LEGACY);
    }

    public ModelBuilder treeModel(ResourceLocation modelId) {
        return treeModel(modelId, modelId);
    }

    public ModelBuilder treeModel(ResourceLocation modelId, ResourceLocation sourceId) {
        return treeModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder treeModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.TREE);
    }

    public ModelBuilder bakedModel(ResourceLocation modelId) {
        return bakedModel(modelId, modelId);
    }

    public ModelBuilder bakedModel(ResourceLocation modelId, ResourceLocation sourceId) {
        return bakedModel(modelId, sourceId, RawResourceLoaders.COMMON_LOADER);
    }

    public ModelBuilder bakedModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader) {
        return new ModelBuilder(modelId, sourceId, modelLoader, ModelType.BAKED);
    }

    public void onReload(Consumer<Map<ResourceLocation, BedrockModelResource>> listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public Dist getDist() {
        return dist;
    }

    public Map<ResourceLocation, BedrockModelEntry> getModelRegistry() {
        return Collections.unmodifiableMap(modelRegistry);
    }

    public Map<ResourceLocation, BedrockAnimationEntry> getAnimationRegistry() {
        return Collections.unmodifiableMap(animationRegistry);
    }

    public List<Consumer<Map<ResourceLocation, BedrockModelResource>>> getReloadListeners() {
        return Collections.unmodifiableList(reloadListeners);
    }

    public final class ModelBuilder {
        private final ResourceLocation modelId;
        private final ResourceLocation sourceId;
        private final RawResourceLoader modelLoader;
        private final ModelType kind;
        private final LinkedHashMap<ResourceLocation, AnimationRegistration> animations = new LinkedHashMap<>();
        private Function<BedrockModelBakeContext, BakerOptions> optionsFactory;
        private boolean lazy;

        private ModelBuilder(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader modelLoader, ModelType kind) {
            this.modelId = Objects.requireNonNull(modelId, "modelId");
            this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
            this.modelLoader = Objects.requireNonNull(modelLoader, "modelLoader");
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        public ModelBuilder lazy() {
            this.lazy = true;
            return this;
        }

        public ModelBuilder options(BakerOptions options) {
            return options(context -> options);
        }

        public ModelBuilder options(Function<BedrockModelBakeContext, BakerOptions> optionsFactory) {
            this.optionsFactory = Objects.requireNonNull(optionsFactory, "optionsFactory");
            return this;
        }

        public ModelBuilder animation(ResourceLocation animationId) {
            return animation(animationId, RawResourceLoaders.COMMON_LOADER, BedrockAnimation::createAnimation);
        }

        public ModelBuilder animation(ResourceLocation animationId, BedrockAnimationFactory factory) {
            return animation(animationId, RawResourceLoaders.COMMON_LOADER, factory);
        }

        public ModelBuilder animation(ResourceLocation animationId, RawResourceLoader animationLoader, BedrockAnimationFactory factory) {
            animations.put(Objects.requireNonNull(animationId, "animationId"), new AnimationRegistration(
                    Objects.requireNonNull(animationLoader, "animationLoader"),
                    Objects.requireNonNull(factory, "factory")));
            return this;
        }

        public void register() {
            if (modelRegistry.containsKey(modelId)) {
                throw new IllegalArgumentException("Bedrock model is already registered: " + modelId);
            }
            for (ResourceLocation animationId : animations.keySet()) {
                if (animationRegistry.containsKey(animationId)) {
                    throw new IllegalArgumentException("Bedrock animation is already registered: " + animationId);
                }
            }

            Function<BedrockModelBakeContext, BakerOptions> factory = optionsFactory != null
                    ? optionsFactory
                    : context -> animations.isEmpty() ? BakerOptions.defaults() : context.optionsFromAnimations();
            modelRegistry.put(modelId, new BedrockModelEntry(
                    modelLoader, sourceId, kind, factory, new ArrayList<>(animations.keySet()), lazy));
            for (Map.Entry<ResourceLocation, AnimationRegistration> entry : animations.entrySet()) {
                AnimationRegistration animation = entry.getValue();
                animationRegistry.put(entry.getKey(), new BedrockAnimationEntry(
                        animation.loader(), animation.factory(), lazy));
            }
        }
    }

    private record AnimationRegistration(
            RawResourceLoader loader,
            BedrockAnimationFactory factory
    ) {
    }
}
