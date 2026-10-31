package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockAnimationSourceRepositoryTest {
    @Test
    void publishedFileMapIsAnImmutableGenerationSnapshot() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "animation");
        BedrockAnimationSourceRepository repository = new BedrockAnimationSourceRepository(Map.of());
        repository.apply(Map.of(id, Optional.<BedrockAnimationFile>empty()), null, InactiveProfiler.INSTANCE);

        Map<ResourceLocation, Optional<BedrockAnimationFile>> first = repository.getAllAnimationFiles();
        repository.apply(Map.of(), null, InactiveProfiler.INSTANCE);

        assertEquals(Map.of(id, Optional.empty()), first);
        assertEquals(Map.of(), repository.getAllAnimationFiles());
        assertThrows(UnsupportedOperationException.class, first::clear);
    }
}
