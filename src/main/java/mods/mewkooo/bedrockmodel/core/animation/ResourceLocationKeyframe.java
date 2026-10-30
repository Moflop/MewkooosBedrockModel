package mods.mewkooo.bedrockmodel.core.animation;

import com.maydaymemory.mae.basic.BaseKeyframe;
import net.minecraft.resources.ResourceLocation;

public class ResourceLocationKeyframe extends BaseKeyframe<ResourceLocation> {
    private final ResourceLocation resourceLocation;

    public ResourceLocationKeyframe(float timeS, ResourceLocation resourceLocation) {
        super(timeS);
        this.resourceLocation = resourceLocation;
    }

    @Override
    public ResourceLocation getValue() {
        return resourceLocation;
    }
}

