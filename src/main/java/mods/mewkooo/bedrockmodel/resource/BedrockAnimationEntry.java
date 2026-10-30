package mods.mewkooo.bedrockmodel.resource;

import mods.mewkooo.bedrockmodel.resource.RawResourceLoader;

public record BedrockAnimationEntry(
        RawResourceLoader rawLoader,
        BedrockAnimationFactory factory,
        boolean lazy
) {
}
