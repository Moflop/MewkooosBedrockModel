package mods.mewkooo.bedrockmodel.core.particle.data.component;

import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangContext;
import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.core.particle.data.component.shape.EmitterShape;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;

import java.util.Random;

import static mods.mewkooo.bedrockmodel.core.particle.data.ParticleJsonUtils.molangFromElement;

public record ParticleInitialSpeed(MolangExpression speed)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -500;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        float s = (float) speed.evaluate(ctx);
        if (s == 0) return;
        EmitterShape shape = p.emitter.getDefinition().emitterPreset().find(EmitterShape.class);
        if (shape != null) {
            shape.applyDirection(p, ctx, new Random(), s);
        }
    }

    public static ParticleInitialSpeed fromJson(JsonElement value, ParticleMolangEnvironment molang) {
        return new ParticleInitialSpeed(molang.compile(molangFromElement(value, "0")));
    }
}

