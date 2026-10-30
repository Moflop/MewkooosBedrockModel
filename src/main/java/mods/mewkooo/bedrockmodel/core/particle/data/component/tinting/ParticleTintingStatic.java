package mods.mewkooo.bedrockmodel.core.particle.data.component.tinting;

import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangContext;
import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.core.particle.data.component.IParticleComponent;
import mods.mewkooo.bedrockmodel.core.particle.data.component.IParticleComponentDefinition;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;

public record ParticleTintingStatic(MolangExpression r, MolangExpression g, MolangExpression b,
                                     @Nullable MolangExpression a)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 210; }
    @Override public boolean requireUpdate() { return false; }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        p.r = (float) r.evaluate(ctx);
        p.g = (float) g.evaluate(ctx);
        p.b = (float) b.evaluate(ctx);
        p.a = a != null ? (float) a.evaluate(ctx) : 1f;
    }
}

