# 客户端环境集成测试

在 PowerShell 中依次运行七种渲染器配置：

```powershell
.\tools\run-client-environment-tests.ps1
```

如需只运行特定测试场景或某一种配置：

```powershell
.\tools\run-client-environment-tests.ps1 -Profile vanilla -Scenario reload
.\tools\run-client-environment-tests.ps1 -Profile embeddium-ar -Scenario render
```

这七种配置分别是 `vanilla`、`ar`、`sodium`、`sodium-ar`、`embeddium`、
`embeddium-ar` 和 `sodium-ar-iris`。Sodium 与 Embeddium 分开运行。
各配置按需使用 Sodium 0.6.13、Embeddium 1.0.11、Accelerated Rendering
1.0.13-1.21.1-alpha 和 Iris 1.8.12。Iris 配置使用默认设置并禁用光影；
它不验证任意光影包的兼容性。

每种配置都有独立的工作目录：
`build/client-environment-tests/<profile>/game`，首次使用时会复制
`run/saves/新的世界`。可通过 `-SourceWorld <directory>` 指定其他源存档。
测试不会导入用户的模组文件夹、光影包或资源包。
Gradle 属性 `-PclientTestProfile=<profile>` 会在编译类路径中保留兼容性 API，
同时选择实际运行时加载的可选模组。常规开发运行和已发布的依赖项不受影响。

默认的 `environment` 场景会在真实的 Minecraft 客户端中执行以下全部测试，
不会使用无界面或模拟渲染器代替：

- 分配、上传、回读并删除真实的 GL 光照缓冲区。检查发生 Java 异常及受控的
  `GL_INVALID_VALUE` 错误后的清理情况，再验证后续上传能否成功，以及原有绑定能否恢复。
- 连续三个世界渲染帧将 TREE 和 BAKED 模型绘制到 128 × 128 的帧缓冲区中。
  从 GPU 回读像素，要求两种模型都呈现可见的几何体。所选的 Sodium/Embeddium
  原生写入器及 AR 后端除了要生成可见像素，还必须明确接受实际使用的顶点消费者。
  AR 通常将渲染阶段回调限定在原版管线中；测试会在自身管理的作用域内，
  通过公开接口临时启用加速管线，并在结束后恢复原管线。
- 执行 12 次真实的客户端资源包重载，以及 12 次内置服务器数据重载；后者使用与
  `/reload` 相同的服务器 API。两个后台读取线程会检查模型、动画、粒子的完整性，
  以及保留的快照。每轮都会等待加载遮罩结束，并让服务器至少运行十个 tick（游戏刻）。
- 检查碰撞和过期事件、锚点运动与粒子速度的叠加，以及发射器重启、管理器清空后的
  外部粒子过期行为。
- 让内置服务器中的玩家前往另一个维度再返回，随后通过 Minecraft 真实的世界清理流程
  断开连接。检查活动及已清空的发射器、旧世界的粒子，以及实际观察到的客户端卸载事件。

副本存档中的测试玩家会被切换为旁观模式，以便跨维度移动。
测试客户端始终不会打开源存档。旧版粒子测试运行器中的单项 `unload` 场景仍然是
直接发布卸载事件；如需测试真实的跨维度移动和断开连接，请使用 `transitions` 或
`environment`。

如需在区块可能仍在生成时，测试快速重复进入同一维度的情况：

```powershell
.\tools\run-client-environment-tests.ps1 -Profile embeddium-ar -Scenario transitions `
    -FreshWorld -TransitionSettleTicks 0 -TransitionCycles 3 -TransitionOrigin 16384
```

以下切换控制参数同时适用于 `transitions` 场景和 `environment` 中的跨维度部分：

| 参数 | 默认值 | 行为 |
| --- | --- | --- |
| `-FreshWorld` | 关闭 | 每次尝试都将源存档复制到一个唯一的世界目录，避免受到之前测试留下的地形和存档状态影响。源存档中已有的地形仍会被复制。 |
| `-TransitionSettleTicks` | `60` | 客户端每次抵达维度后，等待指定数量的真实内置服务器 tick。设为 `0` 可取消这段按服务器 tick 计算的稳定等待；库测试框架与引擎对照测试框架的具体动作时序有所不同，详见下文。 |
| `-TransitionCycles` | `1` | 执行指定次数的主世界与下界往返，共发生 `2 * cycles` 次实际维度切换，然后断开连接。 |
| `-TransitionOrigin` | `0` | 传送至 X、Z 均为该值加 `0.5`、Y 为 `90` 的位置。每次往返都复用相同坐标，以对尚未处理完的区块持有器（chunk holder）复用情况施加压力。测试新地形生成时，应选择源存档中尚未生成的区域。 |
| `-EngineControl` | 关闭 | 仅加载独立的对照模组和所选的可选渲染器，强制使用 `control-transitions` 场景。 |
| `-EngineUnloadRequeue` | 关闭 | 隐含启用 `-EngineControl`，并选择 `control-unload-requeue`：正常跨维度移动后，在服务器关闭期间注入一个受控的过时卸载回调，以及一次真实的生成任务取消操作。 |
| `-EngineUnloadFix` | 关闭 | 额外加载独立的 `sbm_chunk_unload_fix` 兼容性修复模组。它既可用于库测试框架，也可用于引擎对照测试。 |

稳定等待设为零时，库测试框架仍会插入真实粒子，并等待它们进入 Minecraft 粒子引擎后，
才执行下一次维度切换或断开连接；通常是在抵达后约两个客户端 tick。
这里没有隐藏的 60 个服务器 tick 延迟。引擎对照测试不检查粒子，
会在观察到抵达的同一个客户端 tick 中请求下一步动作。
因此，两者都可能遇到尚未完成的区块生成，但动作时序并不完全相同。

如需在不加载本库的情况下，运行对应的引擎对照测试：

```powershell
.\tools\run-client-environment-tests.ps1 -Profile embeddium-ar -EngineControl `
    -FreshWorld -TransitionSettleTicks 0 -TransitionCycles 3 -TransitionOrigin 16384
```

