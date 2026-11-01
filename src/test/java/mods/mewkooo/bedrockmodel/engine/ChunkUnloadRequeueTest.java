package mods.mewkooo.bedrockmodel.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bounded diagnostic reproduction of the upstream Minecraft 1.21.1 unload queue.
 * This calls the actual ChunkMap and ChunkHolder implementations; it does not
 * reproduce their algorithm in test code or establish how often gameplay reaches
 * the input state. No Bedrock Model production class participates in the fixture.
 */
class ChunkUnloadRequeueTest {
    @BeforeAll
    static void bootstrapRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    @Timeout(10)
    void cancelledUnloadKeepsRequeuingUntilGenerationReferenceIsReleased() throws Exception {
        Fixture fixture = new Fixture();
        fixture.pendingUnloads.put(fixture.position, fixture.holder);
        fixture.scheduleUnload();
        assertEquals(1, fixture.queue.size());

        // updateChunkScheduling reclaims a pending holder by removing this entry.
        // Its already scheduled unload continuation remains in unloadQueue. New
        // generation work may now claim the same holder before that callback runs.
        assertSame(fixture.holder, fixture.pendingUnloads.remove(fixture.position));
        fixture.holder.increaseGenerationRefCount();
        assertTrue(fixture.holder.getSaveSyncFuture().isDone());
        assertFalse(fixture.holder.isReadyForSaving());

        int budget = 10_000;
        fixture.processUnloads(budget);

        assertEquals(budget, fixture.queue.nonNullPolls,
                "actual processUnloads must consume the entire finite budget");
        assertEquals(budget + 1, fixture.queue.additions,
                "actual scheduleUnload re-enqueues one callback on every poll");
        assertEquals(1, fixture.queue.size(), "the queue still cannot drain");
        assertTrue(fixture.pendingUnloads.isEmpty(), "this unload was already cancelled");
        assertEquals(1, fixture.holder.getGenerationRefCount());

        // A server-main-thread generation completion would release this claim.
        // Once that work can run, the same unmodified upstream callback drains.
        fixture.holder.decreaseGenerationRefCount();
        fixture.processUnloads(10);
        assertTrue(fixture.holder.isReadyForSaving());
        assertTrue(fixture.queue.isEmpty());
        assertEquals(budget + 1, fixture.queue.nonNullPolls);
        assertEquals(budget + 1, fixture.queue.additions);
    }

    @Test
    @Timeout(10)
    void incompleteSaveFutureWaitsForCompletionWithoutPolling() throws Exception {
        Fixture fixture = new Fixture();
        CompletableFuture<Void> saveDependency = new CompletableFuture<>();
        setField(ChunkHolder.class, fixture.holder, "saveSync", saveDependency);
        fixture.holder.increaseGenerationRefCount();

        fixture.scheduleUnload();
        assertTrue(fixture.queue.isEmpty(), "an incomplete future must not run its continuation yet");
        fixture.processUnloads(10_000);
        assertEquals(0, fixture.queue.nonNullPolls);
        assertEquals(0, fixture.queue.additions);

        // CompletableFuture invokes the selected executor when completion occurs.
        // The queue itself, rather than a busy JDK future, causes the first test.
        saveDependency.complete(null);
        assertEquals(1, fixture.queue.additions);
        assertEquals(1, fixture.queue.size());
        fixture.holder.decreaseGenerationRefCount();
        fixture.processUnloads(10);
        assertTrue(fixture.queue.isEmpty());
        assertEquals(1, fixture.queue.nonNullPolls);
    }

    private static final class Fixture {
        private final ChunkMap chunkMap;
        private final CountingQueue queue = new CountingQueue();
        private final Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads = new Long2ObjectLinkedOpenHashMap<>();
        private final ChunkHolder holder = new ChunkHolder(new ChunkPos(0, 0), 40,
                LevelHeightAccessor.create(0, 256), null, null, null);
        private final long position = holder.getPos().toLong();

        private Fixture() throws Exception {
            // ChunkMap's constructor opens a real world's storage and schedulers.
            // Unsafe only allocates the isolated container, after which the exact
            // fields read by the two real methods are initialized. Neither method
            // is patched, overridden, mocked, or copied into this test.
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeClass.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            Object unsafe = singleton.get(null);
            chunkMap = (ChunkMap) unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, ChunkMap.class);
            setField(ChunkMap.class, chunkMap, "unloadQueue", queue);
            setField(ChunkMap.class, chunkMap, "pendingUnloads", pendingUnloads);
            setField(ChunkMap.class, chunkMap, "toDrop", new LongOpenHashSet());
            setField(ChunkMap.class, chunkMap, "updatingChunkMap", new Long2ObjectLinkedOpenHashMap<ChunkHolder>());
            setField(ChunkMap.class, chunkMap, "visibleChunkMap", new Long2ObjectLinkedOpenHashMap<ChunkHolder>());
        }

        private void scheduleUnload() throws Exception {
            Method method = ChunkMap.class.getDeclaredMethod("scheduleUnload", long.class, ChunkHolder.class);
            method.setAccessible(true);
            method.invoke(chunkMap, position, holder);
        }

        private void processUnloads(int maxCallbacks) throws Exception {
            AtomicInteger remaining = new AtomicInteger(maxCallbacks);
            BooleanSupplier budget = () -> remaining.getAndDecrement() > 0;
            Method method = ChunkMap.class.getDeclaredMethod("processUnloads", BooleanSupplier.class);
            method.setAccessible(true);
            // Queue size stays at most one; its >2000 fallback therefore cannot
            // bypass this finite supplier. Never pass the shutdown's constant true.
            method.invoke(chunkMap, budget);
        }
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class CountingQueue extends ConcurrentLinkedQueue<Runnable> {
        private int additions;
        private int nonNullPolls;

        @Override
        public boolean add(Runnable value) {
            additions++;
            return super.add(value);
        }

        @Override
        public Runnable poll() {
            Runnable result = super.poll();
            if (result != null) nonNullPolls++;
            return result;
        }
    }
}
