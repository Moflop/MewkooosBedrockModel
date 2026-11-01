package mods.sbmcontrol;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import mods.sbmtest.ChunkUnloadDiagnostics;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit fault injection in the standalone engine control, never in the library runtime.
 * It proves an upstream unload-queue mechanism, not the cause of any historical client hang.
 */
@EventBusSubscriber(modid = EngineTransitionControl.MOD_ID)
public final class EngineUnloadRequeueScenario {
    private static final Logger LOGGER = LoggerFactory.getLogger("SBM_ENGINE_UNLOAD_REQUEUE");
    private static final String CASE = "control-unload-requeue";
    private static final long RECOVER_AFTER_MILLIS = 20_000;
    private static volatile Injection injection;

    private EngineUnloadRequeueScenario() {}

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (!CASE.equals(System.getenv("SBM_CLIENT_TEST"))) return;
        if (injection != null) {
            recordFailure(injection, "injection invoked more than once", null);
            return;
        }
        MinecraftServer server = event.getServer();
        Injection state = new Injection(server);
        injection = state;
        try {
            check(server.isSameThread(), "injection must run on the server thread");
            check(!server.isStopped(), "injection must precede stopServer");
            check(ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.EMPTY)
                            .getAccumulatedRadiusOf(ChunkStatus.EMPTY) == 0,
                    "EMPTY task must claim exactly the selected holder");
            Candidate candidate = findCandidate(server);
            state.holder = candidate.holder();
            check(state.holder.getGenerationRefCount() == 0, "selected holder must start with zero generation refs");
            check(state.holder.getSaveSyncFuture().isDone(), "selected holder save future must already be done");
            check(candidate.updating().get(state.holder.getPos().toLong()) == state.holder,
                    "selected holder must be currently updating");
            long position = state.holder.getPos().toLong();
            check(candidate.pending().get(position) == null,
                    "selected position must not have any pending unload holder");

            // INJECTED lifecycle barrier: make the initial schedule legal, then cancel its pending
            // membership while the real callback remains queued. This ensures the optional guard
            // must handle a surviving callback's recursive reschedule, not merely reject setup.
            Method scheduleUnload = ChunkMap.class.getDeclaredMethod("scheduleUnload", long.class, ChunkHolder.class);
            scheduleUnload.setAccessible(true);
            @SuppressWarnings("unchecked")
            Queue<Runnable> queue = (Queue<Runnable>) read(candidate.map(), "unloadQueue");
            // There is no queue consumer during this server-thread event, so size is bounded here.
            int callbacksBefore = queue.size();
            candidate.pending().put(position, state.holder);
            try {
                scheduleUnload.invoke(candidate.map(), position, state.holder);
                check(queue.size() > callbacksBefore, "real initial unload callback must be enqueued");
                state.callbackQueuedBeforeCancellation = true;
            } finally {
                check(candidate.pending().remove(position) == state.holder,
                        "temporary pending membership must be restored after setup");
            }

            // The real task acquires one real generation reference. It has no scheduled layer;
            // cancellation therefore releases only its claim and never applies a generation step.
            state.task = ChunkGenerationTask.create(candidate.map(), ChunkStatus.EMPTY, state.holder.getPos());
            state.task.markForCancellation();
            check(state.holder.getGenerationRefCount() == 1, "real EMPTY task must acquire exactly one ref");
            check(state.holder.getSaveSyncFuture().isDone(), "task creation must not change completed save future");
            // An old-enough TickTask is eligible even if a shutdown pass exhausts its 1 ms budget.
            // The test should measure unload-loop starvation rather than task-age throttling.
            server.tell(new TickTask(server.getTickCount() - 4, () -> releaseOnServer(state)));
            state.injected = true;

