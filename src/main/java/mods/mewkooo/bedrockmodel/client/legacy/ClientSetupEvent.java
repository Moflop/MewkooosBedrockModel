package mods.mewkooo.bedrockmodel.client.legacy;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.client.legacy.compat.embeddium.EmbeddiumCompat;
import mods.mewkooo.bedrockmodel.client.legacy.compat.sodium.SodiumCompat;
import mods.mewkooo.bedrockmodel.client.compat.acceleratedrendering.AcceleratedRenderingCompat;
import mods.mewkooo.bedrockmodel.client.model.render.BakedGeometryChunkRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(value = Dist.CLIENT, modid = BedrockModelMod.MOD_ID)
public class ClientSetupEvent {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SodiumCompat.init();
        EmbeddiumCompat.init();
        BakedGeometryChunkRenderer.installBackend(new BakedGeometryChunkRenderer.Backend() {
            @Override
            public boolean renderQuads(mods.mewkooo.bedrockmodel.core.model.baked.BakedGeometryChunk chunk,
                                       com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                       com.mojang.blaze3d.vertex.PoseStack.Pose pose, int light, int overlay,
                                       float red, float green, float blue, float alpha) {
                return AcceleratedRenderingCompat.renderQuads(chunk, consumer, pose, light, overlay, red, green, blue, alpha);
            }

            @Override
            public boolean renderVertices(mods.mewkooo.bedrockmodel.core.model.baked.BakedGeometryChunk chunk,
                                          com.mojang.blaze3d.vertex.VertexConsumer consumer,
                                          com.mojang.blaze3d.vertex.PoseStack.Pose pose, int light, int overlay,
                                          float red, float green, float blue, float alpha) {
                return AcceleratedRenderingCompat.renderVertices(chunk, consumer, pose, light, overlay, red, green, blue, alpha);
            }

            @Override
            public boolean writeQuads(mods.mewkooo.bedrockmodel.core.model.baked.BakedGeometryChunk chunk,
                                      com.mojang.blaze3d.vertex.VertexConsumer consumer, int light, int overlay,
                                      float red, float green, float blue, float alpha,
                                      org.joml.Matrix4f pose, org.joml.Matrix3f normal, boolean skip) {
                return mods.mewkooo.bedrockmodel.client.compat.sodium.SodiumCompat.writeQuads(chunk, consumer, light, overlay, red, green, blue, alpha, pose, normal, skip);
            }
        });
    }
}
