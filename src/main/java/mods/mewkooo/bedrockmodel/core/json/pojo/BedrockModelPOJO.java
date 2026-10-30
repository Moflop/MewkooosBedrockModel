package mods.mewkooo.bedrockmodel.core.json.pojo;

import mods.mewkooo.bedrockmodel.core.json.exclusion.NeedForRootMotion;
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

    /** Parsed display fields for the same geometry selected by {@link #getGeometryModelNew()}. */
    public ItemDisplayTransformsData getItemDisplayTransformsData() {
        GeometryModelNew geometry = getGeometryModelNew();
        return geometry == null ? ItemDisplayTransformsData.NONE : geometry.getItemDisplayTransformsData();
    }
}


