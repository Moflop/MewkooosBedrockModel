package mods.mewkooo.bedrockmodel.client.model.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.core.model.runtime.BakedModelInstance;
import mods.mewkooo.bedrockmodel.core.model.runtime.TreeModelInstance;

import java.util.Objects;

/** Client entry point for drawing instances obtained through the public model API. */
public final class BedrockModelRenderer {
    private BedrockModelRenderer() {
    }

    public static void render(BedrockModelInstance instance, PoseStack poseStack, VertexConsumer consumer,
                              int packedLight, int packedOverlay) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(poseStack, "poseStack");
        if (instance instanceof TreeModelInstance tree) {
            TreeModelRenderer.renderToBuffer(tree, poseStack, consumer, packedLight, packedOverlay);
        } else if (instance instanceof BakedModelInstance baked) {
            BakedModelRenderer.render(baked, poseStack, consumer, packedLight, packedOverlay);
        } else {
            throw new IllegalArgumentException("Unsupported Bedrock model instance: " + instance.getClass().getName());
        }
    }
}
