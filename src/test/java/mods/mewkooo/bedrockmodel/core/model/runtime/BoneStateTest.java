package mods.mewkooo.bedrockmodel.core.model.runtime;

import mods.mewkooo.bedrockmodel.core.model.baked.BakedBoneDefinition;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoneStateTest {
    @Test
    void foldedParentAndBoneNormalsMatchTheLocalPositionTransform() {
        Matrix4f foldedParent = new Matrix4f().translate(1, 2, 3).rotateXYZ(0.2f, -0.3f, 0.4f)
                .scale(0.5f, 1.5f, 2.0f);
        BoneState bone = new BoneState(definition(foldedParent));
        bone.x = 8;
        bone.rotation.rotateZYX(-0.15f, 0.25f, 0.35f);
        bone.xScale = -2;
        bone.yScale = 0.75f;
        bone.zScale = 1.25f;

        Matrix4f pose = new Matrix4f().rotateY(0.3f).scale(2, 1, 0.5f);
        Matrix3f normal = new Matrix3f(pose).invert().transpose();
        Matrix4f expectedPose = new Matrix4f(pose).mul(bone.getLocalTransform());
        Matrix3f expectedNormal = new Matrix3f(expectedPose).invert().transpose();
        bone.applyLocalTransform(pose, normal);

        assertTrue(expectedPose.equals(pose, 1.0e-6f));
        assertTrue(expectedNormal.equals(normal, 1.0e-5f));
    }

    @Test
    void singularAndNonFiniteLocalTransformsPreserveTheIncomingNormal() {
        BoneState bone = new BoneState(definition(null));
        bone.rotation.rotateXYZ(0.3f, -0.2f, 0.1f);
        Matrix3f incoming = new Matrix3f().rotateY(0.7f).scale(0.5f, 1, 2);
        for (float scale : new float[]{0, Float.MIN_VALUE, Float.POSITIVE_INFINITY, Float.NaN}) {
            bone.xScale = scale;
            Matrix3f normal = new Matrix3f(incoming);
            bone.applyLocalTransform(new Matrix4f(), normal);
            assertEquals(incoming, normal, "Invalid local normal should contribute identity for scale " + scale);
        }

        // A previous fallback must not poison the reusable local normal matrix.
        bone.xScale = 2;
        Matrix4f pose = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        bone.applyLocalTransform(pose, normal);
        assertTrue(new Matrix3f(pose).invert().transpose().equals(normal, 1.0e-6f));
    }

    @Test
    void zeroScaleStillCollapsesPositionsWithoutIntroducingNonFiniteNormals() {
        BoneState bone = new BoneState(definition(null));
        bone.xScale = 0;
        bone.yScale = 2;
        Matrix4f pose = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        bone.applyLocalTransform(pose, normal);

        assertEquals(new Vector3f(0, 2, 1), pose.transformPosition(new Vector3f(1, 1, 1)));
        assertEquals(new Matrix3f(), normal);
    }

    private static BakedBoneDefinition definition(Matrix4f foldedParent) {
        return new BakedBoneDefinition("bone", 0, -1, new int[0],
                0, 0, 0, 0, 0, 0, null, null, foldedParent, null,
                new Quaternionf(), new Vector3f(), true, false);
    }
}
