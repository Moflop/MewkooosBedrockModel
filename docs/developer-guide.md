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
import mods.mewkooo.bedrockmodel.api.resource.RegisterBedrockResourcesEvent;
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

`treeModel` 保留骨骼树并适合每个实体创建运行时实例；`bakedModel` 预先烘焙几何；`legacyModel` 仅用于仍需要客户端 `client.legacy.model.BedrockModel` 渲染实现的场景。一个 builder 必须调用一次 `register()`；同一模型或动画 ID 重复注册会抛出 `IllegalArgumentException`。`animation(...)` 的动画文件必须使用与模型骨骼兼容的来源，默认 loader 会按当前运行端选择 Gson 配置。

## 查询、动画和渲染时机

资源重载完成后再查询仓库。首次重载前或文件缺失时 getter 返回 `null`，调用方应保留回退路径。每个实体保留自己的实例，模型定义可共享：

```java
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.AnimatedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BedrockModelInstance;
import mods.mewkooo.bedrockmodel.api.resource.BedrockModelResource;
import mods.mewkooo.bedrockmodel.api.resource.BedrockResources;
import mods.mewkooo.bedrockmodel.client.model.render.BedrockModelRenderer;
import net.neoforged.fml.LogicalSide;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class ExampleRenderer {
    public static void render(PoseStack poseStack, VertexConsumer vertexConsumer,
                              int packedLight, int packedOverlay, float timeSeconds) {
        ResourceLocation modelId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        ResourceLocation animationId = ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
        var access = BedrockResources.models(LogicalSide.CLIENT);
        if (access == null) return;
        BedrockModelResource resource = access.getResource(modelId);
        if (resource != null && resource.model() instanceof AnimatedBedrockModel model) {
            BedrockModelInstance instance = model.createInstance();
            List<AnimationClip> clips = access.getAnimations(modelId, animationId);
            if (clips != null && !clips.isEmpty()) {
                // timeSeconds 由实体或动画状态机维护；库不自动推进时间。
                instance.resetPose();
                instance.applyPose(clips.get(0).evaluate(timeSeconds));
            }
            BedrockModelRenderer.render(instance, poseStack, vertexConsumer, packedLight, packedOverlay);
        }
    }
}
```

`BedrockResources.models(LogicalSide.CLIENT)` 只提供客户端当前重载世代的只读查询；服务端逻辑使用 `models(LogicalSide.SERVER)`。资源读取、缓存和模型构造由内部 `core.resource` 完成。`BedrockModelResource.model()` 是 `api.model.BedrockModel`，可动画模型实现 `AnimatedBedrockModel` 并创建 `BedrockModelInstance`。动画混合、播放状态、声音/粒子副作用和渲染线程调度由接入方负责。模型对象和实例可能在下一次资源重载后失效，重载回调中应重新按 ID 获取。

## 模块职责与依赖方向

| 包 | 职责 | 依赖方向 |
| --- | --- | --- |
| `api` | 下游唯一入口：资源注册事件、模型类型、烘焙选项、显示变换和动画片段契约 | 只依赖 Minecraft/Java 与稳定数据契约，不引用 `core`、`client` 或 `server` |
| `core.resource` | JSON 路径、reload listener、缓存和模型装配；把 API 注册描述转换为内部 Entry | 依赖 `api` 和共享实现 |
| `core.json`、`core.molang` | Bedrock JSON 解析、序列化和 Molang 求值 | 共享实现；不依赖 `client` 或 `server` |
| `core.model`、`core.particle` | 共享模型数据、烘焙、运行时状态和粒子模拟 | 依赖 `api`；粒子副作用通过 sink 注入 |
| `client.model.render` | 共享模型的客户端渲染适配器、顶点写入和 GPU 加速后端 | 依赖 `core.model` 与 `api`，只在客户端加载 |
| `client.mesh`、`client.particle`、`client.legacy` | 网格/GPU、客户端事件、粒子世界实体和旧渲染器适配 | 依赖 `core` 与 `api`，只在客户端加载 |
| `server` | 逻辑端 reload 装配，覆盖专用服务器和单人游戏内置服务器 | 依赖 `core` 与 `api`，不加载客户端类型 |

注册流程是“`api.resource.RegisterBedrockResourcesEvent` 声明 → `core.resource` 读取 JSON → 构造共享模型/动画 → `api.resource.BedrockResources` 查询 → `client.model.render` 渲染”。新增 JSON 字段放在 `core.json.pojo` 和对应 serializer；新增共享模型算法放在 `core.model`；客户端绘制、GPU 上传和第三方适配放在 `client`；服务端 reload 装配放在 `server`。整个项目仍为单一 Gradle 模块。

`RegisterBedrockResourcesEvent` 的 getter 只返回 API 注册描述；`core.resource` 在 reload 装配时创建内部 `BedrockModelEntry`/`BedrockAnimationEntry`，并把只读视图安装到 `BedrockResources`，因此下游不需要接触缓存和工厂实现。`legacyModel` 的构造器由 `client` 注入，专用服务器注册同名资源时会记录创建失败，不会提前加载客户端类。

## 客户端、专用服务器和线程约束

- 统一注册事件在客户端和专用服务器都会触发；不要在事件回调中加载 `net.minecraft.client` 类型。
- 客户端 reload 注册会额外挂载 `ParticleDefinitionLoader`；逻辑服务端 listener 同时覆盖专用服务器和单人游戏内置服务器，只挂载模型和动画 listener。
- 模型资源 listener 必须在动画源 listener 之后注册；不要在 reload apply 之前查询 lazy 资源。
- `client.mesh`、渲染器、GPU 上传和 `PoseStack` 绘制只能在客户端渲染线程执行。
- `BedrockModelInstance` 和模型定义属于共享状态；一般绘制使用 `client.model.render.BedrockModelRenderer`，它分派库内 tree/baked 实例。需要单独绘制骨骼、几何 pass 或扩展渲染参数时，使用 `TreeModelRenderer` 或 `BakedModelRenderer`。legacy 模型仍由客户端 `BedrockModel` 实现及其旧渲染路径处理；它不是 `AnimatedBedrockModel`。
- Molang 扩展函数绑定中的反射、Mixin、ResourceLocation 和 JSON/存档/网络格式没有随本次包调整改变。客户端配置由模组入口直接注册为 `CLIENT` 类型；移动包名属于开发阶段的破坏性 API 重构，旧 `v1`/`v2` import 不再提供兼容包装。

## 验证

```powershell
./gradlew compileJava compileTestJava
./gradlew test --rerun-tasks
```

`compileJava compileTestJava` 检查源码和测试接入；`test` 覆盖 Molang、JSON、模型烘焙和网格单元测试。本轮还实际运行了 `runServer` 并启动到 `Done`，以及 `runClient` 并完成客户端资源重载；客户端日志中的可选模组、Mixin 版本和音效/着色器提示来自开发环境，没有发现本库启动异常。
