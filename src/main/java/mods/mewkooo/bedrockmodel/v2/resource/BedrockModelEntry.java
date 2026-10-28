package mods.mewkooo.bedrockmodel.v2.resource;

import mods.mewkooo.bedrockmodel.v1.resource.RawResourceLoader;
import mods.mewkooo.bedrockmodel.v2.common.model.baked.BakerOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

public record BedrockModelEntry(
        RawResourceLoader rawLoader,
        ResourceLocation sourceId,
        ModelType kind,
        Function<BedrockModelBakeContext, BakerOptions> optionsFactory,
        List<ResourceLocation> animationSourceIds,
        boolean lazy
) {
    public BedrockModelEntry {
        animationSourceIds = List.copyOf(animationSourceIds);
    }
}