            Thread recovery = new Thread(() -> recover(state), "SBM injected unload recovery");
            recovery.setDaemon(true);
            recovery.start();
            ChunkUnloadDiagnostics.arm(server).join();
            LOGGER.warn("[SBM_CLIENT_TEST] INJECTED {} level={} pos={} refs={} saveDone={} "
                            + "initialCallbackQueued=true pendingMembershipCancelled=true "
                            + "inUpdating=true inPending=false patchPresent={} recoveryAfterMs={} "
                            + "mechanismOnly=true historicalCauseProven=false",
                    CASE, candidate.dimension(), state.holder.getPos(), state.holder.getGenerationRefCount(),
                    state.holder.getSaveSyncFuture().isDone(), state.patchPresent, RECOVER_AFTER_MILLIS);
        } catch (Throwable error) {
            recordFailure(state, "unable to prepare controlled unload state", error);
            // Any claimed task must be cancelled even when an assertion/reflection step failed.
            releaseOnce(state, "setup-failure");
            state.recoveryFinished.complete(null);
        }
    }

    /** Call after Minecraft.disconnect has returned and before reporting control PASS. */
    public static void verifyCompleted() {
        Injection state = injection;
        check(state != null && state.injected, "controlled unload state was not injected");
        state.recoveryFinished.join();
        check(state.failure.get() == null, "controlled unload verification failed: " + state.failure.get());
        check(state.callbackQueuedBeforeCancellation, "initial callback was not queued before simulated cancellation");
        check(state.released.get(), "real generation claim was not released");
        check(state.server.isShutdown(), "integrated server must finish shutdown after release");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedAt);
        if (state.patchPresent) {
            check(state.serverReleaseRanBeforeRecovery.get(), "patched engine must run the queued server release");
            check(!state.recoveryStarted.get() && !state.recoveryReleased,
                    "patched engine must finish without fallback recovery");
            check(elapsedMillis < RECOVER_AFTER_MILLIS, "patched shutdown must finish before recovery deadline");
            LOGGER.info("[SBM_CLIENT_TEST] CONTROL {} verified injected=true patchPresent=true "
                            + "initialCallbackQueued=true queuedReleaseNatural=true fallbackTriggered=false "
                            + "historicalCauseProven=false elapsedMs={}", CASE, elapsedMillis);
            return;
        }
        check(state.observedBlocked, "shutdown did not remain blocked in processUnloads before recovery");
        check(!state.serverReleaseRanBeforeRecovery.get(), "server task was not starved before fallback");
        check(state.recoveryReleased, "fallback did not release the real task's generation claim");
        LOGGER.info("[SBM_CLIENT_TEST] CONTROL {} verified injected=true "
                        + "patchPresent=false blockedInProcessUnloads=true queuedReleaseStarved=true recovered=true "
                        + "historicalCauseProven=false elapsedMs={}",
                CASE, elapsedMillis);
    }

    @SuppressWarnings("unchecked")
    private static Candidate findCandidate(MinecraftServer server) throws ReflectiveOperationException {
        for (ServerLevel level : server.getAllLevels()) {
            ChunkMap map = level.getChunkSource().chunkMap;
            Long2ObjectMap<ChunkHolder> updating = (Long2ObjectMap<ChunkHolder>) read(map, "updatingChunkMap");
            Long2ObjectMap<ChunkHolder> pending = (Long2ObjectMap<ChunkHolder>) read(map, "pendingUnloads");
            for (ChunkHolder holder : updating.values()) {
                if (holder.getGenerationRefCount() == 0 && holder.getSaveSyncFuture().isDone()
                        && holder.getChunkIfPresentUnchecked(ChunkStatus.FULL) != null
                        && pending.get(holder.getPos().toLong()) == null) {
                    return new Candidate(level.dimension().location().toString(), map, holder, updating, pending);
                }
            }
        }
        throw new AssertionError("no real updating holder with zero refs and a completed save future");
    }

    private static void releaseOnServer(Injection state) {
        if (!state.recoveryStarted.get()) {
            state.serverReleaseRanBeforeRecovery.set(true);
            if (!state.patchPresent) {
                recordFailure(state, "unpatched queued server release executed before controlled recovery", null);
            }
        }
        try {
            releaseOnce(state, "server-task");
        } finally {
            state.serverReleaseFinished.complete(null);
        }
    }

    private static void recover(Injection state) {
        try {
            try {
                state.serverReleaseFinished.get(RECOVER_AFTER_MILLIS, TimeUnit.MILLISECONDS);
                check(state.patchPresent, "unpatched server unexpectedly ran its queued release");
                check(state.released.get(), "patched server did not release the real task");
                LOGGER.info("[SBM_CLIENT_TEST] INJECTED {} observed patchPresent=true "
                                + "queuedReleaseNatural=true fallbackTriggered=false elapsedMs={}", CASE,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedAt));
                return;
            } catch (TimeoutException expectedForUnpatchedEngine) {
                check(!state.patchPresent, "patch failed to prevent unload queue starvation before deadline");
            }
            check(!state.server.isShutdown(), "server exited before recovery; no controlled hang observed");
            check(state.server.isStopped(), "server has not entered stopServer");
            check(!state.serverReleaseRanBeforeRecovery.get(), "server already ran the queued release");
            check(state.holder.getGenerationRefCount() == 1, "held real generation ref must remain outstanding");
            check(state.holder.getSaveSyncFuture().isDone(), "save future must remain completed");
            boolean inUnloads = false;
            boolean inStop = false;
            for (StackTraceElement frame : state.server.getRunningThread().getStackTrace()) {
                inUnloads |= frame.getClassName().equals(ChunkMap.class.getName())
                        && frame.getMethodName().equals("processUnloads");
                inStop |= frame.getClassName().equals(MinecraftServer.class.getName())
                        && frame.getMethodName().equals("stopServer");
            }
            check(inUnloads && inStop, "server must be observed inside stopServer/processUnloads");
            state.observedBlocked = true;
            LOGGER.warn("[SBM_CLIENT_TEST] INJECTED {} observed elapsedMs={} refs={} "
                            + "saveDone=true inStopServer=true inProcessUnloads=true queuedReleaseRan=false",
                    CASE, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedAt),
                    state.holder.getGenerationRefCount());
        } catch (Throwable error) {
            recordFailure(state, "controlled hang observation failed", error);
        } finally {
            // This daemon is a bounded recovery barrier. Only the CAS winner calls the real
            // cancelled task; its release path uses holder atomics and does not access chunk maps.
            if (!state.released.get()) {
                state.recoveryStarted.set(true);
                if (releaseOnce(state, "recovery-daemon")) {
                    state.recoveryReleased = true;
                }
            }
            state.recoveryFinished.complete(null);
        }
    }

    private static boolean releaseOnce(Injection state, String source) {
        if (state.task == null || !state.released.compareAndSet(false, true)) return false;
        try {
            CompletableFuture<?> waiting = state.task.runUntilWait();
            check(waiting == null, "a cancelled unscheduled EMPTY task must release without waiting");
            check(state.holder.getGenerationRefCount() == 0, "real task must return the generation ref to zero");
            LOGGER.warn("[SBM_CLIENT_TEST] INJECTED {} release source={} refs=0 elapsedMs={}", CASE,
                    source, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - state.startedAt));
            return true;
        } catch (Throwable error) {
            recordFailure(state, "unable to release real generation claim using " + source, error);
            return false;
        }
    }

    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void recordFailure(Injection state, String message, Throwable error) {
        state.failure.compareAndSet(null, message + (error == null ? "" : ": " + error));
        LOGGER.error("[SBM_CLIENT_TEST] FAIL INJECTED {} {}", CASE, message, error);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Candidate(String dimension, ChunkMap map, ChunkHolder holder,
                             Long2ObjectMap<ChunkHolder> updating, Long2ObjectMap<ChunkHolder> pending) {}

    private static final class Injection {
        private final MinecraftServer server;
        private final boolean patchPresent = ModList.get().isLoaded("sbm_chunk_unload_fix");
        private final long startedAt = System.nanoTime();
        private final AtomicBoolean released = new AtomicBoolean();
        private final AtomicBoolean recoveryStarted = new AtomicBoolean();
        private final AtomicBoolean serverReleaseRanBeforeRecovery = new AtomicBoolean();
        private final AtomicReference<String> failure = new AtomicReference<>();
        private final CompletableFuture<Void> recoveryFinished = new CompletableFuture<>();
        private final CompletableFuture<Void> serverReleaseFinished = new CompletableFuture<>();
        private volatile ChunkHolder holder;
        private volatile ChunkGenerationTask task;
        private volatile boolean injected;
        private volatile boolean callbackQueuedBeforeCancellation;
        private volatile boolean observedBlocked;
        private volatile boolean recoveryReleased;

        private Injection(MinecraftServer server) {
            this.server = server;
        }
    }
}
