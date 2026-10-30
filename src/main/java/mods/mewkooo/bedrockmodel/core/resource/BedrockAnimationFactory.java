package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;

import java.util.List;

@FunctionalInterface
public interface BedrockAnimationFactory {
    List<AnimationClip> create(BedrockAnimationFile animationFile, BedrockModel model);
}


