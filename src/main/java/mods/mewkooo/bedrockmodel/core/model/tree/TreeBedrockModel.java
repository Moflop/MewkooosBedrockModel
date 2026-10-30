package mods.mewkooo.bedrockmodel.core.model.tree;

import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.api.model.AnimatedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.LocatorData;
import mods.mewkooo.bedrockmodel.core.model.runtime.TreeModelInstance;
import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/** Shared, side independent tree model definition. Rendering lives in the client package. */
public class TreeBedrockModel implements AnimatedBedrockModel {
    private final TreeBoneDefinition[] bones;
    private final Map<String, Integer> boneIndexByName;
    private final Map<String, Map.Entry<Integer, LocatorData>> locatorByName;
    private final Pose bindPose;
    private final AABB renderBoundingBox;
    private final ItemDisplayTransforms itemDisplayTransforms;

    public TreeBedrockModel(TreeBoneDefinition[] bones, Map<String, Integer> boneIndexByName,
                            Map<String, Map.Entry<Integer, LocatorData>> locatorByName, Pose bindPose,
                            AABB renderBoundingBox) {
        this(bones, boneIndexByName, locatorByName, bindPose, renderBoundingBox, ItemDisplayTransforms.NONE);
    }

    public TreeBedrockModel(TreeBoneDefinition[] bones, Map<String, Integer> boneIndexByName,
                            Map<String, Map.Entry<Integer, LocatorData>> locatorByName, Pose bindPose,
                            AABB renderBoundingBox, ItemDisplayTransforms itemDisplayTransforms) {
        this.bones = bones.clone();
        for (TreeBoneDefinition bone : this.bones) bone.linkReferences(this.bones);
        this.boneIndexByName = Map.copyOf(boneIndexByName);
        this.locatorByName = Map.copyOf(locatorByName);
        this.bindPose = bindPose;
        this.renderBoundingBox = renderBoundingBox;
        this.itemDisplayTransforms = Objects.requireNonNull(itemDisplayTransforms, "itemDisplayTransforms");
    }

    public static TreeBedrockModel bake(BedrockModelPOJO pojo) { return TreeBedrockModelBaker.bake(pojo); }
    @Override public ModelType kind() { return ModelType.TREE; }
    @Override public TreeModelInstance createInstance() { return new TreeModelInstance(this); }
    public TreeBoneDefinition[] bones() { return bones.clone(); }
    public TreeBoneDefinition bone(int index) { return bones[index]; }
    public int boneCount() { return bones.length; }
    public AABB getRenderBoundingBox() { return renderBoundingBox; }
    public ItemDisplayTransforms getItemDisplayTransforms() { return itemDisplayTransforms; }
    public Pose getBindPose() { return bindPose; }

    @Override
    public int getIndex(String boneName) { return boneIndexByName.getOrDefault(boneName, -1); }

    @Nullable
    public Map.Entry<Integer, LocatorData> locator(String locatorName) { return locatorByName.get(locatorName); }

    public TreeBoneDefinition[] roots() {
        ArrayList<TreeBoneDefinition> roots = new ArrayList<>();
        for (TreeBoneDefinition bone : bones) if (bone.parentIndex() < 0) roots.add(bone);
        return roots.toArray(TreeBoneDefinition[]::new);
    }

    public static void applyLocatorLocalTransform(Matrix4f transform, LocatorData locator) {
        float[] offset = locator.offset();
        if (offset[0] != 0 || offset[1] != 0 || offset[2] != 0) transform.translate(offset[0], offset[1], offset[2]);
        float[] rotation = locator.rotation();
        if (rotation[0] != 0 || rotation[1] != 0 || rotation[2] != 0) {
            transform.rotate(new Quaternionf().rotateZ((float) Math.toRadians(rotation[2]))
                    .rotateY((float) Math.toRadians(rotation[1]))
                    .rotateX((float) Math.toRadians(rotation[0])));
        }
    }

    public static Map.Entry<Integer, LocatorData> locatorEntry(int boneIndex, LocatorData locatorData) {
        return new AbstractMap.SimpleImmutableEntry<>(boneIndex, locatorData);
    }
}
