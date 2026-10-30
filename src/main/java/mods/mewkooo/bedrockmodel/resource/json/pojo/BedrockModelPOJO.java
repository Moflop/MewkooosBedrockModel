package mods.mewkooo.bedrockmodel.resource.json.pojo;

import mods.mewkooo.bedrockmodel.resource.json.exclusion.NeedForRootMotion;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;

public class BedrockModelPOJO {
    @SerializedName("format_version")
    @NeedForRootMotion
    private String formatVersion;

    @SerializedName("geometry.model")
    @Nullable
    @NeedForRootMotion
    private GeometryModelLegacy geometryModelLegacy;

    @SerializedName("minecraft:geometry")
    @Nullable
    @NeedForRootMotion
    private GeometryModelNew[] geometryModelNew;

    public String getFormatVersion() {
        return formatVersion;
    }

    @Nullable
    public GeometryModelLegacy getGeometryModelLegacy() {
        return geometryModelLegacy;
    }

    @Nullable
    public GeometryModelNew getGeometryModelNew() {
        if (geometryModelNew == null || geometryModelNew.length == 0) {
            return null;
        }
        return geometryModelNew[0];
    }

    /** Display settings for the same geometry selected by {@link #getGeometryModelNew()}. */
    public ItemDisplayTransforms getItemDisplayTransforms() {
        GeometryModelNew geometry = getGeometryModelNew();
        return geometry == null ? ItemDisplayTransforms.NONE : geometry.getItemDisplayTransforms();
    }
}
