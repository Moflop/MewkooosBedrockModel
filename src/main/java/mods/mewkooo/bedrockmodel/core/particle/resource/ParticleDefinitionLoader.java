package mods.mewkooo.bedrockmodel.core.particle.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDeserializer;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;

/**
 * 粒子定义资源加载器。
 * <p>
 * 扫描 {@code assets/<namespace>/particle_definitions/<name>.json} 路径，
 * 解析基岩版粒子效果定义并缓存。
 * <p>
 * 每个粒子效果定义在加载时使用共享的 {@link ParticleMolangEnvironment} 编译 Molang 表达式。
 */
public class ParticleDefinitionLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonElement>> {
    private static final String DIRECTORY = "particle_definitions";
    private static final ParticleEffectDeserializer DESERIALIZER = new ParticleEffectDeserializer();

    private static ParticleDefinitionLoader INSTANCE;

    private final ParticleMolangEnvironment molang;
    private volatile DefinitionGeneration generation = new DefinitionGeneration(Map.of(), Map.of());

    private record DefinitionGeneration(Map<ResourceLocation, ParticleEffectDefinition> definitions,
                                        Map<ResourceLocation, ParticleEffectDefinition> identifierIndex) {
        private DefinitionGeneration {
            definitions = Map.copyOf(definitions);
            identifierIndex = Map.copyOf(identifierIndex);
        }
    }

    public ParticleDefinitionLoader(ParticleMolangEnvironment molang) {
        this.molang = molang;
    }

    public static ParticleDefinitionLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ParticleDefinitionLoader(new ParticleMolangEnvironment());
        }
        return INSTANCE;
    }

    @Override
    @NotNull
    @ParametersAreNonnullByDefault
    protected Map<ResourceLocation, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> result = new HashMap<>();
        resourceManager.listResources(DIRECTORY, loc -> loc.getPath().endsWith(".json"))
                .forEach((location, resource) -> {
                    try (InputStream stream = resource.open()) {
                        JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                        String path = location.getPath();
                        String name = path.substring(DIRECTORY.length() + 1, path.length() - 5);
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), name);
                        result.put(id, json);
                    } catch (IOException e) {
                        BedrockModelMod.LOGGER.error("Failed to read particle definition: {}", location, e);
                    }
                });
        return result;
    }

    @Override
    @ParametersAreNonnullByDefault
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ParticleEffectDefinition> definitions = new HashMap<>();
        Map<ResourceLocation, ParticleEffectDefinition> identifierIndex = new HashMap<>();
        prepared.forEach((id, json) -> {
            try {
                ParticleEffectDefinition definition = DESERIALIZER.parse(json, molang);
                if (definition != null) {
                    definitions.put(id, definition);
                    identifierIndex.put(definition.getIdentifier(), definition);
                    BedrockModelMod.LOGGER.debug("Loaded particle definition: {}", id);
                }
            } catch (Exception e) {
                BedrockModelMod.LOGGER.error("Failed to parse particle definition: {}", id, e);
            }
        });
        generation = new DefinitionGeneration(definitions, identifierIndex);
        BedrockModelMod.LOGGER.info("Loaded {} particle definitions", definitions.size());
    }

    @Nullable
    public ParticleEffectDefinition getDefinition(ResourceLocation id) {
        DefinitionGeneration current = generation;
        ParticleEffectDefinition def = current.definitions().get(id);
        if (def != null) return def;
        return current.identifierIndex().get(id);
    }

    public Map<ResourceLocation, ParticleEffectDefinition> getAllDefinitions() {
        return generation.definitions();
    }

    public ParticleMolangEnvironment getMolang() {
        return molang;
    }
}

