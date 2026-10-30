package mods.mewkooo.bedrockmodel.resource;

import mods.mewkooo.bedrockmodel.resource.event.RegisterBedrockResourcesEvent;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModLoader;

import java.util.Objects;
import java.util.function.Consumer;

/** Internal bridge between NeoForge reload events and the Bedrock resource API. */
public final class BedrockResourceReloadRegistration {
    private BedrockResourceReloadRegistration() {
    }

    public static void register(Dist dist, Consumer<PreparableReloadListener> registerListener) {
        Objects.requireNonNull(registerListener, "registerListener");

        RegisterBedrockResourcesEvent event = new RegisterBedrockResourcesEvent(dist);
        ModLoader.postEvent(event);

        BedrockAnimationSourceRepository animationSources = new BedrockAnimationSourceRepository(event.getAnimationRegistry());
        BedrockModelRepository models = new BedrockModelRepository(
                event.getModelRegistry(), event.getReloadListeners(), animationSources);

        registerListener.accept(animationSources);
        registerListener.accept(models);
    }
}
