# 构建与验证

面向本库维护者，以下命令均在仓库根目录执行。文中的历史验证记录保留原有结论和适用范围。

## 接入示例范围

仓库没有真实下游模组的 Java 接入源码；[开发者指南](../docs/developer-guide.md)中的接入片段对应测试中检查的注册 API，资源文件仍需由接入方放入自己的模组资源目录。

## 构建与自动检查

```powershell
./gradlew clean test jar sourcesJar
```

`test` 编译源码和测试接入，覆盖 Molang、JSON、资源注册、显示变换、模型烘焙和网格单元测试；`jar sourcesJar` 生成发布包和源码包。

发布依赖另有独立检查：

```powershell
./gradlew verifyDownstreamRuntime compileDownstreamApi
```

`verifyDownstreamRuntime` 先发布到本地 Maven，再运行 `src/test/downstream-runtime` 中的独立 NeoForge 21.1.264 接入工程。该工程只声明本库，不额外排除 MAE 依赖或强制指定 fastutil；配置缓存保持开启。检查实际 `runtimeClasspath` 文件，要求游戏的 fastutil 8.5.12、MAE 1.1.4 和本库均可解析，且未引入开发用渲染模组，同时编译公开 `Pose` API 接入代码。结果保存在该工程的 `build/reports/runtime-dependencies.json`。这项检查覆盖依赖解析和编译，不启动游戏。

2026-11-01 已验证：修正 POM 前，该工程重现 fastutil 8.5.15 与 Minecraft 严格 8.5.12 的冲突及配置缓存写入错误；修正后成功解析 110 个运行类路径文件，公开 API 编译通过，配置缓存成功保存。

本次 API 运行侧分包在修改前后均通过 166 项测试，并完成清理构建。产物检查确认工具类只存在于 `api.client.display`，源码包与之对应；`jdeps` 静态检查确认通用 API 不依赖侧专属 API，各 API 不依赖本库内部实现，`core` 与 `server` 不依赖客户端类型。本次未重新运行 Minecraft 客户端、专用服务器或单人游戏集成服务器；单元测试和静态依赖检查不代表这些运行场景已验证。
