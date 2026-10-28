package mods.mewkooo.bedrockmodel.v1.common.resource.pojo;

import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/** Explicit display contexts, including Bedrock-only and extension names. */
public final class ItemDisplayTransforms {
    public static final ItemDisplayTransforms NONE = new ItemDisplayTransforms(Map.of());

    private final Map<String, ItemDisplayTransform> transforms;

    public ItemDisplayTransforms(Map<String, ItemDisplayTransform> transforms) {
        this.transforms = Map.copyOf(transforms);
    }

    /** Returns an immutable map of the contexts supplied by the model. */
    public Map<String, ItemDisplayTransform> getTransforms() {
        return transforms;
    }

    /** Returns null for an unspecified context; no right-to-left hand fallback is applied. */
    @Nullable
    public ItemDisplayTransform getTransform(String context) {
        return transforms.get(Objects.requireNonNull(context, "context"));
    }

    @Nullable
    public ItemDisplayTransform getTransform(ItemDisplayContext context) {
        return getTransform(Objects.requireNonNull(context, "context").getSerializedName());
    }
}
