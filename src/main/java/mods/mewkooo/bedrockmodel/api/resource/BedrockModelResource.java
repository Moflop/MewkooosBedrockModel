package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A loaded model and its animation clips. The concrete model is selected by {@link #kind()}. */
public record BedrockModelResource(
        BedrockModel model,
        Map<ResourceLocation, List<AnimationClip>> animations
) {
    public BedrockModelResource {
        java.util.Objects.requireNonNull(model, "model");
        animations = Map.copyOf(animations);
    }

    public ModelType kind() {
        return model.kind();
    }

    @Nullable
    public List<AnimationClip> getAnimations(ResourceLocation animationId) {
        return animations.get(animationId);
    }

    public Map<ResourceLocation, List<AnimationClip>> getAllAnimations() {
        return Collections.unmodifiableMap(animations);
    }
}

