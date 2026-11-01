package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A loaded model and its animation clips. The concrete model is selected by {@link #kind()}. */
public record BedrockModelResource(
        BedrockModel model,
        Map<ResourceLocation, List<AnimationClip>> animations
) {
    public BedrockModelResource {
        Objects.requireNonNull(model, "model");
        Map<ResourceLocation, List<AnimationClip>> immutableAnimations = new LinkedHashMap<>();
        animations.forEach((id, clips) -> immutableAnimations.put(id, List.copyOf(clips)));
        animations = Map.copyOf(immutableAnimations);
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

