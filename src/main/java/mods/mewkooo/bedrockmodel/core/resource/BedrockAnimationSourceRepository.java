package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class BedrockAnimationSourceRepository extends SimplePreparableReloadListener<Map<ResourceLocation, Optional<BedrockAnimationFile>>> {
    private final Map<ResourceLocation, BedrockAnimationEntry> processors;
    private volatile Generation generation = new Generation(Map.of(), null);

    private static final class Generation {
        private final ConcurrentMap<ResourceLocation, Optional<BedrockAnimationFile>> fileCache;
        @Nullable
        private final ResourceManager resourceManager;

        private Generation(Map<ResourceLocation, Optional<BedrockAnimationFile>> files,
                           @Nullable ResourceManager resourceManager) {
            this.fileCache = new ConcurrentHashMap<>(files);
            this.resourceManager = resourceManager;
        }
    }

    BedrockAnimationSourceRepository(Map<ResourceLocation, BedrockAnimationEntry> processors) {
        this.processors = Map.copyOf(processors);
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, Optional<BedrockAnimationFile>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Optional<BedrockAnimationFile>> result = new HashMap<>();
        processors.forEach((location, processor) -> {
            if (processor.lazy()) {
                return;
            }
            result.put(location, loadAnimationFile(resourceManager, location, processor));
        });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<ResourceLocation, Optional<BedrockAnimationFile>> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        generation = new Generation(prepared, resourceManager);
    }

    @Nullable
    BedrockAnimationFile getAnimationFile(ResourceLocation location) {
        Generation current = generation;
        Optional<BedrockAnimationFile> cached = current.fileCache.get(location);
        if (cached != null) {
            return cached.orElse(null);
        }
        BedrockAnimationEntry processor = processors.get(location);
        if (processor == null) {
            BedrockModelMod.LOGGER.error("Not registered Bedrock animation: {}", location);
            return null;
        }
        Optional<BedrockAnimationFile> file = current.fileCache.computeIfAbsent(location, key -> {
            if (current.resourceManager == null) {
                BedrockModelMod.LOGGER.error("Cannot lazy load Bedrock animation before resource reload is applied: {}", key);
                return Optional.empty();
            }
            return loadAnimationFile(current.resourceManager, key, processor);
        });
        return file.orElse(null);
    }

    @Nullable
    BedrockAnimationEntry getProcessor(ResourceLocation location) {
        return processors.get(location);
    }

    void clearLoaded(ResourceLocation location) {
        generation.fileCache.remove(location);
    }

    void clearLoaded() {
        generation.fileCache.clear();
    }

    @UnmodifiableView
    Map<ResourceLocation, Optional<BedrockAnimationFile>> getAllAnimationFiles() {
        return Map.copyOf(generation.fileCache);
    }

    private static Optional<BedrockAnimationFile> loadAnimationFile(ResourceManager resourceManager, ResourceLocation location,
                                                                    BedrockAnimationEntry processor) {
        ResourceLocation path = animationPath(location);
        return resourceManager.getResource(path).map(resource -> {
            try (InputStream stream = resource.open()) {
                return Optional.ofNullable(processor.rawLoader().load(stream, BedrockAnimationFile.class));
            } catch (IOException | RuntimeException e) {
                BedrockModelMod.LOGGER.error("Failed to load Bedrock animation file: {}", path, e);
                return Optional.<BedrockAnimationFile>empty();
            }
        }).orElseGet(() -> {
            BedrockModelMod.LOGGER.error("Not found Bedrock animation file: {}", path);
            return Optional.empty();
        });
    }

    private static ResourceLocation animationPath(ResourceLocation location) {
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "animations/" + location.getPath() + ".json");
    }
}

