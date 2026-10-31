package mods.mewkooo.bedrockmodel.client.particle.world;

import mods.mewkooo.bedrockmodel.core.particle.data.ParticleDescription;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.particle.data.component.ParticleLifetimeEvents;
import mods.mewkooo.bedrockmodel.core.particle.data.event.SoundEffectEvent;
import mods.mewkooo.bedrockmodel.core.particle.runtime.EventExecutor;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleEmitterInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleEventSink;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnowStormParticleAnchorTest {
    @Test
    void deadWorldParticleFiresItsExpirationEventBeforeRemoval() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "expiring_particle");
        ParticleLifetimeEvents lifetimeEvents = new ParticleLifetimeEvents(List.of(), List.of("expire"), new TreeMap<>());
        ParticleDescription description = new ParticleDescription(id, ParticleDescription.Material.PARTICLES_BLEND,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/generic_0"), 0, 0);
        ParticleEffectDefinition definition = new ParticleEffectDefinition(id, description, List.of(lifetimeEvents),
                Map.of(), Map.of("expire", List.of(new SoundEffectEvent("test:expire"))));
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);
        AtomicInteger fired = new AtomicInteger();
        emitter.setEventContext(new EventExecutor.EventContext(emitter, molang, new ParticleEventSink() {
            @Override public void spawnParticle(ResourceLocation effectId, Vec3 position) {}
            @Override public void playSound(ResourceLocation soundId, Vec3 position) { fired.incrementAndGet(); }
        }, Vec3.ZERO));
        ParticleInstance data = new ParticleInstance();
        data.maxLifetime = 10;
        data.alive = false;

        new SnowStormParticle(null, data, definition, emitter).tick();

        assertEquals(1, fired.get());
    }

    @Test
    void anchorMotionAndParticleVelocityBothAffectWorldPosition() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "anchor_particle");
        ParticleDescription description = new ParticleDescription(id, ParticleDescription.Material.PARTICLES_BLEND,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/particle/generic_0"), 0, 0);
        ParticleEffectDefinition definition = new ParticleEffectDefinition(id, description, List.of(), Map.of(), Map.of());
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);
        emitter.setFollowAnchor(true);
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f());

        ParticleInstance data = new ParticleInstance();
        data.x = 1;
        data.vx = 2;
        data.maxLifetime = 10;
        SnowStormParticle particle = new SnowStormParticle(null, data, definition, emitter);

        particle.tick();
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f().translation(10, 0, 0));
        particle.tick();

        assertEquals(11.2, data.x, 1.0E-5);
    }
}
