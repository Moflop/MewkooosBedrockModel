package mods.mewkooo.bedrockmodel.core.particle.resource;

import com.google.gson.JsonParser;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
