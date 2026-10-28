package mods.mewkooo.bedrockmodel.v2.resource;

import mods.mewkooo.bedrockmodel.v1.resource.RawResourceLoader;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record BedrockAnimationEntry(
        RawResourceLoader rawLoader,
        @Nullable ResourceLocation modelId,
        @Nullable BedrockAnimationFactory factory,
        boolean lazy,
        boolean createRuntimeAnimations
) {
}
