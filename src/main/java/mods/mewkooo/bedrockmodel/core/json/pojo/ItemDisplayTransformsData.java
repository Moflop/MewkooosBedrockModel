package mods.mewkooo.bedrockmodel.core.json.pojo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed Bedrock JSON display-context map, kept separate from public model metadata. */
public record ItemDisplayTransformsData(Map<String, ItemDisplayTransformData> transforms) {
    public static final ItemDisplayTransformsData NONE = new ItemDisplayTransformsData(Map.of());

    public ItemDisplayTransformsData {
        transforms = Collections.unmodifiableMap(new LinkedHashMap<>(transforms));
    }
}
