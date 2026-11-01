package mods.mewkooo.bedrockmodel.core.particle.resource;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.AbstractMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleDefinitionLoaderTest {
    private static final String DEFINITION = """
            {"particle_effect":{"description":{"identifier":"test:particle"},"components":{}}}
            """;

    @Test
    void publishedDefinitionsRemainStableAfterReload() {
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "second");
        ParticleDefinitionLoader loader = new ParticleDefinitionLoader(new ParticleMolangEnvironment());
        loader.apply(Map.of(firstId, JsonParser.parseString(DEFINITION)), null, InactiveProfiler.INSTANCE);

        var firstDefinition = loader.getDefinition(firstId);
        var firstSnapshot = loader.getAllDefinitions();
        loader.apply(Map.of(secondId, JsonParser.parseString(DEFINITION.replace("test:particle", "test:second_particle"))),
                null, InactiveProfiler.INSTANCE);

        assertNotNull(firstDefinition);
        assertSame(firstDefinition, firstSnapshot.get(firstId));
        assertEquals(Map.of(firstId, firstDefinition), firstSnapshot);
        assertEquals(Set.of(secondId), loader.getAllDefinitions().keySet());
        assertThrows(UnsupportedOperationException.class, firstSnapshot::clear);
    }

    @Test
    void concurrentReaderKeepsThePreviousGenerationWhileDefinitionsAreParsed() throws Exception {
        ResourceLocation previousId = ResourceLocation.fromNamespaceAndPath("test", "previous");
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "second");
        ParticleDefinitionLoader loader = new ParticleDefinitionLoader(new ParticleMolangEnvironment());
        loader.apply(Map.of(previousId, JsonParser.parseString(DEFINITION.replace("test:particle", "test:previous"))),
                null, InactiveProfiler.INSTANCE);
        var previous = loader.getAllDefinitions();

        CountDownLatch firstDefinitionParsed = new CountDownLatch(1);
        CountDownLatch continueParsing = new CountDownLatch(1);
        Map<ResourceLocation, JsonElement> prepared = new AbstractMap<>() {
            private final List<Entry<ResourceLocation, JsonElement>> entries = List.of(
                    Map.entry(firstId, JsonParser.parseString(DEFINITION.replace("test:particle", "test:first"))),
                    Map.entry(secondId, JsonParser.parseString(DEFINITION.replace("test:particle", "test:second"))));

            @Override public Set<Entry<ResourceLocation, JsonElement>> entrySet() { return Set.copyOf(entries); }

            @Override
            public void forEach(BiConsumer<? super ResourceLocation, ? super JsonElement> action) {
                action.accept(entries.getFirst().getKey(), entries.getFirst().getValue());
                firstDefinitionParsed.countDown();
                try {
                    if (!continueParsing.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to finish definition parsing");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                action.accept(entries.getLast().getKey(), entries.getLast().getValue());
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var reload = executor.submit(() -> loader.apply(prepared, null, InactiveProfiler.INSTANCE));
            assertTrue(firstDefinitionParsed.await(5, TimeUnit.SECONDS));
            assertEquals(previous, loader.getAllDefinitions());
            assertNotNull(loader.getDefinition(previousId));
            continueParsing.countDown();
            reload.get(5, TimeUnit.SECONDS);
            assertEquals(Set.of(firstId, secondId), loader.getAllDefinitions().keySet());
            assertNotNull(loader.getDefinition(firstId));
            assertNotNull(loader.getDefinition(secondId));
        } finally {
            continueParsing.countDown();
            executor.shutdownNow();
        }
    }
}
