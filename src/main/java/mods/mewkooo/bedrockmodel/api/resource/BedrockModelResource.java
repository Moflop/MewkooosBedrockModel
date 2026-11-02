package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A loaded model and its animation clips. The concrete model is selected by {@link #kind()}. */
public record BedrockModelResource(
        BedrockModel model,
        Map<ResourceLocation, List<AnimationClip>> animations,
        Map<ResourceLocation, Map<String, AnimationClip>> animationsByName
) {
    /**
     * Creates a resource from unnamed clips, preserving the original list API.
     * Named lookup is unavailable; use {@link #fromNamedAnimations} to retain names.
     */
    public BedrockModelResource(BedrockModel model, Map<ResourceLocation, List<AnimationClip>> animations) {
        this(model, animations, Map.of());
    }

    public BedrockModelResource {
        Objects.requireNonNull(model, "model");
        Map<ResourceLocation, List<AnimationClip>> immutableAnimations = new LinkedHashMap<>();
        animations.forEach((id, clips) -> immutableAnimations.put(id, List.copyOf(clips)));
        animations = Map.copyOf(immutableAnimations);

        Map<ResourceLocation, Map<String, AnimationClip>> immutableNames = new LinkedHashMap<>();
        for (var entry : animationsByName.entrySet()) {
            List<AnimationClip> clips = animations.get(entry.getKey());
            if (clips == null) {
                throw new IllegalArgumentException("Named animations have no matching file: " + entry.getKey());
            }
            Set<AnimationClip> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            identities.addAll(clips);
            Map<String, AnimationClip> namedClips = Map.copyOf(entry.getValue());
            if (!identities.containsAll(namedClips.values())) {
                throw new IllegalArgumentException("Named animations must reference clips in the matching list: " + entry.getKey());
            }
            immutableNames.put(entry.getKey(), namedClips);
        }
        animationsByName = Map.copyOf(immutableNames);
    }

    /** Creates both immutable views from the same clip objects without inspecting clip implementations. */
    public static BedrockModelResource fromNamedAnimations(
            BedrockModel model,
            Map<ResourceLocation, ? extends Map<String, ? extends AnimationClip>> animationsByName) {
        Map<ResourceLocation, List<AnimationClip>> animations = new LinkedHashMap<>();
        Map<ResourceLocation, Map<String, AnimationClip>> names = new LinkedHashMap<>();
        animationsByName.forEach((id, clips) -> {
            animations.put(id, List.copyOf(clips.values()));
            names.put(id, new LinkedHashMap<>(clips));
        });
        return new BedrockModelResource(model, animations, names);
    }

    public ModelType kind() {
        return model.kind();
    }

    @Nullable
    public List<AnimationClip> getAnimations(ResourceLocation animationId) {
        return animations.get(animationId);
    }

    /**
     * Returns a clip by its exact name within an animation file, or {@code null}
     * when the file/name is unavailable. The index belongs to this resource's reload generation.
     */
    @Nullable
    public AnimationClip getAnimation(ResourceLocation animationId, String animationName) {
        Map<String, AnimationClip> clips = animationsByName.get(animationId);
        return clips == null ? null : clips.get(animationName);
    }

    public Map<ResourceLocation, List<AnimationClip>> getAllAnimations() {
        return Collections.unmodifiableMap(animations);
    }
}

