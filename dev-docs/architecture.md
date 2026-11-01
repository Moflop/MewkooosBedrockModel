# 内部架构与资源装配

面向本库维护者；库接入说明见[开发者指南](../docs/developer-guide.md)。

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

显示变换的数据与操作分开：`api.model.display.ItemDisplayTransform(s)` 是共享模型返回的不可变元数据；`api.client.display.BedrockDisplayTransforms` 是客户端渲染工具。用法见 [显示变换说明](../docs/item-display-transforms.md)。包名只表达使用边界，统一的 `RegisterBedrockResourcesEvent` 仍放在 `api.resource`，订阅方仍需检查 `getLogicalSide()`。

注册流程是“`api.resource.RegisterBedrockResourcesEvent` 声明 → `core.resource` 读取 JSON → 构造共享模型/动画 → `api.resource.BedrockResources` 查询 → `client.model.render` 渲染”。新增 JSON 字段放在 `core.json.pojo` 和对应 serializer；新增共享模型算法放在 `core.model`；客户端绘制、GPU 上传和第三方适配放在 `client`；服务端 reload 装配放在 `server`。整个项目仍为单一 Gradle 模块。

`RegisterBedrockResourcesEvent` 的 getter 只返回 API 注册描述；`core.resource` 在 reload 装配时创建内部 `BedrockModelEntry`/`BedrockAnimationEntry`，并把只读视图安装到 `BedrockResources`，因此下游不需要接触缓存和工厂实现。`legacyModel` 的构造器由 `client` 注入，专用服务器注册同名资源时会记录创建失败，不会提前加载客户端类。

## 重载装配约束

- 客户端 reload 注册会额外挂载 `ParticleDefinitionLoader`；逻辑服务端 listener 同时覆盖专用服务器和单人游戏内置服务器，只挂载模型和动画 listener。
- 模型资源 listener 必须在动画源 listener 之后注册；不要在 reload apply 之前查询 lazy 资源。

## 显示变换解析

`ItemDisplayTransform(s)` 是 `api.model.display` 下的公共模型元数据类型。解析器先把 JSON 读入独立的 `core.json.pojo.ItemDisplayTransformsData`，再由 `core.json.mapping.ItemDisplayTransformsMapper` 转换成 API 值对象；两侧不共享数据类，JSON 字段名只由 `core.json` 处理。

## 包调整与兼容性

Molang 扩展函数绑定中的反射、Mixin、ResourceLocation 和 JSON/存档/网络格式没有随本次包调整改变。客户端配置由模组入口直接注册为 `CLIENT` 类型；移动包名属于开发阶段的破坏性 API 重构，旧 `v1`/`v2` import 不再提供兼容包装。
