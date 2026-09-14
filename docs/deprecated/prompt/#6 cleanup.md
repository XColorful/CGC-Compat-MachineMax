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

# 

```
还有一个需要调查的：
- MachineMax的配置项似乎有配置脱落的part 每tick扣血，检查下是否存在该配置，如果有且可以通过配置极大值来实现掉落后秒没的话，那么“脱落后立即移除”也可以完全清理掉。
```

# 

Good question — let me search the MachineMax fork for any detached-part HP-decay mechanic and re-read the config spec.

Let me look at the detach/split flow and search content packs/schemas for any detached-part decay field.

Let me trace the detach/split code and all damage sources to see if anything decays detached parts.

Let me check the split-vehicle lifecycle, HP behavior, and git history for any detached-part decay feature.

调查完成。结论：**不存在这样的配置**，`discardOnDetach` 不能用配置替代。

## 证据

**1. MachineMax 全部配置项已枚举**（3 个 ModConfigSpec，[`MachineMax.java:59-69`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/MachineMax.java) 注册）：

- [`MMServerConfig.java`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/MMServerConfig.java)：`should_destroy_blocks`、`projectile_destroy_blocks`、`ignore_assembly_tag_requirements`、`subpart_destroy_ticks_per_durability`(10)、`subpart_destroy_min_ticks`(200)、`subpart_destroy_advance_ticks_per_damage`(20)
- `MMClientConfig` / `MMCommonConfig`：视觉、操控、欢迎页、内容包，无相关项

没有任何一项涉及"脱落部件扣血"。

**2. 唯一的"随时间消失"机制是销毁倒计时，且只对已摧毁（耐久=0）的部件生效** —— [`DestroyableObject.java`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/DestroyableObject.java)：

- `postTick` 里只有 `if (isDestroyed()) tickDestroyTimer(1)`，计时归零才 `destroy()`
- 计时初值 = `max(最大耐久 × subpart_destroy_ticks_per_durability, subpart_destroy_min_ticks)`（[:133](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/DestroyableObject.java)）

所以你想到的"配置极大值"实际是**极小值**：把 `subpart_destroy_min_ticks=0` + `subpart_destroy_ticks_per_durability=0`，已摧毁部件会在同 tick 消失。但这只对**耐久耗尽**的部件生效。

**3. 脱落本身不造成耐久衰减。** 分裂走 [`VehicleCore.serverHandleSpilt`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/VehicleCore.java) → 只调 `distributeSplitHp`，仅重算 **VehicleCore 组装体血量**（`setHp`），完全不碰 Part/SubPart 耐久。全部 `setDurability` 调用点只有：受击、维修、读档/装配。

**4. 脱落与摧毁是两条独立路径。** [`SubPart.java:573-578`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java) 里同一次命中会分头触发 `distributeDamageImpactToConnectors`（断连接件→脱落）和 `accumulateDamage`（扣耐久）。连接件先断时，**部件是"活着"脱落的（耐久 > 0）**，不受任何销毁倒计时约束，会作为独立载具永久留存 —— 这正是 prompt #4 里"打中部位会脱落"的场景。

## 结论

- 已摧毁部件脱落后：MachineMax 自己的倒计时会清掉它（可用上述配置加速到瞬没）。
- **存活部件脱落：MachineMax 没有任何衰减或清理机制**，`discardOnDetach` 是唯一手段。

因此 `discardOnDetach` 不能清理 —— 除非你确认只在意"残骸"（已摧毁部件），那才可以只靠 MachineMax 配置。要不要动，你定；我没有改任何代码。
