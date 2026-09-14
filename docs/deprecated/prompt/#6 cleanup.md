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

# 

```
那么需要检查一下目前两个配置项allowEntityRemoval和discardOnDetach的实现方式：
- 目前用CGC Compat里的MachineMax旧版本.jar玩下来是有崩溃问题，有的时候是kill @e[选中]之后立即崩溃，有的没立即，但是之前有kill过
- E:\Software\Minecraft\neoforge1.21.1server\hs_err_pid1032.log有一个崩溃log，注意内容很多，每次只能读几行，不要一下子读完，不然上下文长度一下就满了；这个崩溃log不对应最新的debug.log，之后又崩过两次，但是因为在测试所以没管，并且最新两次的崩溃log删了（大概率是相同问题导致的）
- 检查一下这个log，看看有没有什么信息，以及当前removal的兼容实现方式是否可以改为给一个极大伤害，来融入MachineMax自己的销毁逻辑，而不手动涉及可能导致副作用的操作
```

# 

I'll read the two remaining compat implementations and start on the crash log carefully.

Crash is a native `EXCEPTION_ACCESS_VIOLATION` inside `bulletjme.dll` (JBullet physics). Let me find the stack frames.

Crash is on the **physics thread** (`PhysicsLevel.run` → `PhysicsSpace.stepSimulation`, native `bulletjme.dll`). Let me check whether MachineMax removal is thread-safe and where `removePart` is used.

MachineMax gates all physics mutations through `submitImmediateTask(PPhase...)`. Let me see whether `removePart` respects that.

Let me see how `Part.destroy()` tears down bodies and find the actual physics-body removal API.

Key lead: let me check the thread context of MachineMax's own `removePart` callers.

The crash is on the **"Server PhysicsThread"** inside `stepSimulation`. Let me check thread-safety of joint teardown and look at the log's thread list.

`AbstractConnector.destroy()` wraps joint removal in `submitImmediateTask(PPhase.ALL)` — but `DestroyableRigidObject.destroy()` calls `removePhysicsBody` unwrapped. Let me check whether that API defers internally.

`removePhysicsBody` internally defers via `submitImmediateTask` — so body removal is not the raw race. Let me check the joint/peer teardown path.

我读完了实现和崩溃日志。先说日志结论，再说实现评估。

## 崩溃日志

`hs_err_pid1032.log`（360 KB，我只取了关键段）：

```
Current thread: JavaThread "Server PhysicsThread" daemon [_thread_in_native]
Native frames:  C  [bulletjme.dll+0x79386]
Java frames:
  J  com.jme3.bullet.PhysicsSpace.stepSimulation(JFIFI)V   spark_core
  J  cn.solarmoon.spark_core.physics.level.PhysicsLevel.run
  J  ...PhysicsLevel$run$1.invokeSuspend
siginfo: EXCEPTION_ACCESS_VIOLATION, writing address 0x0000000000000374
```

- 是 **JBullet 原生库崩溃**（`bulletjme.dll`），不是 Java 异常。写入 `0x374` 这种小偏移，典型的"空指针/已释放对象解引用"。
- 崩在 **物理线程** 的 `stepSimulation` 里，up time 12 分钟。
- **整条栈里没有任何 MachineMax / cgccompat / customgun 的帧** —— 说明损坏是更早发生的，物理世界里残留了一个引用已移除刚体的约束/对象，之后某次 step 才踩雷。这和你说的"有的没立即崩、但之前 kill 过"完全吻合。

## 当前实现

- `allowEntityRemoval`：[`MMPartEntityRemovalMixin`](D:/Github/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/MMPartEntityRemovalMixin.java) 覆写 `Entity#remove(RemovalReason)`，启用时 **同步** 调 `VehicleCore.removePart(part)` 并跳过 `super.remove`。
- `discardOnDetach`：[`DetachedPartDiscard`](D:/Github/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/DetachedPartDiscard.java) 在 `addSpiltVehicle` 登记分裂载具，到 `LevelTickEvent.Post` 对它的每个 part 调 `removePart`。

我用 javap 核了底层：`PhysicsBodyExtensionKt.removePhysicsBody` 内部就是 `TaskSubmitOffice.submitImmediateTask`（反编译确认），[`AbstractConnector.detachJoint`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/connector/AbstractConnector.java) 和 `AbstractConnector.destroy` 也都走 `submitImmediateTask(PPhase...)`。

**所以兼容不是在裸改原生对象** —— 刚体/关节的移除本身都被正确投递到物理线程了。崩溃更可能来自**移除的时机与规模**，而不是单纯线程不安全：

