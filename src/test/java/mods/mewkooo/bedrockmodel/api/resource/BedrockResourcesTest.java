package mods.mewkooo.bedrockmodel.api.resource;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;

class BedrockResourcesTest {
    @AfterEach
    void clearInstalledViews() {
        BedrockResources.clear(LogicalSide.CLIENT);
        BedrockResources.clear(LogicalSide.SERVER);
    }

    @Test
    void keepsClientAndIntegratedServerViewsSeparate() {
        BedrockModelAccess client = emptyAccess();
        BedrockModelAccess server = emptyAccess();

        BedrockResources.install(LogicalSide.CLIENT, client);
        BedrockResources.install(LogicalSide.SERVER, server);

        assertSame(client, BedrockResources.models(LogicalSide.CLIENT));
        assertSame(server, BedrockResources.models(LogicalSide.SERVER));
    }

    private static BedrockModelAccess emptyAccess() {
        return new BedrockModelAccess() {
            @Override
            public BedrockModelResource getResource(ResourceLocation modelId) {
                return null;
            }

            @Override
            public Map<ResourceLocation, BedrockModelResource> getAllResources() {
                return Map.of();
            }
        };
    }
}
