> #2 提示词

# 

```
现在需要解决CGC跟MachineMax的camera相关的兼容性问题

目前测试：
- 枪械装备倍镜（enableScope=true）开镜下移动视角，总是会在移动后回弹一点
- 当射击时按下蹲键，无论处于站着还是趴姿，后坐力都会立即停住，然后在松开蹲键的一瞬间镜头抬到天上；这个问题TaCZ也有，但是CGC是衍生自TaCZ的，TaCZ的实现无需特别关注
- 当把一辆载具打得很烂，然后坐在里面的位置时，第三人称下玩家可能是在载具外面（不同位置），此时的射击视角可能还需要根据玩家乘坐的位置和当前视角朝向来计算

- 当前IDEA打开了CGC本体、CGC compat、TaCZ三个窗口，可供浏览源码
- MachineMax的源码在CGC compat窗口浏览
- 当前主窗口是CGC，但是代码修改要写到CGC compat里

辅助快速浏览：
- CGC镜头、后坐力等在dev.xcolorful.customgun.client.renderer.item.gun、dev.xcolorful.customgun.client.entity.shooter.player
- CGC渲染体系文档在CGC目录下./docs/architecture/client/render/
- 注意到MachineMax在io.github.sweetzonzi.machine_max.mixin包下有CameraMixin
- 当玩家坐在载具里时，玩家发送射击包的时候，可能CGC服务端获取到的玩家眼睛位置跟实际不一样，需要考虑在兼容mod修改或者mixin到CGC来添加载具乘坐检测来矫正，矫正方式需要跟MachineMax里得出的一致

一共以下几个问题：
- 1. 开镜后镜头移动会回弹
- 2. 按蹲键会立即无后座，松开蹲键后镜头一下子往头上移
- 3. CGC服务端收到射击包后，生成子弹时的初始位置需要调整为MachineMax下应该有的视线位置

- 如果修复代码涉及需要了解MachineMax系统性问题，则在修复完成后参考"#1 bullet-victim-impl"所完成的文档那样，在./docs/architecture/client/下建立合适的文件夹来放体系文档
- 由于不是特别增加的联动，只是兼容修复，因此修复的设计不需要是一个完整的系统（不管是否需要了解MachineMax体系），不需要对修复方式专门写一个系统（而是写MachineMax体系的内容）
- 如果涉及修复，那么应该放在跟CGC原本功能相应的位置，例如修复枪械后坐移动，那么可能要跟CGC一样放在client.renderer.item.gun包下来让CGC开发者知道这个修复是对应原本哪里的功能

现在开始调查并完成修复。
```

# 

I'll start by understanding the three projects and the camera architecture. Let me explore in parallel.

Let me focus on actual source (not build artifacts) and read the key docs.

I've completed a thorough investigation. Let me record my key findings in memory before implementing, then write the code.

The build passed. Now let me write the MachineMax camera system documentation, mirroring the format from the earlier `#1 bullet-victim-impl` task.

Let me verify the exact `GunProjectile` constructor descriptor against the compiled CGC class to be certain the mixin target is correct.

Build passes. Here's what I found and what I changed.

## 结论（TLDR）

三个问题都定位并修好了。核心根因是：**MachineMax 的 `CameraController.updateCameraRot` 会无条件地用一份平滑值覆盖 `ViewportEvent.ComputeCameraAngles` 的摄像机朝向，即使玩家根本没坐载具**。这直接造成开镜回弹、后坐力被拖慢；而坐进座位后，CGC 用玩家实体坐标算出弹位置，又和 MachineMax 的座位视线位置对不上。

## 根因分析

