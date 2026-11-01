package mods.sbmcontrol;

import mods.sbmtest.ChunkUnloadDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** This control deliberately imports no classes or resources from the library. */
@Mod(EngineTransitionControl.MOD_ID)
@EventBusSubscriber(modid = EngineTransitionControl.MOD_ID, value = Dist.CLIENT)
public final class EngineTransitionControl {
    public static final String MOD_ID = "sbm_engine_control";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final String CASE = System.getenv("SBM_CLIENT_TEST");
    private static final boolean ENABLED = "control-transitions".equals(CASE)
            || "control-unload-requeue".equals(CASE);
    private static final long STARTED_AT = System.nanoTime();
    private static final int SETTLE_TICKS = setting("SBM_TRANSITION_SETTLE_TICKS", 0, 0, 1200);
    private static final int CYCLES = setting("SBM_TRANSITION_CYCLES", 3, 1, 20);
    private static final int ORIGIN = setting("SBM_TRANSITION_ORIGIN", 16384, 0, 29000000);
    private static Stage stage = Stage.WAITING_FOR_WORLD;
    private static boolean inTransitionCall;
    private static boolean completed;
    private static int dimensionChanges;
    private static int unloadEvents;
    private static int arrivalServerTick;
    private static long lastTransitionNanos;
    private static long lastArrivalNanos;
    private static ClientLevel departedLevel;
    private static ResourceKey<Level> targetDimension;
    private static CompletableFuture<?> transitionTask;

