package mods.sbmtest;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Read-only shutdown observations shared by the library test client and the independent engine control. */
public final class ChunkUnloadDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("SBM_CHUNK_UNLOAD_DIAGNOSTICS");
    private static final long FIRST_REPORT_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final long REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final int MAX_DETAILS = 128;
    private static final Map<MinecraftServer, ObserverState> OBSERVERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ChunkUnloadDiagnostics() {}

    /**
     * Snapshot map entries on the server thread before disconnecting. The observer never runs tasks,
     * changes tickets, completes futures, or modifies holders. Its future completes after the snapshot.
     */
    public static CompletableFuture<Void> arm(MinecraftServer server) {
        CompletableFuture<Void> armed = new CompletableFuture<>();
        if (server == null) {
            armed.completeExceptionally(new IllegalArgumentException("server is null"));
            return armed;
        }
        ObserverState state = OBSERVERS.computeIfAbsent(server, ignored -> new ObserverState());
        Runnable prepare = () -> {
            try {
                if (!server.isSameThread()) {
                    throw new IllegalStateException("snapshot must run on the server thread");
                }
                List<LevelSnapshot> levels = new ArrayList<>();
                for (ServerLevel level : server.getAllLevels()) {
                    levels.add(snapshot(level));
                }
                state.levels = List.copyOf(levels);
                if (!state.started) {
                    state.started = true;
                    Thread observer = new Thread(() -> observe(server, state), "SBM chunk unload diagnostics");
                    observer.setDaemon(true);
                    observer.start();
                }
                LOGGER.info("[SBM_UNLOAD_DIAGNOSTICS] armed levels={} holders={}", levels.size(),
                        levels.stream().mapToInt(level -> level.holders().size()).sum());
                armed.complete(null);
            } catch (Throwable error) {
                LOGGER.warn("[SBM_UNLOAD_DIAGNOSTICS] unable to arm", error);
                armed.completeExceptionally(error);
            }
        };
        if (server.isSameThread()) {
            prepare.run();
        } else if (!server.isStopped()) {
            try {
                server.execute(prepare);
            } catch (RuntimeException rejected) {
                armed.completeExceptionally(rejected);
            }
        } else {
            armed.completeExceptionally(new IllegalStateException("server has already entered shutdown"));
        }
        return armed;
    }

    @SuppressWarnings("unchecked")
    private static LevelSnapshot snapshot(ServerLevel level) throws ReflectiveOperationException {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Long2ObjectMap<ChunkHolder> pending = (Long2ObjectMap<ChunkHolder>) field(chunkMap, "pendingUnloads");
        Long2ObjectMap<ChunkHolder> updating = (Long2ObjectMap<ChunkHolder>) field(chunkMap, "updatingChunkMap");
        Long2ObjectMap<ChunkHolder> visible = (Long2ObjectMap<ChunkHolder>) field(chunkMap, "visibleChunkMap");
        LongSet toDrop = (LongSet) field(chunkMap, "toDrop");
        Queue<Runnable> unloadQueue = (Queue<Runnable>) field(chunkMap, "unloadQueue");
        Set<ChunkHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        // This is the only map iteration, and arm schedules it on the server thread.
        holders.addAll(pending.values());
        holders.addAll(updating.values());
        holders.addAll(visible.values());
        return new LevelSnapshot(level.dimension().location().toString(), chunkMap, pending, updating,
                visible, toDrop, unloadQueue, List.copyOf(holders));
    }

    private static void observe(MinecraftServer server, ObserverState state) {
        long stoppedAt = 0;
        long reportAt = Long.MAX_VALUE;
        try {
            while (!server.isShutdown()) {
                if (server.isStopped()) {
                    long now = System.nanoTime();
                    if (stoppedAt == 0) {
                        stoppedAt = now;
                        reportAt = now + FIRST_REPORT_NANOS;
                    }
                    if (now >= reportAt) {
                        LOGGER.warn("[SBM_UNLOAD_DIAGNOSTICS] shutdown still active elapsedSeconds={}",
                                TimeUnit.NANOSECONDS.toSeconds(now - stoppedAt));
                        for (LevelSnapshot level : state.levels) {
                            report(level);
                        }
                        reportAt = now + REPORT_INTERVAL_NANOS;
                    }
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            LOGGER.warn("[SBM_UNLOAD_DIAGNOSTICS] observer failed", error);
        } finally {
            OBSERVERS.remove(server);
        }
    }

    @SuppressWarnings("unchecked")
    private static void report(LevelSnapshot snapshot) throws ReflectiveOperationException {
        // visibleChunkMap is replaced with a new map when chunks are promoted; refresh its reference.
        LevelSnapshot level = new LevelSnapshot(snapshot.dimension(), snapshot.chunkMap(), snapshot.pending(),
                snapshot.updating(), (Long2ObjectMap<ChunkHolder>) field(snapshot.chunkMap(), "visibleChunkMap"),
                snapshot.toDrop(), snapshot.unloadQueue(), snapshot.holders());
        Set<ChunkHolder> holders = Collections.newSetFromMap(new IdentityHashMap<>());
        holders.addAll(level.holders());
        // Look up fixed coordinates; never iterate mutable fastutil maps from this daemon.
        for (ChunkHolder holder : level.holders()) {
            long position = holder.getPos().toLong();
            add(holders, level.pending().get(position));
            add(holders, level.updating().get(position));
            add(holders, level.visible().get(position));
        }
        ChunkHolder callbackHolder = queuedCallbackHolder(level.unloadQueue());
        add(holders, callbackHolder);
        if (callbackHolder != null) {
            logHolder(level, callbackHolder, "queued-callback");
        }
        int nonzeroRefs = 0;
        int completedWithRefs = 0;
        int noLongerPendingWithRefs = 0;
        int details = 0;
        for (ChunkHolder holder : holders) {
            int refs = holder.getGenerationRefCount();
            if (refs == 0) continue;
            nonzeroRefs++;
            if (holder.getSaveSyncFuture().isDone()) {
                completedWithRefs++;
                if (level.pending().get(holder.getPos().toLong()) != holder) {
                    noLongerPendingWithRefs++;
                }
            }
            if (details++ < MAX_DETAILS && holder != callbackHolder) {
                logHolder(level, holder, "snapshot-holder");
            }
        }
        // Queue.size() is deliberately avoided: continuously requeued callbacks can prevent its traversal finishing.
        Object head = level.unloadQueue().peek();
        LOGGER.warn("[SBM_UNLOAD_DIAGNOSTICS] level={} sampledHolders={} refsNonzero={} "
                        + "saveDoneWithRefs={} noLongerPendingWithRefs={} queueHead={} detailsTruncated={}",
                level.dimension(), holders.size(), nonzeroRefs, completedWithRefs, noLongerPendingWithRefs,
                head == null ? "empty-at-sample" : head.getClass().getName(), details > MAX_DETAILS);
    }

    private static void add(Set<ChunkHolder> holders, ChunkHolder holder) {
        if (holder != null) holders.add(holder);
    }

    private static void logHolder(LevelSnapshot level, ChunkHolder holder, String source) {
        long position = holder.getPos().toLong();
        CompletableFuture<?> saveSync = holder.getSaveSyncFuture();
        // Map membership is a best-effort observation during shutdown, not a synchronized snapshot.
        LOGGER.warn("[SBM_UNLOAD_DIAGNOSTICS] level={} source={} pos={} identity={} refs={} "
                        + "saveDone={} saveCancelled={} saveExceptional={} inPending={} inUpdating={} inVisible={} toDrop={}",
                level.dimension(), source, holder.getPos(), Integer.toHexString(System.identityHashCode(holder)),
                holder.getGenerationRefCount(), saveSync.isDone(), saveSync.isCancelled(),
                saveSync.isCompletedExceptionally(), level.pending().get(position) == holder,
                level.updating().get(position) == holder, level.visible().get(position) == holder,
                level.toDrop().contains(position));
    }

    private static ChunkHolder queuedCallbackHolder(Queue<Runnable> queue) {
        // CompletableFuture.UniRun holds the scheduleUnload lambda in fn. Reading it does not claim/run it.
        for (int attempt = 0; attempt < 64; attempt++) {
            Object completion = queue.peek();
            if (completion == null) {
                Thread.onSpinWait();
                continue;
            }
            try {
                Object action = field(completion, "fn");
                if (action != null) {
                    for (Field captured : action.getClass().getDeclaredFields()) {
                        if (ChunkHolder.class.isAssignableFrom(captured.getType())) {
                            return (ChunkHolder) readField(action, captured);
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // JDK module access may deny this optional detail; holder observations remain available.
                return null;
            }
            Thread.onSpinWait();
        }
        return null;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                return readField(target, type.getDeclaredField(name));
            } catch (NoSuchFieldException ignored) {
                // Continue through the superclass chain.
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static Object readField(Object target, Field field) throws ReflectiveOperationException {
        if (field.trySetAccessible()) return field.get(target);
        // Only needed for java.base's private CompletableFuture.fn; this bridge performs object reads only.
        return ReadOnlyUnsafe.read(target, field);
    }

    private static final class ReadOnlyUnsafe {
        private static Object read(Object target, Field field) throws ReflectiveOperationException {
            Class<?> type = Class.forName("sun.misc.Unsafe");
            Field singleton = type.getDeclaredField("theUnsafe");
            if (!singleton.trySetAccessible()) throw new IllegalAccessException("Unsafe is unavailable");
            Object instance = singleton.get(null);
            Method offsetMethod = type.getMethod("objectFieldOffset", Field.class);
            long offset = ((Number) offsetMethod.invoke(instance, field)).longValue();
            return type.getMethod("getObjectVolatile", Object.class, long.class).invoke(instance, target, offset);
        }
    }

    private static final class ObserverState {
        private volatile List<LevelSnapshot> levels = List.of();
        // Read and written only by prepare on the server thread.
        private boolean started;
    }

    private record LevelSnapshot(String dimension, ChunkMap chunkMap, Long2ObjectMap<ChunkHolder> pending,
                                 Long2ObjectMap<ChunkHolder> updating, Long2ObjectMap<ChunkHolder> visible,
                                 LongSet toDrop, Queue<Runnable> unloadQueue, List<ChunkHolder> holders) {}
}
