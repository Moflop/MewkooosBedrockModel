package mods.mewkooo.bedrockmodel.core.model.runtime;

import org.joml.Matrix3f;

/** Shared finite fallback for local and global bone normal transforms. */
final class NormalTransforms {
    private NormalTransforms() {}

    static Matrix3f inverseTransposeOrIdentity(Matrix3f linear) {
        float determinant = linear.determinant();
        if (!Float.isFinite(determinant) || determinant == 0.0F) {
            return linear.identity();
        }
        linear.invert().transpose();
        return isFinite(linear) ? linear : linear.identity();
    }

    private static boolean isFinite(Matrix3f matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22());
    }
}
