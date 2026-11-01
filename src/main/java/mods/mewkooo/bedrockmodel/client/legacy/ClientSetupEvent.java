package mods.mewkooo.bedrockmodel.client.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.client.legacy.compat.embeddium.EmbeddiumCompat;
import mods.mewkooo.bedrockmodel.client.legacy.compat.sodium.SodiumCompat;
import mods.mewkooo.bedrockmodel.client.compat.acceleratedrendering.AcceleratedRenderingCompat;
import mods.mewkooo.bedrockmodel.client.model.render.BakedGeometryChunkRenderer;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedGeometryChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@EventBusSubscriber(value = Dist.CLIENT, modid = BedrockModelMod.MOD_ID)
public class ClientSetupEvent {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SodiumCompat.init();
        EmbeddiumCompat.init();
        BakedGeometryChunkRenderer.installBackend(new BakedGeometryChunkRenderer.Backend() {
            @Override
            public boolean renderQuads(BakedGeometryChunk chunk,
                                       VertexConsumer consumer,
                                       PoseStack.Pose pose, int light, int overlay,
                                       float red, float green, float blue, float alpha) {
                return AcceleratedRenderingCompat.renderQuads(chunk, consumer, pose, light, overlay, red, green, blue, alpha);
            }

            @Override
            public boolean renderVertices(BakedGeometryChunk chunk,
                                          VertexConsumer consumer,
                                          PoseStack.Pose pose, int light, int overlay,
                                          float red, float green, float blue, float alpha) {
                return AcceleratedRenderingCompat.renderVertices(chunk, consumer, pose, light, overlay, red, green, blue, alpha);
            }

            @Override
            public boolean writeQuads(BakedGeometryChunk chunk,
                                      VertexConsumer consumer, int light, int overlay,
                                      float red, float green, float blue, float alpha,
                                      Matrix4f pose, Matrix3f normal, boolean skip) {
                return mods.mewkooo.bedrockmodel.client.compat.sodium.SodiumCompat.writeQuads(chunk, consumer, light, overlay, red, green, blue, alpha, pose, normal, skip);
            }
        });
    }
}
