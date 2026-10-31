package mods.mewkooo.bedrockmodel.api.animation;

import com.maydaymemory.mae.basic.Pose;
import com.maydaymemory.mae.basic.IEvaluationContext;

/**
 * A loaded animation clip. Implementations are created by the library; callers
 * use this contract to evaluate a pose without depending on the parser.
 */
public interface AnimationClip {
    Pose evaluate(float timeSeconds);

    /**
     * Evaluates this clip using the caller's animation context.
     *
     * <p>The default keeps source compatibility for custom clip
     * implementations that do not need a context. Implementations that use
     * expression-driven keyframes should override this method.</p>
     */
    default Pose evaluate(float timeSeconds, IEvaluationContext context) {
        return evaluate(timeSeconds);
    }

    float getSpecifiedEndTimeS();
}

