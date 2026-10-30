package mods.mewkooo.bedrockmodel.resource;

import mods.mewkooo.bedrockmodel.model.BoneIndexProvider;
import mods.mewkooo.bedrockmodel.animation.BedrockAnimation;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record BedrockModelResource(
        BoneIndexProvider model,
        ModelType kind,
        Map<ResourceLocation, List<BedrockAnimation>> animations
) {
    public BedrockModelResource {
        animations = Map.copyOf(animations);
    }

    @Nullable
    public List<BedrockAnimation> getAnimations(ResourceLocation animationId) {
        return animations.get(animationId);
    }

    @UnmodifiableView
    public Map<ResourceLocation, List<BedrockAnimation>> getAllAnimations() {
        return Collections.unmodifiableMap(animations);
    }
}
