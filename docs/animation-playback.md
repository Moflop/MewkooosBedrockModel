# 动画播放最小实现

本文使用当前仓库的 API，面向 Minecraft 1.21.1、Java 21、NeoForge 21.1.x 和 MAE 1.1.4，演示两条路径：

- 按动画 JSON 中的完整名称，播放单个指定动画。
- 使用本库提供的状态/过渡 helper 与 MAE 状态机，在 `idle` 和 `walk` 之间切换并播放动画。

依赖配置见[开发者指南](developer-guide.md#依赖与资源文件)。按该指南依赖本库后，MAE 已作为编译期传递依赖提供，发布的本库 Mod JAR 也已内嵌 MAE。以下 Java 类放在你自己的客户端包中，每个 `public` 类单独保存为同名文件，并使用相同的包声明；示例省略包声明。

## 1. 共用资源和注册代码

将 `examplemod` 替换为自己的模组 ID。为便于直接观察动画，下面用一个只有 `root` 骨骼的方块作为模型。

`src/main/resources/assets/examplemod/models/bedrock/drone.json`：

```json
{
  "format_version": "1.12.0",
  "minecraft:geometry": [{
    "description": {
      "identifier": "geometry.drone",
      "texture_width": 16,
      "texture_height": 16
    },
    "bones": [{
      "name": "root",
      "pivot": [0, 0, 0],
      "cubes": [{"origin": [-2, 0, -2], "size": [4, 4, 4], "uv": [0, 0]}]
    }]
  }]
}
```

`src/main/resources/assets/examplemod/animations/drone.json`：

```json
{
  "format_version": "1.8.0",
  "animations": {
    "animation.drone.idle": {
      "loop": true,
      "animation_length": 1.0,
      "bones": {
        "root": {"position": {"0.0": [0, 0, 0], "0.5": [0, 1, 0], "1.0": [0, 0, 0]}}
      }
    },
    "animation.drone.walk": {
      "loop": true,
      "animation_length": 0.5,
      "bones": {
        "root": {"rotation": {"0.0": [0, 0, -20], "0.25": [0, 0, 20], "0.5": [0, 0, -20]}}
      }
    }
  }
}
```

两个 ID 的用途不同：`examplemod:drone` 是动画**文件 ID**，`animation.drone.walk` 是该文件中某个动画的**名称**。使用 `resource.getAnimation(文件ID, 动画名)` 按名称查询；原有片段列表 API 仍然可用，但 `clips.get(0)` 不表示指定名称。

`ExampleAnimations.java` 负责注册资源，并提供两个播放示例共用的查询/采样方法：

```java
import com.maydaymemory.mae.basic.Pose;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.AnimatedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.RegisterBedrockResourcesEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "examplemod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExampleAnimations {
    public static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
    public static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath("examplemod", "drone");

    @SubscribeEvent
    public static void register(RegisterBedrockResourcesEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT) return;
        event.treeModel(MODEL).animation(ANIMATIONS).register();
    }

    public static BedrockModelInstance createInstance(BedrockModelResource resource) {
        if (resource.model() instanceof AnimatedBedrockModel model) {
            return model.createInstance();
        }
        throw new IllegalArgumentException("示例需要 treeModel 或 bakedModel");
    }

    public static AnimationClip requireClip(BedrockModelResource resource, String name) {
        AnimationClip clip = resource.getAnimation(ANIMATIONS, name);
        if (clip == null) {
            throw new IllegalArgumentException("找不到动画: " + name);
        }
        if (!(clip.getSpecifiedEndTimeS() > 0.0f)) {
            throw new IllegalArgumentException("示例要求正数 animation_length: " + name);
        }
        return clip;
    }

    public static Pose loop(AnimationClip clip, long elapsedNanos) {
        float seconds = (float) (Math.max(0L, elapsedNanos) / 1_000_000_000.0);
        return clip.evaluate(seconds % clip.getSpecifiedEndTimeS());
    }
}
```

名称索引在资源构建时直接从 JSON 保留，查询不再遍历列表，也无需将 `AnimationClip` 转型为 MAE 的 `Animation`。索引由当前模型资源持有，资源重载时随资源更新；手动提供自定义片段时，可使用 `BedrockModelResource.fromNamedAnimations(...)` 显式提供名称映射。示例明确要求正数 `animation_length`，并主动对时间取模；当前加载实现不会仅凭 JSON 的 `loop: true` 自动循环。

## 2. 播放单个指定动画

`SingleAnimationPlayer.java` 不需要状态机，最小流程是：查找片段 → 计算经过的秒数 → 求姿态 → 应用到该对象自己的模型实例。

```java
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.animation.time.AnimationClock;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.client.legacy.event.ClientAnimationClockTicker;

public final class SingleAnimationPlayer {
    private final AnimationClock clock = ClientAnimationClockTicker.getAnimationClock();
    private final BedrockModelInstance instance;
    private final AnimationClip clip;
    private long startedAt;

    public SingleAnimationPlayer(BedrockModelResource resource, String animationName) {
        instance = ExampleAnimations.createInstance(resource);
        clip = ExampleAnimations.requireClip(resource, animationName);
        restart();
    }

    public void restart() {
        startedAt = clock.nowNanos();
    }

    public BedrockModelInstance sample() {
        instance.resetPose();
        instance.applyPose(ExampleAnimations.loop(clip, clock.nowNanos() - startedAt));
        return instance;
    }
}
```

例如构造时传入 `"animation.drone.walk"` 就只播放该片段；调用 `restart()` 可从头重播。示例循环播放，如果要播放一次并停留在末帧，将 `ExampleAnimations.loop` 中求姿态的最后一行改为 `return clip.evaluate(Math.min(seconds, clip.getSpecifiedEndTimeS()));`。停播并恢复初始姿态时，不再应用片段，只调用实例的 `resetPose()`。

## 3. 使用 MAE 创建动画状态机

`StateMachineAnimationPlayer.java` 包含两个状态：`walking == false` 播放 `idle`，`walking == true` 播放 `walk`，切换时混合 0.15 秒。播放时间和状态机使用同一个时钟，两个状态各自保存播放起点。

```java
import com.maydaymemory.mae.control.statemachine.AnimationStateMachine;
import mods.mewkooo.bedrockmodel.api.animation.time.AnimationClock;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.client.legacy.event.ClientAnimationClockTicker;
import mods.mewkooo.bedrockmodel.core.animation.SimpleAnimationState;
import mods.mewkooo.bedrockmodel.core.animation.SimpleTransition;

public final class StateMachineAnimationPlayer {
    private final AnimationClock clock = ClientAnimationClockTicker.getAnimationClock();
    private final BedrockModelInstance instance;
    private final AnimationStateMachine<StateMachineAnimationPlayer> machine;
    private long idleStartedAt;
    private long walkStartedAt;
    private boolean walking;

    public StateMachineAnimationPlayer(BedrockModelResource resource) {
        instance = ExampleAnimations.createInstance(resource);
        var idleClip = ExampleAnimations.requireClip(resource, "animation.drone.idle");
        var walkClip = ExampleAnimations.requireClip(resource, "animation.drone.walk");
        // MAE 的构造器不会调用初始状态的 onEnter，显式初始化起点。
        idleStartedAt = walkStartedAt = clock.nowNanos();

        var idle = new SimpleAnimationState.Builder<StateMachineAnimationPlayer>()
                .evaluatePose(ctx -> ExampleAnimations.loop(
                        idleClip, ctx.clock.nowNanos() - ctx.idleStartedAt))
                .build();
        var walk = new SimpleAnimationState.Builder<StateMachineAnimationPlayer>()
                .evaluatePose(ctx -> ExampleAnimations.loop(
                        walkClip, ctx.clock.nowNanos() - ctx.walkStartedAt))
                .build();

        new SimpleTransition.Builder<StateMachineAnimationPlayer>()
                .from(idle).target(walk)
                .predicate(ctx -> ctx.walking)
                .afterTrigger(ctx -> ctx.walkStartedAt = ctx.clock.nowNanos())
                .duration(0.15f)
                .build();
        new SimpleTransition.Builder<StateMachineAnimationPlayer>()
                .from(walk).target(idle)
                .predicate(ctx -> !ctx.walking)
                .afterTrigger(ctx -> ctx.idleStartedAt = ctx.clock.nowNanos())
                .duration(0.15f)
                .build();

        machine = new AnimationStateMachine<>(idle, this, clock);
    }

    public BedrockModelInstance sample(boolean walking) {
        this.walking = walking;
        if (clock.shouldTick()) machine.tick();
        instance.resetPose();
        instance.applyPose(machine.getPose());
        return instance;
    }
}
```

这里的职责划分和调用顺序是：

- 本库负责资源加载、片段采样和模型实例；`SimpleAnimationState` / `SimpleTransition` 是当前 `core.animation` 下的辅助实现，使用的是 MAE 状态/过渡接口。它们不是 `api.*` 包中的稳定契约，升级库版本时需要核对。
- MAE 的 `AnimationStateMachine` 负责条件判断和过渡混合。构造器第三个参数必须提供纳秒时间源，不能省略；`duration(0.15f)` 的单位则是秒。
- 先写入当前条件，再调用 `machine.tick()` 推进状态，之后用 `machine.getPose()` 取姿态。仅调用 `getPose()` 不会触发状态切换。
- `afterTrigger` 在过渡开始时重置目标片段起点，让目标动画从混合开始就播放。MAE 的目标状态 `onEnter` 要到过渡结束才调用，过渡期间也不调用状态的 `onUpdate`，因此这里直接根据时钟采样。

## 4. 放进已有的渲染器

两种播放器都必须跨帧保存，不能在每次绘制时重新构造。下面是单动画版本的调用桥接；每个被绘制对象保留一个 `ExampleAnimationRenderer`，在已有实体/方块实体/物品渲染方法中调用它的 `render(...)`。对象到世界/视图的平移和旋转由外层渲染器完成。

```java
import com.mojang.blaze3d.vertex.PoseStack;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.client.model.render.BedrockModelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.LogicalSide;

public final class ExampleAnimationRenderer {
    private BedrockModelResource lastResource;
    private SingleAnimationPlayer player;

    public void render(PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        var access = BedrockResources.models(LogicalSide.CLIENT);
        if (access == null) return;
        var resource = access.getResource(ExampleAnimations.MODEL);
        if (resource == null) return;
        // 重载后资源对象会改变，同时重建模型实例和播放器。
        if (resource != lastResource) {
            player = new SingleAnimationPlayer(resource, "animation.drone.walk");
            lastResource = resource;
        }
        var consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(
                ResourceLocation.withDefaultNamespace("textures/block/stone.png")));
        BedrockModelRenderer.render(player.sample(), poseStack, consumer,
                packedLight, packedOverlay);
    }
}
```

示例复用原版石头纹理，无需额外准备 PNG；实际模型可替换为自己的纹理。使用状态机版本时，对这个类做三处替换：

1. 字段类型改为 `private StateMachineAnimationPlayer player;`。
2. 创建播放器的语句改为 `player = new StateMachineAnimationPlayer(resource);`。
3. 给 `render(...)` 增加 `boolean walking` 参数，并将绘制中的 `player.sample()` 改为 `player.sample(walking)`。`walking` 可由实体水平移动速度、按键或其他业务条件计算。

### 生命周期和最小示例边界

- 注册必须在模组事件总线进行，并检查 `LogicalSide.CLIENT`；只限制物理侧 `Dist.CLIENT` 不足以排除单人游戏的集成服务器。
- 首次资源重载完成前 getter 可能返回 `null`，渲染桥接会跳过该帧。片段名称拼错或声明长度无效则明确抛错，便于定位资源问题。
- NeoForge 的实体渲染器通常由同一种实体共享，不能把示例中的播放器字段直接放在这样的共享渲染器上供所有实体共用。应按实体 UUID 等键保存独立桥接/播放器，并在实体移除、世界退出时清理；物品也应按实际播放对象管理状态。
- 资源重载后需要同时更新模型实例、片段和状态机；桥接通过比较 `BedrockModelResource` 的对象身份完成重建，不长期缓存旧的 `BedrockModelAccess`。
- `ClientAnimationClockTicker.getAnimationClock()` 虽位于 `client.legacy.event` 包，也可用于这里的 tree 模型；库已自动逐帧更新该时钟，不需要自己再调用 `update()`。它在单人游戏暂停时停止计时，退出世界后归零，因此播放器也应随世界清理。需要通用/服务端时钟时可用 `AnimationClocks.system()`，但它不会随客户端暂停。
- 所有绘制调用在客户端渲染线程执行。本示例仅展示骨骼姿态播放，不包含声音、粒子、timeline 事件派发、root motion 或服务端状态同步。
