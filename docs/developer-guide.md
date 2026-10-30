# 开发者指南

本库面向 Minecraft 1.21.1、Java 21、NeoForge 21.1.211 和 Parchment 2024.11.17。仓库没有真实下游模组的 Java 接入源码；下面的接入片段对应测试中检查的注册 API，资源文件仍需由接入方放入自己的模组资源目录。

## 依赖与资源文件

发布坐标来自 `gradle.properties` 和 `build.gradle`：

```groovy
repositories { mavenLocal() }
dependencies {
    implementation("mods.mewkooo.bedrockmodel:mewkooos_bedrock_model:1.0.0-neoforge-mc1.21.1")
}
```

本仓库没有远程发布仓库；本地开发可先运行 `./gradlew publishToMavenLocal`。模组资源使用固定路径：模型 ID `examplemod:drone` 对应 `assets/examplemod/models/bedrock/drone.json`，动画文件 ID `examplemod:drone` 对应 `assets/examplemod/animations/drone.json`。

## 最小接入

在模组事件总线注册同一个资源事件处理器。回调只声明资源，不读取文件，也不引用客户端渲染类；客户端和专用服务器都会收到该事件。

```java
import mods.mewkooo.bedrockmodel.resource.event.RegisterBedrockResourcesEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "examplemod", bus = EventBusSubscriber.Bus.MOD)
public final class BedrockResources {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath("examplemod", "drone");

    @SubscribeEvent
    public static void register(RegisterBedrockResourcesEvent event) {
        event.treeModel(MODEL)
                .animation(ANIMATION)
                .register();
    }
}
```

`treeModel` 保留骨骼树并适合每个实体创建运行时实例；`bakedModel` 预先烘焙几何；`legacyModel` 仅用于仍需要旧的 `legacy.model.BedrockModel` 渲染实现的场景。一个 builder 必须调用一次 `register()`；同一模型或动画 ID 重复注册会抛出 `IllegalArgumentException`。`animation(...)` 的动画文件必须使用与模型骨骼兼容的来源，默认 loader 会按当前运行端选择 Gson 配置。

## 查询、动画和渲染时机

资源重载完成后再查询仓库。首次重载前或文件缺失时 getter 返回 `null`，调用方应保留回退路径。每个实体保留自己的实例，模型定义可共享：

```java
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.animation.BedrockAnimation;
import mods.mewkooo.bedrockmodel.model.runtime.TreeModelInstance;
import mods.mewkooo.bedrockmodel.model.tree.TreeBedrockModel;
import mods.mewkooo.bedrockmodel.resource.BedrockModelRepository;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class ExampleRenderer {
public static void render(PoseStack poseStack, VertexConsumer vertexConsumer,
                          int packedLight, int packedOverlay, float timeSeconds) {
    ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
    ResourceLocation animationId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
BedrockModelRepository repository = BedrockModelRepository.getInstance();
TreeBedrockModel model = repository.getTreeModel(modelId);
if (model != null) {
    TreeModelInstance instance = model.createInstance();
    List<BedrockAnimation> clips = repository.getAnimations(modelId, animationId);
    if (clips != null && !clips.isEmpty()) {
        // timeSeconds 由实体或动画状态机维护；库不自动推进时间。
        instance.resetPose();
        instance.applyPose(clips.get(0).evaluate(timeSeconds));
    }
    instance.renderToBuffer(poseStack, vertexConsumer, packedLight, packedOverlay);
}
}
}
```

`BedrockModelRepository` 只负责资源读取、缓存、模型构造和动画关联；动画混合、播放状态、声音/粒子副作用和渲染线程调度由接入方负责。模型对象和实例可能在下一次资源重载后失效，重载回调中应重新按 ID 获取。

## 模块职责与依赖方向

| 包 | 职责 | 依赖方向 |
| --- | --- | --- |
| `resource.event` | 下游声明模型、动画和重载回调的唯一入口 | 指向 `resource` |
| `resource` | 路径读取、动画源缓存、模型仓库、构造工厂 | 消费 `resource.json`，创建 `model`/`animation` |
| `resource.json`、`resource.json.pojo` | Gson 配置、序列化器和 Bedrock JSON 数据对象 | 不依赖客户端渲染 |
| `model.baked`、`model.tree`、`model.runtime` | 模型烘焙、骨骼定义和每实例可变状态 | 消费 JSON；客户端绘制方法只在 client 调用 |
| `animation`、`molang` | 动画曲线、时钟和 Molang 求值 | 消费模型骨骼索引 |
| `particle` | 粒子定义、运行时和客户端绘制 | 通过客户端 reload 生命周期加载 |
| `client.mesh`、`client.compat` | 网格捕获、GPU 缓存和可选渲染器适配 | 仅客户端；不应从公共注册回调触碰 |
| `legacy` | 旧模型实现和旧客户端适配代码 | 仅在选择 `legacyModel` 或旧渲染路径时使用 |

资源加载方向是“注册声明 → JSON 读取 → 模型/动画构造 → 仓库查询 → 客户端渲染”。动画源 listener 先于模型 listener 应用，模型构造时才能读取动画文件并推断动态骨骼。模型和动画 JSON 的 namespace、path、字段格式以及 `models/bedrock`、`animations` 路径属于现有行为，本次未修改。

新增 JSON 字段放在 `resource.json.pojo` 和对应 serializer；新增模型算法放在 `model.baked` 或 `model.tree`；每实例状态放在 `model.runtime`；渲染线程、GPU 或第三方渲染器代码放在 `client`。目前保持单一 Gradle 模块，只有出现独立编译或发布需求时才考虑拆分子模块。

## 客户端、专用服务器和线程约束

- 统一注册事件在客户端和专用服务器都会触发；不要在事件回调中加载 `net.minecraft.client` 类型。
- 客户端 reload 注册会额外挂载 `ParticleDefinitionLoader`；专用服务器只挂载模型和动画 listener。
- 模型资源 listener 必须在动画源 listener 之后注册；不要在 reload apply 之前查询 lazy 资源。
- `client.mesh`、渲染器、GPU 上传和 `PoseStack` 绘制只能在客户端渲染线程执行。
- Mixin、反射、ResourceLocation 和 JSON/存档/网络格式没有随本次包调整改变；移动包名属于开发阶段的破坏性 API 重构，旧 `v1`/`v2` import 不再提供兼容包装。

## 验证

```powershell
./gradlew compileJava compileTestJava
./gradlew test --rerun-tasks
```

`compileJava compileTestJava` 检查源码和测试接入；`test` 覆盖 Molang、JSON、模型烘焙和网格单元测试。`runClient`、`runServer` 需要本地图形/运行时环境，本轮只在环境允许时手动启动，不能把编译通过当作游戏启动验证。
