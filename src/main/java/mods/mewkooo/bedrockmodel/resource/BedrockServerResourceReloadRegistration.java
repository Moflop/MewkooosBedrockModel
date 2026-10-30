package mods.mewkooo.bedrockmodel.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class BedrockServerResourceReloadRegistration {
    private BedrockServerResourceReloadRegistration() {
    }

    @SubscribeEvent
    public static void registerReloadListeners(AddReloadListenerEvent event) {
        BedrockResourceReloadRegistration.register(Dist.DEDICATED_SERVER, event::addListener);
    }
}
