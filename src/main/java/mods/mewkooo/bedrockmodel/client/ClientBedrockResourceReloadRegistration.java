package mods.mewkooo.bedrockmodel.client;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.particle.resource.ParticleDefinitionLoader;
import mods.mewkooo.bedrockmodel.resource.BedrockResourceReloadRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class ClientBedrockResourceReloadRegistration {
    private ClientBedrockResourceReloadRegistration() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        BedrockResourceReloadRegistration.register(Dist.CLIENT, event::registerReloadListener);
        event.registerReloadListener(ParticleDefinitionLoader.getInstance());
    }
}
