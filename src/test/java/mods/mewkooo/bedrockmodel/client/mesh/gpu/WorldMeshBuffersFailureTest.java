package mods.mewkooo.bedrockmodel.client.mesh.gpu;

import mods.mewkooo.bedrockmodel.client.mesh.world.WorldMeshMetrics;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldMeshBuffersFailureTest {
    @Test
    void failedLightBufferUploadRestoresBindingAndDeletesTheBuffer() {
        WorldMeshBuffers buffers = new WorldMeshBuffers(new WorldMeshMetrics());
        AtomicInteger binding = new AtomicInteger(7);
        List<Integer> deleted = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("simulated GL upload failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> buffers.createLightBuffer(ByteBuffer.allocateDirect(16), new WorldMeshBuffers.LightBufferOperations() {
                    @Override public int currentArrayBuffer() { return binding.get(); }
                    @Override public int createBuffer() { return 19; }
                    @Override public void bindArrayBuffer(int id) { binding.set(id); }
                    @Override public void upload(ByteBuffer data) { throw failure; }
                    @Override public int bufferSize() { return 0; }
                    @Override public void deleteBuffer(int id) { deleted.add(id); }
                }));

        assertSame(failure, thrown);
        assertEquals(7, binding.get());
        assertEquals(List.of(19), deleted);
    }

    @Test
    void failedAllocationWithoutAJavaExceptionRestoresBindingAndDeletesTheBuffer() {
        WorldMeshMetrics metrics = new WorldMeshMetrics();
        WorldMeshBuffers buffers = new WorldMeshBuffers(metrics);
        AtomicInteger binding = new AtomicInteger(7);
        List<Integer> deleted = new ArrayList<>();

        assertThrows(IllegalStateException.class,
                () -> buffers.createLightBuffer(ByteBuffer.allocateDirect(16), new WorldMeshBuffers.LightBufferOperations() {
                    @Override public int currentArrayBuffer() { return binding.get(); }
                    @Override public int createBuffer() { return 19; }
                    @Override public void bindArrayBuffer(int id) { binding.set(id); }
                    @Override public void upload(ByteBuffer data) {}
                    @Override public int bufferSize() { return 0; }
                    @Override public void deleteBuffer(int id) { deleted.add(id); }
                }));

        assertEquals(7, binding.get());
        assertEquals(List.of(19), deleted);
        assertEquals(0, metrics.snapshot(true, 0, 0, buffers.pool).totals().lightBuffers());
    }
}
