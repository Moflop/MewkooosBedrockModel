package mods.mewkooo.bedrockmodel.v1.particle.data.component;

import mods.mewkooo.bedrockmodel.v1.molang.runtime.MolangContext;
import mods.mewkooo.bedrockmodel.v1.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.v1.particle.runtime.ParticleInstance;
import mods.mewkooo.bedrockmodel.v1.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import static mods.mewkooo.bedrockmodel.v1.particle.data.ParticleJsonUtils.getMolang;

public record ParticleInitialSpin(MolangExpression rotation, MolangExpression rotationRate)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return -490;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        p.rotation = (float) rotation.evaluate(ctx);
        p.rotationRate = (float) rotationRate.evaluate(ctx);
    }

    public static ParticleInitialSpin fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        return new ParticleInitialSpin(
                molang.compile(getMolang(obj, "rotation", "0")),
                molang.compile(getMolang(obj, "rotation_rate", "0")));
    }
}
