package mods.mewkooo.bedrockmodel.client.particle.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleDescription;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.particle.data.component.ParticleLifetimeEvents;
import mods.mewkooo.bedrockmodel.core.particle.data.component.motion.ParticleMotionCollision;
import mods.mewkooo.bedrockmodel.core.particle.data.event.SoundEffectEvent;
import mods.mewkooo.bedrockmodel.core.particle.runtime.EventExecutor;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleEmitterInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleEventSink;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleInstance;
import mods.mewkooo.bedrockmodel.core.particle.runtime.ParticleMolangEnvironment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class ClientParticleTestHarness {
    private static final String CASE = System.getenv("SBM_CLIENT_TEST");
    private static final int TIMEOUT_TICKS = 600;

    private static Stage stage = Stage.WAITING_FOR_WORLD;
    private static int ticks;
    private static boolean completed;
    private static ParticleEmitterInstance collisionEmitter;
    private static AtomicInteger collisions;
    private static AtomicInteger expirations;
    private static final Map<BlockPos, BlockState> RESTORE_BLOCKS = new java.util.LinkedHashMap<>();
    private static ParticleEmitterInstance lifecycleEmitter;
    private static ParticleEmitterInstance unloadEmitter;

    private ClientParticleTestHarness() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (CASE == null || CASE.isBlank() || completed) return;

        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.level == null) {
                if (++ticks > TIMEOUT_TICKS) {
                    fail(minecraft, "timed out waiting for a client world");
                }
                return;
            }

            if (stage == Stage.WAITING_FOR_WORLD) {
                startTests(minecraft);
            } else if (stage == Stage.WAITING_FOR_COLLISION) {
                if (collisions.get() == 1 && expirations.get() == 1 && collisionEmitter.getParticleCount() == 0) {
                    restoreBlocks(minecraft);
                    if ("all".equals(CASE)) startLifecycleTest(minecraft);
                    else pass(minecraft);
                } else if (++ticks > TIMEOUT_TICKS) {
                    fail(minecraft, "collision test timed out: collisions=" + collisions.get()
                            + ", expirations=" + expirations.get()
                            + ", emitterParticles=" + collisionEmitter.getParticleCount());
                }
            } else if (stage == Stage.WAITING_FOR_UNLOAD_PARTICLE) {
                if (countWorldParticles(minecraft) > 0) {
                    simulateWorldUnload(minecraft);
                } else if (++ticks > TIMEOUT_TICKS) {
                    fail(minecraft, "external particle was not added to ParticleEngine");
                }
            } else if (stage == Stage.WAITING_FOR_LIFECYCLE_PARTICLE) {
                if (countWorldParticles(minecraft) > 0) {
                    verifyRestartAndClearKeepExternalParticle(minecraft);
                } else if (++ticks > TIMEOUT_TICKS) {
                    fail(minecraft, "external lifecycle particle was not added to ParticleEngine");
                }
            } else if (stage == Stage.WAITING_FOR_LIFECYCLE_EXPIRATION) {
                if (lifecycleEmitter.getParticleCount() == 0 && countWorldParticles(minecraft) == 0) {
                    BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] PASS clear/restart retain particles until expiration");
                    if ("all".equals(CASE)) startUnloadTest(minecraft);
                    else pass(minecraft);
                } else if (++ticks > 80) {
                    fail(minecraft, "externally managed particle did not release at its lifetime");
                }
            }
        } catch (Throwable failure) {
            fail(minecraft, failure.toString());
            BedrockModelMod.LOGGER.error("Client particle test failed", failure);
        }
    }

    private static void startTests(Minecraft minecraft) {
        ticks = 0;
        if ("anchor".equals(CASE) || "all".equals(CASE)) {
            verifyAnchorRender(minecraft);
        }
        if ("anchor".equals(CASE)) {
            pass(minecraft);
        } else if ("unload".equals(CASE)) {
            startUnloadTest(minecraft);
        } else if ("lifecycle".equals(CASE)) {
            startLifecycleTest(minecraft);
        } else if ("collision".equals(CASE) || "all".equals(CASE)) {
            startCollisionTest(minecraft);
        } else {
            throw new IllegalArgumentException("Unknown SBM_CLIENT_TEST case: " + CASE);
        }
    }

    private static void startCollisionTest(Minecraft minecraft) {
        collisions = new AtomicInteger();
        expirations = new AtomicInteger();
        ParticleEffectDefinition definition = definition(true);
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        collisionEmitter = new ParticleEmitterInstance(definition, molang);
        collisionEmitter.setEventContext(new EventExecutor.EventContext(collisionEmitter, molang,
                new ParticleEventSink() {
                    @Override public void spawnParticle(ResourceLocation effectId, Vec3 position) {}

                    @Override
                    public void playSound(ResourceLocation soundId, Vec3 position) {
                        if (soundId.equals(ResourceLocation.fromNamespaceAndPath("test", "collision"))) {
                            collisions.incrementAndGet();
                        } else if (soundId.equals(ResourceLocation.fromNamespaceAndPath("test", "expiration"))) {
                            expirations.incrementAndGet();
                        }
                    }
                }, Vec3.ZERO));

        BlockPos base = BlockPos.containing(minecraft.player.getX() + 4, minecraft.player.getY() + 0.2,
                minecraft.player.getZ());
        BlockPos target = new BlockPos(base.getX(), base.getY(), base.getZ());
        for (int x = target.getX() - 2; x < target.getX(); x++) {
            for (int y = target.getY(); y <= target.getY() + 1; y++) {
                BlockPos pos = new BlockPos(x, y, target.getZ());
                RESTORE_BLOCKS.put(pos, minecraft.level.getBlockState(pos));
                minecraft.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        RESTORE_BLOCKS.put(target, minecraft.level.getBlockState(target));
        RESTORE_BLOCKS.put(target.above(), minecraft.level.getBlockState(target.above()));
        minecraft.level.setBlock(target, Blocks.STONE.defaultBlockState(), 3);
        minecraft.level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 3);

        Vec3 start = new Vec3(target.getX() - 1.5, target.getY() + 0.1, target.getZ() + 0.5);
        collisionEmitter.setExternalParticleManagement(data -> {
            data.x = (float) start.x;
            data.y = (float) start.y;
            data.z = (float) start.z;
            data.vx = 8;
            data.maxLifetime = 10;
            data.alive = true;
            minecraft.particleEngine.add(new SnowStormParticle(minecraft.level, data, definition, collisionEmitter));
        });
        collisionEmitter.spawnParticle();
        stage = Stage.WAITING_FOR_COLLISION;
        ticks = 0;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] running collision in {}", target);
    }

    private static void verifyAnchorRender(Minecraft minecraft) {
        ParticleEffectDefinition definition = definition(false);
        ParticleMolangEnvironment molang = new ParticleMolangEnvironment();
        ParticleEmitterInstance emitter = new ParticleEmitterInstance(definition, molang);
        Vec3 player = minecraft.player.position();
        float anchorX = (float) player.x + 4;
        float anchorY = (float) player.y + 4;
        float anchorZ = (float) player.z;
        emitter.setFollowAnchor(true);
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f().translation(anchorX, anchorY, anchorZ));

        ParticleInstance data = new ParticleInstance();
        data.emitter = emitter;
        data.x = anchorX + 1;
        data.y = anchorY;
        data.z = anchorZ;
        data.vx = 2;
        data.maxLifetime = 10;
        SnowStormParticle particle = new SnowStormParticle(minecraft.level, data, definition, emitter);
        particle.tick();
        emitter.setEmitterTransform(new Matrix4f(), new Matrix4f().translation(anchorX + 10, anchorY, anchorZ));
        particle.tick();

        List<float[]> vertices = new ArrayList<>(4);
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addVertex") && args != null && args.length == 3) {
                        vertices.add(new float[]{((Number) args[0]).floatValue(), ((Number) args[1]).floatValue(),
                                ((Number) args[2]).floatValue()});
                        return proxy;
                    }
                    return method.getReturnType().isInstance(proxy) ? proxy : null;
                });
        particle.render(consumer, minecraft.gameRenderer.getMainCamera(), 0.5f);
        check(vertices.size() == 4, "anchor render must emit four quad vertices");

        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        float centerX = (float) vertices.stream().mapToDouble(vertex -> vertex[0]).average().orElseThrow();
        float centerY = (float) vertices.stream().mapToDouble(vertex -> vertex[1]).average().orElseThrow();
        float centerZ = (float) vertices.stream().mapToDouble(vertex -> vertex[2]).average().orElseThrow();
        check(Math.abs(centerX - (data.x - camera.x)) < 1.0e-4,
                "rendered anchor X must include anchor motion and particle velocity");
        check(Math.abs(centerY - (data.y - camera.y)) < 1.0e-4,
                "rendered anchor Y must match particle position");
        check(Math.abs(centerZ - (data.z - camera.z)) < 1.0e-4,
                "rendered anchor Z must match particle position");
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] PASS anchor rendered center {}, {}, {}", centerX, centerY, centerZ);
    }

    private static void startUnloadTest(Minecraft minecraft) {
        ParticleEffectDefinition definition = definition(false);
        Vec3 position = minecraft.player.position().add(0, 2, 0);
        unloadEmitter = WorldEmitterManager.getInstance().addEmitter(minecraft.level, position, Vec3.ZERO, definition);
        check(unloadEmitter != null, "unload emitter must be created");
        unloadEmitter.spawnParticle();
        check(unloadEmitter.getParticleCount() == 1, "unload particle must be tracked before level unload");
        stage = Stage.WAITING_FOR_UNLOAD_PARTICLE;
        ticks = 0;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] waiting for external particle to enter ParticleEngine");
    }

    private static void startLifecycleTest(Minecraft minecraft) {
        Vec3 position = minecraft.player.position().add(0, 2, 0);
        lifecycleEmitter = WorldEmitterManager.getInstance().addEmitter(minecraft.level, position, Vec3.ZERO,
                definition(false));
        check(lifecycleEmitter != null, "lifecycle emitter must be created");
        lifecycleEmitter.spawnParticle();
        check(lifecycleEmitter.getParticleCount() == 1, "lifecycle particle must be tracked before restart");
        stage = Stage.WAITING_FOR_LIFECYCLE_PARTICLE;
        ticks = 0;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] waiting for lifecycle particle to enter ParticleEngine");
    }

    private static void verifyRestartAndClearKeepExternalParticle(Minecraft minecraft) {
        check(countWorldParticles(minecraft) == 1, "one external lifecycle particle must be active");
        lifecycleEmitter.restart();
        check(lifecycleEmitter.getParticleCount() == 1, "restart must retain external particle ownership");
        check(countWorldParticles(minecraft) == 1, "restart must not remove the external particle");
        WorldEmitterManager.getInstance().clear();
        check(WorldEmitterManager.getInstance().getEmitterCount() == 0, "clear must remove active emitters");
        check(lifecycleEmitter.getParticleCount() == 1, "clear must retain external particle ownership");
        check(countWorldParticles(minecraft) == 1, "clear must leave the live external particle in ParticleEngine");
        stage = Stage.WAITING_FOR_LIFECYCLE_EXPIRATION;
        ticks = 0;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] restart and clear retained the external particle");
    }

    private static void simulateWorldUnload(Minecraft minecraft) {
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] unloading client level and clearing ParticleEngine");
        WorldEmitterManager.getInstance().clear();
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(minecraft.level));
        minecraft.particleEngine.setLevel(null);
        verifyWorldUnload(minecraft);
    }

    private static void verifyWorldUnload(Minecraft minecraft) {
        check(unloadEmitter.getParticleCount() == 0,
                "external particle ownership must be released when the client world unloads");
        check(countWorldParticles(minecraft) == 0, "ParticleEngine must discard world particles on level unload");
        check(WorldEmitterManager.getInstance().getEmitterCount() == 0,
                "world emitter manager must release emitters on level unload");
        minecraft.particleEngine.setLevel(minecraft.level);
        pass(minecraft);
    }

    private static long countWorldParticles(Minecraft minecraft) {
        AtomicInteger count = new AtomicInteger();
        minecraft.particleEngine.iterateParticles(particle -> {
            if (particle instanceof SnowStormParticle) count.incrementAndGet();
        });
        return count.get();
    }

    private static ParticleEffectDefinition definition(boolean collision) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "client_particle_test");
        ParticleDescription description = new ParticleDescription(id, ParticleDescription.Material.PARTICLES_BLEND,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png"), 0, 0);
        List<mods.mewkooo.bedrockmodel.core.particle.data.component.IComponent> components = new ArrayList<>();
        Map<String, List<mods.mewkooo.bedrockmodel.core.particle.data.event.IEventNode>> events = Map.of();
        if (collision) {
            components.add(new ParticleMotionCollision(null, 0, 0, 0, true,
                    List.of(new ParticleMotionCollision.CollisionEvent("contact", 0))));
            components.add(new ParticleLifetimeEvents(List.of(), List.of("expired"), new TreeMap<>()));
            events = Map.of("contact", List.of(new SoundEffectEvent("test:collision")),
                    "expired", List.of(new SoundEffectEvent("test:expiration")));
        }
        return new ParticleEffectDefinition(id, description, components, Map.of(), events);
    }

    private static void restoreBlocks(Minecraft minecraft) {
        RESTORE_BLOCKS.forEach((pos, state) -> minecraft.level.setBlock(pos, state, 3));
        RESTORE_BLOCKS.clear();
    }

    private static void pass(Minecraft minecraft) {
        completed = true;
        BedrockModelMod.LOGGER.info("[SBM_CLIENT_TEST] PASS {}", CASE);
        minecraft.stop();
    }

    private static void fail(Minecraft minecraft, String message) {
        if (completed) return;
        completed = true;
        if (minecraft.level != null) restoreBlocks(minecraft);
        BedrockModelMod.LOGGER.error("[SBM_CLIENT_TEST] FAIL {}: {}", CASE, message);
        minecraft.stop();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private enum Stage {
        WAITING_FOR_WORLD,
        WAITING_FOR_COLLISION,
        WAITING_FOR_LIFECYCLE_PARTICLE,
        WAITING_FOR_LIFECYCLE_EXPIRATION,
        WAITING_FOR_UNLOAD_PARTICLE
    }
}
