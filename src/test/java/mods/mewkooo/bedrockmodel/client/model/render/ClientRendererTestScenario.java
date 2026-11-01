package mods.mewkooo.bedrockmodel.client.model.render;

import com.github.argon4w.acceleratedrendering.core.CoreFeature;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.AcceleratedBufferSource;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.IAcceleratedBufferSource;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.BufferSourceExtension;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IAcceleratableBufferSource;
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.layers.LayerDrawType;
import com.github.argon4w.acceleratedrendering.core.buffers.environments.IBufferEnvironment;
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.client.compat.acceleratedrendering.AcceleratedRenderingCompat;
import mods.mewkooo.bedrockmodel.client.compat.sodium.SodiumCompat;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.runtime.BakedModelInstance;
import mods.mewkooo.bedrockmodel.core.model.runtime.TreeModelInstance;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.function.Supplier;

/** Small real GPU readback check, invoked only by the opt-in client integration harness. */
@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class ClientRendererTestScenario {
    private static final String MODEL = """
            {"format_version":"1.12.0","minecraft:geometry":[{
              "description":{"identifier":"geometry.client_smoke","texture_width":16,"texture_height":16},
              "bones":[{"name":"root","pivot":[0,0,0],"cubes":[
                {"origin":[-8,-8,-8],"size":[16,16,16],"uv":[0,0]}]}]}]}
            """;
    private static final RenderType MATERIAL = RenderType.entityCutoutNoCull(
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;
    private static boolean active;
    private static int frames;
    private static int ticks;
    private static Throwable failure;
    private static TreeModelInstance tree;
    private static BakedModelInstance baked;
    private static TextureTarget target;

    private ClientRendererTestScenario() {}

    public static void start(Minecraft minecraft) {
        RenderSystem.assertOnRenderThread();
        check(!active, "renderer scenario is already active");
        BedrockModelPOJO pojo = GsonUtil.CLIENT_GSON.fromJson(MODEL, BedrockModelPOJO.class);
        tree = TreeBedrockModel.bake(pojo).createInstance();
        baked = BakedBedrockModel.bake(pojo, BakerOptions.ofAnimatedBones(Set.of("root"))).createInstance();
        target = new TextureTarget(128, 128, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        minecraft.getMainRenderTarget().bindWrite(true);
        frames = ticks = 0;
        failure = null;
        active = true;
    }

    public static boolean tick(Minecraft minecraft) {
        if (failure != null) throw new AssertionError("renderer scenario failed", failure);
        if (frames >= 3) return true;
        check(++ticks < 600, "renderer scenario timed out waiting for world render frames");
        return false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!active || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        try {
            renderFrame(Minecraft.getInstance());
            if (++frames >= 3) {
                active = false;
                destroyTarget();
                BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] PASS renderer profile={} frames={} TREE+BAKED pixel readback",
                        System.getenv("SBM_CLIENT_PROFILE"), frames);
            }
        } catch (Throwable error) {
            failure = error;
            active = false;
            destroyTarget();
        }
    }

    private static void destroyTarget() {
        if (target == null) return;
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            target.destroyBuffers();
        } finally {
            target = null;
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        }
    }

    private static void renderFrame(Minecraft minecraft) {
        RenderSystem.assertOnRenderThread();
        check(GL11.glGetError() == GL11.GL_NO_ERROR, "renderer frame must start without a GL error");
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        float[] color = RenderSystem.getShaderColor().clone();
        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();
        source.endBatch();
        boolean accelerated = ModList.get().isLoaded("acceleratedrendering");
        RenderSystem.getModelViewStack().pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try (AccelerationScope scope = accelerated ? new ArScope(source) : AccelerationScope.NONE) {
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-1.5f, 1.5f, -1.5f, 1.5f, 0.1f, 10),
                    VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            int treePixels = drawModel(source, scope, false, false, accelerated);
            int bakedPixels = drawModel(source, scope, true, false, accelerated);
            if (ModList.get().isLoaded("sodium") || ModList.get().isLoaded("embeddium")) {
                drawModel(source, scope, false, true, false);
                drawModel(source, scope, true, true, false);
            }
            check(GL11.glGetError() == GL11.GL_NO_ERROR, "model draw/readback must not leave a GL error");
            BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] renderer frame={} treePixels={} bakedPixels={} accelerated={}",
                    frames + 1, treePixels, bakedPixels, accelerated);
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private static int drawModel(MultiBufferSource.BufferSource source, AccelerationScope scope,
                                 boolean useBaked, boolean nativeWriter, boolean assertAccelerated) {
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        PoseStack pose = new PoseStack();
        pose.translate(0, 0, -3);
        pose.mulPose(new Quaternionf().rotateXYZ(0.2f, 0.45f, 0));
        VertexConsumer consumer = source.getBuffer(MATERIAL);
        if (frames == 0) BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] renderer source={} consumer={} baked={} nativeWriter={}",
                source.getClass().getName(), consumer.getClass().getName(), useBaked, nativeWriter);
        int root = tree.baseModel().getIndex("root");
        var chunk = baked.baseModel().chunkForBone(baked.baseModel().getIndex("root"));
        if (nativeWriter) {
            boolean written = useBaked
                    ? SodiumCompat.writeQuads(chunk, consumer, LIGHT, OVERLAY, 1, 1, 1, 1,
                            pose.last().pose(), pose.last().normal(), true)
                    : SodiumCompat.writeCubes(tree.baseModel().bone(root).cubes(), consumer, LIGHT, OVERLAY,
                            1, 1, 1, 1, pose.last().pose(), pose.last().normal(), true);
            check(written, "installed Sodium/Embeddium native vertex writer must accept the live consumer");
        } else {
            BedrockModelRenderer.render(useBaked ? baked : tree, pose, consumer, LIGHT, OVERLAY);
        }
        // Iris FullyBufferedMultiBufferSource.endBatch(RenderType) intentionally does nothing.
        // The source was drained before this test pass, so flush all owned test vertices before readback.
        source.endBatch();
        scope.flush();
        int pixels = coloredPixels();
        check(pixels > 64, (useBaked ? "BAKED" : "TREE") + " must draw visible geometry, got " + pixels + " pixels");
        if (assertAccelerated) {
            // Probe the backend separately after readback, so this draw cannot mask a public renderer failure.
            consumer = source.getBuffer(MATERIAL);
            boolean accepted = useBaked
                    ? AcceleratedRenderingCompat.renderQuads(chunk, consumer, pose.last(), LIGHT, OVERLAY, 1, 1, 1, 1)
                    : AcceleratedRenderingCompat.renderCubes(tree.baseModel().bone(root), consumer, pose.last(),
                            LIGHT, OVERLAY, 1, 1, 1, 1);
            check(accepted, "Accelerated Rendering must accept real geometry during the level render stage");
            source.endBatch();
            scope.flush();
        }
        return pixels;
    }

    private static int coloredPixels() {
        ByteBuffer pixels = ByteBuffer.allocateDirect(128 * 128 * 4);
        GL11.glReadPixels(0, 0, 128, 128, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        int colored = 0;
        for (int offset = 0; offset < pixels.capacity(); offset += 4) {
            if (Byte.toUnsignedInt(pixels.get(offset)) > 20 || Byte.toUnsignedInt(pixels.get(offset + 1)) > 20
                    || Byte.toUnsignedInt(pixels.get(offset + 2)) > 20) colored++;
        }
        return colored;
    }

    private interface AccelerationScope extends AutoCloseable {
        AccelerationScope NONE = new AccelerationScope() {
            @Override public void flush() {}
            @Override public void close() {}
        };
        void flush();
        @Override void close();
    }

    /** Loaded only when AR is installed; the ordinary live source is restored before the world pass continues. */
    private static final class ArScope implements AccelerationScope {
        private final IAcceleratableBufferSource source;
        private final Supplier<IAcceleratedBufferSource> previous;
        private final AcceleratedBufferSource isolated;

        private ArScope(MultiBufferSource.BufferSource source) {
            check(CoreFeature.isRenderingLevel(),
                    "AR must be inside its real level render scope");
            check(AcceleratedEntityRenderingFeature.isEnabled(),
                    "AR entity rendering must be enabled for the accelerated profile");
            BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] AR stage pipeline before explicit opt-in={}, entityEnabled={}, levelScope={}",
                    AcceleratedEntityRenderingFeature.getPipelineSetting(),
                    AcceleratedEntityRenderingFeature.isEnabled(),
                    CoreFeature.isRenderingLevel());
            this.source = BufferSourceExtension.getAcceleratable(source);
            previous = this.source.getBoundAcceleratedBufferSource();
            isolated = new AcceleratedBufferSource(
                    IBufferEnvironment.Presets.ENTITY);
            this.source.bindAcceleratedBufferSource(() -> isolated);
            // AR's stage filter deliberately pushes VANILLA for third-party RenderLevelStageEvent callbacks.
            // Opt this owned test geometry in through AR's public scoped API, then restore the stage setting.
            AcceleratedEntityRenderingFeature.forceUseAcceleratedPipeline();
        }

        @Override public void flush() {
            isolated.prepareBuffers();
            isolated.drawBuffers(LayerDrawType.ALL);
            isolated.clearBuffers();
        }

        @Override public void close() {
            try {
                source.bindAcceleratedBufferSource(previous);
                isolated.delete();
            } finally {
                AcceleratedEntityRenderingFeature.resetPipeline();
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
