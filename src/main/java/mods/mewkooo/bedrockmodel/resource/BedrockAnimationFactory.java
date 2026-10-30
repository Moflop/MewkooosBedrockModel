package mods.mewkooo.bedrockmodel.resource;

import mods.mewkooo.bedrockmodel.model.BoneIndexProvider;
import mods.mewkooo.bedrockmodel.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.resource.json.pojo.BedrockAnimationFile;

import java.util.List;

@FunctionalInterface
public interface BedrockAnimationFactory {
    List<BedrockAnimation> create(BedrockAnimationFile animationFile, BoneIndexProvider model);
}
