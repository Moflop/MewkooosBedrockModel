package mods.mewkooo.bedrockmodel.core.particle.runtime;

import mods.mewkooo.bedrockmodel.core.molang.runtime.value.NumberValue;
import mods.mewkooo.bedrockmodel.core.particle.data.ParticleEffectDefinition;
import mods.mewkooo.bedrockmodel.core.particle.data.component.*;
import mods.mewkooo.bedrockmodel.core.particle.data.component.motion.*;
import mods.mewkooo.bedrockmodel.core.particle.data.component.rate.*;
import mods.mewkooo.bedrockmodel.core.particle.data.component.shape.*;
import mods.mewkooo.bedrockmodel.core.particle.data.component.*;
import mods.mewkooo.bedrockmodel.core.particle.data.component.motion.ParticleMotionCollision;
import mods.mewkooo.bedrockmodel.core.particle.data.component.rate.EmitterRateManual;
import mods.mewkooo.bedrockmodel.core.particle.data.component.shape.EmitterShape;
import mods.mewkooo.bedrockmodel.core.particle.data.curve.ParticleCurve;
import mods.mewkooo.bedrockmodel.core.particle.data.event.IEventNode;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParticleEmitterInstance {
    public static final int MAX_PARTICLES = 16384;
    public static final int POOL_SIZE = 16384 / 4;
    private static final Random RANDOM = new Random();

    private final ParticleEffectDefinition definition;
    private final ParticleMolangEnvironment molang;

    // 发射器状态
    private float emitterAge;
    private float emitterLifetime;
    private boolean active = true;
    private boolean removed;
    private boolean sleeping;
    private float sleepTimer;
    private float currentDt;

    // Runtime 组件调度
    private final List<IEmitterComponent> emitterUpdateComponents = new ArrayList<>();

    /**
     * 每实例 molang 变量覆盖表：在 {@link #bindEmitterContext()} 时写入共享变量存储，
     * 仅在该发射器自身 tick 期间生效（绑定紧随本发射器的粒子更新，天然按实例隔离，不跨发射器串扰）。
     * 定义中的表达式（size/tinting/initial_speed 等）可直接读取这些变量。
     */
    private final Map<String, Float> variableOverrides = new HashMap<>();

    private final int emitterRandom1 = RANDOM.nextInt();
    private final int emitterRandom2 = RANDOM.nextInt();
    private final int emitterRandom3 = RANDOM.nextInt();
    private final int emitterRandom4 = RANDOM.nextInt();

    private final List<ParticleInstance> particles = new ArrayList<>();
    private final List<ParticleInstance> pool = new ArrayList<>();

    private final Vector4f tempSpawnVec = new Vector4f();
    private final Matrix4f emitterTransform = new Matrix4f();
    private final Matrix4f worldTransform = new Matrix4f();
    private boolean hasTransform;

    private boolean localPosition;
    private boolean localRotation;
    private boolean localVelocity;

    private final Map<String, ParticleCurve> curves;

    @Nullable
    private EventExecutor.EventContext eventContext;

    @Nullable
    private ParticleSpawnCallback spawnCallback;
    private boolean externalParticleManagement;

    @FunctionalInterface
    public interface ParticleSpawnCallback {
        void onParticleSpawned(ParticleInstance particle);
    }

    // ==================== 构造 ====================

    public ParticleEmitterInstance(ParticleEffectDefinition definition, ParticleMolangEnvironment molang) {
        this.definition = definition;
        this.molang = molang;
        this.curves = definition.getCurves();
        createRuntimeComponents();
        restartCycle();
    }

    private void createRuntimeComponents() {
        for (IEmitterComponentDefinition def : definition.emitterPreset().components()) {
            IEmitterComponent runtime = def.createRuntime();
            runtime.apply(this);
            if (def.requireUpdate()) {
                emitterUpdateComponents.add(runtime);
            }
        }
    }

    // ==================== 主循环 ====================

    public void tick(float dt) {
        if (removed) {
            updateParticles(dt);
            return;
        }
        if (sleeping) {
            sleepTimer -= dt;
            if (sleepTimer <= 0) restartCycle();
            updateParticles(dt);
            return;
        }

        this.currentDt = dt;
        bindEmitterContext();
        for (IEmitterComponent c : emitterUpdateComponents) {
            c.update(this);
        }
        updateParticles(dt);
    }

    private void restartCycle() {
        removed = false;
        sleeping = false;
        active = true;
        emitterAge = 0;
        bindEmitterContext();

        emitterUpdateComponents.clear();
        for (IEmitterComponentDefinition def : definition.emitterPreset().components()) {
            IEmitterComponent runtime = def.createRuntime();
            runtime.apply(this);
            if (def.requireUpdate()) {
                emitterUpdateComponents.add(runtime);
            }
        }

        bindEmitterContext();
        // 发射器创建事件（由 EmitterLifetimeEvents.Runtime 处理，这里通过预设中的记录触发）
        EmitterLifetimeEvents evts = definition.emitterPreset().find(EmitterLifetimeEvents.class);
        if (evts != null && eventContext != null && !evts.creationEvent().isEmpty()) {
            EventExecutor.fireEvents(evts.creationEvent(), definition, eventContext);
        }
    }

    private void bindEmitterContext() {
        molang.bindEmitter(emitterAge, emitterLifetime, emitterRandom1, emitterRandom2, emitterRandom3, emitterRandom4);
        for (Map.Entry<String, ParticleCurve> entry : curves.entrySet()) {
            float value = CurveEvaluator.evaluate(entry.getValue(), molang.getContext(), entry.getKey());
            molang.getVariableStorage().set(entry.getKey(), NumberValue.of(value));
        }
        // 应用外部注入的每实例变量覆盖
        if (!variableOverrides.isEmpty()) {
            for (Map.Entry<String, Float> entry : variableOverrides.entrySet()) {
                molang.getVariableStorage().set(entry.getKey(), NumberValue.of(entry.getValue()));
            }
        }
    }

    // ==================== 粒子管理 ====================

    private void spawnParticleInternal() {
        ParticleInstance p = obtainParticle();
        p.reset();
        p.random1 = RANDOM.nextFloat();
        p.random2 = RANDOM.nextFloat();
        p.random3 = RANDOM.nextFloat();
        p.random4 = RANDOM.nextFloat();

        p.emitter = this;

        // 绑定粒子变量到 emitter 的共享 molang
        molang.bindParticle(0, 1, p.random1, p.random2, p.random3, p.random4);

        // 发射器形状 → 粒子初始位置（emit 时采样一次）
        EmitterShape shape = definition.emitterPreset().find(EmitterShape.class);
        if (shape != null) {
            shape.applyPosition(p, molang.getContext(), RANDOM);
        }

        // 粒子 Runtime 组件
        p.updateComponents = new ArrayList<>();
        for (IParticleComponentDefinition def : definition.particlePreset().components()) {
            IParticleComponent runtime = def.createRuntime();
            runtime.apply(p);
            if (def.requireUpdate()) {
                p.updateComponents.add(runtime);
            }
        }

        if (hasTransform) applyLocalSpaceOnSpawn(p);

        if (spawnCallback != null && (externalParticleManagement || p.worldSpace)) {
            spawnCallback.onParticleSpawned(p);
        } else {
            particles.add(p);
        }
    }

    /** 公共入口，供 Runtime 组件调用 */
    public void spawnParticle() {
        spawnParticleInternal();
    }

    public int emitManual(int count) {
        EmitterRateManual manual = definition.emitterPreset().find(EmitterRateManual.class);
        if (manual == null || count <= 0 || removed || sleeping || !active) return 0;

        bindEmitterContext();
        int maxParticles = (int) manual.maxParticles().evaluate(molang.getContext());
        int effectiveMax = Math.min(maxParticles, MAX_PARTICLES);
        int available = Math.max(0, effectiveMax - particles.size());
        int spawnCount = Math.min(count, available);
        for (int i = 0; i < spawnCount; i++) {
            spawnParticleInternal();
        }
        return spawnCount;
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            ParticleInstance p = particles.get(i);
            updateSingleParticle(p, dt);
            if (!p.alive) {
                fireParticleExpirationEvents(p);
                int last = particles.size() - 1;
                if (i != last) particles.set(i, particles.get(last));
                particles.remove(last);
                recycleParticle(p);
            }
        }
    }

    public void updateSingleParticle(ParticleInstance p, float dt) {
        molang.bindParticle(p.age, p.maxLifetime, p.random1, p.random2, p.random3, p.random4);

        for (IParticleComponent c : p.updateComponents) {
            c.update(p);
        }

        p.tick(dt);

        // 过期事件由 ParticleLifetimeExpression.Runtime.update() 处理
        // killPlane 由 ParticleLifetimeKillPlane.update() 处理
        // timeline 事件由 ParticleLifetimeEvents.Runtime.update() 处理
    }

    // ==================== 局部空间变换 ====================

    private void applyLocalSpaceOnSpawn(ParticleInstance p) {
        float localScale = extractScale(emitterTransform);
        p.spawnScale = localScale;
        if (!localPosition) {
            // 世界空间粒子的位置与初速度使用同一个世界变换。
            worldTransform.transform(tempSpawnVec.set(p.x, p.y, p.z, 1));
            p.x = tempSpawnVec.x; p.y = tempSpawnVec.y; p.z = tempSpawnVec.z;
            p.worldSpace = true;
            if (!localRotation) transformVelocityByLinearMap(p, worldTransform);
            else { p.vx *= localScale; p.vy *= localScale; p.vz *= localScale; }
        }
    }

    private static float extractScale(Matrix4f mat) {
        float m00 = mat.m00(), m01 = mat.m01(), m02 = mat.m02();
        return (float) Math.sqrt(m00 * m00 + m01 * m01 + m02 * m02);
    }

    /**
     * 用矩阵的线性部分（含非等比缩放）变换速度。
     */
    private static void transformVelocityByLinearMap(ParticleInstance p, Matrix4f mat) {
        float vx = mat.m00() * p.vx + mat.m10() * p.vy + mat.m20() * p.vz;
        float vy = mat.m01() * p.vx + mat.m11() * p.vy + mat.m21() * p.vz;
        float vz = mat.m02() * p.vx + mat.m12() * p.vy + mat.m22() * p.vz;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
    }

    // ==================== 粒子池 ====================

    private ParticleInstance obtainParticle() {
        return !pool.isEmpty() ? pool.remove(pool.size() - 1) : new ParticleInstance();
    }

    private void recycleParticle(ParticleInstance p) {
        if (pool.size() < POOL_SIZE) pool.add(p);
    }

    // ==================== 公共 API ====================

    public void setEmitterTransform(Matrix4f locatorTransform, Matrix4f worldTransformIn) {
        emitterTransform.set(locatorTransform);
        worldTransform.set(worldTransformIn);
        hasTransform = true;
    }

    public Matrix4f getEmitterTransform() { return emitterTransform; }
    public Matrix4f getWorldTransform() { return worldTransform; }

    public void setLocalSpaceFlags(boolean pos, boolean rot, boolean vel) {
        this.localPosition = pos;
        this.localRotation = rot;
        this.localVelocity = vel;
    }

    public boolean isLocalPosition() { return localPosition; }
    public boolean isLocalRotation() { return localRotation; }
    public boolean isLocalVelocity() { return localVelocity; }

    public float getDt() { return currentDt; }
    public int getParticleCount() { return particles.size(); }
    public boolean isActive() { return active; }
    public boolean isFinished() { return removed && particles.isEmpty(); }

    public void setRemoved(boolean removed) { this.removed = removed; }
    public void setActive(boolean active) { this.active = active; }
    public void setSleeping(boolean sleeping) { this.sleeping = sleeping; }
    public void setSleepTimer(float t) { this.sleepTimer = t; }
    public float getSleepTimer() { return sleepTimer; }

    /**
     * 立即废弃该发射器及其仍持有的本地粒子。
     * 已交给外部粒子管理器的世界空间粒子不在 {@link #particles} 中，仍按自身寿命消亡。
     */
    public void discard() {
        removed = true;
        active = false;
        sleeping = false;
        particles.clear();
    }

    public float getEmitterAge() { return emitterAge; }
    public void setEmitterAge(float age) { this.emitterAge = age; }
    public float getEmitterLifetime() { return emitterLifetime; }
    public void setEmitterLifetime(float lt) { this.emitterLifetime = lt; }

    public void bindContextAndCurves() { bindEmitterContext(); }

    /**
     * 注入一个仅对本发射器生效的 molang 变量（定义表达式中以 {@code v.<name>} 读取）。
     * <p>
     * 应在发射器首次 tick（产生粒子）之前调用；覆盖值会随每次上下文绑定写入共享变量存储，
     * 仅在该发射器自身 tick 期间可见，不影响同系统其它发射器。
     *
     * @param name  变量名（不含 {@code v.} 前缀）
     * @param value 数值
     */
    public void setVariable(String name, float value) {
        variableOverrides.put(name, value);
    }

    /**
     * 锚点跟随标志：启用后，本发射器产出的世界粒子（SnowStormParticle）在渲染时按
     * {@link #setEmitterTransform} 的 worldTransform 相对出生锚点的平移逐帧补偿，
     * 使粒子跟随移动的锚点（如持枪者的枪口）。
     */
    private boolean followAnchor;

    public void setFollowAnchor(boolean followAnchor) {
        this.followAnchor = followAnchor;
    }

    public boolean isFollowAnchor() {
        return followAnchor;
    }

    /**
     * 移除此前注入的变量覆盖。
     */
    public void removeVariable(String name) {
        variableOverrides.remove(name);
    }

    /**
     * 清空全部变量覆盖。
     */
    public void clearVariables() {
        variableOverrides.clear();
    }

    public ParticleEffectDefinition getDefinition() { return definition; }
    public ParticleMolangEnvironment getMolang() { return molang; }
    public List<ParticleInstance> getParticles() { return particles; }

    /** 暴露 emitter 级 Runtime 组件列表，供 Loop 重启等场景遍历重置。 */
    public List<IEmitterComponent> getEmitterUpdateComponents() { return emitterUpdateComponents; }

    public void restart() {
        active = true;
        removed = false;
        sleeping = false;
        sleepTimer = 0;
        particles.clear();
        hasTransform = false;
        emitterTransform.identity();
        worldTransform.identity();
        restartCycle();
    }

    public void setExternalParticleManagement(@Nullable ParticleSpawnCallback callback) {
        this.spawnCallback = callback;
        this.externalParticleManagement = callback != null;
    }

    public void setWorldSpaceParticleCallback(@Nullable ParticleSpawnCallback callback) {
        this.spawnCallback = callback;
        this.externalParticleManagement = false;
    }

    // ==================== 事件系统 ====================

    public void setEventContext(@Nullable EventExecutor.EventContext context) { this.eventContext = context; }

    @Nullable
    public EventExecutor.EventContext getEventContext() { return eventContext; }

    public void fireCreationEvents() {
        if (eventContext == null) return;
        EmitterLifetimeEvents evts = definition.emitterPreset().find(EmitterLifetimeEvents.class);
        if (evts != null && !evts.creationEvent().isEmpty()) {
            EventExecutor.fireEvents(evts.creationEvent(), definition, eventContext);
        }
    }

    public void fireExpirationEvents() {
        if (eventContext == null) return;
        EmitterLifetimeEvents evts = definition.emitterPreset().find(EmitterLifetimeEvents.class);
        if (evts != null && !evts.expirationEvent().isEmpty()) {
            EventExecutor.fireEvents(evts.expirationEvent(), definition, eventContext);
        }
    }

    public void fireParticleExpirationEvents(ParticleInstance p) {
        if (eventContext == null) return;
        ParticleLifetimeEvents evts = definition.particlePreset().find(ParticleLifetimeEvents.class);
        if (evts == null || evts.expirationEvent().isEmpty()) return;
        molang.bindParticle(p.age, p.maxLifetime, p.random1, p.random2, p.random3, p.random4);
        EventExecutor.fireEvents(evts.expirationEvent(), definition, eventContext);
    }

    public void fireCollisionEvents(ParticleInstance p, float speed) {
        if (eventContext == null) return;
        ParticleMotionCollision collision = definition.findComponent(ParticleMotionCollision.class);
        if (collision == null || collision.events().isEmpty()) return;
        molang.bindParticle(p.age, p.maxLifetime, p.random1, p.random2, p.random3, p.random4);
        for (ParticleMotionCollision.CollisionEvent event : collision.events()) {
            if (speed >= event.minSpeed()) {
                List<IEventNode> nodes = definition.getEvents().get(event.event());
                if (nodes != null) {
                    for (IEventNode node : nodes) EventExecutor.execute(node, eventContext);
                }
            }
        }
    }

}

