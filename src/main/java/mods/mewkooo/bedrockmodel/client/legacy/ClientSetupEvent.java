package mods.mewkooo.bedrockmodel.client.legacy;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.client.legacy.compat.embeddium.EmbeddiumCompat;
import mods.mewkooo.bedrockmodel.client.legacy.compat.sodium.SodiumCompat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = BedrockModelMod.MOD_ID)
public class ClientSetupEvent {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SodiumCompat.init();
        EmbeddiumCompat.init();
    }
}
