package mods.mewkooo.bedrockmodel.core.animation;

import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.IEvaluationContext;
import com.maydaymemory.mae.basic.Pose;
import mods.mewkooo.bedrockmodel.api.animation.time.AnimationClock;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;
import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangContext;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BedrockAnimationTest {
    private static final BedrockModel ROOT_MODEL = new BedrockModel() {
        @Override public ModelType kind() { return ModelType.BAKED; }
        @Override public int getIndex(String boneName) { return "root".equals(boneName) ? 0 : -1; }
        @Override public AABB getRenderBoundingBox() { return null; }
        @Override public ItemDisplayTransforms getItemDisplayTransforms() { return ItemDisplayTransforms.NONE; }
    };

    @Test
    void defaultFactoryCompilesAndEvaluatesMolangTime() {
        BedrockAnimation animation = createAnimation("""
                {"animations":{"test":{"animation_length":1,
                  "bones":{"root":{"position":{"0":[0,"query.anim_time",0]}}}}}}
                """);

        BoneTransform transform = onlyTransform(animation.evaluate(0.75f));
        assertEquals(0.75f, transform.translation().y(), 1.0e-5f);
    }

    @Test
    void explicitContextReceivesAnimationTime() {
        BedrockAnimation animation = createAnimation("""
                {"animations":{"test":{"animation_length":1,
                  "bones":{"root":{"position":{"0":[0,"query.anim_time",0]}}}}}}
                """);
        MolangContext<?> context = new MolangContext<>();

        BoneTransform transform = onlyTransform(animation.evaluate(0.25f, context));
        assertEquals(0.25f, transform.translation().y(), 1.0e-5f);
        assertEquals(0.25f, context.getTimeS(), 1.0e-5f);
    }

    @Test
    void mixedNumericAndMolangKeyframeKeepsBothSides() {
        BedrockAnimation animation = createAnimation("""
                {"animations":{"test":{"animation_length":1,
                  "bones":{"root":{"position":{"0":[0,0,0],
                  "1":{"pre":[1,2,3],"post":["query.anim_time",2,3]},
                  "2":[0,0,0]}}}}}}
                """);

        BoneTransform preTransform = onlyTransform(animation.evaluate(0.5f));
        assertEquals(-0.5f, preTransform.translation().x(), 1.0e-5f);
        assertEquals(1.0f, preTransform.translation().y(), 1.0e-5f);

        BoneTransform postTransform = onlyTransform(animation.evaluate(1.5f));
        assertEquals(-0.75f, postTransform.translation().x(), 1.0e-5f);
        assertEquals(1.0f, postTransform.translation().y(), 1.0e-5f);
    }

    @Test
    void resetClearsRateLimiterCache() {
        AnimationClock clock = () -> 0L;
        AnimationRateLimiter<String> limiter = new AnimationRateLimiter<>(clock, AnimationRateLimiter.FPS_60);
        limiter.setCachedValue("cached");

        limiter.reset();

        assertNull(limiter.getCachedValue());
        assertTrue(limiter.shouldUpdate());
    }

    private static BedrockAnimation createAnimation(String json) {
        BedrockAnimationFile file = GsonUtil.CLIENT_GSON.fromJson(json, BedrockAnimationFile.class);
        return BedrockAnimation.createAnimation(file, ROOT_MODEL).getFirst();
    }

    private static BoneTransform onlyTransform(Pose pose) {
        return pose.getBoneTransforms().iterator().next();
    }
}
