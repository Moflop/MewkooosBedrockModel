package mods.mewkooo.bedrockmodel.api.animation;

import com.maydaymemory.mae.basic.Pose;

/**
 * A loaded animation clip. Implementations are created by the library; callers
 * use this contract to evaluate a pose without depending on the parser.
 */
public interface AnimationClip {
    Pose evaluate(float timeSeconds);

    float getSpecifiedEndTimeS();
}

