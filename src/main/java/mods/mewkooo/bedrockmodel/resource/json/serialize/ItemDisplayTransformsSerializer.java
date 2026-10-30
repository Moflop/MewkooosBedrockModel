package mods.mewkooo.bedrockmodel.resource.json.serialize;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import mods.mewkooo.bedrockmodel.resource.json.pojo.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.resource.json.pojo.ItemDisplayTransforms;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

public class ItemDisplayTransformsSerializer implements JsonDeserializer<ItemDisplayTransforms>, JsonSerializer<ItemDisplayTransforms> {
    @Override
    public ItemDisplayTransforms deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected item_display_transforms to be an object");
        }
        Map<String, ItemDisplayTransform> transforms = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            if (!entry.getValue().isJsonObject()) {
                throw new JsonSyntaxException("Expected item_display_transforms." + name + " to be an object");
            }
            try {
                transforms.put(name, context.deserialize(entry.getValue(), ItemDisplayTransform.class));
            } catch (JsonParseException exception) {
                throw new JsonSyntaxException("Invalid item_display_transforms." + name + ": " + exception.getMessage(), exception);
            }
        }
        return transforms.isEmpty() ? ItemDisplayTransforms.NONE : new ItemDisplayTransforms(transforms);
    }

    @Override
    public JsonElement serialize(ItemDisplayTransforms src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        src.getTransforms().forEach((name, transform) -> object.add(name, context.serialize(transform)));
        return object;
    }
}
