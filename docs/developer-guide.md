# 开发者指南

本库面向 Minecraft 1.21.1、Java 21、NeoForge 21.1.211 和 Parchment 2024.11.17。仓库没有真实下游模组的 Java 接入源码；下面的接入片段对应测试中检查的注册 API，资源文件仍需由接入方放入自己的模组资源目录。

## 依赖与资源文件

发布坐标来自 `gradle.properties` 和 `build.gradle`：

```groovy
repositories { mavenLocal() }
dependencies {
    implementation("mods.mewkooo.bedrockmodel:mewkooos_bedrock_model:1.0.3-neoforge-mc1.21.1")
}
```

发布 POM 会将 `com.maydaymemory:mae:1.1.4` 作为编译期传递依赖声明，因此下游按此示例声明本库即可编译 `AnimationClip.evaluate` 返回的 `Pose`；不需要手动把 Mod JAR 内嵌的运行时 Jar 加入编译 classpath。

本仓库没有远程发布仓库；本地开发可先运行 `./gradlew publishToMavenLocal`。模组资源使用固定路径：模型 ID `examplemod:drone` 对应 `assets/examplemod/models/bedrock/drone.json`，动画文件 ID `examplemod:drone` 对应 `assets/examplemod/animations/drone.json`。

## 最小接入

在模组事件总线订阅资源注册事件。事件分别为逻辑客户端和逻辑服务端仓库触发；下面的示例只注册位于 `assets/` 的客户端模型和动画。

`event.getDist()` 表示物理运行环境，`event.getLogicalSide()` 表示当前注册的资源仓库。单人游戏的集成服务器对应 `Dist.CLIENT` 和 `LogicalSide.SERVER`，因此 `@EventBusSubscriber(value = Dist.CLIENT)` 不能代替逻辑侧判断。在声明客户端模型、动画和 `onReload` 回调之前，先检查 `getLogicalSide() == LogicalSide.CLIENT`；服务端数据则使用 `LogicalSide.SERVER`。`onReload` 只接收其注册事件所对应仓库的重载结果，客户端状态的回调也必须先经过逻辑侧筛选。

事件由库的重载桥接创建并发布，订阅方无需自行构造。测试或自定义装配需要创建事件时，必须显式提供 `Dist` 和 `LogicalSide`：仅检查注册声明使用 `new RegisterBedrockResourcesEvent(dist, logicalSide)`，实际读取资源还需传入第三个参数 `RawResourceLoader`。原有 `(Dist)` 和 `(Dist, RawResourceLoader)` 构造器已删除，迁移时根据目标仓库补入逻辑侧，不能根据 `Dist` 推算。

```java
import mods.mewkooo.bedrockmodel.api.resource.RegisterBedrockResourcesEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "examplemod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BedrockResources {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath("examplemod", "drone");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath("examplemod", "drone");

    @SubscribeEvent
    public static void register(RegisterBedrockResourcesEvent event) {
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        event.treeModel(MODEL)
                .animation(ANIMATION)
                .register();
    }
}
```

`treeModel` 保留骨骼树并适合每个实体创建运行时实例；`bakedModel` 预先烘焙几何；`legacyModel` 仅用于仍需要客户端 `client.legacy.model.BedrockModel` 渲染实现的场景。一个 builder 必须调用一次 `register()`；同一模型 ID 重复注册会抛出 `IllegalArgumentException`。多个模型可以关联同一个动画文件 ID，前提是使用同一个 `RawResourceLoader` 实例（省略 loader 时自动共享事件的默认实例）；不同 loader 实例会被视为冲突，整个 `register()` 在修改注册表前失败。每个模型保留自己的动画关联和 lazy 标记；共享动画源只有在所有使用者均调用 `.lazy()` 时才延迟加载，任一 eager 使用者都会使该源在重载时预加载，注册顺序不影响结果。`animation(...)` 的动画文件必须使用与模型骨骼兼容的来源，默认 loader 会按当前运行端选择 Gson 配置。

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

TREE 和 BAKED 骨骼的法线变换使用局部位置变换线性部分的逆转置，逐层组合父骨骼变换，支持非均匀缩放和负缩放；普通顶点写入与加速后端共用该法线矩阵。零缩放等奇异变换不存在逆矩阵：若局部逆转置不可用或产生非有限值，则该骨骼的局部法线贡献回退为单位矩阵，保留传入的父级法线矩阵，位置仍按原变换处理。`getGlobalNormal()` 则对整个全局变换求逆转置，失败时返回单位矩阵。奇异姿态下两者只保证有限的法线回退值，不保证退化面的光照方向。

## 模块职责与依赖方向

