package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.resource.RawResourceLoader;
import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
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


