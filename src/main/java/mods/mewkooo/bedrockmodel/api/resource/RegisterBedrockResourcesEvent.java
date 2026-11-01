package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.event.IModBusEvent;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Registers resources and reload callbacks for one logical-side repository.
 * Use {@link #getLogicalSide()} to distinguish client assets from server data;
 * {@link #getDist()} is CLIENT for both repositories in singleplayer.
 * The logical side must be supplied explicitly when constructing the event.
 */
public final class RegisterBedrockResourcesEvent extends Event implements IModBusEvent {
    private final Map<ResourceLocation, ModelRegistration> modelRegistry = new LinkedHashMap<>();
    private final Map<ResourceLocation, AnimationRegistration> animationRegistry = new LinkedHashMap<>();
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> reloadListeners = new ArrayList<>();
    private final Dist dist;
    private final LogicalSide logicalSide;
    private final RawResourceLoader defaultLoader;

    /** Constructor useful for tests that only inspect registrations. */
    public RegisterBedrockResourcesEvent(Dist dist, LogicalSide logicalSide) {
        this(dist, logicalSide, new RawResourceLoader() {
            @Override
            public <T> T load(InputStream input, Class<T> type) {
                throw new IllegalStateException("A runtime resource loader was not supplied");
            }
        });
    }

    /** Used by the reload bridge to provide the physical and logical sides. */
    public RegisterBedrockResourcesEvent(Dist dist, LogicalSide logicalSide, RawResourceLoader defaultLoader) {
        this.dist = Objects.requireNonNull(dist, "dist");
        this.logicalSide = Objects.requireNonNull(logicalSide, "logicalSide");
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

    /** Registers a callback for this event's logical-side repository only. */
    public void onReload(Consumer<Map<ResourceLocation, BedrockModelResource>> listener) { reloadListeners.add(Objects.requireNonNull(listener, "listener")); }

    /** Returns the physical distribution; an integrated server also reports {@link Dist#CLIENT}. */
    public Dist getDist() { return dist; }

    /** Returns the repository side; an integrated server reports {@link LogicalSide#SERVER}. */
    public LogicalSide getLogicalSide() { return logicalSide; }
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

        /** Defers this model and its animation sources unless another model requires a shared source eagerly. */
        public ModelBuilder lazy() { lazy = true; return this; }
        public ModelBuilder options(BakerOptions options) { return options(context -> options); }
        public ModelBuilder options(Function<BedrockModelBakeContext, BakerOptions> factory) { optionsFactory = Objects.requireNonNull(factory, "factory"); return this; }
        public ModelBuilder animation(ResourceLocation animationId) { return animation(animationId, defaultLoader); }
        /**
         * Associates an animation source with this model. Other models may share the same source ID
         * only with the same loader instance. A shared source is lazy only when all its models are lazy.
         */
        public ModelBuilder animation(ResourceLocation animationId, RawResourceLoader loader) {
            animations.put(Objects.requireNonNull(animationId, "animationId"), Objects.requireNonNull(loader, "loader"));
            return this;
        }
        public void register() {
            if (modelRegistry.containsKey(modelId)) throw new IllegalArgumentException("Bedrock model is already registered: " + modelId);
            // Validate every source before adding the model or promoting any shared source to eager.
            for (Map.Entry<ResourceLocation, RawResourceLoader> animation : animations.entrySet()) {
                AnimationRegistration existing = animationRegistry.get(animation.getKey());
                if (existing != null && existing.rawLoader() != animation.getValue()) {
                    throw new IllegalArgumentException("Bedrock animation is already registered with a different loader instance: " + animation.getKey());
                }
            }
            Function<BedrockModelBakeContext, BakerOptions> factory = optionsFactory == null ? BedrockModelBakeContext::optionsFromAnimations : optionsFactory;
            modelRegistry.put(modelId, new ModelRegistration(modelLoader, sourceId, kind, factory, List.copyOf(animations.keySet()), lazy));
            for (Map.Entry<ResourceLocation, RawResourceLoader> animation : animations.entrySet()) {
                AnimationRegistration existing = animationRegistry.get(animation.getKey());
                if (existing == null || existing.lazy() && !lazy) {
                    animationRegistry.put(animation.getKey(), new AnimationRegistration(animation.getValue(), lazy));
                }
            }
        }
    }

    public record ModelRegistration(RawResourceLoader rawLoader, ResourceLocation sourceId, ModelType kind,
                                    Function<BedrockModelBakeContext, BakerOptions> optionsFactory,
                                    List<ResourceLocation> animationSourceIds, boolean lazy) {
        public ModelRegistration { animationSourceIds = List.copyOf(animationSourceIds); }
    }
    public record AnimationRegistration(RawResourceLoader rawLoader, boolean lazy) { }
}


