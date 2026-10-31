package mods.mewkooo.bedrockmodel.core.particle.runtime;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.particle.data.event.*;
import mods.mewkooo.bedrockmodel.core.particle.resource.ParticleDefinitionLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 粒子事件执行引擎。
 */
public final class EventExecutor {

    private static final int MAX_EVENT_DEPTH = 64;
    private static final ThreadLocal<Integer> EVENT_DEPTH = ThreadLocal.withInitial(() -> 0);

    private EventExecutor() {}

    /**
     * 事件执行上下文。
     */
    public record EventContext(
            ParticleEmitterInstance emitter,
            ParticleMolangEnvironment molang,
            @Nullable ParticleEventSink effects,
            @Nullable Vec3 position,
            Random random  // 本地随机源，用于 EventRandomize
    ) {
        public EventContext(ParticleEmitterInstance emitter, ParticleMolangEnvironment molang,
                            @Nullable ParticleEventSink effects, @Nullable Vec3 position) {
            this(emitter, molang, effects, position, new Random());
        }
    }

    public static void fireEvents(List<String> eventNames, ParticleEffectDefinition definition, EventContext ctx) {
        if (eventNames.isEmpty()) return;
        Map<String, List<IEventNode>> allEvents = definition.getEvents();
        if (allEvents.isEmpty()) return;

        for (String name : eventNames) {
            List<IEventNode> nodes = allEvents.get(name);
            if (nodes == null) continue;
            for (IEventNode node : nodes) {
                execute(node, ctx);
            }
        }
    }

    public static void execute(IEventNode node, EventContext ctx) {
        int depth = EVENT_DEPTH.get();
        if (depth >= MAX_EVENT_DEPTH) {
            BedrockModelMod.LOGGER.warn("Particle event recursion exceeded {} levels", MAX_EVENT_DEPTH);
            return;
        }
        EVENT_DEPTH.set(depth + 1);
        try {
            executeInternal(node, ctx);
        } finally {
            if (depth == 0) EVENT_DEPTH.remove();
            else EVENT_DEPTH.set(depth);
        }
    }

    private static void executeInternal(IEventNode node, EventContext ctx) {
        if (node instanceof EventSequence seq) {
            for (IEventNode child : seq.nodes()) {
                execute(child, ctx);
            }
        } else if (node instanceof EventRandomize randomize) {
            executeRandomize(randomize, ctx);
        } else if (node instanceof ParticleEffectEvent effect) {
            executeParticleEffect(effect, ctx);
        } else if (node instanceof SoundEffectEvent sound) {
            executeSoundEffect(sound, ctx);
        } else if (node instanceof MolangExpressionEvent expr) {
            executeMolangExpression(expr, ctx);
        } else if (node instanceof EventLog log) {
            executeLog(log);
        }
    }

    private static void executeRandomize(EventRandomize randomize, EventContext ctx) {
        List<EventRandomize.WeightedEntry> entries = randomize.entries();
        if (entries.isEmpty()) return;

        float totalWeight = 0;
        for (EventRandomize.WeightedEntry entry : entries) {
            totalWeight += entry.weight();
        }
        if (totalWeight <= 0) return;

        float roll = ctx.random().nextFloat() * totalWeight;
        float cumulative = 0;
        for (EventRandomize.WeightedEntry entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                execute(entry.node(), ctx);
                return;
            }
        }
        execute(entries.get(entries.size() - 1).node(), ctx);
    }

    private static void executeParticleEffect(ParticleEffectEvent effect, EventContext ctx) {
        if (ctx.effects == null || ctx.position == null) return;

        // 执行预编译的 pre_effect_expression
        if (effect.compiledPreEffect() != null) {
            try {
                effect.compiledPreEffect().evaluate(ctx.molang.getContext());
            } catch (Exception e) {
                BedrockModelMod.LOGGER.warn("Failed to evaluate pre_effect_expression: {}", effect.preEffectExpression(), e);
            }
        }

        ResourceLocation effectId = ResourceLocation.tryParse(effect.effect());
        if (effectId == null) return;
        ParticleEffectDefinition childDef = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (childDef == null) {
            BedrockModelMod.LOGGER.debug("Particle effect event references unknown effect: {}", effect.effect());
            return;
        }

        ctx.effects.spawnParticle(effectId, ctx.position);
    }

    private static void executeSoundEffect(SoundEffectEvent sound, EventContext ctx) {
        if (ctx.effects == null || ctx.position == null) return;
        String eventName = sound.eventName();
        if (eventName == null || eventName.isEmpty()) return;

        try {
            ResourceLocation soundId = ResourceLocation.parse(eventName);
            ctx.effects.playSound(soundId, ctx.position);
        } catch (Exception e) {
            BedrockModelMod.LOGGER.warn("Failed to play sound effect: {}", eventName, e);
        }
    }

    private static void executeMolangExpression(MolangExpressionEvent expr, EventContext ctx) {
        // 使用预编译的表达式
        if (expr.compiledExpression() == null) return;
        try {
            expr.compiledExpression().evaluate(ctx.molang.getContext());
        } catch (Exception e) {
            BedrockModelMod.LOGGER.warn("Failed to evaluate event molang expression: {}", expr.expression(), e);
        }
    }

    private static void executeLog(EventLog log) {
        BedrockModelMod.LOGGER.info("[ParticleEvent] {}", log.message());
    }
}

