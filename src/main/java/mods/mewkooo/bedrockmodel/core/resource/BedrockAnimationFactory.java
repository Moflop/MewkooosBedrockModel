package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;

import java.util.Map;

@FunctionalInterface
public interface BedrockAnimationFactory {
    Map<String, AnimationClip> create(BedrockAnimationFile animationFile, BedrockModel model);
}


