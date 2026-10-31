package mods.mewkooo.bedrockmodel.client.mesh.world;

import mods.mewkooo.bedrockmodel.client.mesh.gpu.WorldMeshPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceMeshBatchesRecoveryTest {
    @Test
    void invalidActiveEntryIsRequeuedForTheSameGeometryKey() {
        StubCache cache = new StubCache();
        StubCache.StubEntry first = cache.enqueue("geometry");
        InstanceMeshBatches<String> batches = new InstanceMeshBatches<>(cache);
        batches.put("instance", "geometry", Vec3.ZERO, new Matrix4f(), 0);
        batches.prepare(null);
        assertTrue(batches.ready("instance", "geometry"));

        first.ready = false;
        StubCache.StubEntry replacement = cache.enqueue("geometry");
        batches.put("instance", "geometry", Vec3.ZERO, new Matrix4f(), 0);
        batches.prepare(null);

        assertEquals(1, first.releases);
        assertEquals(1, replacement.preparations);
        assertTrue(batches.ready("instance", "geometry"));
    }

    private static final class StubCache extends GeometryCache {
        private final ArrayDeque<StubEntry> entries = new ArrayDeque<>();

        private StubEntry enqueue(Object key) {
            StubEntry entry = new StubEntry(key);
            entries.addLast(entry);
            return entry;
        }

        @Override
        public Entry acquire(Object key) {
            return entries.removeFirst();
        }

        private final class StubEntry extends GeometryCache.Entry {
            private boolean ready;
            private int releases;
            private int preparations;
            private final WorldMeshPart handle = new WorldMeshPart(null, "test", null, null, null, null,
                    0, CompletableFuture.completedFuture(null), -1, null, WorldMeshPart.LightMode.FIXED);

            private StubEntry(Object key) {
                super(key);
            }

            @Override public boolean captured() { return true; }
            @Override public boolean queued() { return false; }
            @Override public boolean prepareGpu(WorldMeshGroup<?> owner) {
                preparations++;
                ready = true;
                return true;
            }
            @Override public boolean ready() { return ready; }
            @Override public List<WorldMeshPart> handles() { return List.of(handle); }
            @Override public void release() { releases++; }
        }
    }
}
