package mods.mewkooo.bedrockmodel.v1.particle.render;

import mods.mewkooo.bedrockmodel.v1.particle.data.ParticleDescription;
import mods.mewkooo.bedrockmodel.v1.particle.data.component.ParticleAppearanceBillboard;
import mods.mewkooo.bedrockmodel.v1.particle.data.component.ParticleAppearanceLighting;
import mods.mewkooo.bedrockmodel.v1.particle.runtime.ParticleEmitterInstance;
import mods.mewkooo.bedrockmodel.v1.particle.runtime.ParticleInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 粒子渲染器。将发射器中的局部空间粒子渲染到 PoseStack 局部坐标系中。
 * <p>
 * 世界空间粒子由原版 {@code ParticleEngine} 渲染，不经过此渲染器。
 */
@OnlyIn(Dist.CLIENT)
public final class ParticleRenderer {

    private ParticleRenderer() {}

    /**
     * 渲染一个发射器的所有局部空间粒子。
     * <p>
     * 发射器变换矩阵从 emitter 实例上读取。粒子坐标在发射器局部空间中，
     * 渲染时叠加发射器变换。
     *
     * @param cameraPitch    摄像机 pitch（弧度）
     * @param cameraRoll     摄像机 roll（弧度）
     */
    public static void render(ParticleEmitterInstance emitter, PoseStack poseStack,
                               MultiBufferSource bufferSource, int light, float partialTick,
                               float cameraPitch, float cameraRoll) {
        List<ParticleInstance> particles = emitter.getParticles();
        if (particles.isEmpty()) return;

        var definition = emitter.getDefinition();
        ParticleDescription desc = definition.getDescription();
        ResourceLocation texture = desc.getTexture();

        // 选择 RenderType
        RenderType renderType = getRenderType(desc.getMaterial(), texture);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // 确定朝向模式
        ParticleAppearanceBillboard billboard = definition.findComponent(ParticleAppearanceBillboard.class);
        ParticleAppearanceBillboard.FaceCameraMode mode = billboard != null
                ? billboard.faceCameraMode()
                : ParticleAppearanceBillboard.FaceCameraMode.ROTATE_XYZ;

        Matrix4f emitterTransform = emitter.getEmitterTransform();
        boolean localPos = emitter.isLocalPosition();
        boolean localRot = emitter.isLocalRotation();

        // 光照：存在 particle_appearance_lighting 组件时按环境光照（传入 light）着色，否则自发光恒满亮度
        boolean hasLighting = definition.findComponent(ParticleAppearanceLighting.class) != null;
        int particleLight = hasLighting ? light : LightTexture.FULL_BRIGHT;

        for (ParticleInstance particle : particles) {
            BillboardHelper.renderBillboard(particle, poseStack, consumer, particleLight, mode,
                    emitterTransform, localPos, localRot, cameraPitch, cameraRoll);
        }
    }

    /**
     * 根据材质类型选择 RenderType。
     */
    private static RenderType getRenderType(ParticleDescription.Material material, ResourceLocation texture) {
        return switch (material) {
            case PARTICLES_OPAQUE -> RenderType.entityCutout(texture);
            case PARTICLES_ALPHA -> RenderType.entityTranslucent(texture);
            case PARTICLES_BLEND -> RenderType.entityTranslucent(texture);
            case PARTICLES_ADD -> ParticleRenderType.additiveParticle(texture);
            case ENERGY_SWIRL -> RenderType.energySwirl(texture, 1, 1);
            case PARTICLES_EMISSIVE -> ParticleRenderType.emissiveParticle(texture);
        };
    }
}
