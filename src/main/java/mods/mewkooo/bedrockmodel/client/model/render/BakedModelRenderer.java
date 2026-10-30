package mods.mewkooo.bedrockmodel.client.model.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBoneDefinition;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedGeometryChunk;
import mods.mewkooo.bedrockmodel.core.model.runtime.BakedModelInstance;
import mods.mewkooo.bedrockmodel.core.model.runtime.BoneState;
import net.minecraft.client.renderer.LightTexture;

/** Client side renderer for shared baked model data. */
public final class BakedModelRenderer {
    private static final int MAX_LIGHT_TEXTURE = LightTexture.pack(15, 15);
    private BakedModelRenderer() {}

    public static void render(BakedModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                              int packedLight, int packedOverlay) {
        render(instance, poseStack, consumer, packedLight, packedOverlay, 1, 1, 1, 1, true, false);
    }

    public static void render(BakedModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                              int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                              boolean quadsPass, boolean skipNormalVisibilityCull) {
        BakedBedrockModel model = instance.baseModel();
        BakedGeometryChunk root = model.rootChunk();
        if (root != null) renderChunkForPass(root, poseStack, consumer, packedLight, packedOverlay, red, green, blue, alpha,
                quadsPass, skipNormalVisibilityCull);
        for (BakedBoneDefinition bone : model.bones()) {
            if (bone.parentIndex() < 0) renderBone(instance, bone.index(), poseStack, consumer, packedLight, packedOverlay,
                    red, green, blue, alpha, quadsPass, skipNormalVisibilityCull);
        }
    }

    public static void renderBone(BakedModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha,
                                  boolean quadsPass, boolean skipNormalVisibilityCull) {
        BakedBedrockModel model = instance.baseModel();
        BakedBoneDefinition definition = model.bones()[boneIndex];
        if (quadsPass ? !definition.hasQuadsInTree() : !definition.hasVerticesInTree()) return;
        BoneState bone = instance.getBone(boneIndex);
        if (bone == null || !bone.visible) return;
        int light = bone.illuminated ? MAX_LIGHT_TEXTURE : packedLight;
        poseStack.pushPose();
        bone.applyLocalTransform(poseStack.last().pose(), poseStack.last().normal());
        BakedGeometryChunk chunk = model.chunkForBone(boneIndex);
        if (chunk != null) renderChunkForPass(chunk, poseStack, consumer, light, packedOverlay, red, green, blue, alpha,
                quadsPass, skipNormalVisibilityCull);
        for (int child : definition.children()) renderBone(instance, child, poseStack, consumer, light, packedOverlay,
                red, green, blue, alpha, quadsPass, skipNormalVisibilityCull);
        poseStack.popPose();
    }

    public static void renderSingleBone(BakedModelInstance instance, int boneIndex, PoseStack poseStack, VertexConsumer consumer,
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

    public static void renderChunkForPass(BakedGeometryChunk chunk, PoseStack poseStack, VertexConsumer consumer,
                                          int lightmap, int overlay, float red, float green, float blue, float alpha,
                                          boolean quadsPass, boolean skipNormalVisibilityCull) {
        if (quadsPass) BakedGeometryChunkRenderer.INSTANCE.renderQuadChunk(chunk, poseStack, consumer, lightmap, overlay,
                red, green, blue, alpha, skipNormalVisibilityCull);
        else BakedGeometryChunkRenderer.INSTANCE.renderVertexChunk(chunk, poseStack, consumer, lightmap, overlay,
                red, green, blue, alpha);
    }
}
