package mods.mewkooo.bedrockmodel.core.model.runtime;

import com.maydaymemory.mae.basic.BoneTransform;
import com.maydaymemory.mae.basic.RotationView;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Mutable pose state for one model bone. All transforms are side independent. */
public class BoneState {
    private final BoneDefinition definition;
    private final Matrix3f localNormalTransform = new Matrix3f();
    public float x, y, z;
    public final Quaternionf rotation = new Quaternionf();
    public final Vector3f rotationInEuler = new Vector3f();
    public float xScale = 1, yScale = 1, zScale = 1;
    public boolean visible = true;
    public boolean illuminated;

    BoneState(BoneDefinition definition) { this.definition = definition; reset(); }
    public BoneDefinition definition() { return definition; }
    public String name() { return definition.name(); }
    public int index() { return definition.index(); }
    public int parentIndex() { return definition.parentIndex(); }
    public int[] children() { return definition.children(); }

    public void reset() {
        x = definition.bindX(); y = definition.bindY(); z = definition.bindZ();
        rotation.set(definition.bindRotation()); rotationInEuler.set(definition.bindEulerRotation());
        xScale = yScale = zScale = 1; visible = true; illuminated = false;
    }

    public Matrix4f getLocalTransform() {
        Matrix4f matrix = new Matrix4f();
        Matrix4f foldedParentTransform = definition.foldedParentTransform();
        if (foldedParentTransform != null) matrix.mul(foldedParentTransform);
        if (x != 0 || y != 0 || z != 0) matrix.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        if (definition.rotateAroundPivot()) {
            matrix.translate(definition.pivotX(), definition.pivotY(), definition.pivotZ());
            matrix.rotate(rotation).scale(xScale, yScale, zScale);
            matrix.translate(-definition.pivotX(), -definition.pivotY(), -definition.pivotZ());
        } else matrix.rotate(rotation).scale(xScale, yScale, zScale);
        return matrix;
    }

    /**
     * Applies this bone's local transform, including any baked parent transform.
     * Normals use the inverse transpose of the local linear transform. If that transform
     * is singular or its inverse is non-finite, its normal contribution is identity:
     * the incoming normal matrix is preserved while positions still receive the transform.
     */
    public void applyLocalTransform(Matrix4f pose, Matrix3f normal) {
        Matrix4f foldedParentTransform = definition.foldedParentTransform();
        if (foldedParentTransform != null) {
            pose.mul(foldedParentTransform);
            localNormalTransform.set(foldedParentTransform).rotate(rotation);
        } else {
            localNormalTransform.rotation(rotation);
        }
        if (x != 0 || y != 0 || z != 0) pose.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        if (definition.rotateAroundPivot()) {
            pose.translate(definition.pivotX(), definition.pivotY(), definition.pivotZ());
            pose.rotate(rotation);
            pose.scale(xScale, yScale, zScale);
            pose.translate(-definition.pivotX(), -definition.pivotY(), -definition.pivotZ());
        } else {
            pose.rotate(rotation);
            pose.scale(xScale, yScale, zScale);
        }
        localNormalTransform.scale(xScale, yScale, zScale);
        normal.mul(NormalTransforms.inverseTransposeOrIdentity(localNormalTransform));
    }

    public Matrix4f getBindLocalTransform() {
        Matrix4f bindLocalTransform = definition.bindLocalTransform();
        return bindLocalTransform == null ? new Matrix4f() : new Matrix4f(bindLocalTransform);
    }

    public Matrix4f getGlobalTransform(BoneTreeInstance instance) { return instance.getGlobalTransform(index()); }

    public BoneTransform getBoneTransform() {
        return new BoneTransform(index(), new Vector3f(x, y, z), new BindRotationView(rotation, rotationInEuler),
                new Vector3f(xScale, yScale, zScale));
    }

    private record BindRotationView(Quaternionf quaternion, Vector3f euler) implements RotationView {
        private BindRotationView(Quaternionf quaternion, Vector3f euler) {
            this.quaternion = new Quaternionf(quaternion); this.euler = new Vector3f(euler);
        }
        @Override public Vector3f asEulerAngle() { return new Vector3f(euler); }
        @Override public Quaternionf asQuaternion() { return new Quaternionf(quaternion); }
    }
}
