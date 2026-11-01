# 客户端粒子测试

在 PowerShell 中运行客户端集成测试：

```powershell
.\tools\run-client-particle-tests.ps1 -Scenario all
```

测试框架会在 `build/client-particle-test` 下创建 `run/saves/新的世界` 的副本，并进入该副本进行测试，因此不会修改源存档。首次运行时，会将本地客户端的模组和设置复制到该测试游戏目录。可选择 `collision`、`anchor`、`lifecycle` 或 `unload`，单独运行对应的基础粒子测试场景。`transitions` 会执行真实的跨维度移动和断开连接；`gpu`、`render` 和 `reload` 则分别运行对应的单项集成测试。

`all` 场景会使用客户端中的真实方块检查碰撞和过期事件，验证锚点移动与粒子速度叠加后，渲染出的四边形面片中心位置是否正确，并确认调用 `restart()` 和管理器的 `clear()` 后，外部粒子仍会存活，直到其自身生命周期结束。随后，测试会发布 NeoForge 客户端世界卸载事件，并像原版切换世界时那样清空正在运行的 `ParticleEngine`。它会检查：即使引擎没有调用粒子的 `remove()`，外部管理的粒子归属关系也能被释放。测试结果会以 `[SBM_CLIENT_TEST] PASS ...` 或 `[SBM_CLIENT_TEST] FAIL ...` 的形式写入游戏日志；测试失败时，PowerShell 命令会返回非零退出码。

如需测试相互隔离的渲染器组合，或运行完整的 `environment` 场景，请按照
[客户端环境集成测试](client-environment-tests.md) 中的说明，使用
`tools/run-client-environment-tests.ps1`。每次运行都要求生成包含本次唯一运行 ID 的新日志，
因此，之前的通过记录不能让本次启动失败的测试被判定为通过。
