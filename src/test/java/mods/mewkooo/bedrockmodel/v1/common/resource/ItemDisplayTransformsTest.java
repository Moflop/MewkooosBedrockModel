package mods.mewkooo.bedrockmodel.v1.common.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ItemDisplayTransforms;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemDisplayTransformsTest {
    @Test
    void readsBedrockContextsAndPreservesExtensionNames() {
        ItemDisplayTransforms transforms = parse("""
                {
                  "gui": {"rotation": [30, 225, 0], "fit_to_frame": false},
                  "firstperson_righthand": {"translation": [1, 2, 3]},
                  "firstperson_lefthand": {"translation": [4, 5, 6]},
                  "thirdperson_righthand": {"scale": [0.5, 1, 2]},
                  "thirdperson_lefthand": {"scale": [2, 1, 0.5]},
                  "ground": {}, "fixed": {}, "head": {}, "embedded": {}, "shelf": {},
                  "custom_context": {"rotation_pivot": [8, 4, 2], "scale_pivot": [-1, -2, -3]}
                }
                """);

        assertEquals(11, transforms.getTransforms().size());
        assertEquals(new Vector3f(30, 225, 0), transforms.getTransform(ItemDisplayContext.GUI).getRotation());
        assertFalse(transforms.getTransform(ItemDisplayContext.GUI).isFitToFrame());
        assertEquals(new Vector3f(1, 2, 3), transforms.getTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).getTranslation());
        assertEquals(new Vector3f(4, 5, 6), transforms.getTransform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND).getTranslation());
        assertEquals(new Vector3f(0.5f, 1, 2), transforms.getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND).getScale());
        assertEquals(new Vector3f(2, 1, 0.5f), transforms.getTransform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND).getScale());
        assertNotNull(transforms.getTransform(ItemDisplayContext.GROUND));
        assertNotNull(transforms.getTransform(ItemDisplayContext.FIXED));
        assertNotNull(transforms.getTransform(ItemDisplayContext.HEAD));
        assertNotNull(transforms.getTransform("embedded"));
        assertNotNull(transforms.getTransform("shelf"));
        assertEquals(new Vector3f(8, 4, 2), transforms.getTransform("custom_context").getRotationPivot());
        assertEquals(new Vector3f(-1, -2, -3), transforms.getTransform("custom_context").getScalePivot());
        assertNull(transforms.getTransform(ItemDisplayContext.NONE));
    }

    @Test
    void missingComponentsPreserveContextDefaultsAndHandsRemainIndependent() {
        ItemDisplayTransforms transforms = parse("""
                {"gui": {}, "firstperson_righthand": {"scale": [0, 0.5, 1]}}
                """);
        ItemDisplayTransform gui = transforms.getTransform("gui");

        assertNull(gui.getRotation());
        assertNull(gui.getTranslation());
        assertNull(gui.getScale());
        assertEquals(new Vector3f(), gui.getRotationPivot());
        assertEquals(new Vector3f(), gui.getScalePivot());
        assertTrue(gui.isFitToFrame());
        assertEquals(new Vector3f(0, 0.5f, 1), transforms.getTransform("firstperson_righthand").getScale());
        assertNull(transforms.getTransform("firstperson_lefthand"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"item_display_transforms\": null}", "{\"item_display_transforms\": {}}"})
    void missingNullOrEmptyDisplayObjectMeansNoOverrides(String geometry) {
        BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson(
                "{\"format_version\":\"1.21.0\",\"minecraft:geometry\":[" + geometry + "]}", BedrockModelPOJO.class);

        assertSame(ItemDisplayTransforms.NONE, pojo.getItemDisplayTransforms());
    }

    @Test
    void readsOnlyTheSelectedGeometryAndDoesNotMergeOtherEntries() {
        BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson("""
                {
                  "format_version": "1.21.0",
                  "minecraft:geometry": [
                    {"item_display_transforms": {"gui": {"translation": [16, 0, 0]}}},
                    {"item_display_transforms": {"head": {}, "gui": {"translation": [32, 0, 0]}}}
                  ]
                }
                """, BedrockModelPOJO.class);

        assertSame(pojo.getGeometryModelNew().getItemDisplayTransforms(), pojo.getItemDisplayTransforms());
        assertEquals(new Vector3f(16, 0, 0), pojo.getItemDisplayTransforms().getTransform("gui").getTranslation());
        assertNull(pojo.getItemDisplayTransforms().getTransform("head"));
    }

    @Test
    void immutableDataCanBeSharedBetweenModels() {
        Vector3f source = new Vector3f(1, 2, 3);
        ItemDisplayTransform transform = new ItemDisplayTransform(source, source, source, source, source, true);
        Map<String, ItemDisplayTransform> sourceMap = new HashMap<>(Map.of("gui", transform));
        ItemDisplayTransforms transforms = new ItemDisplayTransforms(sourceMap);

        source.zero();
        sourceMap.clear();
        transform.getRotation().zero();
        transform.getTranslation().zero();
        transform.getScale().zero();
        transform.getRotationPivot().zero();
        transform.getScalePivot().zero();

        assertEquals(new Vector3f(1, 2, 3), transform.getRotation());
        assertEquals(new Vector3f(1, 2, 3), transform.getTranslation());
        assertEquals(new Vector3f(1, 2, 3), transform.getScale());
        assertEquals(new Vector3f(1, 2, 3), transform.getRotationPivot());
        assertEquals(new Vector3f(1, 2, 3), transform.getScalePivot());
        assertSame(transform, transforms.getTransform("gui"));
        assertThrows(UnsupportedOperationException.class, () -> transforms.getTransforms().clear());
    }

    @Test
    void gsonRoundTripKeepsBedrockFieldNamesAndUnsetComponents() {
        ItemDisplayTransforms transforms = parse("""
                {"gui": {"translation": [16, -8, 4], "rotation_pivot": [1, 2, 3],
                "scale_pivot": [4, 5, 6], "fit_to_frame": false}}
                """);
        String json = GsonUtil.CLIENT_GSON.toJson(transforms);
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();

        assertFalse(object.has("transforms"));
        assertTrue(object.getAsJsonObject("gui").has("rotation_pivot"));
        assertTrue(object.getAsJsonObject("gui").has("scale_pivot"));
        assertFalse(object.getAsJsonObject("gui").has("rotation"));
        ItemDisplayTransform restored = parse(json).getTransform("gui");
        assertEquals(new Vector3f(16, -8, 4), restored.getTranslation());
        assertEquals(new Vector3f(1, 2, 3), restored.getRotationPivot());
        assertEquals(new Vector3f(4, 5, 6), restored.getScalePivot());
        assertNull(restored.getRotation());
        assertNull(restored.getScale());
        assertFalse(restored.isFitToFrame());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]", "{\"gui\": null}", "{\"gui\": []}",
            "{\"gui\": {\"rotation\": [1, 2]}}",
            "{\"gui\": {\"translation\": [1, 2, 3, 4]}}",
            "{\"gui\": {\"scale\": 1}}",
            "{\"gui\": {\"rotation_pivot\": [1, \"2\", 3]}}",
            "{\"gui\": {\"scale_pivot\": [1, null, 3]}}",
            "{\"gui\": {\"translation\": [1e100, 0, 0]}}",
            "{\"gui\": {\"fit_to_frame\": \"false\"}}"
    })
    void malformedDisplayTransformsReportJsonErrors(String json) {
        JsonSyntaxException exception = assertThrows(JsonSyntaxException.class, () -> parse(json));
        assertTrue(exception.getMessage().contains("item_display_transforms"));
    }

    private static ItemDisplayTransforms parse(String json) {
        return GsonUtil.CLIENT_GSON.fromJson(json, ItemDisplayTransforms.class);
    }
}
