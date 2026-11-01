# Minecraft 1.21.1 分块卸载兼容补丁

独立、可选的 NeoForge 模组，ID 为 `sbm_chunk_unload_fix`，用于防止失效的区块卸载回调反复排队、阻塞停服流程。无需安装 Bedrock 模型库，也不包含在主库的默认 JAR 中。

## 适用版本

- Minecraft **1.21.1**，NeoForge **21.1**（版本范围 `[21.1,21.2)`）。
- 已验证 NeoForge **21.1.211**；其他 21.1 构建尚未验证。

## 构建与安装

在项目根目录打开 PowerShell，执行：

```powershell
.\gradlew.bat engineUnloadFixJar
```

将生成的 `build/libs/sbm-chunk-unload-fix-1.0.0+mc1.21.1.jar` 放入目标实例的 `mods` 目录，然后启动游戏。

## 修复范围与测试

补丁仅忽略与 `pendingUnloads` 当前条目不匹配的失效卸载调度，正常区块的保存和卸载仍沿用原版流程。

测试方法的说明见[客户端环境测试说明](client-environment-tests.md)。
