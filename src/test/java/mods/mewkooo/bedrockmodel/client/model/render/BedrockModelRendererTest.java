package mods.mewkooo.bedrockmodel.client.model.render;

import com.mojang.blaze3d.vertex.PoseStack;
import mods.mewkooo.bedrockmodel.api.model.AnimatedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BedrockModelRendererTest {
    private static final String EMPTY_MODEL = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [{
                "description": {"identifier":"geometry.empty", "texture_width":16, "texture_height":16},
                "bones": []
              }]
            }
            """;

    @Test
    void rendersInstancesCreatedThroughThePublicModelContract() {
        BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson(EMPTY_MODEL, BedrockModelPOJO.class);
        assertRenders(TreeBedrockModel.bake(pojo));
        assertRenders(BakedBedrockModel.bake(pojo));
    }

    private static void assertRenders(BedrockModel model) {
        AnimatedBedrockModel animated = assertInstanceOf(AnimatedBedrockModel.class, model);
        BedrockModelInstance instance = animated.createInstance();

        BedrockModelRenderer.render(instance, new PoseStack(), null, 0, 0);
    }
}
