package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Stable information supplied to a custom baked-model options callback. */
public record BedrockModelBakeContext(ResourceLocation modelId, Set<String> animatedBones) {
    public BedrockModelBakeContext {
        animatedBones = Set.copyOf(animatedBones);
    }

    public BakerOptions optionsFromAnimations() {
        return animatedBones.isEmpty() ? BakerOptions.defaults() : BakerOptions.ofAnimatedBones(animatedBones);
    }
}

