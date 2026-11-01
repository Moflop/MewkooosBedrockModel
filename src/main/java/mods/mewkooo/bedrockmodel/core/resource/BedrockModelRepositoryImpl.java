package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelAccess;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.fml.LogicalSide;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;

final class BedrockModelRepositoryImpl extends SimplePreparableReloadListener<Map<ResourceLocation, Optional<BedrockModelPOJO>>>
        implements BedrockModelAccess {
    

    private final Map<ResourceLocation, BedrockModelEntry> entries;
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners;
    private final BedrockModelFactory modelFactory;
    private final LogicalSide logicalSide;
    private volatile Generation generation;

    private static final class Generation {
        private final ConcurrentMap<ResourceLocation, Optional<BedrockModelPOJO>> pojoCache;
        private final ConcurrentMap<ResourceLocation, Optional<BedrockModelResource>> resourceCache;
        @Nullable
        private final ResourceManager resourceManager;
        private final BedrockModelFactory modelFactory;

        private Generation(Map<ResourceLocation, Optional<BedrockModelPOJO>> pojos,
                           Map<ResourceLocation, Optional<BedrockModelResource>> resources,
                           @Nullable ResourceManager resourceManager, BedrockModelFactory modelFactory) {
            this.pojoCache = new ConcurrentHashMap<>(pojos);
            this.resourceCache = new ConcurrentHashMap<>(resources);
            this.resourceManager = resourceManager;
            this.modelFactory = modelFactory;
        }
    }

    BedrockModelRepositoryImpl(LogicalSide logicalSide,
                           Map<ResourceLocation, BedrockModelEntry> entries,
                           List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners,
                           BedrockAnimationSourceRepository animationSources,
                           Function<BedrockModelPOJO, ? extends BedrockModel> legacyModelFactory) {
        this.entries = Map.copyOf(entries);
        this.listeners = List.copyOf(listeners);
        this.modelFactory = new BedrockModelFactory(animationSources, legacyModelFactory);
        this.logicalSide = Objects.requireNonNull(logicalSide, "logicalSide");
        this.generation = new Generation(Map.of(), Map.of(), null, modelFactory.snapshot());
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, Optional<BedrockModelPOJO>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Optional<BedrockModelPOJO>> result = new HashMap<>();
        entries.forEach((location, entry) -> {
            if (entry.lazy()) {
                return;
            }
            result.put(location, loadModelPojo(resourceManager, entry));
        });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<ResourceLocation, Optional<BedrockModelPOJO>> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        BedrockModelFactory preparedFactory = modelFactory.snapshot();
        Map<ResourceLocation, Optional<BedrockModelResource>> resources = new ConcurrentHashMap<>();
        entries.forEach((location, entry) -> {
            if (entry.lazy()) {
                return;
            }
            Optional<BedrockModelPOJO> pojo = prepared.get(location);
            if (pojo == null || pojo.isEmpty()) {
                return;
            }
            resources.put(location, preparedFactory.create(location, entry, pojo.get()));
        });
        Map<ResourceLocation, BedrockModelResource> listenerResources = resourceMap(resources);
        for (Consumer<Map<ResourceLocation, BedrockModelResource>> listener : listeners) {
            listener.accept(listenerResources);
        }
        generation = new Generation(prepared, resources, resourceManager, preparedFactory);
        // Server /reload builds new listener instances. Publish only after their
        // generation is complete, so readers keep the previous repository while preparing.
        BedrockResources.install(logicalSide, this);
    }

    @Override
    @Nullable
    public BedrockModelResource getResource(ResourceLocation location) {
        Generation current = generation;
        Optional<BedrockModelResource> cached = current.resourceCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry entry = entries.get(location);
        if (entry == null) {
            BedrockModelMod.LOGGER.error("Not registered Bedrock model: {}", location);
            return null;
        }
        Optional<BedrockModelResource> resource = current.resourceCache.computeIfAbsent(location, key -> {
            BedrockModelPOJO pojo = getModelPojo(current, key, entry);
            return pojo == null ? Optional.empty() : current.modelFactory.create(key, entry, pojo);
        });
        return resource.orElse(null);
    }


    @Nullable
    public BakedBedrockModel getBakedModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.BAKED) {
            return null;
        }
        return (BakedBedrockModel) resource.model();
    }

    @Nullable
    public BedrockModel getLegacyModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.LEGACY) {
            return null;
        }
        return resource.model();
    }

    @Nullable
    public TreeBedrockModel getTreeModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.TREE) {
            return null;
        }
        return (TreeBedrockModel) resource.model();
    }

    @Nullable
    public BedrockModelPOJO getModelPojo(ResourceLocation location) {
        Generation current = generation;
        return getModelPojo(current, location, entries.get(location));
    }

    private BedrockModelPOJO getModelPojo(Generation current, ResourceLocation location, BedrockModelEntry entry) {
        Optional<BedrockModelPOJO> cached = current.pojoCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        if (entry == null) {
            BedrockModelMod.LOGGER.error("Not registered Bedrock model: {}", location);
            return null;
        }
        Optional<BedrockModelPOJO> pojo = current.pojoCache.computeIfAbsent(location, key -> {
            if (current.resourceManager == null) {
                BedrockModelMod.LOGGER.error("Cannot lazy load Bedrock model before resource reload is applied: {}", key);
                return Optional.empty();
            }
            return loadModelPojo(current.resourceManager, entry);
        });
        return pojo.orElse(null);
    }

    public void clearLoaded(ResourceLocation location) {
        Generation current = generation;
        current.pojoCache.remove(location);
        current.resourceCache.remove(location);
    }

    public void clearLoaded() {
        Generation current = generation;
        current.pojoCache.clear();
        current.resourceCache.clear();
    }

    @Override
    @UnmodifiableView
    public Map<ResourceLocation, BedrockModelResource> getAllResources() {
        return resourceMap(generation.resourceCache);
    }

    private static Map<ResourceLocation, BedrockModelResource> resourceMap(
            Map<ResourceLocation, Optional<BedrockModelResource>> cache) {
        Map<ResourceLocation, BedrockModelResource> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Optional<BedrockModelResource>> entry : cache.entrySet()) {
            entry.getValue().ifPresent(resource -> result.put(entry.getKey(), resource));
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<ResourceLocation, BakedBedrockModel> getAllBakedModels() {
        Map<ResourceLocation, BakedBedrockModel> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BedrockModelResource> entry : getAllResources().entrySet()) {
            if (entry.getValue().kind() == ModelType.BAKED) {
                result.put(entry.getKey(), (BakedBedrockModel) entry.getValue().model());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<ResourceLocation, TreeBedrockModel> getAllTreeModels() {
        Map<ResourceLocation, TreeBedrockModel> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, BedrockModelResource> entry : getAllResources().entrySet()) {
            if (entry.getValue().kind() == ModelType.TREE) {
                result.put(entry.getKey(), (TreeBedrockModel) entry.getValue().model());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @UnmodifiableView
    public Map<ResourceLocation, Optional<BedrockModelPOJO>> getModelPojos() {
        return Map.copyOf(generation.pojoCache);
    }

    private static Optional<BedrockModelPOJO> loadModelPojo(ResourceManager resourceManager, BedrockModelEntry processor) {
        ResourceLocation path = modelPath(processor.sourceId());
        return resourceManager.getResource(path).map(resource -> {
            try (InputStream stream = resource.open()) {
                return Optional.ofNullable(processor.rawLoader().load(stream, BedrockModelPOJO.class));
            } catch (IOException | RuntimeException e) {
                BedrockModelMod.LOGGER.error("Failed to load Bedrock model file: {}", path, e);
                return Optional.<BedrockModelPOJO>empty();
            }
        }).orElseGet(() -> {
            BedrockModelMod.LOGGER.error("Not found Bedrock model file: {}", path);
            return Optional.empty();
        });
    }

    private static ResourceLocation modelPath(ResourceLocation location) {
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "models/bedrock/" + location.getPath() + ".json");
    }
}



