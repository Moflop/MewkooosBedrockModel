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
import com.google.common.collect.Maps;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

final class BedrockModelRepositoryImpl extends SimplePreparableReloadListener<Map<ResourceLocation, Optional<BedrockModelPOJO>>>
        implements BedrockModelAccess {
    

    private final Map<ResourceLocation, BedrockModelEntry> entries;
    private final List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners;
    private final Map<ResourceLocation, Optional<BedrockModelPOJO>> pojoCache;
    private final Map<ResourceLocation, Optional<BedrockModelResource>> resourceCache;
    private final BedrockModelFactory modelFactory;
    @Nullable
    private ResourceManager resourceManager;

    BedrockModelRepositoryImpl(LogicalSide logicalSide,
                           Map<ResourceLocation, BedrockModelEntry> entries,
                           List<Consumer<Map<ResourceLocation, BedrockModelResource>>> listeners,
                           BedrockAnimationSourceRepository animationSources,
                           java.util.function.Function<BedrockModelPOJO, ? extends BedrockModel> legacyModelFactory) {
        this.entries = Map.copyOf(entries);
        this.listeners = List.copyOf(listeners);
        this.pojoCache = Maps.newHashMap();
        this.resourceCache = Maps.newHashMap();
        this.modelFactory = new BedrockModelFactory(animationSources, legacyModelFactory);
        BedrockResources.install(logicalSide, this);
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, Optional<BedrockModelPOJO>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Optional<BedrockModelPOJO>> result = Maps.newHashMap();
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
        this.resourceManager = resourceManager;
        pojoCache.clear();
        resourceCache.clear();
        pojoCache.putAll(prepared);
        entries.forEach((location, entry) -> {
            if (entry.lazy()) {
                return;
            }
            Optional<BedrockModelPOJO> pojo = pojoCache.get(location);
            if (pojo == null || pojo.isEmpty()) {
                return;
            }
            resourceCache.put(location, modelFactory.create(location, entry, pojo.get()));
        });
        Map<ResourceLocation, BedrockModelResource> resources = getAllResources();
        for (Consumer<Map<ResourceLocation, BedrockModelResource>> listener : listeners) {
            listener.accept(resources);
        }
    }

    @Override
    @Nullable
    public synchronized BedrockModelResource getResource(ResourceLocation location) {
        Optional<BedrockModelResource> cached = resourceCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry entry = entries.get(location);
        if (entry == null) {
            BedrockModelMod.LOGGER.error("Not registered Bedrock model: {}", location);
            return null;
        }
        BedrockModelPOJO pojo = getModelPojo(location);
        if (pojo == null) {
            resourceCache.put(location, Optional.empty());
            return null;
        }
        Optional<BedrockModelResource> resource = modelFactory.create(location, entry, pojo);
        resourceCache.put(location, resource);
        return resource.orElse(null);
    }


    @Nullable
    public synchronized BakedBedrockModel getBakedModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.BAKED) {
            return null;
        }
        return (BakedBedrockModel) resource.model();
    }

    @Nullable
    public synchronized BedrockModel getLegacyModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.LEGACY) {
            return null;
        }
        return resource.model();
    }

    @Nullable
    public synchronized TreeBedrockModel getTreeModel(ResourceLocation location) {
        BedrockModelResource resource = getResource(location);
        if (resource == null || resource.kind() != ModelType.TREE) {
            return null;
        }
        return (TreeBedrockModel) resource.model();
    }

    @Nullable
    public synchronized BedrockModelPOJO getModelPojo(ResourceLocation location) {
        Optional<BedrockModelPOJO> cached = pojoCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockModelEntry entry = entries.get(location);
        if (entry == null) {
            BedrockModelMod.LOGGER.error("Not registered Bedrock model: {}", location);
            return null;
        }
        if (resourceManager == null) {
            BedrockModelMod.LOGGER.error("Cannot lazy load Bedrock model before resource reload is applied: {}", location);
            return null;
        }
        Optional<BedrockModelPOJO> pojo = loadModelPojo(resourceManager, entry);
        pojoCache.put(location, pojo);
        return pojo.orElse(null);
    }

    public synchronized void clearLoaded(ResourceLocation location) {
        pojoCache.remove(location);
        resourceCache.remove(location);
    }

    public synchronized void clearLoaded() {
        pojoCache.clear();
        resourceCache.clear();
    }

    @Override
    @UnmodifiableView
    public Map<ResourceLocation, BedrockModelResource> getAllResources() {
        Map<ResourceLocation, BedrockModelResource> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Optional<BedrockModelResource>> entry : resourceCache.entrySet()) {
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
        return Collections.unmodifiableMap(pojoCache);
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



