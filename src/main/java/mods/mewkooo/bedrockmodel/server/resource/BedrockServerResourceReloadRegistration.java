package mods.mewkooo.bedrockmodel.server.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.resource.BedrockResourceReloadRegistration;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// AddReloadListenerEvent is fired by both a dedicated server and the logical
// server embedded in a single-player client.
@EventBusSubscriber(modid = BedrockModelMod.MOD_ID)
public final class BedrockServerResourceReloadRegistration {
    private BedrockServerResourceReloadRegistration() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(AddReloadListenerEvent event) {
        // On a dedicated server this is DEDICATED_SERVER; in single-player it
        // is the physical client hosting the logical server.
        BedrockResourceReloadRegistration.register(FMLEnvironment.dist, LogicalSide.SERVER, event::addListener, null);
    }

    @SubscribeEvent
    public static void clearResourcesAfterStop(ServerStoppedEvent event) {
        BedrockResources.clear(LogicalSide.SERVER);
    }
}

