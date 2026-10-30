package mods.mewkooo.bedrockmodel.particle.data.component.shape;

import mods.mewkooo.bedrockmodel.molang.runtime.MolangContext;
import mods.mewkooo.bedrockmodel.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapePoint(MolangExpression[] offset, @Nullable MolangExpression[] direction,
                                 DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        p.x = (float) offset[0].evaluate(ctx);
        p.y = (float) offset[1].evaluate(ctx);
        p.z = (float) offset[2].evaluate(ctx);
    }
}
