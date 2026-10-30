package mods.mewkooo.bedrockmodel.example;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.model.runtime.TreeModelInstance;
import mods.mewkooo.bedrockmodel.model.tree.TreeBedrockModel;
import mods.mewkooo.bedrockmodel.resource.BedrockModelRepository;
import mods.mewkooo.bedrockmodel.resource.event.RegisterBedrockResourcesEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;

/** Compile-only example kept in sync with docs/developer-guide.md. */
@EventBusSubscriber(modid = "examplemod", bus = EventBusSubscriber.Bus.MOD)
public final class ExampleBedrockResources {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
    private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");

    private ExampleBedrockResources() {
    }

    @SubscribeEvent
    public static void register(RegisterBedrockResourcesEvent event) {
        event.treeModel(MODEL).animation(ANIMATION).register();
    }

    public static void render(PoseStack poseStack, VertexConsumer vertexConsumer,
                              int packedLight, int packedOverlay, float timeSeconds) {
        BedrockModelRepository repository = BedrockModelRepository.getInstance();
        if (repository == null) {
            return;
        }
        TreeBedrockModel model = repository.getTreeModel(MODEL);
        if (model == null) {
            return;
        }
        TreeModelInstance instance = model.createInstance();
        List<BedrockAnimation> clips = repository.getAnimations(MODEL, ANIMATION);
        if (clips != null && !clips.isEmpty()) {
            instance.resetPose();
            instance.applyPose(clips.get(0).evaluate(timeSeconds));
        }
        instance.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay);
    }
}
