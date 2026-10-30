package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Read-only view of the models produced by a completed resource reload.
 *
 * <p>This contract intentionally exposes {@link BedrockModelResource} rather
 * than the reload listener or JSON parser implementation.  A caller may keep
 * the view for the current reload generation, but must obtain a new resource
 * after the next reload.</p>
 */
public interface BedrockModelAccess {
    /** Returns the loaded resource, or {@code null} before reload/when loading failed. */
    @Nullable
    BedrockModelResource getResource(ResourceLocation modelId);

    /** Returns animations attached to a model, or {@code null} when unavailable. */
    @Nullable
    default List<AnimationClip> getAnimations(ResourceLocation modelId, ResourceLocation animationId) {
        BedrockModelResource resource = getResource(modelId);
        return resource == null ? null : resource.getAnimations(animationId);
    }

    /** Returns the resources loaded in the current reload generation. */
    Map<ResourceLocation, BedrockModelResource> getAllResources();
}