**问题 1（开镜回弹）与问题 2（后坐力异常）**：MachineMax 的 `updateCameraRot` 对 pitch/yaw/roll 做 EMA + lerp 双重平滑后 `event.setPitch/Yaw/Roll`，而这段代码在非乘坐时也会走到（没有座位守卫）。CGC 后坐力是直接改 `LocalPlayer#setXRot` 实现的，被这份滞后平滑覆盖后就会「回弹」「压平」。我在 [machine-max-camera-system.md](app://localhost/epitaxy/CGC-Compat-MachineMax/docs/architecture/client/camera/machine-max-camera-system.md) 里详细记了这段逻辑。

**问题 3（出弹位置偏移）**：CGC 用 `IShooterState#getShooterEyePos`（玩家实体坐标 + 眼睛高度）作子弹出生点。坐座位时玩家是 `MMPartEntity` 的原版骑乘者，实体坐标 ≠ MachineMax 第一人称摄像机所在的座位 locator 位置，服务端出弹就偏了。

## 改动（都在 CGC compat 仓库）

|文件|作用|
|---|---|
|[GunCameraCompat.java](app://localhost/epitaxy/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/renderer/item/gun/GunCameraCompat.java)（新增）|`LOWEST` 优先级监听 `ViewportEvent.ComputeCameraAngles`，非乘坐时把 pitch/yaw 还原为 `entity.getViewXRot/YRot`，抵消 MachineMax 的平滑覆盖。对应 CGC 的 `client.renderer.item.gun.GunCameraHelper`|
|[GunProjectileMixin.java](app://localhost/epitaxy/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/projectile/GunProjectileMixin.java)（新增）|mixin 进 CGC 的 `GunProjectile`，构造完成后若射手正坐座位，按 MachineMax `updateCameraPos` 的第一人称分支重算出生点，并同步 `shootPos`（距离衰减伤害基准）。对应 CGC 的 `core.entity.projectile.GunProjectile`|
|[cgccmachinemax.mixins.json](app://localhost/epitaxy/CGC-Compat-MachineMax/core/src/main/resources/cgccmachinemax.mixins.json)（修改）|注册 `GunProjectileMixin`|

文档（新增 `docs/architecture/client/camera/`，格式对齐 `#1 bullet-victim-impl`）：

- [Home.md](app://localhost/epitaxy/CGC-Compat-MachineMax/docs/architecture/client/camera/Home.md) — 总览
- [machine-max-camera-system.md](app://localhost/epitaxy/CGC-Compat-MachineMax/docs/architecture/client/camera/machine-max-camera-system.md) — MachineMax 摄像机体系
- [cgc-compat.md](app://localhost/epitaxy/CGC-Compat-MachineMax/docs/architecture/client/camera/cgc-compat.md) — 兼容修改点

并在 [client/Home.md](app://localhost/epitaxy/CGC-Compat-MachineMax/docs/architecture/client/Home.md) 加了导航。

## 需要你确认的一点（关于问题 2）

你提到「这个问题 TaCZ 也有」，说明后坐力的蹲键 bug 有一部分是 CGC 从 TaCZ 继承来的（后坐力代码确实和 TaCZ 完全同源）。我这次的 `GunCameraCompat` 修的是 MachineMax 干扰的那部分（还原后 CGC 后坐力恢复到「没装 MachineMax」的原生表现）。如果还原之后蹲键仍会导致后坐力「停住→松键跳天」——也就是纯 CGC 自身的差分累加器 bug——那还需要在 CGC 的 `GunCameraHelper._applyCameraRecoil` 上单独打一个补丁，这个我没动，因为无法在没跑游戏的情况下确认具体触发点，不想凭猜测改 CGC 的核心后坐力逻辑。建议先实机验证一下问题 1 和 2 是否已被当前修复消除，再决定要不要追问题 2 的纯 CGC 部分。

改动均已编译通过，未提交。

# 

```
我说的这个按蹲后后坐力立即清零是只在装了MachineMax才有的，正常情况下CGC和TaCZ都没有。

以及，虽然只有1.21.1neoforge一个版本，但是仓库目录下./core/仍然是不允许使用@SubscribeEvent这种的：
- 对于CGC已经封装过的事件，用CGC的，使用方式参考CustomGunClient#init里调用的ClientEventHandlers.registerAll，对IEventHandler的实现方式也参考CGC里调用的监听器的实现方式
- CGC的事件注册，优先级顺序是严格保证的，只需要EventPriority比MachineMax低1级就够

经测试：
- 开镜后回弹的问题已经解决
- 按蹲键后不会突然零后坐力然后松开时飞天，但是在射击时按蹲时，无法拖动鼠标来压枪（鼠标往下拖没反应）

现在需要完成：
- 1. 注册改成CGC事件监听（即GunCameraCompat包括整个仓库根目录./core下都不含import net.neoforged
- 2. 解决按蹲键后无法压枪的问题

完成后通知我检查，不要提交，也不需要fetch origin，当前只有执行这一个任务
```

# 

Now I understand the CGC event system. Let me rewrite `GunCameraCompat` to use CGC's `IEventHandler` and register it properly.

Let me examine the NeoForge `ViewportEvent.ComputeCameraAngles` implementation to understand how it processes the angles, which may be key to the compensation issue.

Let me check the `get_file_problems` on my current code and then address the compensation issue.

Now let me update the documentation to reflect the CGC event registration (instead of the earlier `@EventBusSubscriber` approach). Let me check the current doc content.

改动完成，编译通过。下面是本次的修改与说明。

## 任务 1：注册改成 CGC 事件监听（已完成）

- [GunCameraCompat.java](app://localhost/epitaxy/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/renderer/item/gun/GunCameraCompat.java) 改为实现 CGC 的 `IEventHandler`（参考 `GunCameraHelper` 的写法），不再 `import net.neoforged`。
- [CgccMachineMaxClient.java](app://localhost/epitaxy/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/CgccMachineMaxClient.java) 里用 `CoreEventHandlers.register(CustomGun.getEventRegister(), ..., COMPUTE_CAMERA_ANGLES_EVENT, EventPriority.LOW, false)` 注册，即比 MachineMax 的 NORMAL 低一级。
- 已确认 `./core/` 下没有任何 `net.neoforged` 引用（仅 `neoforge-compat` 入口保留）。

## 任务 2：压枪补偿（pitch clamp）

在还原朝向时对 pitch 加了 `Mth.clamp(getViewXRot(partialTick), -90, 90)`。

原因：CGC 后坐力是直接 `LocalPlayer#setXRot` 改玩家真实 pitch，而 `setXRot` 不 clamp（原版的 `turn` 才会 clamp 到 [-90,90]）。原版 CGC 里摄像机读到的是 `turn` 已经 clamp 过的值；而我在 LOW 优先级、后坐力之后读 `getViewXRot`，拿到的是被后坐力顶出 [-90,90] 的未 clamp 值——镜头会被顶到「越过正头顶」，然后鼠标下拖又被原版 clamp 拉回 -90，看起来就是「往下拖没反应」。clamp 回 [-90,90] 后就和 CGC 单独运行时的表现一致。

## 需要你验证

任务 1 是确定的；任务 2 的 clamp 是我基于代码推的根因，**没有跑游戏验证**。麻烦你实测一下「射击时按蹲 → 压枪」是否恢复。如果还不行，请告诉我：现象是「往下拖镜头纹丝不动」还是「往下拖了但压不住（横向也在飘）」，我好进一步定位（后者可能还要处理 yaw）。

另外：文档 [cgc-compat.md](app://localhost/epitaxy/CGC-Compat-MachineMax/docs/architecture/client/camera/cgc-compat.md) 已同步更新为 CGC 事件注册 + pitch clamp 的描述。

未提交，未 fetch。

# 
---

```
本次对话接着CGC Compat目录下./docs/deprecated/prompt/#2 camera-compat.md继续完成

- 当前IDEA打开了CGC本体、CGC compat、TaCZ三个窗口，可供浏览源码
- 当前主窗口是CGC，但是代码修改要写到CGC compat里

目前还留下的问题：

- 开火时按住蹲下，不只是无法压枪，而是按蹲后鼠标无论往哪滑都没反应，镜头只按鼠标不移动时的后坐力往上移动

完成后通知我检查
```

# 

I'll start by reading the task documentation and understanding the current state of the camera-compat work.

I've done an extremely thorough investigation. Let me verify one concrete hypothesis before concluding — the key conflict I found between CGC's prone key and MachineMax's free-cam key.

I've completed a thorough investigation. Let me verify one final thing — whether MachineMax's free-cam flag is the actual culprit by re-reading the exact cancel path.