| 包 | 职责 | 依赖方向 |
| --- | --- | --- |
| `api.model`、`api.animation`、`api.resource` | 两侧共享的公共契约：模型类型、烘焙选项、显示元数据、动画片段、资源注册和查询 | 不依赖侧专属 API 或本库内部实现 |
| `api.client.display` | 客户端公共工具：将共享显示元数据应用到 `PoseStack` | 依赖通用 API 与客户端渲染类型，不依赖本库内部实现；只在客户端使用 |
| `core.resource` | JSON 路径、reload listener、缓存和模型装配；把 API 注册描述转换为内部 Entry | 依赖通用 API 和共享实现 |
| `core.json`、`core.molang` | Bedrock JSON 解析、序列化和 Molang 求值 | 共享实现；不依赖 `client` 或 `server` |
| `core.model`、`core.particle` | 共享模型数据、烘焙、运行时状态和粒子模拟 | 依赖通用 API，不依赖 `api.client`；粒子副作用通过 sink 注入 |
| `client.model.render` | 共享模型的客户端渲染适配器、顶点写入和 GPU 加速后端 | 依赖 `core.model` 与 `api`，只在客户端加载 |
| `client.mesh`、`client.particle`、`client.legacy` | 网格/GPU、客户端事件、粒子世界实体和旧渲染器适配 | 依赖 `core` 与 `api`，只在客户端加载 |
| `server` | 逻辑端 reload 装配，覆盖专用服务器和单人游戏内置服务器 | 依赖 `core` 与通用 API，不依赖 `api.client` 或客户端实现 |

目前没有服务端专属的公共契约，因此不创建空的 `api.server`。新增此类契约时应放入 `api.server`，覆盖专用服务器和集成服务器；两侧共享的契约仍按功能放在 `api.model`、`api.animation` 或 `api.resource`。依赖方向为 `api.client`/`api.server` → 通用 API，各 API 包都不依赖本库内部实现。

显示变换的数据与操作分开：`api.model.display.ItemDisplayTransform(s)` 是共享模型返回的不可变元数据；`api.client.display.BedrockDisplayTransforms` 是客户端渲染工具。用法见 [显示变换说明](item-display-transforms.md)。包名只表达使用边界，统一的 `RegisterBedrockResourcesEvent` 仍放在 `api.resource`，订阅方仍需检查 `getLogicalSide()`。

注册流程是“`api.resource.RegisterBedrockResourcesEvent` 声明 → `core.resource` 读取 JSON → 构造共享模型/动画 → `api.resource.BedrockResources` 查询 → `client.model.render` 渲染”。新增 JSON 字段放在 `core.json.pojo` 和对应 serializer；新增共享模型算法放在 `core.model`；客户端绘制、GPU 上传和第三方适配放在 `client`；服务端 reload 装配放在 `server`。整个项目仍为单一 Gradle 模块。

`RegisterBedrockResourcesEvent` 的 getter 只返回 API 注册描述；`core.resource` 在 reload 装配时创建内部 `BedrockModelEntry`/`BedrockAnimationEntry`，并把只读视图安装到 `BedrockResources`，因此下游不需要接触缓存和工厂实现。`legacyModel` 的构造器由 `client` 注入，专用服务器注册同名资源时会记录创建失败，不会提前加载客户端类。

## 客户端、专用服务器和线程约束

- 注册事件分别为逻辑客户端和逻辑服务端仓库触发，集成服务器的 `getDist()` 也是 `Dist.CLIENT`。客户端资源和回调使用 `getLogicalSide() == LogicalSide.CLIENT` 筛选；通用订阅类不要提前引用 `net.minecraft.client` 类型。
- 客户端 reload 注册会额外挂载 `ParticleDefinitionLoader`；逻辑服务端 listener 同时覆盖专用服务器和单人游戏内置服务器，只挂载模型和动画 listener。
- 模型资源 listener 必须在动画源 listener 之后注册；不要在 reload apply 之前查询 lazy 资源。
- `api.client` 只能从客户端隔离的调用类中引用；`api.client.display.BedrockDisplayTransforms`、`client.mesh`、渲染器、GPU 上传和 `PoseStack` 绘制应在客户端渲染线程执行。
- `BedrockModelInstance` 和模型定义属于共享状态；一般绘制使用 `client.model.render.BedrockModelRenderer`，它分派库内 tree/baked 实例。需要单独绘制骨骼、几何 pass 或扩展渲染参数时，使用 `TreeModelRenderer` 或 `BakedModelRenderer`。legacy 模型仍由客户端 `BedrockModel` 实现及其旧渲染路径处理；它不是 `AnimatedBedrockModel`。
- Molang 扩展函数绑定中的反射、Mixin、ResourceLocation 和 JSON/存档/网络格式没有随本次包调整改变。客户端配置由模组入口直接注册为 `CLIENT` 类型；移动包名属于开发阶段的破坏性 API 重构，旧 `v1`/`v2` import 不再提供兼容包装。

## 验证

```powershell
./gradlew clean test jar sourcesJar
```

`test` 编译源码和测试接入，覆盖 Molang、JSON、资源注册、显示变换、模型烘焙和网格单元测试；`jar sourcesJar` 生成发布包和源码包。

本次 API 运行侧分包在修改前后均通过 166 项测试，并完成清理构建。产物检查确认工具类只存在于 `api.client.display`，源码包与之对应；`jdeps` 静态检查确认通用 API 不依赖侧专属 API，各 API 不依赖本库内部实现，`core` 与 `server` 不依赖客户端类型。本次未重新运行 Minecraft 客户端、专用服务器或单人游戏集成服务器；单元测试和静态依赖检查不代表这些运行场景已验证。
