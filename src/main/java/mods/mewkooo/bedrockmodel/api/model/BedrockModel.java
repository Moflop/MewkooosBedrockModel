package mods.mewkooo.bedrockmodel.api.model;

import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Common model metadata shared by every representation. */
public interface BedrockModel {
    ModelType kind();

    /** Returns the bone index, or {@code -1} when the name is not present. */
    int getIndex(String boneName);

    /** Returns the model-space bounds, when the source defines them. */
    @Nullable
    AABB getRenderBoundingBox();

    ItemDisplayTransforms getItemDisplayTransforms();
}
