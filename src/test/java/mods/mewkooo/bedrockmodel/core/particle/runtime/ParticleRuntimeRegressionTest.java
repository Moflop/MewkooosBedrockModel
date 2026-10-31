package mods.mewkooo.bedrockmodel.core.particle.runtime;

import mods.mewkooo.bedrockmodel.core.particle.data.ParticleDescription;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.molang.runtime.MolangExpression;
import mods.mewkooo.bedrockmodel.core.particle.data.component.IComponent;
import mods.mewkooo.bedrockmodel.core.particle.data.component.ParticleLifetimeEvents;
import mods.mewkooo.bedrockmodel.core.particle.data.component.motion.ParticleMotionParametric;
import mods.mewkooo.bedrockmodel.core.particle.data.component.rate.EmitterRateSteady;
import mods.mewkooo.bedrockmodel.core.particle.data.event.EventSequence;
import mods.mewkooo.bedrockmodel.core.particle.data.event.IEventNode;
import mods.mewkooo.bedrockmodel.core.particle.data.event.SoundEffectEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParticleRuntimeRegressionTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "particle");

    @Test
    void steadyRateCountsExternallyManagedParticlesAgainstConfiguredLimit() {
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        EmitterRateSteady rate = new EmitterRateSteady(molang.compile("20000"), molang.compile("50"));
        ParticleEmitterInstance emitter = emitter(List.of(rate), molang);
        List<ParticleInstance> delivered = new ArrayList<>();
        emitter.setExternalParticleManagement(delivered::add);

        emitter.tick(1f / 20f);

        assertEquals(50, delivered.size());
        assertEquals(50, emitter.getParticleCount());
        emitter.releaseExternalParticle(delivered.getFirst());
        emitter.spawnParticle();
        assertEquals(50, emitter.getParticleCount());
    }

    @Test
    void parametricPositionAndDirectionUseBirthTransformInWorldSpace() {
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = emitter(List.of(), molang);
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f().translation(100, 200, 300));
        ParticleInstance particle = new ParticleInstance();
        particle.emitter = emitter;
        particle.worldSpace = true;
        particle.spawnTransform = new Matrix4f().translation(10, 20, 30).rotateY((float) Math.PI / 2);
        ParticleMotionParametric parametric = new ParticleMotionParametric(
                expressions(molang, "1", "2", "3"),
                expressions(molang, "1", "0", "0"),
                null);
        particle.updateComponents = List.of(parametric);

        emitter.updateSingleParticle(particle, 1f / 20f);

        assertTrue(particle.parametricPosition);
        assertTrue(particle.parametricDirection);
        assertEquals(13, particle.x, 1.0E-5);
        assertEquals(22, particle.y, 1.0E-5);
        assertEquals(29, particle.z, 1.0E-5);
        assertEquals(0, particle.directionX, 1.0E-5);
        assertEquals(0, particle.directionY, 1.0E-5);
        assertEquals(-1, particle.directionZ, 1.0E-5);
    }

    @Test
    void particleEventContextUsesParticleWorldPosition() {
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = emitter(List.of(), molang);
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f().translation(10, 20, 30));
        emitter.setEventContext(new EventExecutor.EventContext(emitter, molang, null, new Vec3(100, 100, 100)));
        ParticleInstance particle = new ParticleInstance();
        particle.emitter = emitter;
        particle.x = 1;
        particle.y = 2;
        particle.z = 3;

        Vec3 position = emitter.particleEventContext(particle).position();

        assertEquals(new Vec3(11, 22, 33), position);
    }

    @Test
    void parametricTimelineEventUsesWorldPositionBeforeParticleTransformIsCommitted() {
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleMotionParametric parametric = new ParticleMotionParametric(
                expressions(molang, "1", "2", "3"), null, null);
        TreeMap<Float, List<String>> timeline = new TreeMap<>();
        timeline.put(0f, List.of("timeline"));
        ParticleLifetimeEvents lifetimeEvents = new ParticleLifetimeEvents(List.of(), List.of(), timeline);
        ParticleEmitterInstance emitter = emitter(List.of(parametric, lifetimeEvents), molang,
                Map.of("timeline", List.of(new SoundEffectEvent("test:timeline"))));
        List<Vec3> soundPositions = new ArrayList<>();
        emitter.setEventContext(new EventExecutor.EventContext(emitter, molang, new ParticleEventSink() {
            @Override
            public void spawnParticle(ResourceLocation effectId, Vec3 position) {}

            @Override
            public void playSound(ResourceLocation soundId, Vec3 position) {
                soundPositions.add(position);
            }
        }, new Vec3(100, 100, 100)));
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f().translation(100, 200, 300));

        ParticleInstance particle = new ParticleInstance();
        particle.emitter = emitter;
        particle.worldSpace = true;
        particle.spawnTransform = new Matrix4f().translation(10, 20, 30).rotateY((float) Math.PI / 2);
        particle.updateComponents = List.of(parametric, lifetimeEvents.createRuntime());

        emitter.updateSingleParticle(particle, 1f / 20f);

        Vec3 expectedWorldPosition = new Vec3(13, 22, 29);
        assertEquals(List.of(expectedWorldPosition), soundPositions);
        assertEquals(13, particle.x, 1.0E-5);
        assertEquals(22, particle.y, 1.0E-5);
        assertEquals(29, particle.z, 1.0E-5);
        assertFalse(particle.parametricPositionPendingTransform);
    }

    @Test
    void deeplyRecursiveEventNodesStopAtDepthLimit() {
        List<IEventNode> nodes = new ArrayList<>();
        EventSequence recursive = new EventSequence(nodes);
        nodes.add(recursive);

        assertDoesNotThrow(() -> EventExecutor.execute(recursive, null));
    }

    private static MolangExpression[] expressions(ParticleMolangEnvironment molang, String x, String y, String z) {
        return new MolangExpression[]{molang.compile(x), molang.compile(y), molang.compile(z)};
    }

    private static ParticleEmitterInstance emitter(List<? extends IComponent> components,
                                                   ParticleMolangEnvironment molang) {
        return emitter(components, molang, Map.of());
    }

    private static ParticleEmitterInstance emitter(List<? extends IComponent> components,
                                                   ParticleMolangEnvironment molang,
                                                   Map<String, List<IEventNode>> events) {
        ParticleDescription description = new ParticleDescription(ID, ParticleDescription.Material.PARTICLES_BLEND,
                ResourceLocation.fromNamespaceAndPath("test", "particle"), 0, 0);
        ParticleEffectDefinition definition = new ParticleEffectDefinition(ID, description,
                new ArrayList<>(components), Map.of(), events);
        return new ParticleEmitterInstance(definition, molang);
    }
}
