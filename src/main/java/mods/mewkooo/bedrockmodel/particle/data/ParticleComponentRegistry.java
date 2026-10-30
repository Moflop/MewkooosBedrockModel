package mods.mewkooo.bedrockmodel.particle.data;

import mods.mewkooo.bedrockmodel.particle.data.component.*;
import mods.mewkooo.bedrockmodel.particle.data.component.lifetime.*;
import mods.mewkooo.bedrockmodel.particle.data.component.motion.*;
import mods.mewkooo.bedrockmodel.particle.data.component.rate.*;
import mods.mewkooo.bedrockmodel.particle.data.component.shape.*;
import mods.mewkooo.bedrockmodel.particle.data.component.tinting.*;
import mods.mewkooo.bedrockmodel.particle.data.component.*;
import mods.mewkooo.bedrockmodel.particle.data.component.lifetime.EmitterLifetimeExpression;
import mods.mewkooo.bedrockmodel.particle.data.component.lifetime.EmitterLifetimeLooping;
import mods.mewkooo.bedrockmodel.particle.data.component.lifetime.EmitterLifetimeOnce;
import mods.mewkooo.bedrockmodel.particle.data.component.motion.ParticleMotionCollision;
import mods.mewkooo.bedrockmodel.particle.data.component.motion.ParticleMotionDynamic;
import mods.mewkooo.bedrockmodel.particle.data.component.motion.ParticleMotionParametric;
import mods.mewkooo.bedrockmodel.particle.data.component.rate.EmitterRateInstant;
import mods.mewkooo.bedrockmodel.particle.data.component.rate.EmitterRateManual;
import mods.mewkooo.bedrockmodel.particle.data.component.rate.EmitterRateSteady;
import mods.mewkooo.bedrockmodel.particle.data.component.shape.EmitterShape;
import mods.mewkooo.bedrockmodel.particle.data.component.tinting.ParticleTintingFactory;
import mods.mewkooo.bedrockmodel.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 粒子组件注册表。
 */
public final class ParticleComponentRegistry {

    @FunctionalInterface
    public interface ComponentDeserializer {
        IComponent deserialize(String key, JsonElement value, ParticleMolangEnvironment molang);
    }

    private static final Map<String, ComponentDeserializer> DESERIALIZERS = new HashMap<>();

    static {
        // Emitter Rate
        register("minecraft:emitter_rate_instant", EmitterRateInstant::fromJson);
        register("minecraft:emitter_rate_steady", EmitterRateSteady::fromJson);
        register("minecraft:emitter_rate_manual", EmitterRateManual::fromJson);

        // Emitter Lifetime
        register("minecraft:emitter_lifetime_looping", EmitterLifetimeLooping::fromJson);
        register("minecraft:emitter_lifetime_once", EmitterLifetimeOnce::fromJson);
        register("minecraft:emitter_lifetime_expression", EmitterLifetimeExpression::fromJson);

        // Emitter Initialization
        register("minecraft:emitter_initialization",
                (key, elem, molang) -> EmitterInitialization.fromJson(elem.getAsJsonObject(), molang));

        // Emitter Local Space
        register("minecraft:emitter_local_space",
                (key, elem, molang) -> EmitterLocalSpace.fromJson(elem.getAsJsonObject()));

        // Emitter Shape
        register("minecraft:emitter_shape_point", EmitterShape::fromJson);
        register("minecraft:emitter_shape_sphere", EmitterShape::fromJson);
        register("minecraft:emitter_shape_box", EmitterShape::fromJson);
        register("minecraft:emitter_shape_disc", EmitterShape::fromJson);
        register("minecraft:emitter_shape_entity_aabb", EmitterShape::fromJson);

        // Emitter Lifetime Events
        register("minecraft:emitter_lifetime_events",
                (key, elem, molang) -> EmitterLifetimeEvents.fromJson(elem.getAsJsonObject()));

        // Particle Appearance
        register("minecraft:particle_appearance_billboard",
                (key, elem, molang) -> ParticleAppearanceBillboard.fromJson(elem.getAsJsonObject(), molang));
        register("minecraft:particle_appearance_tinting", ParticleTintingFactory::fromJson);
        register("minecraft:particle_appearance_lighting",
                (key, elem, molang) -> ParticleAppearanceLighting.instance());

        // Particle Motion
        register("minecraft:particle_motion_dynamic", ParticleMotionDynamic::fromJson);
        register("minecraft:particle_motion_parametric", ParticleMotionParametric::fromJson);
        register("minecraft:particle_motion_collision", ParticleMotionCollision::fromJson);

        // Particle Lifetime
        register("minecraft:particle_lifetime_expression",
                (key, elem, molang) -> ParticleLifetimeExpression.fromJson(elem.getAsJsonObject(), molang));
        register("minecraft:particle_lifetime_kill_plane",
                (key, elem, molang) -> ParticleLifetimeKillPlane.fromJson(elem));
        register("minecraft:particle_lifetime_events",
                (key, elem, molang) -> ParticleLifetimeEvents.fromJson(elem.getAsJsonObject()));

        // Particle Expire
        register("minecraft:particle_expire_if_in_blocks",
                (key, elem, molang) -> ParticleExpireIfInBlocks.fromJson(elem));
        register("minecraft:particle_expire_if_not_in_blocks",
                (key, elem, molang) -> ParticleExpireIfNotInBlocks.fromJson(elem));

        // Particle Initial
        register("minecraft:particle_initial_speed",
                (key, elem, molang) -> ParticleInitialSpeed.fromJson(elem, molang));
        register("minecraft:particle_initial_spin",
                (key, elem, molang) -> ParticleInitialSpin.fromJson(elem.getAsJsonObject(), molang));
        register("minecraft:particle_initialization",
                (key, elem, molang) -> ParticleInitialization.fromJson(elem.getAsJsonObject(), molang));
    }

    private ParticleComponentRegistry() {}

    private static void register(String key, ComponentDeserializer deserializer) {
        DESERIALIZERS.put(key, deserializer);
    }

    @Nullable
    public static IComponent deserialize(String key, JsonElement value, ParticleMolangEnvironment molang) {
        var deserializer = DESERIALIZERS.get(key);
        if (deserializer == null) return null;
        return deserializer.deserialize(key, value, molang);
    }
}
