package mods.mewkooo.bedrockmodel.core.particle.data.component.motion;

import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangContext;
import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.core.particle.data.component.IParticleComponent;
import mods.mewkooo.bedrockmodel.core.particle.data.component.IParticleComponentDefinition;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static mods.mewkooo.bedrockmodel.core.particle.data.ParticleJsonUtils.getMolang;
import static mods.mewkooo.bedrockmodel.core.particle.data.ParticleJsonUtils.getMolangArray3;

/**
 * 参数化运动。对应 "minecraft:particle_motion_parametric"。
 */
public record ParticleMotionParametric(
        @Nullable MolangExpression[] relativePosition,
        @Nullable MolangExpression[] direction,
        @Nullable MolangExpression rotation
) implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 300; }
    @Override public boolean requireUpdate() { return true; }

    @Override
    public void apply(ParticleInstance p) { /* 首次由 update 处理 */ }

    @Override
    public void update(ParticleInstance p) {
        if (p.emitter == null) return;
        applyLegacy(p, p.emitter.getMolang().getContext(), 1f / 20f);
    }

    /** 兼容旧调用方（Phase 4 后移除） */
    public void applyLegacy(ParticleInstance p, MolangContext<?> ctx, float dt) {
        if (relativePosition != null) {
            p.x = (float) relativePosition[0].evaluate(ctx);
            p.y = (float) relativePosition[1].evaluate(ctx);
            p.z = (float) relativePosition[2].evaluate(ctx);
            p.parametricPosition = true;
            p.parametricPositionPendingTransform = p.worldSpace;
        }
        if (direction != null) {
            Vector3f facing = new Vector3f((float) direction[0].evaluate(ctx),
                    (float) direction[1].evaluate(ctx), (float) direction[2].evaluate(ctx));
            if (p.worldSpace && p.emitter != null) {
                Matrix4f transform = p.emitter.isFollowAnchor()
                        ? p.emitter.getWorldTransform() : p.spawnTransform;
                if (transform != null) transform.transformDirection(facing);
            }
            if (facing.isFinite() && facing.lengthSquared() > 0) facing.normalize();
            else facing.zero();
            p.directionX = facing.x; p.directionY = facing.y; p.directionZ = facing.z;
            p.parametricDirection = true;
        }
        if (rotation != null) {
            p.rotation = (float) rotation.evaluate(ctx);
        }
    }

    public static ParticleMotionParametric fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new ParticleMotionParametric(
                obj.has("relative_position")
                        ? compileArray3(molang, getMolangArray3(obj, "relative_position", "0", "0", "0")) : null,
                obj.has("direction")
                        ? compileArray3(molang, getMolangArray3(obj, "direction", "0", "0", "0")) : null,
                obj.has("rotation") ? molang.compile(getMolang(obj, "rotation", "0")) : null);
    }

    private static MolangExpression[] compileArray3(ParticleMolangEnvironment molang, String[] exprs) {
        return new MolangExpression[]{molang.compile(exprs[0]), molang.compile(exprs[1]), molang.compile(exprs[2])};
    }
}

