> #6 提示词

# 

```
现在有以下问题：
- MachineMax的配置文件似乎包含了阻止载具破坏方块，这意味着一部分兼容是不必要的，需要清理
- 检查MachineMax是否已经能配置载具渲染距离

- 注意当前打开的主窗口是CGC，而修改的代码需要写在CGC-Compat-MachineMax
- CGC compat里的MachineMax版本较旧，MachineMax目录下是fork的最新进度
- MachineMax fork的gradle环境下的很慢，可能没有IDEA构建代码引用

现在开始。
```

# 

I'll start by exploring the three directories to understand the structure.

Now let me explore the compat project structure and find the MachineMax compatibility code.

Let me find the actual source directories (excluding build output).

Now let me read the compat config and the MachineMax configs.

Now let me examine the compat's griefing/render-distance implementations, and search MachineMax for render distance.

Now the compat render-distance mixins and MachineMax's renderer/entity sync code.

Now the griefing mixin and MachineMax's CollisionHandler + PartEntityRenderer in full.

Let me trace where these config values are actually used on both sides.

Let me read MachineMax's entity registry, the block-damage gate, and the fork's recent history.

Let me look at MachineMax's DistantVehicleRenderer (proxy renderer) and the compat's bundled libs + git history.

Let me compare the bundled old MachineMax jar with the fork, and check ObjectManager's client vehicle list.

Let me check the fork's generated config files and the compat's entity/setup classes.

Let me check MachineMax's config registration, part-entity tracking range, and the compat's remaining files.

Let me confirm whether vehicle cores are synced to clients independent of distance (which is what DistantVehicleRenderer relies on).

Let me read the remaining compat pieces and changelog to scope the cleanup.

Let me read the design docs that motivated these features.

Let me do a final check for any per-vehicle render distance setting in MachineMax's UI/config panels.

I've finished the investigation. Here's what I found before touching any code.

## 1. 方块破坏（mobGriefing 兼容）

MachineMax 的 `MMServerConfig` 里确实有开关：

- [`MMServerConfig.java:18`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/MMServerConfig.java) `should_destroy_blocks`（默认 `true`）— "Whether or not the parts should destroy blocks when the impact is big enough."
- 它门控载具撞坏方块的**唯一路径**：[`CollisionHandler.java:342`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/collision/CollisionHandler.java) 的 `shouldDestroyBlocks() && ...` → `applyBlockDamage`。

对照 compat 的两个选项（[`BlockDestructionGuard.java`](D:/Github/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/BlockDestructionGuard.java) + [`CollisionHandlerMixin.java`](D:/Github/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/vehicle/CollisionHandlerMixin.java)）：

- `forceDisableGriefing`（默认 `false`）= 无条件拦截 `applyBlockDamage`。**与 MachineMax 的 `should_destroy_blocks=false` 完全等价 → 冗余。**
- `followMobGriefing`（默认 `true`）= 让破坏跟随原版 `mobGriefing` 游戏规则。我全库搜过，**MachineMax 没有任何 mobGriefing 判定**，所以这一项不是冗余，它是 prompt #5 真正要加的功能。
- 补充：MachineMax 还有独立的 `projectile_destroy_blocks`（射弹穿块路径），compat 的 guard 从来没覆盖它。

结论：真正"不必要"的是 `forceDisableGriefing`；`followMobGriefing` 是 MachineMax 没有的能力。

## 2. 载具渲染距离

MachineMax **不能配置**渲染距离 —— 三个 ModConfigSpec 和每载具 GUI 配置里都没有该项。

但 fork 已经有了 `DistantVehicleRenderer`（[`DistantVehicleRenderer.java`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/DistantVehicleRenderer.java)），代理渲染超出实体渲染距离的载具，阈值取自原版客户端渲染距离设置。关键证据：

