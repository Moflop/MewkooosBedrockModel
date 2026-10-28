package mods.mewkooo.bedrockmodel.v2.common.model.baked;

import mods.mewkooo.bedrockmodel.v1.common.model.LocatorData;

public record BoneLocator(
        String name,
        int boneIndex,
        LocatorData data
) {
}
