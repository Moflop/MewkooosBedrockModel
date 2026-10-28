package mods.mewkooo.bedrockmodel.v1.resource;

import mods.mewkooo.bedrockmodel.v1.common.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.v1.common.model.BedrockModel;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.BiFunction;

public record BedrockAnimationResourceProcessor(RawResourceLoader rawLoader,
                                                ResourceLocation modelKey,
                                                BiFunction<BedrockAnimationFile, BedrockModel, List<BedrockAnimation>> converter) {
}
