package mods.mewkooo.bedrockmodel.core.json.pojo;

import it.unimi.dsi.fastutil.doubles.Double2ObjectRBTreeMap;

public class ParticleEffectKeyframes {
    private final Double2ObjectRBTreeMap<ParticleEffectData> keyframes;

    public ParticleEffectKeyframes(Double2ObjectRBTreeMap<ParticleEffectData> keyframes) {
        this.keyframes = keyframes;
    }

    public Double2ObjectRBTreeMap<ParticleEffectData> getKeyframes() {
        return keyframes;
    }
}

