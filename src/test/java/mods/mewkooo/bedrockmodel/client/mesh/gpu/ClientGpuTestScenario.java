package mods.mewkooo.bedrockmodel.client.mesh.gpu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.client.mesh.world.WorldMeshMetrics;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;

/** Integration checks invoked only by the opt-in client test harness, on its render thread. */
public final class ClientGpuTestScenario {
    private ClientGpuTestScenario() {}

    public static void run() {
        RenderSystem.assertOnRenderThread();
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "GPU scenario must start without an outstanding GL error");
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] GPU vendor={}, renderer={}, version={}",
                GL11.glGetString(GL11.GL_VENDOR), GL11.glGetString(GL11.GL_RENDERER),
                GL11.glGetString(GL11.GL_VERSION));

        int originalBinding = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int sentinel = GlStateManager._glGenBuffers();
        WorldMeshMetrics metrics = new WorldMeshMetrics();
        WorldMeshBuffers buffers = new WorldMeshBuffers(metrics);
        try {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, sentinel);
            verifySuccessfulUpload(buffers, metrics, sentinel);
            verifyFailedUpload(buffers, metrics, sentinel, false);
            verifySuccessfulUpload(buffers, metrics, sentinel);
            verifyFailedUpload(buffers, metrics, sentinel, true);
            verifySuccessfulUpload(buffers, metrics, sentinel);
            check(metrics.snapshot(true, 0, 0, buffers.pool).totals().lightBuffers() == 0,
                    "GPU scenario must release every successful light buffer");
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "GPU scenario must leave no GL error");
        } finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, originalBinding);
            RenderSystem.glDeleteBuffers(sentinel);
        }
        check(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == originalBinding,
                "GPU scenario must restore the original array-buffer binding");
        check(!GL15.glIsBuffer(sentinel), "GPU scenario must delete its sentinel buffer");
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] PASS gpu real buffers, upload exception, GL_INVALID_VALUE, recovery");
    }

    private static void verifySuccessfulUpload(WorldMeshBuffers buffers, WorldMeshMetrics metrics, int sentinel) {
        ByteBuffer data = ByteBuffer.allocateDirect(16);
        for (int index = 0; index < data.capacity(); index++) data.put(index, (byte) (index * 7));
        int id = buffers.createLightBuffer(data);
        try {
            check(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == sentinel,
                    "successful light-buffer upload must restore the previous binding");
            check(GL15.glIsBuffer(id), "successful light-buffer upload must produce a real GL buffer");
            check(metrics.snapshot(true, 0, 0, buffers.pool).totals().lightBuffers() == 1,
                    "only the live successful buffer must be counted");
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, id);
            check(GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE) == data.remaining(),
                    "real GL buffer must have the requested storage size");
            ByteBuffer actual = ByteBuffer.allocateDirect(data.remaining());
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, actual);
            for (int index = 0; index < data.remaining(); index++) {
                check(actual.get(index) == data.get(index), "real light-buffer contents must round trip");
            }
        } finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, sentinel);
            RenderSystem.glDeleteBuffers(id);
            metrics.recordLightBufferReleased();
        }
        check(!GL15.glIsBuffer(id), "successful light-buffer storage must be deleted at cleanup");
    }

    private static void verifyFailedUpload(WorldMeshBuffers buffers, WorldMeshMetrics metrics, int sentinel,
                                           boolean driverError) {
        RealOperations operations = new RealOperations(driverError);
        boolean failed = false;
        try {
            buffers.createLightBuffer(ByteBuffer.allocateDirect(16), operations);
        } catch (IllegalStateException expected) {
            failed = true;
        }
        try {
            check(failed, "a failed GL allocation or Java upload must not publish a light-buffer handle");
            check(!driverError || operations.invalidUploadReturned,
                    "the real invalid GL upload must return normally before storage validation rejects it");
            check(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == sentinel,
                    "failed light-buffer upload must restore the previous real GL binding");
            check(operations.bufferId > 0 && !GL15.glIsBuffer(operations.bufferId),
                    "failed light-buffer upload must delete its real GL buffer");
            check(metrics.snapshot(true, 0, 0, buffers.pool).totals().lightBuffers() == 0,
                    "failed light-buffer upload must not increment the live buffer count");
            int error = GL11.glGetError();
            check(error == (driverError ? GL11.GL_INVALID_VALUE : GL11.GL_NO_ERROR),
                    "only the deliberately invalid GL upload may produce GL_INVALID_VALUE, got " + error);
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "expected GPU error must be fully consumed by the test");
        } finally {
            // Also clean up if a regression publishes a buffer instead of rejecting the failed allocation.
            if (operations.bufferId > 0 && GL15.glIsBuffer(operations.bufferId)) {
                RenderSystem.glDeleteBuffers(operations.bufferId);
            }
        }
    }

    private static final class RealOperations implements WorldMeshBuffers.LightBufferOperations {
        private final boolean driverError;
        private int bufferId;
        private boolean invalidUploadReturned;

        private RealOperations(boolean driverError) { this.driverError = driverError; }

        @Override public int currentArrayBuffer() { return GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING); }
        @Override public int createBuffer() { return bufferId = GlStateManager._glGenBuffers(); }
        @Override public void bindArrayBuffer(int id) { GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, id); }
        @Override public int bufferSize() { return GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE); }
        @Override public void deleteBuffer(int id) { RenderSystem.glDeleteBuffers(id); }

        @Override
        public void upload(ByteBuffer data) {
            if (driverError) {
                // GL rejects a negative size without allocating storage; this does not stress GPU memory.
                BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] deliberately requesting GL_INVALID_VALUE for GPU cleanup check");
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, -1L, GL15.GL_DYNAMIC_DRAW);
                invalidUploadReturned = true;
            } else {
                RenderSystem.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_DYNAMIC_DRAW);
                throw new IllegalStateException("deliberate Java exception after a real GL allocation");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
