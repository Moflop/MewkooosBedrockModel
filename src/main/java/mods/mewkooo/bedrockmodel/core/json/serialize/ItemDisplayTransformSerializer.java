package mods.mewkooo.bedrockmodel.core.json.serialize;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSyntaxException;
import mods.mewkooo.bedrockmodel.core.json.pojo.ItemDisplayTransformData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Type;

public class ItemDisplayTransformSerializer implements JsonDeserializer<ItemDisplayTransformData>, JsonSerializer<ItemDisplayTransformData> {
    @Override
    public ItemDisplayTransformData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (!json.isJsonObject()) {
            throw new JsonSyntaxException("Expected item display transform to be an object");
        }
        JsonObject object = json.getAsJsonObject();
        Vector3f rotation = readVector(object, "rotation");
        Vector3f translation = readVector(object, "translation");
        Vector3f scale = readVector(object, "scale");
        Vector3f rotationPivot = readVector(object, "rotation_pivot");
        Vector3f scalePivot = readVector(object, "scale_pivot");
        boolean fitToFrame = true;
        JsonElement fit = object.get("fit_to_frame");
        if (fit != null && !fit.isJsonNull()) {
            if (!fit.isJsonPrimitive() || !fit.getAsJsonPrimitive().isBoolean()) {
                throw new JsonSyntaxException("Expected fit_to_frame to be a boolean");
            }
            fitToFrame = fit.getAsBoolean();
        }
        return new ItemDisplayTransformData(rotation, translation, scale,
                rotationPivot == null ? new Vector3f() : rotationPivot,
                scalePivot == null ? new Vector3f() : scalePivot, fitToFrame);
    }

    @Override
    public JsonElement serialize(ItemDisplayTransformData transform, Type typeOfSrc,
                                 JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        writeVector(object, "rotation", transform.rotation());
        writeVector(object, "translation", transform.translation());
        writeVector(object, "scale", transform.scale());
        writeVector(object, "rotation_pivot", transform.rotationPivot());
        writeVector(object, "scale_pivot", transform.scalePivot());
        object.addProperty("fit_to_frame", transform.fitToFrame());
        return object;
    }

    private static void writeVector(JsonObject object, String name, @Nullable Vector3f vector) {
        if (vector == null) {
            return;
        }
        JsonArray array = new JsonArray();
        array.add(vector.x);
        array.add(vector.y);
        array.add(vector.z);
        object.add(name, array);
    }

    @Nullable
    private static Vector3f readVector(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new JsonSyntaxException("Expected " + name + " to be an array of three numbers");
        }
        JsonArray array = element.getAsJsonArray();
        Vector3f result = new Vector3f();
        for (int i = 0; i < 3; i++) {
            JsonElement component = array.get(i);
            if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) {
                throw new JsonSyntaxException("Expected " + name + "[" + i + "] to be a number");
            }
            float value = component.getAsFloat();
            if (!Float.isFinite(value)) {
                throw new JsonSyntaxException("Expected " + name + "[" + i + "] to be finite");
            }
            result.setComponent(i, value);
        }
        return result;
    }
}

