package mods.mewkooo.bedrockmodel.v1.common;

import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ParticleEffectData;
import com.maydaymemory.mae.basic.BaseKeyframe;

public class ParticleEffectDataKeyframe extends BaseKeyframe<ParticleEffectData> {
    private final ParticleEffectData particleEffectData;

    public ParticleEffectDataKeyframe(float timeS, ParticleEffectData particleEffectData) {
        super(timeS);
        this.particleEffectData = particleEffectData;
    }

    @Override
    public ParticleEffectData getValue() {
        return particleEffectData;
    }
}
