package mods.mewkooo.bedrockmodel.core.json.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class BedrockAnimationFile {
    @SerializedName("format_version")
    private String version;

    @SerializedName("animations")
    private Map<String, BedrockAnimationPOJO> animations;

    public String getVersion() {
        return version;
    }

    public Map<String, BedrockAnimationPOJO> getAnimations() {
        return animations;
    }
}

