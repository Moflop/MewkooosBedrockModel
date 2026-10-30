package mods.mewkooo.bedrockmodel.core.json.pojo;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Parsed Bedrock JSON fields for one display context. */
public record ItemDisplayTransformData(
        @Nullable Vector3f rotation,
        @Nullable Vector3f translation,
        @Nullable Vector3f scale,
        Vector3f rotationPivot,
        Vector3f scalePivot,
        boolean fitToFrame) {
}