- 这个类**不在** compat 编译依赖的 `libs/MachineMax-1.21.1-1.0.1-beta.1.jar` 里（`unzip -l` 确认），是 fork 新增的。
- 它遍历 `ObjectManager.clientAllVehicles`，而载具数据是 `sendToPlayersInDimension`（全维度广播）同步的，不受实体追踪范围限制。

这意味着 compat 的渲染距离那套（`PartEntityRendererMixin` 去掉距离截断 + `ModEntities` 把 `clientTrackingRange` 拉到 20 区块）现在**不只是冗余，还会和 `DistantVehicleRenderer` 重叠渲染**：MachineMax 的代理渲染器负责"超出原版渲染距离"的部分，compat 又把 `PartEntityRenderer` 的距离上限去掉、同步范围拉到 20 区块，两个渲染器会在重叠区间画同一个 SubPart。

在动手前，有两处需要你定夺:

The user chose to delete both modules entirely. Let me trace all references before editing.

Both modules are authorized for full removal. Let me check the compat repo state, then execute.

There's a `#6 cleanup.md` doc already staged — that's likely this task's brief. Let me read it.

That's just the stored prompt. Now let me execute the cleanup.

Now the mixin config and mods.toml.

Now the config tag class.

Now the config class — four targeted removals.

Now `CommonSetup` — drop the render-distance call.

Now let me verify no dangling references remain.

`git rm` cleaned the empty parent dirs. Now the README.

Now let me verify compilation. First check the JDK/toolchain situation.

Compilation succeeded (quiet mode, exit 0). Let me confirm explicitly and check the stale classes are gone.

Let me confirm no stale compiled mixin classes linger in the build output.

All changes verified. Let me record the cross-repo context that made this cleanup necessary.

清理完成，`core` 与 `neoforge-compat` 编译通过（`BUILD SUCCESSFUL`）。

## 调查结论

**方块破坏** — MachineMax 的 [`MMServerConfig.should_destroy_blocks`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/MMServerConfig.java) 门控载具撞坏方块的唯一路径 [`CollisionHandler.java:342`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/collision/CollisionHandler.java)；`forceDisableGriefing` 与它完全等价。`followMobGriefing` 是 MachineMax 完全没有的 mobGriefing 联动 —— 但按你的选择，整个模块一并删除。

**渲染距离** — MachineMax 无法配置（三个 ModConfigSpec 与每载具 GUI 都没有该项），但 fork 新增了 [`DistantVehicleRenderer`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/DistantVehicleRenderer.java) 自动代理渲染远处载具（旧 jar 里没有这个类）。compat 的 mixin 去掉 `PartEntityRenderer` 距离上限 + 把 `clientTrackingRange` 拉到 20 区块，会与它重叠渲染 —— 删除后重叠问题消除。

## 改动

删除：
- griefing：`BlockDestructionGuard.java`、`CollisionHandlerMixin.java`
- 渲染距离：`PartEntityRendererMixin.java`、`EntityTypeAccessor.java`、`ModEntities.java`、整个 `cgccmachinemaxclient.mixins.json`（唯一 client mixin）

修改：`CgccMMConfig.java` / `CgccMMConfigTag.java`（去掉 4 个配置项，保留注释掉的 sound 两行）、`cgccmachinemax.mixins.json`、`neoforge.mods.toml`（去掉 client mixin 引用）、`CommonSetup.java`、`README.md`（中英配置列表）。

配置文件现在只剩 `allowEntityRemoval`、`discardOnDetach`。已有实例的 `cgccmachinemax.json` 会自动迁移：加载器跳过未知键，回写时按固定顺序重写。

## 待你处理

- **未提交**：删除已由 `git rm` 暂存，其余编辑未暂存。当前分支 `cleanup`。
- **`changelog.md` 未动**（`0.0.12.1` 那条是历史记录，仍准确）；如需记录本次移除和版本号，你来定。
- 未实机验证。建议进游戏确认远处载具由 MachineMax 自身渲染正常、且无重叠。
