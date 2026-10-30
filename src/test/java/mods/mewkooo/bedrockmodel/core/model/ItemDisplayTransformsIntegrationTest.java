package mods.mewkooo.bedrockmodel.core.model;

import mods.mewkooo.bedrockmodel.client.legacy.model.BedrockModel;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.json.pojo.ItemDisplayTransformsData;
import mods.mewkooo.bedrockmodel.core.json.mapping.ItemDisplayTransformsMapper;
import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ItemDisplayTransformsIntegrationTest {
    @Test
    void modelConstructionPreservesDisplayTransformsWithBones() {
        BedrockModelPOJO pojo = loadModel(modernModel("\"bones\": [{\"name\": \"root\", \"pivot\": [1, 2, 3]}],"));

        assertModelsPreserveDisplayTransforms(pojo);
    }

    @Test
    void modelConstructionPreservesDisplayTransformsWithoutBones() {
        assertModelsPreserveDisplayTransforms(loadModel(modernModel("\"bones\": [],")));
        assertModelsPreserveDisplayTransforms(loadModel(modernModel("")));
    }

    @Test
    void modernModelsWithoutDisplayTransformsRemainCompatible() {
        BedrockModelPOJO pojo = loadModel("""
                {
                  "format_version": "1.12.0",
                  "minecraft:geometry": [{
                    "bones": [{"name": "root", "pivot": [0, 0, 0]}]
                  }]
                }
                """);

        assertModelsHaveNoDisplayTransforms(pojo);
    }

    @Test
    void legacyModelsRemainCompatible() {
        BedrockModelPOJO pojo = loadModel("""
                {
                  "format_version": "1.10.0",
                  "geometry.model": {
                    "bones": [{"name": "root", "pivot": [0, 0, 0]}]
                  }
                }
                """);

        assertModelsHaveNoDisplayTransforms(pojo);
    }

    @Test
    void rootMotionLoadingSkipsDisplayTransformsButKeepsBones() {
        BedrockModelPOJO pojo = GsonUtil.SERVER_GSON_FOR_ROOT_MOTION.fromJson(
                modernModel("\"bones\": [{\"name\": \"root\", \"pivot\": [1, 2, 3]}],"),
                BedrockModelPOJO.class);

        assertNotNull(pojo.getGeometryModelNew());
        assertEquals(1, pojo.getGeometryModelNew().getBones().length);
        assertEquals("root", pojo.getGeometryModelNew().getBones()[0].getName());
        assertArrayEquals(new float[]{1, 2, 3}, pojo.getGeometryModelNew().getBones()[0].getPivot());
        assertSame(ItemDisplayTransformsData.NONE, pojo.getItemDisplayTransformsData());
        assertModelsHaveNoDisplayTransforms(pojo);
    }

    private static void assertModelsPreserveDisplayTransforms(BedrockModelPOJO pojo) {
        assertGuiTransform(ItemDisplayTransformsMapper.toApi(pojo.getItemDisplayTransformsData()));
        assertGuiTransform(new BedrockModel(pojo).getItemDisplayTransforms());
        assertGuiTransform(TreeBedrockModel.bake(pojo).getItemDisplayTransforms());
        assertGuiTransform(BakedBedrockModel.bake(pojo).getItemDisplayTransforms());
    }

    private static void assertGuiTransform(ItemDisplayTransforms transforms) {
        assertEquals(1, transforms.getTransforms().size());
        ItemDisplayTransform gui = transforms.getTransform("gui");
        assertNotNull(gui);
        assertEquals(new Vector3f(30, 45, 60), gui.getRotation());
        assertEquals(new Vector3f(16, -8, 4), gui.getTranslation());
        assertEquals(new Vector3f(0.5f, 1.5f, 2), gui.getScale());
        assertEquals(new Vector3f(1, 2, 3), gui.getRotationPivot());
        assertEquals(new Vector3f(4, 5, 6), gui.getScalePivot());
        assertFalse(gui.isFitToFrame());
    }

    private static void assertModelsHaveNoDisplayTransforms(BedrockModelPOJO pojo) {
        assertTrue(pojo.getItemDisplayTransformsData().transforms().isEmpty());
        assertTrue(new BedrockModel(pojo).getItemDisplayTransforms().getTransforms().isEmpty());
        assertTrue(TreeBedrockModel.bake(pojo).getItemDisplayTransforms().getTransforms().isEmpty());
        assertTrue(BakedBedrockModel.bake(pojo).getItemDisplayTransforms().getTransforms().isEmpty());
    }

    private static BedrockModelPOJO loadModel(String json) {
        return GsonUtil.CLIENT_GSON.fromJson(json, BedrockModelPOJO.class);
    }

    private static String modernModel(String bonesMember) {
        return """
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [{
                    %s
                    "item_display_transforms": {
                      "gui": {
                        "rotation": [30, 45, 60],
                        "translation": [16, -8, 4],
                        "scale": [0.5, 1.5, 2],
                        "rotation_pivot": [1, 2, 3],
                        "scale_pivot": [4, 5, 6],
                        "fit_to_frame": false
                      }
                    }
                  }]
                }
                """.formatted(bonesMember);
    }
}

