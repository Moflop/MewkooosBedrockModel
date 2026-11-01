# 静态网格开发验证

本文面向维护本仓库的开发者，记录构建、开发专用命令和历史接入背景。公共使用说明见[即时与批量静态网格](../../docs/mesh/static-mesh-api.md)和[世界网格渲染组](../../docs/mesh/world-mesh-rendering.md)；内部设计与历史验证依据见[本目录说明](README.md)。

## 历史接入背景

历史上的 1.20.1 TaCZ 接入使用 `tacz:external_item_guns`，在第三人称右手、GROUND 和世界内 FIXED 即时绘制，展示枪使用[世界渲染组](../../docs/mesh/world-mesh-rendering.md)。该旧集成说明不是当前版本的真实下游示例；接入方应按当前源码包和所选 LOD 重新实现。

## 构建与检查

```powershell
./gradlew build --offline
./gradlew publishToMavenLocal --offline
```

Maven Local 坐标为 `mods.mewkooo.bedrockmodel:mewkooos_bedrock_model:1.0.0-neoforge-mc1.21.1`。当前仓库未包含 GL 像素读回脚本；上面的 `build` 只验证编译和自动化测试，不代表真实游戏画面或 GPU 路径已验证。

开发客户端的最小画面／性能对照：

```text
/sbmmesh batch view immediate same 128
/sbmmesh batch view batched same 128
/sbmmesh batch start all 128 120 3
/sbmmesh batch stop
/sbmmesh attrib view_glow production 24
/sbmmesh attrib glow 128 1200 10
/sbmmesh attrib stop
```

两种测试不能同时运行；保持视角、画质与世界负载不变，先等几何就绪。结果在游戏目录的 `benchmark-results/`；CPU 包含实际提交／排空，GPU elapsed 不等于纯 shader 时间。`/sbmmesh` 仅用于开发环境。

## 世界网格开发压测

开发环境可对照策略和局部光照：

```text
/sbmmesh stress same 256
/sbmmesh path instance
/sbmmesh path section
/sbmmesh stats detail
/sbmmesh stress light start 20 16
/sbmmesh stress light stop
/sbmmesh stress clear
```

path 会启用绘制层。先等 `waitingGeometry=0,dirty=0,pending=0`，再等至少 120 帧记录数据；只改光照时 builds／submittedVertices 应不变，rangeBytes 反映独立 UV2 上传量。frameMs 是最近最多 120 帧的阶段间隔均值，cpuUs 是本层 CPU 时间。

定时光照 start 的参数为 tick 间隔／每轮数量，默认 20 tick／全部实例；stop 保留当前合成光照，reset 恢复生成时采样值。`/sbmmesh cache clear` 保留对象并失效缓存。测试期间固定镜头与世界负载，资源重载、策略切换后重新预热。
