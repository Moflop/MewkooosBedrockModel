package mods.mewkooo.bedrockmodel.core.model.runtime;

import mods.mewkooo.bedrockmodel.core.model.LocatorData;
import mods.mewkooo.bedrockmodel.core.model.tree.ICube;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBoneDefinition;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;

public class TreeModelInstance extends BoneTreeInstance {
    private final TreeBedrockModel baseModel;

    public TreeModelInstance(TreeBedrockModel baseModel) {
        super(baseModel.bones(), baseModel.getBindPose());
        this.baseModel = baseModel;
    }

    public TreeBedrockModel baseModel() {
        return baseModel;
    }

    @Override
    public int getIndex(String boneName) {
        return baseModel.getIndex(boneName);
    }

    @Override
    protected void rayTraceCubes(ModelRayTracer tracer) {
        for (TreeBoneDefinition definition : baseModel.bones()) {
            if (definition.ownCubeBounds() == null) {
                continue;
            }
            BoneState bone = getBone(definition.index());
            if (bone == null || !bone.visible) {
                continue;
            }
            Matrix4f boneTransform = getGlobalTransform(definition.index());
            if (!tracer.traceGroup(definition.ownCubeBounds(), boneTransform)) {
                continue;
            }
            ICube[] cubes = definition.cubes();
            for (int cubeIndex = 0; cubeIndex < cubes.length; cubeIndex++) {
                ICube cube = cubes[cubeIndex];
                tracer.traceCube(definition.index(), cubeIndex, cube.x(), cube.y(), cube.z(), cube.width(), cube.height(), cube.depth(),
                        boneTransform, cubeLocalTransform(cube));
            }
        }
    }

    private static Matrix4f cubeLocalTransform(ICube cube) {
        Matrix4f transform = new Matrix4f();
        if (cube.hasRotation()) {
            float[] pivot = cube.pivot();
            transform.translate(pivot[0], pivot[1], pivot[2]);
            transform.rotate(cube.rotation());
            transform.translate(-pivot[0], -pivot[1], -pivot[2]);
        }
        return transform;
    }

    @Nullable
    public Matrix4f getLocatorTransform(String locatorName) {
        Map.Entry<Integer, LocatorData> locator = baseModel.locator(locatorName);
        if (locator == null) return null;
        Matrix4f transform = new Matrix4f(getGlobalTransform(locator.getKey()));
        TreeBedrockModel.applyLocatorLocalTransform(transform, locator.getValue());
        return transform;
    }

    @Nullable
    public Matrix4f getQueryTransform(String queryName) {
        BoneState bone = getBone(queryName);
        return bone == null ? null : getGlobalTransform(bone.index());
    }
}

