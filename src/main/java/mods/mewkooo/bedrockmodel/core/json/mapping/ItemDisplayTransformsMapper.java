package mods.mewkooo.bedrockmodel.core.json.mapping;

import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.core.json.pojo.ItemDisplayTransformData;
import mods.mewkooo.bedrockmodel.core.json.pojo.ItemDisplayTransformsData;

import java.util.LinkedHashMap;
import java.util.Map;

/** Converts parser-owned JSON data into public model metadata. */
public final class ItemDisplayTransformsMapper {
    private ItemDisplayTransformsMapper() {}

    public static ItemDisplayTransforms toApi(ItemDisplayTransformsData data) {
        if (data.transforms().isEmpty()) {
            return ItemDisplayTransforms.NONE;
        }

        Map<String, ItemDisplayTransform> transforms = new LinkedHashMap<>();
        data.transforms().forEach((context, transform) -> transforms.put(context, toApi(transform)));
        return new ItemDisplayTransforms(transforms);
    }

    private static ItemDisplayTransform toApi(ItemDisplayTransformData data) {
        return new ItemDisplayTransform(data.rotation(), data.translation(), data.scale(),
                data.rotationPivot(), data.scalePivot(), data.fitToFrame());
    }
}
