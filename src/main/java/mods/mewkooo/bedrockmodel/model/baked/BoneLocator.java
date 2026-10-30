package mods.mewkooo.bedrockmodel.model.baked;

import mods.mewkooo.bedrockmodel.model.LocatorData;

public record BoneLocator(
        String name,
        int boneIndex,
        LocatorData data
) {
}
