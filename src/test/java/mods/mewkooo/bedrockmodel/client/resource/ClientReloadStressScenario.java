package mods.mewkooo.bedrockmodel.client.resource;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelAccess;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.api.resource.RegisterBedrockResourcesEvent;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.particle.resource.ParticleDefinitionLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Actual client assets and integrated-server data reloads, with concurrent public API readers. */
@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class ClientReloadStressScenario {
    public static final ResourceLocation FIRST_MODEL = id("reload_tree");
    public static final ResourceLocation SECOND_MODEL = id("reload_baked");
    public static final ResourceLocation LAZY_MODEL = id("reload_lazy");
    public static final ResourceLocation ANIMATION = id("reload_animation");
    public static final ResourceLocation LAZY_ANIMATION = id("reload_lazy_animation");
    public static final ResourceLocation FIRST_PARTICLE = id("reload_first");
    public static final ResourceLocation SECOND_PARTICLE = id("reload_second");
    private static final List<ResourceLocation> MODELS = List.of(FIRST_MODEL, SECOND_MODEL);
    private static final List<ResourceLocation> PARTICLES = List.of(FIRST_PARTICLE, SECOND_PARTICLE);
    private static final AtomicInteger CLIENT_APPLIES = new AtomicInteger();
    private static final AtomicInteger SERVER_APPLIES = new AtomicInteger();
    private static final int RELOAD_CYCLES = Integer.parseInt(System.getenv().getOrDefault("SBM_RELOAD_CYCLES", "12"));
    private static final AtomicReference<Throwable> READER_FAILURE = new AtomicReference<>();
    private static final AtomicLong CLIENT_READS = new AtomicLong();
    private static final AtomicLong SERVER_READS = new AtomicLong();
    private static ScheduledExecutorService readers;
    private static Map<ResourceLocation, BedrockModelResource> initialClient;
    private static Map<ResourceLocation, BedrockModelResource> initialServer;
    private static Map<ResourceLocation, ParticleEffectDefinition> initialParticles;
    private static Map<ResourceLocation, BedrockModelResource> initialClientContents;
    private static Map<ResourceLocation, BedrockModelResource> initialServerContents;
    private static Map<ResourceLocation, ParticleEffectDefinition> initialParticleContents;
    private static Map<ResourceLocation, BedrockModelResource> previousClient;
    private static Map<ResourceLocation, BedrockModelResource> previousServer;
    private static Map<ResourceLocation, ParticleEffectDefinition> previousParticles;
    private static CompletableFuture<Void> pendingClient;
    private static CompletableFuture<Void> pendingServer;
    private static int completed;
    private static int idleTicks;
    private static int clientTicks;
    private static int startingServerTick;
    private static int lastCompletedServerTick;
    private static int startingClientApplies;
    private static int startingServerApplies;
    private static long startedAt;

    private ClientReloadStressScenario() {}

    @SubscribeEvent
    public static void registerFixtures(RegisterBedrockResourcesEvent event) {
        if (!enabled()) return;
        event.treeModel(FIRST_MODEL).animation(ANIMATION).register();
        event.bakedModel(SECOND_MODEL).animation(ANIMATION).register();
        event.treeModel(LAZY_MODEL, FIRST_MODEL).animation(LAZY_ANIMATION).lazy().register();
        event.onReload(resources -> {
            validateModels(resources, event.getLogicalSide().name() + " apply");
            (event.getLogicalSide() == LogicalSide.CLIENT ? CLIENT_APPLIES : SERVER_APPLIES).incrementAndGet();
        });
    }

    private static boolean enabled() {
        String scenario = System.getenv("SBM_CLIENT_TEST");
        return "reload".equals(scenario) || "environment".equals(scenario);
    }

    public static void start(Minecraft minecraft) {
        check(readers == null, "reload stress cannot be started twice");
        check(RELOAD_CYCLES > 0 && RELOAD_CYCLES <= 100, "reload cycles must be between 1 and 100");
        check(minecraft.getSingleplayerServer() != null, "reload stress requires an integrated server");
        startedAt = System.nanoTime();
        startingServerTick = minecraft.getSingleplayerServer().getTickCount();
        lastCompletedServerTick = startingServerTick;
        startingClientApplies = CLIENT_APPLIES.get();
        startingServerApplies = SERVER_APPLIES.get();
        initialClient = modelSnapshot(LogicalSide.CLIENT);
        initialServer = modelSnapshot(LogicalSide.SERVER);
        initialParticles = particleSnapshot();
        initialClientContents = Map.copyOf(initialClient);
        initialServerContents = Map.copyOf(initialServer);
        initialParticleContents = Map.copyOf(initialParticles);
        previousClient = initialClient;
        previousServer = initialServer;
        previousParticles = initialParticles;
        readers = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "sbm-reload-reader");
            thread.setDaemon(true);
            return thread;
        });
        readers.scheduleWithFixedDelay(() -> readConcurrently(LogicalSide.CLIENT), 0, 1, TimeUnit.MILLISECONDS);
        readers.scheduleWithFixedDelay(() -> readConcurrently(LogicalSide.SERVER), 0, 1, TimeUnit.MILLISECONDS);
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] reload stress starting {} client + server reloads", RELOAD_CYCLES);
    }

    /** Call each client tick. Completion means every future and every concurrent assertion passed. */
    public static boolean tick(Minecraft minecraft) {
        clientTicks++;
        Throwable readerFailure = READER_FAILURE.get();
        if (readerFailure != null) throw new AssertionError("concurrent resource reader failed", readerFailure);
        check(System.nanoTime() - startedAt < TimeUnit.MINUTES.toNanos(10), "resource reload stress exceeded ten minutes");
        if (pendingClient == null) {
            // A completed asset future can precede the reload overlay's fade-out.
            // Starting the next reload immediately can keep singleplayer paused forever.
            if (minecraft.getOverlay() != null || minecraft.screen != null) {
                idleTicks = 0;
                return false;
            }
            int liveServerTicks = minecraft.getSingleplayerServer().getTickCount() - lastCompletedServerTick;
            if (++idleTicks >= 10 && liveServerTicks >= 10) startReload(minecraft);
            return false;
        }
        if (!pendingClient.isDone() || !pendingServer.isDone()) return false;
        pendingClient.join();
        pendingServer.join();
        Map<ResourceLocation, BedrockModelResource> client = modelSnapshot(LogicalSide.CLIENT);
        Map<ResourceLocation, BedrockModelResource> server = modelSnapshot(LogicalSide.SERVER);
        Map<ResourceLocation, ParticleEffectDefinition> particles = particleSnapshot();
        for (ResourceLocation id : MODELS) {
            check(client.get(id) != previousClient.get(id), "client model did not reload: " + id);
            check(server.get(id) != previousServer.get(id), "server model did not reload: " + id);
        }
        for (ResourceLocation id : PARTICLES) {
            check(particles.get(id) != previousParticles.get(id), "particle definition did not reload: " + id);
        }
        previousClient = client;
        previousServer = server;
        previousParticles = particles;
        completed++;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] resource reload round {}/{} complete; clientReads={}, serverReads={}",
                completed, RELOAD_CYCLES, CLIENT_READS.get(), SERVER_READS.get());
        if (completed < RELOAD_CYCLES) {
            pendingClient = null;
            pendingServer = null;
            idleTicks = 0;
            lastCompletedServerTick = minecraft.getSingleplayerServer().getTickCount();
            return false;
        }
        close();
        check(READER_FAILURE.get() == null, "concurrent resource reader failed during shutdown");
        check(CLIENT_APPLIES.get() - startingClientApplies == RELOAD_CYCLES, "client model apply count mismatch");
        check(SERVER_APPLIES.get() - startingServerApplies == RELOAD_CYCLES, "server model apply count mismatch");
        check(CLIENT_READS.get() > RELOAD_CYCLES && SERVER_READS.get() > RELOAD_CYCLES,
                "background readers must actually overlap reload execution");
        int serverTicks = minecraft.getSingleplayerServer().getTickCount() - startingServerTick;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] reload tick progress: clientTicks={}, serverTicks={}",
                clientTicks, serverTicks);
        check(clientTicks >= RELOAD_CYCLES && serverTicks >= RELOAD_CYCLES,
                "client and integrated server must tick throughout the scenario");
        assertRetainedSnapshots();
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] PASS reload stress: clientReloads={}, serverReloads={}, clientReads={}, serverReads={}, clientTicks={}, serverTicks={}",
                completed, completed, CLIENT_READS.get(), SERVER_READS.get(), clientTicks, serverTicks);
        return true;
    }

    private static void startReload(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        check(server != null, "integrated server disappeared during reload stress");
        pendingServer = server.submit(() -> server.reloadResources(server.getPackRepository().getSelectedIds()))
                .thenCompose(future -> future);
        pendingClient = minecraft.reloadResourcePacks();
    }

    private static void readConcurrently(LogicalSide side) {
        if (READER_FAILURE.get() != null) return;
        try {
            modelSnapshot(side);
            if (side == LogicalSide.CLIENT) particleSnapshot();
            assertRetainedSnapshots();
            (side == LogicalSide.CLIENT ? CLIENT_READS : SERVER_READS).incrementAndGet();
        } catch (Throwable failure) {
            READER_FAILURE.compareAndSet(null, failure);
        }
    }

    private static Map<ResourceLocation, BedrockModelResource> modelSnapshot(LogicalSide side) {
        BedrockModelAccess access = BedrockResources.models(side);
        check(access != null, side + " repository disappeared during reload");
        Map<ResourceLocation, BedrockModelResource> snapshot = access.getAllResources();
        validateModels(snapshot, side.name());
        for (Map.Entry<ResourceLocation, BedrockModelResource> entry : snapshot.entrySet()) {
            check(entry.getKey() != null && entry.getValue() != null, "model snapshot contains null");
        }
        for (ResourceLocation id : MODELS) {
            BedrockModelResource resource = access.getResource(id);
            check(resource != null, side + " direct model getter returned null: " + id);
            check(resource.getAnimations(ANIMATION) != null && resource.getAnimations(ANIMATION).size() == 1,
                    side + " direct model getter has incomplete animations: " + id);
        }
        BedrockModelResource lazy = access.getResource(LAZY_MODEL);
        check(lazy != null && lazy.model().getIndex("root") >= 0, side + " lazy model getter returned an incomplete model");
        check(lazy.getAnimations(LAZY_ANIMATION) != null && lazy.getAnimations(LAZY_ANIMATION).size() == 1,
                side + " lazy model getter returned incomplete animations");
        return snapshot;
    }

    private static void validateModels(Map<ResourceLocation, BedrockModelResource> snapshot, String label) {
        for (ResourceLocation id : MODELS) {
            BedrockModelResource resource = snapshot.get(id);
            check(resource != null, label + " snapshot has a missing fixture: " + id);
            check(resource.model().getIndex("root") >= 0, label + " model has no root bone");
            check(resource.getAnimations(ANIMATION) != null && resource.getAnimations(ANIMATION).size() == 1,
                    label + " model has incomplete animation source: " + id);
        }
    }

    private static Map<ResourceLocation, ParticleEffectDefinition> particleSnapshot() {
        ParticleDefinitionLoader loader = ParticleDefinitionLoader.getInstance();
        Map<ResourceLocation, ParticleEffectDefinition> snapshot = loader.getAllDefinitions();
        for (ResourceLocation id : PARTICLES) {
            check(snapshot.get(id) != null, "particle snapshot has a missing fixture: " + id);
            check(loader.getDefinition(id) != null, "particle direct getter returned null: " + id);
        }
        snapshot.forEach((id, definition) -> check(id != null && definition != null, "particle snapshot contains null"));
        return snapshot;
    }

    private static void assertRetainedSnapshots() {
        if (initialClient == null) return;
        check(initialClient.equals(initialClientContents), "retained CLIENT snapshot was mutated");
        check(initialServer.equals(initialServerContents), "retained SERVER snapshot was mutated");
        check(initialParticles.equals(initialParticleContents), "retained particle snapshot was mutated");
        validateModels(initialClient, "retained CLIENT");
        validateModels(initialServer, "retained SERVER");
        for (ResourceLocation id : PARTICLES) check(initialParticles.get(id) != null, "retained particle snapshot was mutated");
    }

    /** Also call from a harness failure path so no background reader survives a failed scenario. */
    public static void close() {
        if (readers != null) {
            readers.shutdownNow();
            try {
                check(readers.awaitTermination(5, TimeUnit.SECONDS), "resource reader threads did not stop");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while stopping reload readers", failure);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("sbm_client_test", path);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
