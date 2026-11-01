# 开发者指南

本库面向 Minecraft 1.21.1、Java 21、NeoForge 21.1.211 和 Parchment 2024.11.17。以下示例介绍依赖配置、模型注册、资源查询和渲染调用；资源文件应放入你自己的模组资源目录。

## 依赖与资源文件

发布坐标来自 `gradle.properties` 和 `build.gradle`：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
dependencies {
    implementation("mods.mewkooo.bedrockmodel:mewkooos_bedrock_model:1.0.5-neoforge-mc1.21.1")
}
```

发布 POM 会将 `com.maydaymemory:mae:1.1.4` 作为编译期传递依赖声明，因此下游按此示例声明本库即可编译 `AnimationClip.evaluate` 返回的 `Pose`。修正后的 POM 同时排除 MAE 带入的 fastutil、JOML、Gson 和 jsr305，由 Minecraft/NeoForge 提供对应依赖。Gradle 开发运行时通过 POM 解析 MAE；发布的 Mod JAR 则已内嵌 MAE，不需要手动提取内嵌 Jar 加入编译 classpath。

本仓库没有远程发布仓库；上述 `1.0.5` 示例对应当前源码，本地开发须先在本库仓库运行 `./gradlew publishToMavenLocal`，再刷新下游 Gradle 依赖。模组资源使用固定路径：模型 ID `examplemod:drone` 对应 `assets/examplemod/models/bedrock/drone.json`，动画文件 ID `examplemod:drone` 对应 `assets/examplemod/animations/drone.json`。

### fastutil 依赖冲突

如果使用修正前的 POM，启动任务可能报 MAE 需要 `fastutil:8.5.15`，但 Minecraft 1.21.1 严格要求 `8.5.12`。可先在下游声明本库的这条依赖上排除 MAE 带入的 fastutil；下例保留旧版坐标，实际使用时保持自己的库版本号：

```groovy
dependencies {
    implementation("mods.mewkooo.bedrockmodel:mewkooos_bedrock_model:1.0.3-neoforge-mc1.21.1") {
        exclude group: 'it.unimi.dsi', module: 'fastutil'
    }
}
```

这会保留 MAE 的公开 API，并让运行依赖使用 Minecraft 提供的 fastutil。若下游还单独声明了 MAE，也需对那条依赖添加相同排除。不要强制升级游戏的 fastutil 来消除冲突。日志最外层的 configuration cache 写入失败是依赖解析失败的连带结果，关闭配置缓存不会解决版本冲突。

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

## API 使用边界

`api.model`、`api.animation`、`api.resource` 提供两侧共享的模型、动画和资源契约；`api.client.display` 提供客户端显示变换工具，只能从客户端代码调用。

显示变换元数据通过 `api.model.display.ItemDisplayTransform(s)` 读取，客户端使用 `api.client.display.BedrockDisplayTransforms` 应用到渲染姿态。用法见[显示变换说明](item-display-transforms.md)。

## 客户端、专用服务器和线程约束

- 注册事件分别为逻辑客户端和逻辑服务端仓库触发，集成服务器的 `getDist()` 也是 `Dist.CLIENT`。客户端资源和回调使用 `getLogicalSide() == LogicalSide.CLIENT` 筛选；通用订阅类不要提前引用 `net.minecraft.client` 类型。
- 不要在 reload apply 之前查询 lazy 资源。
- `api.client` 只能从客户端隔离的调用类中引用；`api.client.display.BedrockDisplayTransforms`、`client.mesh`、渲染器、GPU 上传和 `PoseStack` 绘制应在客户端渲染线程执行。
- `BedrockModelInstance` 和模型定义属于共享状态；一般绘制使用 `client.model.render.BedrockModelRenderer`，它分派库内 tree/baked 实例。需要单独绘制骨骼、几何 pass 或扩展渲染参数时，使用 `TreeModelRenderer` 或 `BakedModelRenderer`。legacy 模型仍由客户端 `BedrockModel` 实现及其旧渲染路径处理；它不是 `AnimatedBedrockModel`。
- 旧 `v1`/`v2` import 不再提供兼容包装。显示变换工具类的迁移方式见[显示变换说明](item-display-transforms.md)。
