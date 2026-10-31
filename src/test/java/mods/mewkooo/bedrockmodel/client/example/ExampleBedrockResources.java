package mods.mewkooo.bedrockmodel.client.example;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.AnimatedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.api.resource.RegisterBedrockResourcesEvent;
import mods.mewkooo.bedrockmodel.client.model.render.BedrockModelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.LogicalSide;

import java.util.List;

/** Compile-only example kept in sync with docs/developer-guide.md. */
@EventBusSubscriber(modid = "examplemod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExampleBedrockResources {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");

    private ExampleBedrockResources() {
    }

    @SubscribeEvent
    public static void register(RegisterBedrockResourcesEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        event.treeModel(MODEL).animation(ANIMATION).register();
    }

    public static void render(PoseStack poseStack, VertexConsumer vertexConsumer,
                              int packedLight, int packedOverlay, float timeSeconds) {
        var access = BedrockResources.models(LogicalSide.CLIENT);
        if (access == null) {
            return;
        }
        BedrockModelResource resource = access.getResource(MODEL);
        if (resource == null || !(resource.model() instanceof AnimatedBedrockModel model)) {
            return;
        }
        BedrockModelInstance instance = model.createInstance();
        List<AnimationClip> clips = access.getAnimations(MODEL, ANIMATION);
        if (clips != null && !clips.isEmpty()) {
            instance.resetPose();
            instance.applyPose(clips.get(0).evaluate(timeSeconds));
        }
        BedrockModelRenderer.render(instance, poseStack, vertexConsumer, packedLight, packedOverlay);
    }
}
