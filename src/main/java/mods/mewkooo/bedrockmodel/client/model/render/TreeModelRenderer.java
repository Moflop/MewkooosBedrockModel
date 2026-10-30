package mods.mewkooo.bedrockmodel.client.model.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.client.compat.acceleratedrendering.AcceleratedRenderingCompat;
import mods.mewkooo.bedrockmodel.core.model.runtime.BoneState;
import mods.mewkooo.bedrockmodel.core.model.runtime.TreeModelInstance;
import mods.mewkooo.bedrockmodel.core.model.tree.ICube;
import mods.mewkooo.bedrockmodel.core.model.tree.PolyMesh;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBoneDefinition;
import net.minecraft.client.renderer.LightTexture;

/** Client side renderer for the shared tree model and runtime state. */
public final class TreeModelRenderer {
    private static final int MAX_LIGHT_TEXTURE = LightTexture.pack(15, 15);

    private TreeModelRenderer() {}

    public static void render(TreeModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                              int packedLight, int packedOverlay) {
        render(instance, poseStack, consumer, packedLight, packedOverlay, 1, 1, 1, 1, true, false);
    }

    public static void renderBoneTree(TreeModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                                      int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                                      boolean quadsPass) {
        render(instance, poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha, quadsPass, false);
    }

    public static void renderToBuffer(TreeModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                                      int packedLight, int packedOverlay) {
        render(instance, poseStack, consumer, packedLight, packedOverlay, 1, 1, 1, 1, true, false);
    }

    public static void render(TreeModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                              boolean quadsPass, boolean skipNormalVisibilityCull) {
        for (TreeBoneDefinition bone : instance.baseModel().roots()) {
            renderBone(instance, bone.index(), poseStack, consumer, packedLight, packedOverlay,
                    red, green, blue, alpha, quadsPass, skipNormalVisibilityCull);
        }
    }

    public static void renderBone(TreeModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                                  boolean quadsPass, boolean skipNormalVisibilityCull) {
        TreeBoneDefinition definition = instance.baseModel().bone(boneIndex);
        if (quadsPass ? !definition.hasQuadsInTree() : !definition.hasVerticesInTree()) return;
        BoneState bone = instance.getBone(boneIndex);
        if (bone == null || !bone.visible) return;

        int light = bone.illuminated ? MAX_LIGHT_TEXTURE : packedLight;
        poseStack.pushPose();
        bone.applyLocalTransform(poseStack.last().pose(), poseStack.last().normal());
        if (quadsPass) {
            ICube[] cubes = definition.cubes();
            if (cubes.length > 0 && !AcceleratedRenderingCompat.renderCubes(definition, consumer, poseStack.last(), light, packedOverlay, red, green, blue, alpha)) TreeGeometryWriter.writeCubes(cubes, consumer, poseStack.last().pose(), poseStack.last().normal(),
                    light, packedOverlay, red, green, blue, alpha, skipNormalVisibilityCull);
        } else {
            PolyMesh[] meshes = definition.polyMeshes();
            if (meshes.length > 0 && !AcceleratedRenderingCompat.renderPolyMeshes(definition, consumer, poseStack.last(), light, packedOverlay, red, green, blue, alpha)) TreeGeometryWriter.writePolyMeshes(meshes, consumer, poseStack.last().pose(), poseStack.last().normal(),
                    light, packedOverlay, red, green, blue, alpha);
        }
        for (int childIndex : definition.children()) {
            renderBone(instance, childIndex, poseStack, consumer, light, packedOverlay,
                    red, green, blue, alpha, quadsPass, skipNormalVisibilityCull);
        }
        poseStack.popPose();
    }

    public static void renderSingleBone(TreeModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
                                        int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                                        boolean quadsPass, boolean skipNormalVisibilityCull) {
        poseStack.pushPose();
        BoneState bone = instance.getBone(boneIndex);
        int parent = bone == null ? -1 : bone.parentIndex();
        if (parent >= 0) {
            poseStack.last().pose().mul(instance.getGlobalTransform(parent));
            poseStack.last().normal().mul(instance.getGlobalNormal(parent));
        }
        renderBone(instance, boneIndex, poseStack, consumer, packedLight, packedOverlay,
                red, green, blue, alpha, quadsPass, skipNormalVisibilityCull);
        poseStack.popPose();
    }
}
