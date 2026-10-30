package mods.mewkooo.bedrockmodel.core.json.pojo;

import mods.mewkooo.bedrockmodel.core.json.exclusion.ClientOnly;
import mods.mewkooo.bedrockmodel.core.json.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

public class GeometryModelNew {
    @SerializedName("description")
    @ClientOnly
    private Description description;

    @SerializedName("bones")
    @NeedForRootMotion
    private BonesItem[] bones;

    @SerializedName("item_display_transforms")
    @ClientOnly
    private ItemDisplayTransformsData itemDisplayTransforms = ItemDisplayTransformsData.NONE;

    public Description getDescription() {
        return description;
    }

    public BonesItem[] getBones() {
        return bones;
    }

    public ItemDisplayTransformsData getItemDisplayTransformsData() {
        return itemDisplayTransforms == null ? ItemDisplayTransformsData.NONE : itemDisplayTransforms;
    }

    public void deco() {
        if (bones == null) {
            return;
        }
        for (BonesItem bonesItem : this.bones) {
            if (bonesItem.getCubes() == null) {
                continue;
            }
            for (CubesItem cubesItem : bonesItem.getCubes()) {
                if (!cubesItem.isHasMirror()) {
                    cubesItem.setMirror(bonesItem.isMirror());
                }
            }
        }
    }
}