- compat 在 `Entity#remove`（`kill @e` 实体遍历中）和 `LevelTickEvent.Post` 里成批拆载具；`removePart` 内部还会 `partNetSpiltCheck()` 再分裂出**新载具并立刻又移除**（`DetachedPartDiscard` 的 while 循环会在同一 tick 内级联处理这些新载具），一 tick 内产生大量"加刚体 / 拆刚体+关节"的任务突发。MachineMax 自己只在 `VehicleCore.preTick` 一次处理一个已摧毁部件。

## "给极大伤害"能否替代

方向合理，但有坑：

1. **最终仍会走 `removePart`**。伤害路径是：耐久→0 → `preTick` 的 `checkDestroyed` → `setDestroyed` → 销毁倒计时 → `SubPart.destroy` → `Part.onTick` 标 `destroyed` → **`VehicleCore.preTick` 调 `removePart`**。也就是说它没有绕开 `removePart`，只是把它挪到了 MachineMax 自己的 tick 点上。如果损坏源就是 `removePart` 本身，这样改救不了。
2. **会有延迟**。倒计时初值 = `max(最大耐久 × subpart_destroy_ticks_per_durability, subpart_destroy_min_ticks)`，默认至少 **200 tick（10 秒）**。要让部件"秒没"就得把 MachineMax 服务端配置 `subpart_destroy_min_ticks=0` + `subpart_destroy_ticks_per_durability=0` —— 变成依赖用户改 MachineMax 配置，而且所有被摧毁部件都会失去黑化/淡出动画。
3. 若用 `onHurt` 施加伤害，还会附带命中管线副作用（冲击分配到连接件→可能再次分裂、击退、音效、子系统伤害）。要就走 `subPart.setDurability(0)`（public，直接进销毁流程）。

**更对症的做法**（如果确认是时机的锅）：不加伤害，而是把移除**排队**，改在 MachineMax 自己的 [`VehicleCore.preTick`](D:/Github/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/VehicleCore.java) 尾部（mixin TAIL）统一执行 `removePart`。这样既避免了在 `Entity#remove` / `LevelTickEvent.Post` 这种"任意时机 + 级联突发"，又没有倒计时延迟，也不引入伤害语义。

## 建议的下一步（先定位再改）

1. **二分开关**：分别关掉 `allowEntityRemoval` / `discardOnDetach` 复现，定位是哪个在触发。
2. **换 fork 复现**：这份 log 是旧 jar（1.0.1-beta.1）的，fork 大改了 split/detach（`ConnectorDetachEvent`、`distributeSplitHp` 等）。先在 fork 上测，很可能上游已经修了。
3. **裸 MachineMax 对照**：不打兼容、纯 MachineMax 摧毁部件/`kill @e` 部件，看是否也会崩 —— 如果是，那是 MachineMax/SparkCore 的悬挂约束问题，兼容只是把它放大了。

我没有改任何代码。你决定方向后我再动手（我倾向先做 1 和 2 的定位，再谈是否需要改实现）。

# 

```
目前发现fork的MachineMax依赖其他几个github仓库，有个SparkCore虽然MIT但是MachineMax实际上需要github token来访问spark core剩余部分，也就是最新fork默认是用不了的。
需要做以下几件事：
- 之前对话里有检查是否MachineMax的config已有实现从而移除，如果是最新fork有实现但是旧版本没有实现的，要从git历史里补回来
- 更改discard逻辑部分也要实现，但以旧版本为准，新fork的修改什么时候上线都是未知的，到时候再适配最新版
```

# 

Let me re-verify what the OLD jar (which the compat actually compiles against) contains, before restoring anything.

Old jar lacks `DistantVehicleRenderer` but **has** `should_destroy_blocks`. Let me check the old API surface for the discard path and confirm the deleted files are recoverable from HEAD.

My earlier changes appear committed. Let me check the log and re-run javap with the old package names.

The cleanup landed in commit `fba3504`. Let me see exactly what it changed and get the old `VehicleCore` API.

Old API confirmed compatible. Let me check the old `VehicleCore.preTick` body to see if it already removes destroyed parts (which would give a clean, native hook).

Now I have what I need. Let me restore the render-distance compat from the pre-cleanup commit (`fba3504^`) — the old jar genuinely lacks `DistantVehicleRenderer`.

Now re-apply the render-distance config edits (excluding the griefing ones).

Let me verify the restore compiles against the old jar.

Task A compiles. Now for the removal logic. Let me verify MachineMax's tick driver and whether the `destroyed` flag is used elsewhere.

