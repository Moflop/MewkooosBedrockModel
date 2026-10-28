package mods.mewkooo.bedrockmodel.v1.common.resource;

import mods.mewkooo.bedrockmodel.v1.common.resource.exclusion.NullAdapter;
import mods.mewkooo.bedrockmodel.v1.common.resource.exclusion.ServerExclusionStrategyForRootMotion;
import mods.mewkooo.bedrockmodel.v1.common.resource.exclusion.ServerNormalExclusionStrategy;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.AnimationKeyframes;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.CubesItem;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ItemDisplayTransform;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.ParticleEffectKeyframes;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.SoundEffectKeyframes;
import mods.mewkooo.bedrockmodel.v1.common.resource.pojo.TimelineKeyframes;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.AnimationKeyframesSerializer;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.ItemDisplayTransformSerializer;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.ItemDisplayTransformsSerializer;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.ParticleEffectKeyframesSerializer;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.SoundEffectKeyframesSerializer;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.TimelineKeyframesSerializer;
import mods.mewkooo.bedrockmodel.v1.common.resource.serialize.Vector3fSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public class GsonUtil {
    public static final Gson CLIENT_GSON = new GsonBuilder()
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .registerTypeAdapter(CubesItem.class, new CubesItem.Deserializer())
            .registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
            .registerTypeAdapter(ItemDisplayTransform.class, new ItemDisplayTransformSerializer())
            .registerTypeAdapter(ItemDisplayTransforms.class, new ItemDisplayTransformsSerializer())
            .registerTypeAdapter(AnimationKeyframes.class, new AnimationKeyframesSerializer())
            .registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer())
            .registerTypeAdapter(ParticleEffectKeyframes.class, new ParticleEffectKeyframesSerializer())
            .registerTypeAdapter(TimelineKeyframes.class, new TimelineKeyframesSerializer())
            .create();

    public static final Gson SERVER_NORMAL_GSON = new GsonBuilder()
            .addDeserializationExclusionStrategy(new ServerNormalExclusionStrategy())
            .registerTypeAdapter(BedrockModelPOJO.class, new NullAdapter<>())
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer())
            .registerTypeAdapter(ParticleEffectKeyframes.class, new ParticleEffectKeyframesSerializer())
            .registerTypeAdapter(TimelineKeyframes.class, new TimelineKeyframesSerializer())
            .create();

    public static final Gson SERVER_GSON_FOR_ROOT_MOTION = new GsonBuilder()
            .addDeserializationExclusionStrategy(new ServerExclusionStrategyForRootMotion())
            .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
            .registerTypeAdapter(Vector3f.class, new Vector3fSerializer())
            .registerTypeAdapter(AnimationKeyframes.class, new AnimationKeyframesSerializer())
            .registerTypeAdapter(SoundEffectKeyframes.class, new SoundEffectKeyframesSerializer())
            .registerTypeAdapter(ParticleEffectKeyframes.class, new ParticleEffectKeyframesSerializer())
            .registerTypeAdapter(TimelineKeyframes.class, new TimelineKeyframesSerializer())
            .create();
}
