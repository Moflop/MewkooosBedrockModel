package mods.mewkooo.bedrockmodel.v2.resource;

import mods.mewkooo.bedrockmodel.v1.common.BoneIndexProvider;
import mods.mewkooo.bedrockmodel.v1.common.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.BedrockAnimationFile;

import java.util.List;

@FunctionalInterface
public interface BedrockAnimationFactory {
    List<BedrockAnimation> create(BedrockAnimationFile animationFile, BoneIndexProvider model);
}
