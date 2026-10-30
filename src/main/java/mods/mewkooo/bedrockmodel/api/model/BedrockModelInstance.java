package mods.mewkooo.bedrockmodel.api.model;

import com.maydaymemory.mae.basic.Skeleton;

/** Mutable pose state owned by one entity or other animated model consumer. */
public interface BedrockModelInstance extends Skeleton {
    /** Restores this instance to its model's bind pose. */
    void resetPose();

    /** Returns the bone index, or {@code -1} when the name is not present. */
    int getIndex(String boneName);
}