对照测试会确认本库模组及其类均未加载，检查所选渲染器的模组列表，
并执行真实的跨维度移动和断开连接。它不测试本库的渲染、资源重载、粒子归属或其他行为；
这些内容需要使用主 `environment` 测试框架。对照测试的隔离目录为
`build/client-environment-tests/control-<profile>/game`，报告目录也使用 `control-` 前缀。
如果内置服务器的关闭过程持续至少 15 秒，只读诊断观察器会记录区块持有器状态。
对并发映射中成员是否存在的采样仅是尽力获取的观察结果，单凭缺少某条诊断采样，
不能排除关闭过程存在问题。

如需对过时卸载回调机制进行受控的 A/B 验证，请使用相同配置和源存档，分别运行以下两条命令：

```powershell
# A：未修复的引擎；观察到任务饥饿后，在限定时间内执行恢复。
.\tools\run-client-environment-tests.ps1 -Profile vanilla -EngineUnloadRequeue `
    -FreshWorld -TransitionSettleTicks 0 -TransitionCycles 1 -TransitionOrigin 16384

# B：使用独立的引擎兼容性修复，执行相同的故障注入。
.\tools\run-client-environment-tests.ps1 -Profile vanilla -EngineUnloadRequeue -EngineUnloadFix `
    -FreshWorld -TransitionSettleTicks 0 -TransitionCycles 1 -TransitionOrigin 16384
```

这里使用的是显式故障注入，并不表示自然发生的维度切换复现了任何历史卡死问题。
未修复版本的断言要求关闭流程一直停留在 `stopServer`/`processUnloads` 中，
同时保存操作的 future 已完成、仍有一个未释放的真实生成引用，且一个已排队的服务器任务
因饥饿而无法执行。20 秒后，测试的兜底恢复逻辑会取消真实的生成任务，释放其持有的引用，
使隔离客户端能够退出。此处的 `PASS` 表示已验证该机制及限定时间内的恢复，
并不表示未修复的引擎避免了故障。已修复版本的断言则要求已排队的服务器任务能够自然执行，
并在恢复截止时间前完成关闭，且不能触发兜底逻辑。日志会记录 `INJECTED`、`patchPresent`、
`queuedReleaseNatural`/`queuedReleaseStarved` 以及兜底状态。

该修复使用单独的 `*-engine-fix` 游戏及报告目录，需要通过
`-PclientTestEngineFix=true` 显式启用。默认的库构建产物不包含此修复。
关于独立 JAR 的构建与安装，请参阅[独立补丁说明](engine-chunk-unload-fix.md)。
这些命令用于说明测试流程；实际执行结果应记录在验证报告中。

Gradle 的对照测试开关为 `-PclientTestControl=true`；可选属性
`-PclientTestWorld=<world-directory-name>` 用于在库模式和对照模式中选择 QuickPlay 存档
（默认为 `sbm-client-test`）。运行器会传入其复制后的存档名称，并在 `run.json` 中
记录所有选定的维度切换设置。零稳定等待的运行属于关闭流程与区块生成压力测试，
其结果应与默认等待 60 个 tick 的常规游戏场景覆盖结果分开报告。

GL 故障测试会故意发出一个无效的微小分配请求，不属于 GPU 显存耗尽或设备丢失测试。
此测试矩阵仅覆盖当前安装的 GPU、驱动和上文列出的精确模组版本，
并不覆盖所有驱动或光影包。

结果会归档到以下目录：
`build/client-environment-tests/reports/<timestamp>/<profile>/`，其中包括：

- `client.log` 以及 Gradle 标准输出和标准错误日志；
- `run.json` 和 `result.json`，包含配置、场景、唯一运行 ID、时限、进程退出码及是否通过；
- `resolved-runtime.json`，包含解析得到的运行时构件坐标；
- NeoForge 启动参数及其传统类路径（legacy classpath）。

测试通过必须同时满足：退出码为 0、客户端日志为本次新生成、唯一运行 ID 匹配、
存在所选场景的 `PASS` 标记，且没有 `FAIL` 标记。
每种配置的默认时限为 600 秒，可通过 `-TimeoutSeconds` 修改。
超时只会终止该配置所启动的进程树。可使用 `-ContinueOnFailure`，
在某个配置失败后继续收集后续配置的测试证据；只要有任意配置失败，命令最终仍会返回非零退出码。
