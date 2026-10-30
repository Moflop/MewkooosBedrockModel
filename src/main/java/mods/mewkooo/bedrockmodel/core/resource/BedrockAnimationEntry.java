package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.resource.RawResourceLoader;
import mods.mewkooo.bedrockmodel.core.resource.BedrockAnimationFactory;

public record BedrockAnimationEntry(
        RawResourceLoader rawLoader,
        BedrockAnimationFactory factory,
        boolean lazy
) {
}



