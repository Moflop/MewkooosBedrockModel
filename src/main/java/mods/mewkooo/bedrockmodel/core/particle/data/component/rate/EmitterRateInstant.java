package mods.mewkooo.bedrockmodel.core.particle.data.component.rate;

import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangContext;
import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.core.particle.data.component.IEmitterComponent;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleEmitterInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static mods.mewkooo.bedrockmodel.core.particle.data.ParticleJsonUtils.getMolang;

/**
 * 一次性发射。对应 "minecraft:emitter_rate_instant"。
 */
public record EmitterRateInstant(MolangExpression amount) implements RateComponent {

    @Override public int order() { return 530; }
    @Override public boolean requireUpdate() { return true; }

    @Override
    public IEmitterComponent createRuntime() {
        return new Runtime(amount);
    }

    public static EmitterRateInstant fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new EmitterRateInstant(molang.compile(getMolang(obj, "num_particles", "10")));
    }

    // ===== Runtime =====

    static final class Runtime implements IEmitterComponent {
        private final MolangExpression amount;
        private boolean hasEmitted;

        Runtime(MolangExpression amount) { this.amount = amount; }

        @Override
        public void apply(ParticleEmitterInstance emitter) {
            this.hasEmitted = false;
        }

        @Override
        public void update(ParticleEmitterInstance emitter) {
            if (hasEmitted || !emitter.isActive()) return;
            MolangContext<?> ctx = emitter.getMolang().getContext();
            double requested = amount.evaluate(ctx);
            int count = Double.isFinite(requested)
                    ? Math.min(ParticleEmitterInstance.MAX_PARTICLES, Math.max(0, (int) requested))
                    : 0;
            count = Math.min(count,
                    Math.max(0, ParticleEmitterInstance.MAX_PARTICLES - emitter.getParticleCount()));
            for (int i = 0; i < count; i++) {
                emitter.spawnParticle();
            }
            hasEmitted = true;
        }
    }
}

