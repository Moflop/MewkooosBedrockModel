package mods.mewkooo.bedrockmodel.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.legacy.model.BedrockModel;
import mods.mewkooo.bedrockmodel.model.BoneIndexProvider;
import mods.mewkooo.bedrockmodel.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.model.baked.BakerOptions;
import mods.mewkooo.bedrockmodel.model.tree.TreeBedrockModel;
import mods.mewkooo.bedrockmodel.resource.json.pojo.BedrockAnimationFile;
import mods.mewkooo.bedrockmodel.resource.json.pojo.BedrockModelPOJO;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class BedrockModelFactory {
    private final BedrockAnimationSourceRepository animationSources;

    BedrockModelFactory(BedrockAnimationSourceRepository animationSources) {
        this.animationSources = animationSources;
    }

    Optional<BedrockModelResource> create(ResourceLocation modelId, BedrockModelEntry entry, BedrockModelPOJO pojo) {
        try {
            BoneIndexProvider model = switch (entry.kind()) {
                case LEGACY -> new BedrockModel(pojo);
                case BAKED -> createBakedModel(modelId, entry, pojo);
                case TREE -> TreeBedrockModel.bake(pojo);
            };
            return Optional.of(new BedrockModelResource(model, entry.kind(), createAnimations(entry, model)));
        } catch (RuntimeException e) {
            BedrockModelMod.LOGGER.error("Failed to create Bedrock model resource: {}", modelId, e);
            return Optional.empty();
        }
    }

    private BakedBedrockModel createBakedModel(ResourceLocation modelId, BedrockModelEntry entry, BedrockModelPOJO pojo) {
        BedrockModelBakeContext context = new BedrockModelBakeContext(modelId, pojo, collectAnimationFiles(entry));
        BakerOptions options = entry.optionsFactory().apply(context);
        return BakedBedrockModel.bake(pojo, options == null ? BakerOptions.defaults() : options);
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

    private Map<ResourceLocation, List<BedrockAnimation>> createAnimations(BedrockModelEntry entry, BoneIndexProvider model) {
        if (entry.animationSourceIds().isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, List<BedrockAnimation>> animations = new LinkedHashMap<>();
        for (ResourceLocation animationId : entry.animationSourceIds()) {
            BedrockAnimationEntry animationEntry = animationSources.getProcessor(animationId);
            if (animationEntry == null) {
                continue;
            }
            BedrockAnimationFile file = animationSources.getAnimationFile(animationId);
            if (file == null) {
                continue;
            }
            List<BedrockAnimation> created = animationEntry.factory().create(file, model);
            if (created != null) {
                animations.put(animationId, created);
            }
        }
        return animations;
    }
}
