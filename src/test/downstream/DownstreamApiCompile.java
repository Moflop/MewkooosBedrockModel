import com.maydaymemory.mae.basic.Pose;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;

/** Compile-only consumer fixture for the published animation API. */
final class DownstreamApiCompile {
    private DownstreamApiCompile() {
    }

    static void apply(AnimationClip clip, BedrockModelInstance instance, float timeSeconds) {
        Pose pose = clip.evaluate(timeSeconds);
        instance.applyPose(pose);
    }
}
