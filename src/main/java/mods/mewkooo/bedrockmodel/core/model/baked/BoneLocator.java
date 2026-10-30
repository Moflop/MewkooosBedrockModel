package mods.mewkooo.bedrockmodel.core.model.baked;

import mods.mewkooo.bedrockmodel.core.model.LocatorData;

public record BoneLocator(
        String name,
        int boneIndex,
        LocatorData data
) {
}

