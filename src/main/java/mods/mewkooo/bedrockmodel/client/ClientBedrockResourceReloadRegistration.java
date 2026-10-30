package mods.mewkooo.bedrockmodel.client;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.particle.resource.ParticleDefinitionLoader;
import mods.mewkooo.bedrockmodel.core.resource.BedrockResourceReloadRegistration;
import mods.mewkooo.bedrockmodel.client.legacy.model.BedrockModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class ClientBedrockResourceReloadRegistration {
    private ClientBedrockResourceReloadRegistration() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        BedrockResourceReloadRegistration.register(Dist.CLIENT, LogicalSide.CLIENT,
                event::registerReloadListener, BedrockModel::new);
        event.registerReloadListener(ParticleDefinitionLoader.getInstance());
    }
}
