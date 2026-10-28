package mods.mewkooo.bedrockmodel;

import mods.mewkooo.bedrockmodel.v2.client.config.WorldMeshConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(BedrockModelMod.MOD_ID)
public class BedrockModelMod {
    public static final String MOD_ID = "mewkooos_bedrock_model";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public BedrockModelMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, WorldMeshConfig.SPEC);
        LOGGER.info("Mewkooo's Bedrock Model initialized!");
    }

    public static ResourceLocation prefix(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
