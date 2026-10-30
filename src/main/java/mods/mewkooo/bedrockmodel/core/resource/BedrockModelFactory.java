package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BedrockModelFactory {
    private final BedrockAnimationSourceRepository animationSources;
    private final java.util.function.Function<BedrockModelPOJO, ? extends BedrockModel> legacyModelFactory;

    BedrockModelFactory(BedrockAnimationSourceRepository animationSources,
                        java.util.function.Function<BedrockModelPOJO, ? extends BedrockModel> legacyModelFactory) {
        this.animationSources = animationSources;
        this.legacyModelFactory = legacyModelFactory;
    }

    Optional<BedrockModelResource> create(ResourceLocation modelId, BedrockModelEntry entry, BedrockModelPOJO pojo) {
        try {
            BedrockModel model = switch (entry.kind()) {
                case LEGACY -> createLegacyModel(pojo);
                case BAKED -> createBakedModel(modelId, entry, pojo);
                case TREE -> TreeBedrockModel.bake(pojo);
            };
            return Optional.of(new BedrockModelResource(model, createAnimations(entry, model)));
        } catch (RuntimeException e) {
            BedrockModelMod.LOGGER.error("Failed to create Bedrock model resource: {}", modelId, e);
            return Optional.empty();
        }
    }

    private BedrockModel createLegacyModel(BedrockModelPOJO pojo) {
        if (legacyModelFactory == null) {
            throw new IllegalStateException("Legacy Bedrock models require a client model factory");
        }
        BedrockModel model = legacyModelFactory.apply(pojo);
        if (model == null) {
            throw new IllegalStateException("Legacy Bedrock model factory returned null");
        }
        return model;
    }

    private BakedBedrockModel createBakedModel(ResourceLocation modelId, BedrockModelEntry entry, BedrockModelPOJO pojo) {
        List<BedrockAnimationFile> animationFiles = collectAnimationFiles(entry);
        BedrockModelBakeContext context = new BedrockModelBakeContext(modelId, collectAnimatedBones(animationFiles));
        BakerOptions options = entry.optionsFactory().apply(context);
        return BakedBedrockModel.bake(pojo, options == null ? BakerOptions.defaults() : options);
    }

    private static java.util.Set<String> collectAnimatedBones(List<BedrockAnimationFile> files) {
        java.util.LinkedHashSet<String> bones = new java.util.LinkedHashSet<>();
        for (BedrockAnimationFile file : files) {
            if (file == null || file.getAnimations() == null) continue;
            file.getAnimations().values().forEach(animation -> {
                if (animation != null && animation.getBones() != null) bones.addAll(animation.getBones().keySet());
            });
        }
        return java.util.Set.copyOf(bones);
    }

    private List<BedrockAnimationFile> collectAnimationFiles(BedrockModelEntry entry) {
        List<BedrockAnimationFile> files = new ArrayList<>();
        for (ResourceLocation animationId : entry.animationSourceIds()) {
            BedrockAnimationFile file = animationSources.getAnimationFile(animationId);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }

    private Map<ResourceLocation, List<AnimationClip>> createAnimations(BedrockModelEntry entry, BedrockModel model) {
        if (entry.animationSourceIds().isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, List<AnimationClip>> animations = new LinkedHashMap<>();
        for (ResourceLocation animationId : entry.animationSourceIds()) {
            BedrockAnimationEntry animationEntry = animationSources.getProcessor(animationId);
            if (animationEntry == null) {
                continue;
            }
            BedrockAnimationFile file = animationSources.getAnimationFile(animationId);
            if (file == null) {
                continue;
            }
            List<AnimationClip> created = animationEntry.factory().create(file, model);
            if (created != null) {
                animations.put(animationId, created);
            }
        }
        return animations;
    }
}



