package mods.mewkooo.bedrockmodel.client.legacy.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.mewkooo.bedrockmodel.api.json.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.api.json.ItemDisplayTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BedrockDisplayTransformsTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    void missingContextAppliesDefaultTranslationAndScale() {
        PoseStack poseStack = new PoseStack();
        ItemDisplayTransform defaults = new ItemDisplayTransform(
                new Vector3f(), new Vector3f(16, 32, 48), new Vector3f(2),
                new Vector3f(), new Vector3f(), false);

        BedrockDisplayTransforms.applyDisplayTransform(
                poseStack, ItemDisplayTransforms.NONE, ItemDisplayContext.GUI, defaults);

        assertTransformedPosition(poseStack, new Vector3f(), new Vector3f(1, 2, 3));
        assertTransformedPosition(poseStack, new Vector3f(1), new Vector3f(3, 4, 5));
    }

    @Test
    void partialContextOverridesTranslationAndFallsBackToDefaultRotationAndScale() {
        PoseStack poseStack = new PoseStack();
        ItemDisplayTransform defaults = new ItemDisplayTransform(
                new Vector3f(), new Vector3f(16, 32, 48), new Vector3f(2),
                new Vector3f(), new Vector3f(), false);
        ItemDisplayTransform gui = new ItemDisplayTransform(
                null, new Vector3f(32, 48, 64), null,
                new Vector3f(), new Vector3f(), false);

        BedrockDisplayTransforms.applyDisplayTransform(
                poseStack, new ItemDisplayTransforms(Map.of("gui", gui)), ItemDisplayContext.GUI, defaults);

        assertTransformedPosition(poseStack, new Vector3f(), new Vector3f(2, 3, 4));
        assertTransformedPosition(poseStack, new Vector3f(1), new Vector3f(4, 5, 6));
    }

    @Test
    void missingContextWithNoneDefaultsPreservesExistingPoseAndNormals() {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(2, -3, 4);
        poseStack.mulPose(new Quaternionf().rotationXYZ(0.3f, -0.6f, 0.8f));
        poseStack.scale(2, 3, 0.5f);
        Matrix4f originalPose = new Matrix4f(poseStack.last().pose());
        Matrix3f originalNormal = new Matrix3f(poseStack.last().normal());

        BedrockDisplayTransforms.applyDisplayTransform(
                poseStack, ItemDisplayTransforms.NONE, ItemDisplayContext.GUI, ItemDisplayTransform.NONE);

        assertTrue(originalPose.equals(poseStack.last().pose(), EPSILON), "Position matrix changed");
        assertTrue(originalNormal.equals(poseStack.last().normal(), EPSILON), "Normal matrix changed");
    }

    @Test
    void noneDefaultsCannotBeChangedThroughReturnedVectorsAndDoNotFitToFrame() {
        ItemDisplayTransform.NONE.getTranslation().set(16, 32, 48);
        ItemDisplayTransform.NONE.getScale().zero();

        assertEquals(new Vector3f(), ItemDisplayTransform.NONE.getTranslation());
        assertEquals(new Vector3f(1), ItemDisplayTransform.NONE.getScale());
        assertFalse(ItemDisplayTransform.NONE.isFitToFrame());
    }

    private static void assertTransformedPosition(PoseStack poseStack, Vector3f position, Vector3f expected) {
        Vector3f actual = poseStack.last().pose().transformPosition(position, new Vector3f());
        assertTrue(expected.equals(actual, EPSILON), () -> "Expected " + expected + " but was " + actual);
    }
}