`VehicleCore.preTick` is driven by `ObjectManager`. Let me confirm the old jar drives it the same way (the mechanism depends on it).

Now the removal-logic change. I'll fold removal into MachineMax's own `VehicleCore.preTick` by setting `Part#destroyed` instead of calling `removePart` directly.

Let me compile and review the full diff.

全部完成，编译通过（`BUILD SUCCESSFUL`）。

## 1. 渲染距离兼容：已从 git 恢复

核实结论：**旧 jar（1.0.1-beta.1）没有 `DistantVehicleRenderer`**（`unzip -l` 确认），所以"fork 能自动渲染远处载具"这条理由对 compat 实际面对的旧版不成立 —— 渲染距离兼容必须留着。

从 `fba3504^` 恢复了：
- 文件：`PartEntityRendererMixin.java`、`ModEntities.java`、`EntityTypeAccessor.java`、`cgccmachinemaxclient.mixins.json`
- 配置与接线：`CgccMMConfigTag`/`CgccMMConfig` 的 `modifyRenderDistance`/`renderDistance`、`CommonSetup` 调用、`neoforge.mods.toml` 的 client mixin 条目、`cgccmachinemax.mixins.json` 的 `entity.EntityTypeAccessor`、README 中英两行

**griefing 没有恢复** —— 旧 jar 的 `MMServerConfig` **有** `should_destroy_blocks`（javap 确认），"冗余"判断对旧版同样成立，所以不属于"fork 有、旧版没有"。

> ⚠️ 但有一条要你定：`followMobGriefing`（跟随 mobGriefing 游戏规则）**MachineMax 新旧都没有**，它不是被我按"冗余"删的，而是你当时决定整模块删除。它不属于你这条规则覆盖的范围，要不要单独恢复，你说。

（另：旧 jar 没有 `projectile_destroy_blocks`，那是 fork 新增的；compat 本来也没覆盖射弹破坏路径。）

## 2. discard / 移除逻辑：改为融入 MachineMax 自身销毁流程

**改前**：`MMPartEntityRemovalMixin` 在 `Entity#remove` 里直接 `vehicle.removePart(part)`；`DetachedPartDiscard` 在 `LevelTickEvent.Post` 直接 `removePart`。

**改后**：两处都只置 `Part.destroyed = true`，实际移除交给 MachineMax 自己的 `VehicleCore#preTick`（由 `ObjectManager.onPreTick` → `LevelTickEvent.Pre` 驱动）里那句 `if (part.isDestroyed()) removePart(part);` —— 与 MachineMax 正常摧毁部件**完全同一条路径**。兼容端不再在任何时机同步拆刚体/关节。

**为什么没用你说的"给极大伤害"**：伤害路径会走 `checkDestroyed → setDestroyed → 销毁倒计时`，`getInitialDestroyTimeTicks()` 默认 `max(最大耐久×10, 200)`，也就是**至少 10 秒**才移除，还带黑化/销毁音效；要做到"秒没"还得让用户去改 MachineMax 服务端配置 `subpart_destroy_min_ticks=0`。直接置 `destroyed` 则一步跳进 `preTick` 那个移除分支：无延迟、不经过伤害管线（不会附带冲击分配/击退/音效）。

已核实的旧版事实（都用 javap 对 `libs/MachineMax-1.21.1-1.0.1-beta.1.jar` 验证）：
- `VehicleCore.preTick` 确有该分支：字节码 `Part.isDestroyed()Z → removePart(Part)V`
- `ObjectManager.onPreTick` 对维度内**所有**载具无条件调 `preTick`（`updateVehicleChunk` 只更新标志，不门控调用）
- `Part.destroyed` 是 `public` 字段，且只被 `VehicleCore.preTick` 读取，无隐藏语义

`discardOnDetach` 的延后结构保留（仍在 `LevelTickEvent.Post` 标记）：让标记落在分裂流程之外，实际移除在下一 tick 的 `preTick`，移除包自然晚于 `ConnectorDetachPayload`。

## 注意

- 这是对"物理线程崩溃"的**假设性修复**，我无法保证根因就是这个 —— 建议实机验证：`kill @e[type=machine_max:part]` 后部件是否一两 tick 内消失且重进不回来；打轮胎脱落部件同理。
- `allowEntityRemoval` 现在有**一 tick 延迟**（实体在下个 `LevelTickEvent.Pre` 才被移除）。
- 未提交。改动都在工作区（恢复的文件是 staged，编辑未 staged）。
