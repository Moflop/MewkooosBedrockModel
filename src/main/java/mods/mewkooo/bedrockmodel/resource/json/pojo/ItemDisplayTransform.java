package mods.mewkooo.bedrockmodel.resource.json.pojo;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Objects;

/**
 * Immutable display overrides, preserving JSON values without coordinate or unit conversion.
 * Translation uses pixels and rotation uses degrees.
 * Unspecified translation, rotation and scale stay null because their defaults depend on the display context.
 */
public final class ItemDisplayTransform {
    /** Neutral transform with unit scale and GUI auto-fitting disabled. */
    public static final ItemDisplayTransform NONE = new ItemDisplayTransform(
            new Vector3f(), new Vector3f(), new Vector3f(1),
            new Vector3f(), new Vector3f(), false);

    @Nullable
    private final Vector3f rotation;
    @Nullable
    private final Vector3f translation;
    @Nullable
    private final Vector3f scale;
    @SerializedName("rotation_pivot")
    private final Vector3f rotationPivot;
    @SerializedName("scale_pivot")
    private final Vector3f scalePivot;
    @SerializedName("fit_to_frame")
    private final boolean fitToFrame;

    public ItemDisplayTransform(@Nullable Vector3fc rotation, @Nullable Vector3fc translation,
                                @Nullable Vector3fc scale, Vector3fc rotationPivot,
                                Vector3fc scalePivot, boolean fitToFrame) {
        this.rotation = copy(rotation);
        this.translation = copy(translation);
        this.scale = copy(scale);
        this.rotationPivot = new Vector3f(Objects.requireNonNull(rotationPivot, "rotationPivot"));
        this.scalePivot = new Vector3f(Objects.requireNonNull(scalePivot, "scalePivot"));
        this.fitToFrame = fitToFrame;
    }

    @Nullable
    public Vector3f getRotation() {
        return copy(rotation);
    }

    @Nullable
    public Vector3f getTranslation() {
        return copy(translation);
    }

    @Nullable
    public Vector3f getScale() {
        return copy(scale);
    }

    public Vector3f getRotationPivot() {
        return new Vector3f(rotationPivot);
    }

    public Vector3f getScalePivot() {
        return new Vector3f(scalePivot);
    }

    /** GUI auto-fitting flag; other display contexts do not use it. */
    public boolean isFitToFrame() {
        return fitToFrame;
    }

    @Nullable
    private static Vector3f copy(@Nullable Vector3fc vector) {
        return vector == null ? null : new Vector3f(vector);
    }
}
