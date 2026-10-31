package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.resource.BedrockModelBakeContext;
import mods.mewkooo.bedrockmodel.api.resource.RegisterBedrockResourcesEvent;
import mods.mewkooo.bedrockmodel.core.animation.BedrockAnimation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModLoader;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;

/** Internal bridge between NeoForge reload events and the public resource API. */
public final class BedrockResourceReloadRegistration {
    private BedrockResourceReloadRegistration() {
    }

    /** Registers listeners and installs the query view for the selected logical side. */
    public static void register(Dist dist, LogicalSide logicalSide,
                                Consumer<PreparableReloadListener> registerListener,
                                Function<BedrockModelPOJO, ? extends BedrockModel> legacyModelFactory) {
        Objects.requireNonNull(registerListener, "registerListener");
        Objects.requireNonNull(logicalSide, "logicalSide");
        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(
                dist, logicalSide, RawResourceLoaders.COMMON_LOADER);
        ModLoader.postEvent(event);

        Map<ResourceLocation, BedrockAnimationEntry> animations = new LinkedHashMap<>();
        event.getAnimationRegistry().forEach((id, registration) -> animations.put(id,
                new BedrockAnimationEntry(registration.rawLoader(),
                        (file, model) -> new java.util.ArrayList<>(BedrockAnimation.createAnimation(file, model)),
                        registration.lazy())));
        Map<ResourceLocation, BedrockModelEntry> models = new LinkedHashMap<>();
        event.getModelRegistry().forEach((id, registration) -> models.put(id, new BedrockModelEntry(
                registration.rawLoader(), registration.sourceId(), registration.kind(), registration.optionsFactory(),
                registration.animationSourceIds(), registration.lazy())));

        BedrockAnimationSourceRepository animationSources = new BedrockAnimationSourceRepository(animations);
        BedrockModelRepositoryImpl repository = new BedrockModelRepositoryImpl(
                logicalSide, models, event.getReloadListeners(), animationSources, legacyModelFactory);
        registerListener.accept(animationSources);
        registerListener.accept(repository);
    }
}

