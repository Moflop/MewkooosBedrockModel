package mods.mewkooo.bedrockmodel.particle.data.component;

import mods.mewkooo.bedrockmodel.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.particle.runtime.ParticleEmitterInstance;
import mods.mewkooo.bedrockmodel.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import static mods.mewkooo.bedrockmodel.particle.data.ParticleJsonUtils.getMolang;

public record EmitterInitialization(
        @Nullable MolangExpression creationExpression,
        @Nullable MolangExpression perUpdateExpression
) implements IEmitterComponentDefinition, IEmitterComponent {

    @Override
    public int order() {
        return 505;
    }

    @Override
    public boolean requireUpdate() {
        return perUpdateExpression != null;
    }

    @Override
    public void apply(ParticleEmitterInstance emitter) {
        if (creationExpression != null) {
            creationExpression.evaluate(emitter.getMolang().getContext());
        }
    }

    @Override
    public void update(ParticleEmitterInstance emitter) {
        if (perUpdateExpression != null) {
            perUpdateExpression.evaluate(emitter.getMolang().getContext());
        }
    }

    public static EmitterInitialization fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression creation = null;
        MolangExpression perUpdate = null;
        if (obj.has("creation_expression")) {
            String expr = getMolang(obj, "creation_expression", "");
            if (!expr.isEmpty()) creation = molang.compile(expr);
        }
        if (obj.has("per_update_expression")) {
            String expr = getMolang(obj, "per_update_expression", "");
            if (!expr.isEmpty()) perUpdate = molang.compile(expr);
        }
        return new EmitterInitialization(creation, perUpdate);
    }
}
