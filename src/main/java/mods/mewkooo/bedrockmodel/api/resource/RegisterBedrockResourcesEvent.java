package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
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

/** Public registration entry point; reload repositories and parsed JSON stay in core.resource. */
public final class RegisterBedrockResourcesEvent extends Event implements IModBusEvent {
    private final Map<ResourceLocation, ModelRegistration> modelRegistry = new LinkedHashMap<>();
    private final Map<ResourceLocation, AnimationRegistration> animationRegistry = new LinkedHashMap<>();
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> reloadListeners = new ArrayList<>();
    private final Dist dist;
    private final RawResourceLoader defaultLoader;

    /** Constructor useful for tests that only inspect registrations. */
    public RegisterBedrockResourcesEvent(Dist dist) {
        this(dist, new RawResourceLoader() {
            @Override
            public <T> T load(java.io.InputStream input, Class<T> type) {
                throw new IllegalStateException("A runtime resource loader was not supplied");
            }
        });
    }

    /** Used by the mod bootstrap to supply the side-aware JSON loader. */
    public RegisterBedrockResourcesEvent(Dist dist, RawResourceLoader defaultLoader) {
        this.dist = Objects.requireNonNull(dist, "dist");
        this.defaultLoader = Objects.requireNonNull(defaultLoader, "defaultLoader");
    }

    public ModelBuilder legacyModel(ResourceLocation modelId) { return legacyModel(modelId, modelId); }
    public ModelBuilder legacyModel(ResourceLocation modelId, ResourceLocation sourceId) { return legacyModel(modelId, sourceId, defaultLoader); }
    public ModelBuilder legacyModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader loader) { return new ModelBuilder(modelId, sourceId, loader, ModelType.LEGACY); }
    public ModelBuilder treeModel(ResourceLocation modelId) { return treeModel(modelId, modelId); }
    public ModelBuilder treeModel(ResourceLocation modelId, ResourceLocation sourceId) { return treeModel(modelId, sourceId, defaultLoader); }
    public ModelBuilder treeModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader loader) { return new ModelBuilder(modelId, sourceId, loader, ModelType.TREE); }
    public ModelBuilder bakedModel(ResourceLocation modelId) { return bakedModel(modelId, modelId); }
    public ModelBuilder bakedModel(ResourceLocation modelId, ResourceLocation sourceId) { return bakedModel(modelId, sourceId, defaultLoader); }
    public ModelBuilder bakedModel(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader loader) { return new ModelBuilder(modelId, sourceId, loader, ModelType.BAKED); }

    public void onReload(Consumer<Map<ResourceLocation, BedrockModelResource>> listener) { reloadListeners.add(Objects.requireNonNull(listener, "listener")); }
    public Dist getDist() { return dist; }
    public Map<ResourceLocation, ModelRegistration> getModelRegistry() { return Collections.unmodifiableMap(modelRegistry); }
    public Map<ResourceLocation, AnimationRegistration> getAnimationRegistry() { return Collections.unmodifiableMap(animationRegistry); }
    public List<Consumer<Map<ResourceLocation, BedrockModelResource>>> getReloadListeners() { return Collections.unmodifiableList(reloadListeners); }

    public final class ModelBuilder {
        private final ResourceLocation modelId;
        private final ResourceLocation sourceId;
        private final RawResourceLoader modelLoader;
        private final ModelType kind;
        private final LinkedHashMap<ResourceLocation, RawResourceLoader> animations = new LinkedHashMap<>();
        private Function<BedrockModelBakeContext, BakerOptions> optionsFactory;
        private boolean lazy;

        private ModelBuilder(ResourceLocation modelId, ResourceLocation sourceId, RawResourceLoader loader, ModelType kind) {
            this.modelId = Objects.requireNonNull(modelId, "modelId");
            this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
            this.modelLoader = Objects.requireNonNull(loader, "loader");
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        public ModelBuilder lazy() { lazy = true; return this; }
        public ModelBuilder options(BakerOptions options) { return options(context -> options); }
        public ModelBuilder options(Function<BedrockModelBakeContext, BakerOptions> factory) { optionsFactory = Objects.requireNonNull(factory, "factory"); return this; }
        public ModelBuilder animation(ResourceLocation animationId) { return animation(animationId, defaultLoader); }
        public ModelBuilder animation(ResourceLocation animationId, RawResourceLoader loader) {
            animations.put(Objects.requireNonNull(animationId, "animationId"), Objects.requireNonNull(loader, "loader"));
            return this;
        }
        public void register() {
            if (modelRegistry.containsKey(modelId)) throw new IllegalArgumentException("Bedrock model is already registered: " + modelId);
            for (ResourceLocation animationId : animations.keySet()) if (animationRegistry.containsKey(animationId)) throw new IllegalArgumentException("Bedrock animation is already registered: " + animationId);
            Function<BedrockModelBakeContext, BakerOptions> factory = optionsFactory == null ? BedrockModelBakeContext::optionsFromAnimations : optionsFactory;
            modelRegistry.put(modelId, new ModelRegistration(modelLoader, sourceId, kind, factory, List.copyOf(animations.keySet()), lazy));
            for (Map.Entry<ResourceLocation, RawResourceLoader> animation : animations.entrySet()) animationRegistry.put(animation.getKey(), new AnimationRegistration(animation.getValue(), lazy));
        }
    }

    public record ModelRegistration(RawResourceLoader rawLoader, ResourceLocation sourceId, ModelType kind,
                                    Function<BedrockModelBakeContext, BakerOptions> optionsFactory,
                                    List<ResourceLocation> animationSourceIds, boolean lazy) {
        public ModelRegistration { animationSourceIds = List.copyOf(animationSourceIds); }
    }
    public record AnimationRegistration(RawResourceLoader rawLoader, boolean lazy) { }
}