    public EngineTransitionControl() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!ENABLED || completed || inTransitionCall) return;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            check(System.nanoTime() - STARTED_AT < TimeUnit.MINUTES.toNanos(10),
                    "control exceeded ten minutes");
            if (stage == Stage.WAITING_FOR_EXIT) {
                if (minecraft.level == null && minecraft.getSingleplayerServer() == null) {
                    check(dimensionChanges == CYCLES * 2, "missing actual dimension changes");
                    check(unloadEvents >= dimensionChanges + 1, "missing actual client unload events");
                    if ("control-unload-requeue".equals(CASE)) EngineUnloadRequeueScenario.verifyCompleted();
                    completed = true;
                    LOGGER.info("[SBM_CLIENT_TEST] CONTROL complete {} dimensions={} unloadEvents={} elapsedMs={}",
                            CASE, dimensionChanges, unloadEvents, elapsedMillis(STARTED_AT));
                    LOGGER.info("[SBM_CLIENT_TEST] PASS {}", CASE);
                    minecraft.stop();
                }
                return;
            }
            if (minecraft.level == null || minecraft.player == null) return;
            if (stage == Stage.WAITING_FOR_WORLD) {
                if (minecraft.screen != null || minecraft.getOverlay() != null) return;
                verifyIsolationAndProfile();
                check(minecraft.getSingleplayerServer() != null, "control requires an integrated server");
                check(minecraft.level.dimension().equals(Level.OVERWORLD), "control must start in the overworld");
                LOGGER.info("[SBM_CLIENT_TEST] RUN {} profile={} scenario={} settleTicks={} cycles={} origin={}",
                        System.getenv("SBM_CLIENT_RUN_ID"), System.getenv("SBM_CLIENT_PROFILE"), CASE,
                        SETTLE_TICKS, CYCLES, ORIGIN);
                requestDimensionOrExit(minecraft);
            } else if (stage == Stage.WAITING_FOR_DIMENSION) {
                if (transitionTask.isDone()) transitionTask.join();
                if (minecraft.level != departedLevel && minecraft.level.dimension().equals(targetDimension)
                        && minecraft.screen == null) {
                    dimensionChanges++;
                    lastArrivalNanos = System.nanoTime();
                    arrivalServerTick = minecraft.getSingleplayerServer().getTickCount();
                    LOGGER.info("[SBM_CLIENT_TEST] CONTROL arrival {} dimension={} serverTick={} elapsedMs={}",
                            dimensionChanges, targetDimension.location(), arrivalServerTick,
                            elapsedMillis(lastTransitionNanos));
                    stage = Stage.WAITING_FOR_SETTLE;
                    // Zero means act in this same tick, without a hidden world-settling delay.
                    if (SETTLE_TICKS == 0) requestDimensionOrExit(minecraft);
                }
            } else if (stage == Stage.WAITING_FOR_SETTLE) {
                if (minecraft.getSingleplayerServer().getTickCount() - arrivalServerTick >= SETTLE_TICKS
                        && minecraft.screen == null && minecraft.getOverlay() == null) {
                    requestDimensionOrExit(minecraft);
                }
            }
        } catch (Throwable failure) {
            completed = true;
            LOGGER.error("[SBM_CLIENT_TEST] FAIL {} {}", CASE, failure.toString(), failure);
            minecraft.stop();
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (ENABLED && !completed && event.getLevel() instanceof ClientLevel) {
            unloadEvents++;
        }
    }

    private static void verifyIsolationAndProfile() throws ClassNotFoundException {
        check(!ModList.get().isLoaded("mewkooos_bedrock_model"), "library must not be loaded in control");
        boolean expectedEngineFix = Boolean.parseBoolean(System.getenv("SBM_CLIENT_ENGINE_FIX"));
        check(ModList.get().isLoaded("sbm_chunk_unload_fix") == expectedEngineFix,
                "unexpected engine-fix mod presence");
        try {
            Class.forName("mods.mewkooo.bedrockmodel.BedrockModelMod", false,
                    EngineTransitionControl.class.getClassLoader());
            throw new AssertionError("library classes must not be present in control classpath");
        } catch (ClassNotFoundException expected) {
            LOGGER.info("[SBM_CLIENT_TEST] CONTROL library mod and classes absent");
        }
        String profile = System.getenv("SBM_CLIENT_PROFILE");
        check(profile != null, "control needs SBM_CLIENT_PROFILE");
        for (String mod : List.of("sodium", "embeddium", "acceleratedrendering", "iris")) {
            boolean expected = mod.equals("acceleratedrendering")
                    ? profile.equals("ar") || profile.contains("-ar") : profile.contains(mod);
            check(ModList.get().isLoaded(mod) == expected, "unexpected mod presence: " + mod + " in " + profile);
        }
        ModList.get().getMods().forEach(mod -> LOGGER.info("[SBM_CLIENT_TEST] MOD {} {}",
                mod.getModId(), mod.getVersion()));
    }

    private static void requestDimensionOrExit(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        check(server != null, "integrated server must exist before transition");
        if (dimensionChanges == CYCLES * 2) {
            stage = Stage.WAITING_FOR_EXIT;
            LOGGER.info("[SBM_CLIENT_TEST] CONTROL disconnect after {} dimensions at serverTick={} arrivalAgeMs={}",
                    dimensionChanges, server.getTickCount(), elapsedMillis(lastArrivalNanos));
            // The diagnostic queues before shutdown, on the server thread only.
            server.execute(() -> logServerState(server, "before-disconnect", ORIGIN));
            ChunkUnloadDiagnostics.arm(server);
            inTransitionCall = true;
            try {
                minecraft.level.disconnect();
                minecraft.disconnect(new TitleScreen());
            } finally {
                inTransitionCall = false;
            }
            LOGGER.info("[SBM_CLIENT_TEST] CONTROL disconnect returned");
            return;
        }
        departedLevel = minecraft.level;
        targetDimension = departedLevel.dimension().equals(Level.NETHER) ? Level.OVERWORLD : Level.NETHER;
        var target = targetDimension;
        var playerId = minecraft.player.getUUID();
        int requestedTransition = dimensionChanges + 1;
        int coordinate = ORIGIN;
        stage = Stage.WAITING_FOR_DIMENSION;
        lastTransitionNanos = System.nanoTime();
        LOGGER.info("[SBM_CLIENT_TEST] CONTROL request {} dimension={} coordinate={}",
                requestedTransition, target.location(), coordinate);
        transitionTask = server.submit(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            check(player != null, "integrated server player must exist");
            var destination = server.getLevel(target);
            check(destination != null, "destination dimension must exist");
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(destination, coordinate + 0.5, 90, coordinate + 0.5, 0, 0);
            logServerState(server, "after-teleport-" + target.location(), coordinate);
            ChunkUnloadDiagnostics.arm(server);
        });
    }

    private static void logServerState(MinecraftServer server, String point, int coordinate) {
        for (var level : server.getAllLevels()) {
            int holders = 0;
            int generationReferences = 0;
            int notReadyForSaving = 0;
            int center = coordinate >> 4;
            for (int x = center - 10; x <= center + 10; x++) {
                for (int z = center - 10; z <= center + 10; z++) {
                    var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
                    if (holder != null) {
                        holders++;
                        generationReferences += holder.getGenerationRefCount();
                        if (!holder.isReadyForSaving()) notReadyForSaving++;
                    }
                }
            }
            LOGGER.info("[SBM_CLIENT_TEST] CONTROL chunks point={} dimension={} tick={} pendingTasks={} "
                            + "visibleHolders={} generationRefs={} notReadyForSaving={}",
                    point, level.dimension().location(), server.getTickCount(),
                    level.getChunkSource().getPendingTasksCount(), holders, generationReferences, notReadyForSaving);
        }
    }

    private static int setting(String name, int defaultValue, int min, int max) {
        String raw = System.getenv(name);
        int value = raw == null || raw.isBlank() ? defaultValue : Integer.parseInt(raw);
        if (value < min || value > max) throw new IllegalArgumentException("invalid " + name + ": " + value);
        return value;
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private enum Stage { WAITING_FOR_WORLD, WAITING_FOR_DIMENSION, WAITING_FOR_SETTLE, WAITING_FOR_EXIT }
}
