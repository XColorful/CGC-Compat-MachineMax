> #7 提示词

# 

```
现在有以下问题：
- 在专用服务器上运行，载具的声音会全部玩家播放（即使隔了几千格），玩家可以乘坐已经有驾驶员的驾驶位，并且会导致其他玩家的视角也传送到车里；假设车里A在驾驶位，B上驾驶位，A切位置，此时A可以按W触发音效但车子不动，B的键盘可以操纵车，B按下车后两人都会下车，但是A在车的位置，B在上车前的位置，并且B在开车的时候会出现看不见区块（可能是因为服务端判定其位置仍在上车前）

- 注意当前打开的主窗口是CGC，而修改的代码需要写在CGC-Compat-MachineMax
- CGC compat里的MachineMax版本较旧，MachineMax目录下是fork的最新进度，但由于MachineMax的构建环境前置依赖无法公开获取的部分（见#6 cleanup.md提及），因此最终修改以旧版本为准，最新fork仅供参考

目前怀疑：
- MachineMax可能有对全体玩家共享了一些变量
- MachineMax可能所有消息都是广播，但是没有过滤和进行声音距离过滤

现在开始研究并修复这个问题。
```

# 

I'll start by exploring the repos and their docs to understand the architecture before touching anything.

There's a `#7 multiplayer-fix.md` doc that looks directly relevant. Let me read the docs.

Now let me explore the MachineMax source — particularly vehicle seating, sound, and networking.

The compat targets an older MachineMax with a different package layout. Let me verify the fork's structure and inspect the old jar directly.

The compat repo is on branch `multiplayer-fix`. Now let me map the old MachineMax jar's structure.

Let me look at the old jar's mixin config and key multiplayer-related classes.

Now let me read the key classes from the old jar (authoritative) via IDEA.

Let me read the network registry and payloads — this is where broadcast vs. targeted sends live.

Found something significant. Let me read the input payloads and then hunt the sound code.

Now let me find where vehicle sounds are played.

Let me examine SparkCore's sound system — that's where the vehicle sound spreading lives.

Let me inspect the old jar's subsystem sound implementation, since the fork has diverged.

Let me see which side constructs these payloads and whether clients apply input locally.

I need to understand the client/server sync model before changing the broadcast. Let me inspect the entity sync path.

Let me examine the seat interaction path and the controlling-subsystem tracking.

Let me check the build setup so I can verify any changes compile.

Let me check how often vehicle pose is synced — this decides whether client-side input echo is load-bearing.

Let me check the fork's newer SeatSubsystem — it may already contain the intended fix.

Let me verify the subsystem synced-data machinery I'd need for an authoritative fix.

I've traced the root cause. Let me lay out a plan for your approval before touching anything.

Root cause is clear. One scope decision before I write the plan.

Plan approved. Let me verify the exact API surface I'll reference, then implement.

Now implementing. First the interact payload mixin.

IDEA build timed out. Let me compile with Gradle instead.

Let me make the authority check robust to vehicle-structure rebuilds (avoid identity comparison against a possibly stale subsystem instance).

已按方案改完并编译通过，未提交。

## 根因（从旧版 `MachineMax-1.21.1-1.0.1-beta.1.jar` 反编译确认）

MachineMax 把「客户端上行包」在服务端处理完后又 `sendToPlayersInDimension` 广播给全维度，而各客户端的 `clientHandler` 会拿**自己的本地玩家**重放一次：

- `SubsystemInteractPayload.serverHandler` → 每个客户端都执行 `interactBox.interact(自己)` → `SeatSubsystem.onInteract` → `setPassenger(自己)` → 设置 `controllingSubsystem`。**这是「视角被传送进车里」的直接原因**；镜头/听者既然到了车边，载具的位置音效自然听着像「全场播放」，B 的客户端也因此以「自己是驾驶位乘客」的身份对外发驾驶输入。
- `RegularInputPayload.serverHandler` → 每个客户端执行含 `LEAVE_VEHICLE` 的 `handle`，该分支作用于本地玩家自己 → 「一个人下车，两个人一起掉下来」。
- `MovementInputPayload.serverHandler` → `handler` 只按 UUID/名字定位子系统就 `setMoveInputSignal`，**完全不校验发送者** → 没坐在驾驶位也能驱动该车。

## 改动（`CGC-Compat-MachineMax`，分支 `multiplayer-fix`，核心 3 个新 mixin + 1 行配置）

- [SubsystemInteractPayloadMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/network/SubsystemInteractPayloadMixin.java) — 回声只发给发起者；若服务端因座位已被他人占用而拒绝，则连发起者也不回声（客户端不再乐观地把自己塞进被占座位）。
- [RegularInputPayloadMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/network/RegularInputPayloadMixin.java) — 回声只发给发起者，`LEAVE_VEHICLE` 不再影响其他人。
- [MovementInputPayloadMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/network/MovementInputPayloadMixin.java) — 服务端校验发送者正操控的座位就是本包寻址的座位（用座位名 + 部件/子部件/载具 UUID 判定，避免载具结构重建后误杀正常输入），否则丢弃；回声同样收窄为发起者。
- [cgccmachinemax.mixins.json](core/src/main/resources/cgccmachinemax.mixins.json) — 注册三个 mixin。

验证情况：`./gradlew :core:compileJava :neoforge-compat:compileJava` 通过（JDK21 工具链需 `-Dorg.gradle.java.installations.paths=E:/Software/Minecraft/JDK/jdk-21.0.8`，账号下 Gradle 找不到 21 且无法联网下载）；并用 `javap` 静态核对了三个 `serverHandler` 里各自只有一处、且描述符完全一致的广播调用点，`@Redirect` 注入点唯一。**联机行为我无法在此环境实测**，需要你开专用服务器 + 2 客户端按计划里的验收标准跑一遍。

另外两处未动（已在计划里说明理由）：`MovementInputPayload` 的广播本身按你的选择保留；`ScriptablePayload` 的全维度广播看起来是刻意为之（客户端脚本钩子）。新增的平台 API 只有 NeoForge 的 `PacketDistributor.sendToPlayer`（MM 自己也在用，无需兼容包装）。

# 

```
已经修复了座位占用的问题，但是远处的玩家还是会听到声音，你看是不是刚才没修的那几个问题导致的？我的运行环境只使用模组内置的几个载具，本地脚本如果在.jar里那你就检查一下到底干了什么，看是否可以调整服务端逻辑。
```

#

Let me dig into the actual sound definitions and the built-in pack/scripts in the MachineMax jar.

The built-in pack is inside the jar. Let me inspect its engine/sound definitions.

Let me find every sound-emission site in the old jar by scanning class constant pools.

Let me examine vanilla's SoundEngine to see whether the volume path SparkCore hooks is the one actually used.

This is the crux — let me check whether SparkCore's mixin overrides `play` (which would bypass vanilla's per-tick volume recalculation).

Let me check one specific lead: what `VehicleCore.activate()` does (it's called by the still-broadcast movement input).

Found a strong lead — a JVM-local audio path outside Minecraft's sound engine. Let me see who calls it.

Let me check the CGC side too — its gun sounds may be broadcast without distance filtering.

Strong lead — CGC broadcasts projectile-impact messages to *all* players. Let me see what they carry.

Let me check CGC's own custom sound instances — this may be the actual source.

我把声音这条路彻底走了一遍，结论和你的推测相反——**MachineMax 根本没有「服务端播放载具声音」这条路径**，所以「调整服务端逻辑」没有可调的地方。以下是查到的实证，以及为什么远处玩家理论上不该听到、以及怎么定死它。

## 1. 脚本：查过了，无关

- MM jar 里只有一个 `.js`：[`public_scripts/functions.js`](../Machine-Max/src/main/resources/public_scripts/functions.js)，内容是 `print` / `getMethods` / `getFields` / `Nbt` / `equals` / `duration` 这类**纯函数库**，没有顶层执行代码，也不会自动跑（没有任何类去调用它）。它是给你在脚本里手动调用的调试工具。
- 内置内容包 `spark_modules/Machine-Max_Official_Pack/` 里**没有任何脚本**，也没有 `scriptable` 子系统 / `"script"` 字段。所以那几个 `ScriptablePayload` 广播在你的环境里是空转，产生不了声音。

## 2. 没修的那几项，也解释不了声音

- `MovementInputPayload` 仍在全维度广播，但它的客户端处理只落到**载具子系统**（`setMoveInputSignal` / `activate()`，`VehicleCore` 里连一个 Sound 引用都没有），不涉及任何玩家坐标。
- `SubsystemInteractPayload` 的距离校验：只影响「谁能操作」，与音频无关。

## 3. 载具声音的完整清单（旧 jar 实测）——全部是客户端位置音，且都有距离上限

| 声音 | 触发侧 | 距离限制 |
|---|---|---|
| 引擎/电机工作音、启动/熄火、碰撞效果音 | **客户端**（`EngineSubsystem.onTick` 内 `level.isClientSide()` 守卫）→ SparkCore `SpreadingSoundInstance` | `maxRange = SoundEvent.getRange(1.0f)`，官方包 JSON 里全部 ≤ 64（78 处 64、69 处 16、10 处 48…），衰减还是平方的 |
| 手刹 / 换挡 / 轮刹车 | 客户端 `Level.playLocalSound` | 原版线性衰减，`sounds.json` 未写 `attenuation_distance` → 16 格 |
| 命中/穿透/撕裂、击退 | 服务端 `Level.playSound(null, x,y,z, …, 0.5f, 1)` | 原版 `broadcast(… 16*volume …)` → 16 格 |
| 服务端→客户端的声音包 | —— | **不存在**：整个 jar 里没有任何类引用 SparkCore 的 `SpreadingSoundPayload`，`BasicSubsystem.playLifecycleSound` 在服务端直接 return |

也就是说：按设计，载具声音**只能被 64 格内的人听到**（引擎实际听感约 40~60 格）。远处玩家能听到，只有两种可能：**(a) 客户端侧的距离衰减在你的环境里没生效；或 (b) 你听到的根本不是 MM 的载具音，而是别的来源。**

关于 (a) 有个具体嫌疑：SparkCore 的 `SpreadingSoundInstance` 用的是 `Attenuation.NONE`（把衰减完全交给自己的 `SoundEngineMixin.spark_core$calculateVolume` 每 tick 重算）。我在 **1.0.1023 / 1.0.1031** 两个版本里都确认过这个 mixin 存在且生效；但 MM 的依赖写的是 `spark_core versionRange="[1.0.1021,)"`——**没有上限**。如果你装的是明显更新的 SparkCore，它的音频实现可能已经不一样了，那 `Attenuation.NONE` 的音就会**全音量、无距离**播放，症状和你描述的一模一样。另外 MM 还 required 依赖 `terminal_ballistics`，那也是个可能出声的第三方。

## 需要你提供的信息（能问一次就定位，不用猜）

1. **具体是哪种声音**：引擎循环？启动/熄火那一下？手刹/换挡？还是撞击/子弹命中？持续循环还是偶发？
2. **隔着一个维度还听得到吗**（让远处玩家传到别的维度）？
    - 还听得到 → 不可能是 MM 的位置音，是别的 mod 或"在听者身上播放"的音；
    - 只有同维度 → 才可能是维度广播/位置音问题。
3. **车停着、熄火、没人在里面时，还听得到吗**？
4. **mods 里 SparkCore 的版本号**（`SparkCore-1.21.1-neoforge-1.0.xxxx.jar`），以及是否装了 `terminal_ballistics`。
5. 远处玩家客户端的 `logs/latest.log`（看有没有 mixin apply 失败的告警，或 `维度xx收到不存在载具的同步数据包` 这类行）。

## 我可以马上做的下一步（你选）

- **A. 加一个临时诊断 mixin**（放在 compat 客户端侧，dev 构建用）：每次客户端真正播放载具音时打一行日志——声音名、声源世界坐标、听者坐标、实际距离、SparkCore 算出的音量。让远处玩家跑一次，日志会直接告诉我们是哪个声音、距离和音量是多少，一次定死。
- **B. 你先把上面 1~4 的答案给我**，我据此直接定位并出修复。
- **C. 若确认是 SparkCore 那套 `Attenuation.NONE` 在你的版本里失配**，我可以在 compat 里加一个补偿 mixin（把 MM 载具音的衰减重新接回原版 OpenAL 线性衰减 / 或钳住 `maxRange`），不动 SparkCore 本体。

另外提醒一句：**这条声音问题不属于我这次改的那三个包（座位/输入回声）**，现在的改动可以照常保留。

# 

```
测试服务端在E:\Software\Minecraft\neoforge1.21.1server\mods，用的是SparkCore-1.21.1-neoforge-1.0.1023.jar版本

我重新测试，以下几个情况稳定触发：
- AB在同一纬度，距离上千格，A放置载具（还没上车）后B能听到声音；A切换座位，B能听到；A刚起步和拉手刹停下时，B没听到手刹声，但是有跟之前两个情况好像是一样的声音；当A的载具在平路上开，车身有略微倾斜（然后回正）时有触发该声音；注意到A驾驶的载具的挡位变化（增和减）时，B有听到该声音
- 当A在主世界，B在下届时，以上情况都没听到声音
```

# 

Now I have decisive data. Let me find every `SoundEvent` range constant in MM — a large fixed range would explain "audible 1000+ blocks in the same dimension".

Let me check the remaining sound call sites' position arguments in the fork (readable source).

The user runs SparkCore **1.0.1023** — I've been reading 1031's sources. Let me decompile the 1023 binaries.

Let me decompile the exact attenuation math in the 1023 binary.

Let me look directly at the test server's mods folder and logs — that path is on this machine.

The server loads content packs from `spark_modules/` — those can differ from the jar's copy. Let me check them and find the client logs.

Let me find the actual client instance (it will have the client-side log).

Let me check whether any other mod also mixins into `SoundEngine` (a conflict would break SparkCore's volume hook).

Let me examine how the contact-sound positions are produced on the client.

This is converging. Let me read the impact-sound path in `CollisionEffectManager`.

Let me check for mods that hook `PlaySoundEvent` (a wrapper there would bypass SparkCore's per-tick volume).

I've narrowed it to the client. Let me add a diagnostic mixin so we can see exactly what B's client plays. First, checking available accessors.

诊断包已构建好（含我上一个修复 + 一个临时诊断 mixin）。

## 先回答「是不是没修的那几个导致的」

不是。我把种子都查完了：

- **脚本**：jar 里唯一的 `.js` 是 `public_scripts/functions.js`，纯调试函数库（`print`/`getMethods`/`getFields`），无顶层执行、无任何类调用；官方内容包（`spark_modules/Machine-Max_Official_Pack.zip`，服务端实装的版本）里没有任何脚本、没有 `scriptable` 子系统。`ScriptablePayload` 在你环境里是空转。
- **`MovementInputPayload` 全维度广播**：它的客户端处理只动载具子系统（`setMoveInputSignal`/`activate()`，`VehicleCore` 里一个 Sound 引用都没有），不涉及任何玩家坐标，产生不了声音。
- **服务端根本没有「播放载具音效」这条路径**：整个 MM jar 里没有任何类引用 SparkCore 的声音包（`SpreadingSoundPayload` 零引用）；服务端仅有的发声是 `Level.playSound`（半径 `16×volume`）和 SparkCore 的 `sendToPlayersNear(3×SoundEvent range)`，而 21.1.1 的 `PlayerList.broadcast` 是按**半径 + 维度**过滤的（我核过源码）。所以「调整服务端逻辑」这件事没有可调的对象。

载具的引擎/启停/碰撞/连接件等音效**全部是客户端侧、每个客户端各播一份**（因为 MM 把载具状态全维度广播，每个客户端都持有一份完整的物理副本），它们的位置都是权威同步坐标。

## 为什么你的现象只剩一种解释

关键线索是**B 听不到手刹声**：手刹走原版 `Level.playLocalSound`，16 格线性衰减 → 远的听不到 ✔。而 B 能听到的那些（放置、切座位、起步/停下、车身倾斜、换挡）都走 SparkCore 的 `SpreadingSoundInstance`：`Attenuation.NONE`，距离完全靠它自己的 `SoundEngine#calculateVolume` 每 tick 重算 `volume·(1−d/maxRange)²`，`maxRange = SoundEvent range`（官方包全部 ≤ 64；我逐字节确认过你实装的 **SparkCore 1.0.1023**：公式、`maxRange` 赋值、`@Inject(method="calculateVolume*", at=RETURN, cancellable=true)` 注解都在）。按代码，1000 格外必须是静音——**但你能听到，所以这套衰减在你客户端上要么没生效，要么声源坐标根本不在车上。**

## 我加了临时诊断（已编译打包）

- 新文件 [VehicleSoundDebugMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/VehicleSoundDebugMixin.java)，已注册进 [cgccmachinemaxclient.mixins.json](core/src/main/resources/cgccmachinemaxclient.mixins.json)。
- 产物：`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`（23:10）。把它替换到**客户端** mods（服务端也换成同一个即可）。

请 B 客户端按你上面那套步骤复现一次，然后把 B 客户端 `logs/latest.log` 里 `[cgcc-sound]` 开头的行发我（每个音效名最多 5 行，不会刷屏）。会打出：

```
[cgcc-sound] name=... src=(x,y,z) listener=(x,y,z) dist=... maxRange=... instanceVolume=... expectedVolume=... engineVolume=... beyondRange=true/false
```

判读方式：
- `dist` 只有几十格 → **声源坐标被放到了玩家身边**，问题在「谁给了这个坐标」；
- `dist` 上千、`beyondRange=true` 而 `engineVolume` 仍 > 0 → **距离衰减没生效**，我在 compat 里按权威坐标硬过滤/重算音量即可。

## 顺手能做的三个免重编译验证（可选，能更快收敛）

1. B 在能听到时，依次静音「方块 / 中立生物 / 玩家」分类：摩擦刮擦音走 `Blocks`、子系统启停走 `Neutral`、引擎循环走 `Players` —— 一关就没的那个分类直接指出代码路径。
2. A 原地绕圈开 10 秒：B 听到的是**连续**声（引擎循环）还是**只有间隔事件声**？连续声能听到 = 衰减彻底失效的铁证。
3. B 从 1000 格走到 300/80/20 格，注意音量是否变化：**恒定** = 音量重算没跑；**随距离变小、但 1000 格仍可闻** = `maxRange` 异常大（另一种 bug）。

拿到日志我就出正式修复（预计是「载具音效距离过滤」：按**权威同步的载具坐标**过滤或重算，绕开 SparkCore 的 `Attenuation.NONE`），并撤掉这个诊断 mixin。另外如果客户端启动时报 `VehicleSoundDebugMixin` 注入失败，把报错发我（正常情况下不会，SparkCore 用的是同一种注入）。

# 

```
完全没有触发你的cgcc日志
A玩家为XiaoColorful，电脑关闭音量，B电脑为XColorful，在听到声音后，我用XiaoColorful发数字消息，结果这期间没有log
我搜索了cgccmachinemaxclient.mixins，相关日志如下：

[14Sep2026 23:15:02.513] [Render thread/DEBUG] [mixin/]: Mixing sound.SoundEngineMixin from spark_core.mixins.json into net.minecraft.client.sounds.SoundEngine
[14Sep2026 23:15:02.523] [Render thread/DEBUG] [mixin/]: Mixing sound.VehicleSoundDebugMixin from cgccmachinemaxclient.mixins.json into net.minecraft.client.sounds.SoundEngine
[14Sep2026 23:15:02.523] [Render thread/DEBUG] [mixin/]: Renaming synthetic method lambda$cgcc$logVehicleSound$0(Ljava/lang/String;)Ljava/util/concurrent/atomic/AtomicInteger; to md2b8ccc$cgccmachinemax$lambda$cgcc$logVehicleSound$0$0 in cgccmachinemaxclient.mixins.json:sound.VehicleSoundDebugMixin from mod cgccmachinemax
[14Sep2026 23:15:02.532] [Render thread/DEBUG] [mixin/]: spark_core.mixins.json:sound.SoundEngineMixin from mod spark_core->@Inject::spark_core$tickSpreadingSounds(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V doesn't use it's CallbackInfo
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: spark_core.mixins.json:sound.SoundEngineMixin from mod spark_core->@Inject::spark_core$tickSpreadingSounds(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V has 0 override(s) in child classes
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: spark_core.mixins.json:sound.SoundEngineMixin from mod spark_core->@Inject::spark_core$tickSpreadingSounds(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V won't be passed a CallbackInfo as a result
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: spark_core.mixins.json:sound.SoundEngineMixin from mod spark_core->@Inject::calculatePitch(Lnet/minecraft/client/resources/sounds/SoundInstance;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V does use it's CallbackInfoReturnable
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: spark_core.mixins.json:sound.SoundEngineMixin from mod spark_core->@Inject::calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V does use it's CallbackInfoReturnable
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: spark_core.mixins.json:sound.SoundEngineMixin from mod spark_core->@Inject::calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V does use it's CallbackInfoReturnable
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: cgccmachinemaxclient.mixins.json:sound.VehicleSoundDebugMixin from mod cgccmachinemax->@Inject::cgcc$logVehicleSound(Lnet/minecraft/client/resources/sounds/SoundInstance;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V does use it's CallbackInfoReturnable
[14Sep2026 23:15:02.533] [Render thread/DEBUG] [mixin/]: cgccmachinemaxclient.mixins.json:sound.VehicleSoundDebugMixin from mod cgccmachinemax->@Inject::cgcc$logVehicleSound(Lnet/minecraft/client/resources/sounds/SoundInstance;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V does use it's CallbackInfoReturnable
[14Sep2026 23:15:03.019] [Render thread/DEBUG] [mixin/]: Mixing renderer.PartEntityRendererMixin from cgccmachinemaxclient.mixins.json into io.github.sweetzonzi.machine_max.client.render.renderer.PartEntityRenderer
[14Sep2026 23:15:03.021] [Render thread/DEBUG] [mixin/]: cgccmachinemaxclient.mixins.json:renderer.PartEntityRendererMixin from mod cgccmachinemax->@Inject::cgcc$shouldRenderWithoutDistanceLimit(Lio/github/sweetzonzi/machine_max/common/entity/MMPartEntity;Lnet/minecraft/client/renderer/culling/Frustum;DDDLorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V does use it's CallbackInfoReturnable

[14Sep2026 23:15:40.307] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [System] [CHAT] 已将自己的游戏模式设置为旁观模式
[14Sep2026 23:15:41.210] [Render thread/WARN] [星火核心//]: Client PhysicsThread tick 171 overloaded, last tick time: 117ms, speed: 100%, rigid body in world: 21 with 物理区块: 25, Section总数: 600, 活跃Section: 8, while 547 chunks loaded.
[14Sep2026 23:15:42.352] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [System] [CHAT] 已将自己的游戏模式设置为创造模式
[14Sep2026 23:15:49.189] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 1
[14Sep2026 23:16:01.997] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 2
[14Sep2026 23:16:05.211] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 3
[14Sep2026 23:16:08.796] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 4
[14Sep2026 23:16:12.798] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 5
[14Sep2026 23:16:20.430] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [System] [CHAT] [XiaoColorful: 正在关闭服务器]
[14Sep2026 23:16:21.985] [Render thread/WARN] [net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl/]: Client disconnected with reason: 服务器已关闭
[14Sep2026 23:16:24.977] [Render thread/INFO] [net.minecraft.client.Minecraft/]: Stopping!
```

# 

The mixin applied — but you grepped for the config name, and my log tag is `[cgcc-sound]`. Let me make the diagnostic much easier to catch.

诊断 mixin 确实生效了（你贴的日志里 `Mixing sound.VehicleSoundDebugMixin ... into ... SoundEngine`），问题出在两处，都很好修：

1. **搜索词不对**：我的日志标签是 **`[cgcc-sound]`**，`cgccmachinemaxclient.mixins` 只会匹配到 mixin 装载那几行。请改搜 `cgcc-sound`。
2. **这份日志是 A 的客户端（XiaoColorful，正在关服、发 1~5 的那个）**。要定位「B 为什么能听到」，最关键的是 **B（XColorful）客户端的日志**。而且反过来看：如果那些音效在 A 自己的客户端也会播，A 的日志里就应该有 `[cgcc-sound]` —— **若 A 一条都没有、B 却一堆，这个不对称本身就是决定性线索**（说明音效只在「旁观这份载具的客户端」上产生）。

我把诊断改成更容易抓了（已重新打包，23:23）：

- 文件：[VehicleSoundDebugMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/VehicleSoundDebugMixin.java)
- 产物：`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`
- 现在 **每个音效实例「开始播放」时必定记一条**（`phase=START`，不节流），另外距离衰减每 tick 重算时按实例每秒最多一条（`phase=tick` / `phase=BEYOND`），并且「距离超过 maxRange 却仍在算音量」的首次出现一定记。每条都带**游戏刻**和**本地玩家坐标**，方便和你的触发动作对齐。

## 请这样跑一次

1. 新 jar 装到**两台客户端**（服务端也可一起换，不影响）。
2. B 站在离 A 1000 格以上，A 依次做：放置载具 → 上车 → 切座位 → 起步/行驶 → 车身倾斜回正 → 换挡 → 拉手刹停下；**先让日志开始写再做这些动作**（这样 `START` 行会落在同一个时间窗里）。
3. 把两台客户端 `logs/latest.log` 里所有含 `cgcc-sound` 的行发我（或直接把日志文件给我）。A 那边如果是空的，也明确告诉我「A 空、B 有」。

判读表（拿到就能定位）：

- `dist` 只有几十格 → 声源坐标被放到了听者身边，问题在「谁给了这个坐标」；
- `dist` 上千、`beyondRange=true` 而 `engineVolume` 仍 > 0 → **距离衰减没生效**，我在 compat 里按权威坐标硬过滤/重算音量即可；
- 只有 `START` 没有后续 `tick` → 音效实例根本没进入每 tick 重算（另一类失效路径，也能据此修）。

顺带一提，从你这份日志我另外确认了两件事：SparkCore 的 `SoundEngineMixin` 与我的诊断 mixin 都正常注入（所以客户端的可观测性没问题）；以及客户端确实在本地跑载具物理（`Client PhysicsThread tick ... 21 rigid body`），符合「每个客户端各持一份载具副本、各播一份音」的模型。

如果暂时不方便重建客户端，也可以先用免重编译的判别法：B 在能听到时依次静音「方块 / 中立生物 / 玩家」三个音效分类，告诉我哪个一关就安静了（刮擦摩擦音走 `Blocks`、子系统启停走 `Neutral`、引擎循环走 `Players`），这能直接把范围锁到某一条代码路径。

# 

```
我上一个对话发的日志就是B（XColorful）的

现在编译后重新跑一边，在D:\Temp-File\debug-A.log是A（XiaoColorful）的日志，A开载具，有cgcc log，windows已经静音防止误听；D:\Temp-File\debug-B.log是B（XColorful）的日志，windows声音开着，无log
在听到声音后，我用A发送数字消息
注意不要一次性读取全部日志，不然上下文窗口会瞬间满
```

# 

A has logs, B has none — that's decisive: B's client plays **no** SparkCore sound, so what B hears isn't MM's vehicle audio. Let me look at a few of A's lines (bounded) and then extend the probe to catch *all* sounds.

A plays the engine/tire sounds correctly at ~2 blocks. B logs nothing — so B hears a *vanilla* sound. Let me check the `playLocalSound` call sites' volumes (a huge volume extends linear attenuation range).

A plays the vehicle sounds correctly (2 blocks away), B plays none — so B's sound comes from a path outside the vehicle audio. Let me widen the probe to catch *every* sound B's client plays.

B 的日志是 0 条——这个结果非常关键，我重新判读一下。

## 从这两份日志能确定什么

- **A 客户端（有 217 条）**：`phase=START` 是 7 条 `machine_max:subsystem.engine.i4_*`（引擎各转速档），随后是 `part.collision.terrain.tire_concrete`（轮胎摩擦）。声源坐标 `(-2160.6, 24.3, -1917.4)` 距离听者只有 **1~3 格** ✔。也就是说 MM/SparkCore 的音效系统在 A 这边**完全按设计工作**。
- **B 客户端（0 条）**：B 的客户端**从头到尾没有播放过任何 SparkCore 扩散音效**。而 MM 的载具音效（引擎、启停、碰撞摩擦、连接件、零件摧毁）**全部**走 SparkCore 这一条路——我已经同时挂了 `play` 和 `calculateVolume` 两个点，只要播放就必留痕。

⇒ **结论：B 听到的声音不是 MM 载具自己的音效。** 由于 B 在 1000 格外，SparkCore 那套距离判定正确地让 B 什么都不播（这反而证明我上一轮怀疑的「衰减失效」不成立）。B 听到的是**另一个来源**的声音，而它只在同维度出现 ⇒ 只可能是：某个客户端侧代码/另一个 mod，针对载具状态播了一个音（或一个 `relative`/媒体类声音），**或者干脆绕过了 Minecraft 音频系统**。

## 我把探针加宽了（已重打包，23:33）

`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`

- `phase=PLAY`：现在**任何**音效实例被播放都会记一条，含**实例类名**（谁造的）、音效名、声源坐标、听者坐标、本地玩家坐标、距离、`relative`、衰减模式、音量、音源分类、游戏刻。每个「类名+音效名」最多 3 条、全局上限 400 条，跳过音乐/唱片/环境音，不会刷屏。
- `phase=START` / `tick` / `BEYOND`：SparkCore 那几条保留，方便和 A 对比。

装到两台客户端，重跑同样的复现（放置 → 上车 → 切座位 → 起步/行驶 → 倾斜 → 换挡 → 拉手刹停）。这次 B 的日志里会出现那个「神秘声音」的**类名与音效名**，基本一眼就能定位是谁播的。

## 同时请顺手做两个免重编译的判断（能提前定死方向）

1. **在游戏内把主音量（Master）拉到 0**，再让 B 听：
   - 还有声音 → 说明它**绕过了 Minecraft 音频系统**（某个 mod 直接开 Java/系统音频，比如 `javax.sound.sampled` 那类），那就跟 MM/SparkCore 的音效代码无关，直接去查那个 mod；
   - 没声音了 → 走的是 Minecraft 音频，等 `phase=PLAY` 的日志点名即可。
2. **依次静音「方块 / 中立生物 / 玩家」分类**：告诉我哪个一关就安静（`Blocks`=摩擦刮擦、`Neutral`=子系统启停/换挡/刹车、`Players`=引擎循环/武器），能把范围再收一半。

另外顺带确认一件事：B 听到的声音**音量是否随距离变化**（1000 格 vs 100 格 vs 10 格）。恒定不变 → 说明它压根没做距离衰减（或 `relative`/媒体类），这也和「不是载具位置音」互相印证。

把 B 的新日志里含 `cgcc-sound` 的行发我（有上限，不会长），我就能给出正式修法。

# 

```
你现在改的这个版本，专用服务端启动了，客户端点击完MachineMax开屏界面的“确定”按钮就立即崩了，A电脑的崩溃日志在D:\Temp-File\crash-2026-09-14_23.36.10-client.txt
```

# 

我这边的问题，抱歉——`phase=PLAY` 里我调了 `sound.getVolume()`，而这个方法在音效**解析前**会 NPE（按钮点击音效就是在解析前播放的，所以你一点「确定」就崩）。诊断代码绝不该让游戏崩，我马上修掉。

崩溃是我诊断代码的 bug，跟正式修复无关——抱歉。原因和修法：

- 我在 `SoundEngine#play` **入口**读了 `sound.getVolume()`，而 `AbstractSoundInstance.getVolume()` 内部要读**已解析**的 `Sound`；入口处还没 `resolve`，所以是 NPE。你点的正是「确定」按钮 → `AbstractWidget.playDownSound` → 按钮音效，于是必崩。
- 已修（新包 **23:38**）：入口处不再读音量，只读字段级安全信息（实例类名、音效名、坐标、`relative`、衰减模式、音源分类、游戏刻）；SparkCore 实例有自己实现的 `getVolume()` 才读。并且**全部诊断逻辑都包了 try/catch**，再出错只写一条 warn，绝不影响游戏。
- 服务端那三个 mixin（座位/输入回声）没有变动，诊断只加在客户端。

新产物：`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`（23:38）

请两台客户端换上再跑一次同样的复现。这次 B 的日志里应该会出现 `phase=PLAY` 的行，里面带**实例类名**——那基本就是「谁在播这个声音」的答案（例如 `SimpleSoundInstance` / `ResourceSoundInstance`(CGC) / 别的 mod 自己的类，以及它的音效名和坐标）。

顺便，上一轮那两个免重编译判断仍然很有价值，建议这次一起做掉：

1. **游戏内主音量拉到 0**：B 还能听到 → 声音绕过了 Minecraft 音频系统（某个 mod 直接开系统音频），跟 MM/SparkCore 音效代码无关；听不到了 → 等 `phase=PLAY` 点名。
2. **依次静音「方块 / 中立生物 / 玩家」分类**，告诉我哪个一关就安静。

把 B 的新日志里 `cgcc-sound` 的行（有上限，不会长）发我就行。

# 

```
B有日志了：
[14Sep2026 23:44:37.104] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169544 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.clutch_out src=(-2160.968505859375, 23.737289428710938, -1917.4049072265625) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2889.722835262115 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:38.791] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 1
[14Sep2026 23:44:39.457] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169591 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.clutch_in src=(-2161.043212890625, 23.73725128173828, -1917.4061279296875) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2889.779525751501 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:40.980] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 2
[14Sep2026 23:44:43.060] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169663 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.clutch_out src=(-2170.838623046875, 23.735319137573242, -1913.1322021484375) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2894.2861650835516 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:43.060] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169663 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.up.light src=(-2170.838623046875, 23.735319137573242, -1913.1322021484375) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2894.2861650835516 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:43.299] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169668 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.clutch_in src=(-2173.111572265625, 23.5655460357666, -1912.0230712890625) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2895.259797146537 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:43.799] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169678 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.clutch_out src=(-2178.12353515625, 22.84592628479004, -1909.930419921875) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2897.647868625577 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:43.800] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169678 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.up.light src=(-2178.12353515625, 22.84592628479004, -1909.930419921875) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2897.647868625577 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:44.005] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169682 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.clutch_in src=(-2180.386474609375, 22.650632858276367, -1909.1663818359375) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2898.847285904807 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:44.462] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 3
[14Sep2026 23:44:45.953] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169721 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.down.light src=(-2203.680908203125, 21.504817962646484, -1902.2861328125) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2911.9183097056675 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:46.953] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169741 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.up.light src=(-2215.151611328125, 19.993896484375, -1899.4935302734375) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2918.800808103038 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:47.700] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169756 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.down.light src=(-2223.818359375, 19.021930694580078, -1897.2530517578125) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2923.9365070887598 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:48.151] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 4
[14Sep2026 23:44:49.254] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=169787 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=machine_max:subsystem.gearbox.down.light src=(-2239.43212890625, 18.749195098876953, -1893.154541015625) listener=(0.5, 37.61999988555908, 0.5) player=(0.5, 36.0, 0.5) dist=2933.185907863475 relative=false attenuation=LINEAR category=NEUTRAL
[14Sep2026 23:44:50.655] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [CHAT] <XiaoColorful> 5
[14Sep2026 23:44:53.446] [Render thread/INFO] [net.minecraft.client.gui.components.ChatComponent/]: [System] [CHAT] [XiaoColorful: 正在关闭服务器]
[14Sep2026 23:44:55.188] [Render thread/WARN] [net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl/]: Client disconnected with reason: 服务器已关闭
[14Sep2026 23:44:57.086] [Render thread/INFO] [cgcc-sound-debug/]: [cgcc-sound] phase=PLAY tick=0 class=net.minecraft.client.resources.sounds.SimpleSoundInstance name=minecraft:ui.button.click src=(0.0, 0.0, 0.0) listener=(0.5, 37.61999988555908, 0.5) player=(0.0, 0.0, 0.0) dist=37.626644700125276 relative=true attenuation=NONE category=MASTER
[14Sep2026 23:44:57.832] [Render thread/INFO] [net.minecraft.client.Minecraft/]: Stopping!
```

# 

**抓到了！** B 那边唯一被播放的声音是：

```
class=SimpleSoundInstance name=machine_max:subsystem.gearbox.{clutch_in,clutch_out,up.light,down.light}
src=(-2160,23.7,-1917)≈A的车  listener=(0.5,37.6,0.5)=B  dist=2889  relative=false attenuation=LINEAR category=NEUTRAL
```

也就是**原版路径的变速箱/离合器音效**（`GearboxSubsystem` 用 `Level.playLocalSound` 播的），不是 SparkCore 那套。让我确认它为什么能传到 2889 格。

`Sound`'s `attenuation_distance` 默认 16，而 `Channel.linearAttenuation` 用的是 `AL_LINEAR_DISTANCE_CLAMPED` + `AL_MAX_DISTANCE=16` —— 按数学这些变速箱音效在 2894 格外**必须是 0 音量**。所以要么 B 听到的其实是别的声音（我的探针有盲点：同一音效名只记前 3 次、且跳过 MUSIC/RECORDS/AMBIENT 分类），要么客户端解析到的 `attenuation_distance` 被谁改大了。我把探针盲点去掉。

B 的日志给出了决定性信息——但方向和我上一轮的猜测不同：

## B 的客户端到底尝试播了什么

唯一被*尝试*播放的是 `machine_max:subsystem.gearbox.*`（`clutch_in/clutch_out`、`up/down.light`），实例类是**原版 `SimpleSoundInstance`**、`attenuation=LINEAR`、坐标就是 A 的车（离 B 2894 格）、分类 `NEUTRAL`。**B 的客户端从头到尾没有播放任何一个 SparkCore 载具音效**（0 条 `START`/`BEYOND`）——说明引擎、启停、摩擦这些音效在 B 这边被距离判定正确拦掉了 ✔（我上一轮怀疑的「衰减失效」不成立）。

## 但这里有个矛盾，需要你去实测一下

按原版实现，`Channel.linearAttenuation` 会把音源设成 `AL_LINEAR_DISTANCE_CLAMPED`，`AL_MAX_DISTANCE = max(volume,1) × Sound.getAttenuationDistance()`，而 `attenuation_distance` 的默认值是 **16**（我在 1.21.1 源码里核过：`SoundEventRegistrationSerializer:62`）。最大距离 16 时，2894 格外的增益是**精确的 0**，数学上不可能听见。

所以只剩两种可能，而这两个测试不用重编译就能分辨：

1. **B 在游戏内把「中立生物(NEUTRAL)」分类拉到 0**（那些变速箱/离合音效正好是这个分类）：
   - 声音没了 → B 听到的**就是**这些变速箱音效 ⇒ 说明客户端解析到的 `attenuation_distance` 不是 16（客户端资源栈里有人给 `machine_max` 的音效写了超大的 `attenuation_distance`，或者把 pack 当资源包加载了）⇒ 我据此在 compat 里把 MM 音效的衰减距离钳住即可；
   - 还有声音 → B 听到的是**别的东西**，继续下面第 2 条。
2. **主音量(Master)拉到 0**：还有声音 → 它**绕过了 Minecraft 音频系统**（某个 mod 直接开系统音频），与 MM/SparkCore 音效代码无关；没声音了 → 还是走原版音频，等新探针点名。
3. 顺手：在 B 的日志里搜一行 `Unable to play`（原版解析不到声音事件时会打 `Unable to play unknown soundEvent: machine_max:subsystem.gearbox...`）。如果**有**这行，说明这些变速箱音效其实**根本没能解析、压根没响**，那 B 听到的一定是别的声音。

## 探针已去掉两个盲点（新包 23:48）

`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`

- 不再跳过 MUSIC/RECORDS/AMBIENT 分类；
- **不再按「前 3 次」封顶**——改成同一「类名+音效名」每秒最多一条，所以反复播放的声音每次窗口都会留痕（这正是上一版可能漏掉真凶的地方）；
- 记录条件：**非 `minecraft` 命名空间的音效一律记**，原版的只在「距离 > 500 格 / relative / 唱片机」时记；全局上限 2000 条。

装上后重跑同样的复现（A：放置 → 上车 → 切座位 → 起步 → 倾斜 → 换挡 → 拉手刹停），把 B 的新 `cgcc-sound` 行 + 上面两个静音测试的结果发我。这次要么点出真凶的类名/音效名，要么确认「绕过 MC 音频」这条路。

# 

```
B的log在D:\Temp-File\debug.log，关闭MC主音量后听不见，关闭友好生物（Friendly creatures）后听不见，开启友好生物声音后听见
```

# 

**破案了！** 你用「中立生物(NEUTRAL)」静音后听不到、主音量拉 0 也听不到 ⇒ B 听到的就是 `SoundSource.NEUTRAL` 分类、走**原版**音频的那批声音 = `machine_max:subsystem.gearbox.*`（变速箱/离合，由 `GearboxSubsystem`/`CarControllerSubsystem`/`WheelDriverSubsystem` 用 `Level.playLocalSound` 播放）。

也就是说：这些「咔哒」类音效依赖**原版线性衰减**，在你的音频环境里衰减到尾音仍可闻（1/d 尾巴），于是几千格外还能听见——而引擎等走 SparkCore 的音效被正确拦掉了。修法就是给这些原版路径的音效补上**硬距离门**（按音效自己声明的 range，MM 里这些都是 16 格）。

两个测试把根因钉死了：**主音量 0 听不到 ⇒ 走的是 Minecraft 音频；中立生物(NEUTRAL) 静音后听不到 ⇒ 它是 `NEUTRAL` 分类**。而 B 日志里唯一的 `NEUTRAL` 声音就是 `machine_max:subsystem.gearbox.*` —— 也就是 `GearboxSubsystem` / `CarControllerSubsystem` / `WheelDriverSubsystem` 用 **原版 `Level.playLocalSound`** 播的那批变速箱/离合/刹车「咔哒」声，在 2894 格外仍能听见。

**为什么只有它们漏过来**：这批音效把距离完全交给原版线性衰减（`Channel.linearAttenuation` → `AL_MAX_DISTANCE = max(volume,1)×Sound.getAttenuationDistance()`，`attenuation_distance` 默认 16）。在你的音频环境里这条衰减收不到 0（远距离留了尾巴），于是几千格外仍是「很轻但听得到」——这正是最初「载具声音所有人都能听到、即使隔了几千格」的那个症状。而引擎/摩擦等走 SparkCore 的音效被正确拦掉了（B 日志 0 条），所以问题范围就是这三个子系统的原版音效。

## 修复：给这些音效加硬距离门（客户端侧，新包 00:02）

- 新增 [LocalSoundRange.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/util/LocalSoundRange.java)：按**音效自己声明的传播范围**（这些在 MM/内容包里都是 16 格）判断本机玩家是否在范围内；被拦下时打一条 `[cgcc-sound] GATED name=... dist=... range=...`，方便确认门限真的生效、并顺便暴露客户端解析出的范围。
- 新增三个 mixin，逐点拦住 `playLocalSound` 调用（已按字节码核对过调用点数量与次序）：
   - [GearboxSubsystemSoundMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/GearboxSubsystemSoundMixin.java)（4 处，各在独立 lambda，整类重定向）
   - [CarControllerSubsystemSoundMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/CarControllerSubsystemSoundMixin.java)（`onTick` 2 处，ordinal 0/1）
   - [WheelDriverSubsystemSoundMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/WheelDriverSubsystemSoundMixin.java)（`onTick` 2 处，ordinal 0/1）
- 产物：`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`
- 驾驶员本人不受影响：这些声音就在他脚下（距离 0），门限必然放行 ✔。

## 请再跑一次确认

两台客户端换上新包，复现同样动作。预期：

- **B 听不到那些咔哒声了**；
- B 的日志里出现 `GATED name=machine_max:subsystem.gearbox... dist=2894 range=16` —— 门限生效的证据；若这里打印的 `range` 明显大得离谱，说明客户端资源里有人给这些音效写了超大范围，我再顺手钳住；
- A 侧近距音效照常（仍会有 `phase=PLAY` 与 `GATED` 混合）。

确认后我会把临时诊断（`VehicleSoundDebugMixin` 和 `GATED` 日志）删掉，只留门限。另外两点补充：

1. 这次的修法是**绕过**原版衰减机制（不去纠结 OpenAL 那套尾巴），只对 MM 的这三个子系统的音效生效，不动原版和其它 mod 的声音。如果你发现别的 mod 的原版路径音效也在远处漏声，我可以把门限提升到 `Level.playLocalSound` 那一层（会影响全局，所以默认没这么做）。
2. 更「正统」的修法在 MachineMax 一侧：把这些音效也改走它自己的距离管理音效系统（就像引擎那样），我这边是兼容模组，只能做门限补偿。

# 

```
你刚才修改的版本，B照样听到声音，日志已经放在Temp-File里两个log了
```

# 

B 的日志现在是 **0 条** `cgcc-sound`（连 `GATED` 都没有）。让我看看 mixin 有没有真的应用、以及 A 那边发生了什么。

关键线索出来了：**B 站在世界原点**（`player=(0.5, 36.0, 0.5)`，上一次也是），而我的探针按「命名空间 + 距离」过滤——**播在 (0,0,0)/B 脚边的原版声音恰好被漏掉**。同时 A 的日志显示这次 `phase=PLAY=0`、`GATED=0`，即这次运行**根本没触发变速箱音效**（三个门限 mixin 已确认应用 ✔）。我把过滤彻底去掉。

这次日志里有两条关键信息：

## 1. B 一直站在**世界原点**

两次运行 B 的坐标都是 `player=(0.5, 36.0, 0.5)`、`listener=(0.5, 37.6, 0.5)`。而我上一版探针按「命名空间 + 距离」过滤——**「原版命名空间 + 离听者很近」的声音恰好被排除**，而「有人把声音播在 (0,0,0)/B 脚边」看起来就长这样。也就是说真凶可能一直被我的过滤条件挡在日志外。

## 2. 这次三个门限 mixin 应用了，但没被触发

- 两台客户端都有 `Mixing sound.GearboxSubsystemSoundMixin / CarControllerSubsystemSoundMixin / WheelDriverSubsystemSoundMixin` ✔
- 但 A 侧 `phase=PLAY=0`、`GATED=0`，B 侧 `cgcc-sound` 全 0 ⇒ **这次运行根本没触发变速箱/离合/手刹/轮刹音效**（上次那批 gearbox 行是它们唯一会被门限拦下的东西）。所以这次「B 照样听到」是**另一个触发点**。

## 已改：探针不再做任何过滤（新包 00:10）

`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`

现在 `phase=PLAY` 会记录**每一个**被播放的音效（不管命名空间、不管距离），同一「类名+音效名」每秒最多一条、全局上限 2 万条。B 站着不动声音不多，日志不会爆。

## 请做两件事

1. **重跑复现，并且这次一定要有换挡**：起步 → 逐级升挡 → 降挡 → 刹停（让门限被真正走到一次）。
2. **把 B 从原点挪走**再听一次，例如：
   ```
   /tp XColorful 2000 70 2000
   ```
   - 挪走后**听不到了** ⇒ 说明有人把声音播在了 (0,0,0) 附近，我直接去找那个调用点；
   - 挪走后**照样跟着听到** ⇒ 说明是「播在 B 自己身上 / relative」的声音，新探针会点名。

然后把 B 的 `[cgcc-sound]` 行发我（这次会有很多原版环境音，正常的；我要找的是 `machine_max` / `spark_core` / 其它 mod 命名空间、或者 `src` 看起来不对的，比如 `src=(0.0, 0.0, 0.0)`）。

# 

```
把B挪开原点之后依然能听到，日志还是在Temp-File下两个文件。
由于上下文长度限制，如果本次还没修复，也需要做说明，我会新开一个对话并让其读取历史对话来接着修bug
```

# 

B 的日志这次**一条都没有**（即使探针已不做任何过滤）。让我确认 B 客户端到底装没装、有没有 mixin 报错。

两个都是我这次的 bug，正好互相掩盖了，我找到了：

1. **门限 mixin 根本没生效**：`@At` 的 target 我多打了一个冒号（`...Level;playLocalSound:(...)V`），Mixin 解析成「字段描述符」→ `Invalid name: playLocalSound:` → **注入失败**（日志里是 `Mixin apply for mod cgccmachinemax failed ... CarControllerSubsystemSoundMixin/GearboxSubsystemSoundMixin`，只 WARN 不崩）。所以上一次「B 照样听到」其实还是**没被修过的**变速箱音效。
2. **探针这次全哑**：节流用的哨兵值 `Long.MIN_VALUE` 让 `now - state[0]` **溢出成负数**，于是「首次必记」的判断永远成立地返回 ⇒ 一条都不打（这也解释了 `phase=tick/BEYOND` 从来是 0）。

两个都修掉。

两个 bug 都是我这边的，已修好并重新打包（**00:18**）：

- **门限没生效**：`@At` 的 target 我写成了 `Lnet/minecraft/world/level/Level;playLocalSound:(...)V`（方法名后多一个冒号），Mixin 当成字段描述符解析 → `Invalid name: playLocalSound:` → 三个门限 mixin 注入失败。而本项目的 mixin 配置没设 `required`，失败**只 WARN 不崩**，所以表面上什么都没发生。已改成 `Level;playLocalSound(DDD...)V`（5 处全部核对过）。
- **探针全哑**：节流用 `Long.MIN_VALUE` 当「未记录」哨兵，`now - MIN_VALUE` 溢出成负数 ⇒ 首次必记的判断永远走 return ⇒ 一条日志都没有（`phase=tick/BEYOND` 从来是 0 也是这个原因）。改成 `-1L` 哨兵。

产物：`D:\Github\CGC-Compat-MachineMax\neoforge-compat\build\libs\cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar`

## 请再测一次（这次重点是看日志里的三条）

1. 两台客户端换上新包，A 驾驶并**明确换挡**（起步→升挡→降挡→刹停），B 在 1000+ 格外听。
2. 在**客户端**日志里搜这四个关键词：
   - `Mixin apply for mod cgccmachinemax failed` → **必须为 0 条**（有就说明又没注入，把那条贴我）；
   - `GATED name=machine_max:subsystem.gearbox` → 应出现，`range=16`、`dist=` 几千 —— 门限生效的证据；
   - `phase=PLAY` → 若 B 还能听到，这次它会列出 B 播放的每个音效（类名+音效名+坐标）；
   - `phase=START` → B 侧应基本为 0（SparkCore 载具音效本来就该被距离拦掉）。

---

## 交接说明（给下一个对话）

**已修好并经验证**：座位重复占用 / 旁观者视角被拉进车 / 一人下车两人同下 / 未入座也能开车。根因：MachineMax 把客户端上行包在服务端处理后 `sendToPlayersInDimension` 全维度广播、各客户端拿自己的本地玩家重放；已在 `core/mixin/network/` 三个 mixin 里收窄回声 + 服务端校验发送者 + 座位被占用时不回声。

**音效（本轮）**：根因已定位 —— MM 的变速箱/离合/手刹/轮刹走**原版 `Level.playLocalSound`**（`SoundSource.NEUTRAL`，`attenuation_distance` 默认 16），在你的音频环境下衰减收不到 0，几千格外仍有尾巴（引擎等 SparkCore 音效反而被正确静音，B 侧 0 条）。修复 = 客户端硬距离门：`client/util/LocalSoundRange.java` + `client/mixin/sound/{Gearbox,CarController,WheelDriver}SubsystemSoundMixin.java`（按音效自身 `range` 判定，驾驶员距离 0 不受影响）。**本轮修复是否生效尚未验证**（上一轮因注入失败而未生效）。

**发布前必须删除的临时诊断**：`client/mixin/sound/VehicleSoundDebugMixin.java`、`cgccmachinemaxclient.mixins.json` 里的对应条目、`LocalSoundRange` 里的 `GATED` 日志。

**若 B 仍能听到**：看 B 日志 `phase=PLAY` 的 `name=` 与 `src=`（特别留意 `src=(0.0,0.0,0.0)` 或与玩家同坐标的行）；若命中的仍是 gear/handbrake/wheel，看 `GATED` 有没有出现、`range=` 打了多少。备选方案：把门限提升到 `Level.playLocalSound` 全局层（影响原版/其它 mod，默认没做），或建议 MM 侧把这些音效改走它自己的距离管理音效系统。另外两处刻意未改：`MovementInputPayload` 回声仍是全维度广播（只加了发送者校验）、`ScriptablePayload` 仍全维度广播（无脚本时无害）。

**环境/构建**：测试服 `E:\Software\Minecraft\neoforge1.21.1server`（MachineMax 1.0.1-beta.1、SparkCore 1.0.1023、customgun 0.0.14）；A=XiaoColorful、B=XColorful；日志 `D:\Temp-File\debug-{A,B}.log`。构建必须显式指定工具链：

```bash
./gradlew :neoforge-compat:jar -Dorg.gradle.java.installations.paths=E:/Software/Minecraft/JDK/jdk-21.0.8
```

我已把这些写进项目记忆（`cgc-machinemax-multiplayer-fix.md`，并加到 `MEMORY.md` 索引），新开对话在本项目里会**自动读到**，不必依赖历史对话。改动仍**未提交**（按你的规矩等你 review）。

# 
---

```
本次对话和要求接着CGC compat的./docs/deprecated/prompt/#7 multiplayer-fix.md

目前最新版测试下，B已经听不到数千格外的载具声音了，日志已经放在Temp-File\下两个log，如果没有问题，就把当前git的日志代码给去了
```

# 

I'll pick up from the prompt file and memory context.

Fix verified in both logs. Now reading the code to strip the diagnostics.

Verified clean. Now stripping the diagnostics.

Diagnostics are out and the rebuilt jar is clean. Updating project memory to reflect the verified state.

已核完日志，修复确认有效，诊断代码已全部移除并重新打包。

## 日志结论（`D:\Temp-File\debug-A.log` / `debug-B.log`）

**B 在 3600 格外（关键判据）**
- B 侧 15 条 `GATED`，全部是 `machine_max:subsystem.gearbox.{clutch_in,clutch_out,up.light,down.light}` 与 `car_controller.handbrake_on`，形如 `dist=3603.7 range=16.0` —— 门限按音效自身声明范围正确拦下。
- B 侧 **0 条** `machine_max` 的 `phase=PLAY` —— 这些音效根本没被提交给音频引擎播放。
- B 侧 `phase=START` 的 21 条全是 `engine.i4_*`，位于 B 身边 17.5 格的**另一台载具**，且 `engineVolume=0.0`（SparkCore 音量算成 0），与 A 的车无关 ✔

**A（驾驶员）不受影响**
- 64 条 `machine_max` 音效**全部** `dist<20` 正常播放（引擎、离合、手刹、轮刹、碰撞摩擦）。
- A 侧另有 9 条 `GATED`，`dist=114/127/217/238` —— 是附近**其它载具**（B 那台等）的音效，A 本来就不该听见，正是在被正确拦下，不是误伤。

**健康检查**：两端 `Mixin apply for mod cgccmachinemax failed` 均为 **0 条**。

## 已移除的诊断代码

| 动作 | 文件 |
|---|---|
| 删除 | `client/mixin/sound/VehicleSoundDebugMixin.java` |
| 去掉条目 | [cgccmachinemaxclient.mixins.json](core/src/main/resources/cgccmachinemaxclient.mixins.json) 里的 `sound.VehicleSoundDebugMixin` |
| 去掉日志 | [LocalSoundRange.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/util/LocalSoundRange.java) 里的 `LOGGER` / `GATE_LOG_COUNTS` / `cgcc$logGate` 及相应 import |

门限逻辑本身未动（只删了留痕部分）。重新构建通过，产物 [cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar](neoforge-compat/build/libs/cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar)（00:29）已核对：**不含** `VehicleSoundDebugMixin` 条目或类，`LocalSoundRange.class` 里也搜不到 `cgcc-sound` 字符串。

## 当前 git 状态（未提交，等你 review）

- **已提交**：`76f054e fix: Correct vehicle seat occupancy validation`（座位/输入回声那三个 `core/mixin/network/` mixin）
- **未提交**：音效门限修复（3 个 sound mixin + `LocalSoundRange` 为新文件，`cgccmachinemaxclient.mixins.json` 为修改）
- 另外 `docs/deprecated/prompt/#7 multiplayer-fix.md` 在工作区里是 modified 状态（前几轮追加的对话记录），我没动它——需要的话我可以把本轮结论也追加进去，或者把这个改动还原。

要我提交音效这部分吗？

# 

```
我已经提交刚才的代码状态，当前的修复方案是否是客户端本地过滤?是否能改成服务端就判定距离然后拦截发送，而不要等到客户端才过滤?
```

# 

好问题——让我先确认这几处音效的触发侧，再回答能否挪到服务端。

Let me verify what the server actually broadcasts for vehicles, and check the render-distance mixin.

核完了字节码，结论明确。先直接回答：**是客户端本地过滤；而且这套音效不存在可以「发送前拦截」的服务端声音包**。

## 为什么服务端没有可拦截的对象

| 音效 | 实际触发点（旧版 jar 字节码） |
|---|---|
| `gearbox.clutch_in/out`、`up/down.light` | 全在 `GearboxSubsystem` 的 `lambda$onSyncedDataUpdated$1..4` 里 —— **`onSyncedDataUpdated` 只在客户端被调用** |
| `car_controller.handbrake_on/off` | `CarControllerSubsystem.onTick` 内直接调 `Level.playLocalSound` |
| `wheel_driver.brake_on/off` | `WheelDriverSubsystem.onTick` 内同上 |

而 `Level.playLocalSound(DDD…)` 在 `Level.java:524` 是**空方法体**，只有 `ClientLevel` 覆盖它 —— 服务端调它等于什么都没做。所以这五个声音从头到尾没有服务端参与，也就没有「服务端发出去的声音包」可拦。

服务端真正发的只有**载具状态**，而且是无距离过滤的全维度广播：

```
AbstractSubsystem.syncToClient():
    getSynchedData().packDirty()  →  SubsystemSyncPayload(subPartId, name, dirty)
    PacketDistributor.sendToPlayersInDimension(serverLevel, payload)   // 整个类里 0 处距离判断
```

B 的客户端就是收到这条包 → 应用到本地副本 → 触发 `onSyncedDataUpdated` → **在本地**播了那个声音。距离从来没进入这条链路。

## 服务端方案存在，但不是「只拦声音」

技术上可行：mixin `AbstractSubsystem#syncToClient`（以及 `VehicleCore#syncStatusToClient`），改成只发给载具附近 N 格内的玩家。但代价不对等：

- **它拦的是状态，不是声音**。被拦掉的玩家拿不到挡位/离合/刹车/状态同步 → 车里轮子不转、挡位相关的表现停在旧值，而按本兼容模组的 `modifyRenderDistance`（默认 20 区块可见）这些玩家**本来是在看着这台车的**。
- **顺带也会杀掉引擎音**：引擎音是各客户端按引擎子系统状态自己建的，状态不发了声音自然也没了 —— 这次是对的，但超出了「过滤咔哒声」的范围。
- 一旦客户端状态不同步，本地物理副本还会各自漂移（MM 就是这么设计的：状态全量广播、各端自跑一份）。

换句话说，服务端只能决定「**谁收得到这台车**」，决定不了「**多远还能听见**」——距离衰减在 Minecraft 音频里本质是客户端计算，因为听者在客户端。MM 自己那套正确做法（SparkCore `SpreadingSoundInstance`，引擎音用的）同样是**每 tick 在客户端重算音量**，只是它把 `maxRange` 声明清楚了，所以 B 那边自动静音。

## 我的建议

保留客户端门限。它只做一次距离比较，音效实例根本不会被构造，没有额外开销，也不改变任何状态同步语义。真要把这段从兼容模组里去掉，正确的位置是上游 MachineMax：让这三个子系统也走 SparkCore 的距离管理音效（照 `EngineSubsystem` 那样发），那时这个 mixin 可以直接删掉——但那是改 MM 本体，不是兼容层能替代的。

如果你仍想验证服务端方案的观感差异，我可以在 `CgccMMConfig` 后面加一个默认关闭的开关，mixin `syncToClient` 做半径过滤，方便你实测对比——要的话我就动手。

# 

```
那就保留这个客户端侧计算距离的方案。

我现在需要增加两个兼容模组配置项，一个bool控制是否启用，一个int控制载具声音距离
- 根据之前的对话，好像载具声音是传播64距离，这个对于一些小游戏肯定是太近了，尤其是我还添加配置把渲染距离搞到20
- 不仅要能控制载具声音距离，比如设置声音距离到20，那么在最远320格处应该声音比较小，近距离时声音大（保留音量衰减），并且还要不干扰一些固定传播距离的声音，比如之前修复的B听到的器件声，这种非“竞技游戏中的信息性质”的声音就保留原本的短的传播距离即可。

现在开始完成新增的配置项。
```

# 

这个需求要动 SparkCore 的距离判定入口，我先把相关 jar 和调用路径摸清。

SparkCore is a compile dep. Inspecting its sound classes.

Now checking how SparkCore consumes `maxRange`, and which namespaces MM's vehicle sounds use.

设计上有三个点需要你定，其余我已从字节码核实：

- `SpreadingSoundInstance` 的 `maxRange` 就是在两个构造器里 `soundEvent.getRange(1.0f)` 赋的（字节码 offset 113/117，接收者是 MM 传入的那个 SoundEvent）；
- 音量公式是 `volume × (1 − min(dist/maxRange, 1))²`，所以改 `maxRange` 天然保留衰减、在边界处归零；
- 官方内容包实际范围分布：**72 处 64**（机械工作音）、**6 处 48**（玻璃/木头材质命中）、**5 处 16**（轮刹，走 `playLocalSound` 即已修复的那条）、**1 处 32**（default_glass）；工具音（撬棍/焊枪等 `item.*`）由代码硬编码 `createFixedRangeEvent(..., 32.0f)`。

用户的答复里「碰撞/命中保持原样」这一条需要我确认轮胎摩擦音在数据里的声明方式，先看材质配置。

我需要看清 `BasicSubsystem` 是怎么分别播「工作音」和「启停/破坏音」的，这决定了能不能精确排除破坏音。

在动手前还要确认一件事：SparkCore 是否用 AABB（而非 maxRange）提前拦掉远处的音源实例——否则只改 maxRange 没用。

设计确认可行：`maxRange` 同时决定「波前能传多远」（`tick()` 里 `spreadDistance > maxRange` 就丢弃声源点）和音量衰减，所以改它正好扩大了可听半径。开始实现。

配置项与 mixin 已实现、编译通过并打包。

## 改了什么

**配置（`CgccMMConfig` + `CgccMMConfigTag`，沿用 `modifyRenderDistance`/`renderDistance` 的命名与读写方式）**

| 键 | 类型 | 默认 | 含义 |
|---|---|---|---|
| `modifyVehicleSoundDistance` | bool | `true` | 是否接管载具音效传播距离 |
| `vehicleSoundDistance` | int | `20` | 载具音效传播距离，**单位为区块**（下限 1） |

两个默认值都与你现有的 `renderDistance: 20` 对齐，所以开箱即是「引擎声传满可见范围」。配置文件在客户端实例的 `config/cgccompatmachinemax.json`……更正：`config/cgccmachinemax.json`（`CgccMachineMax.FILE_NAME`），客户端/服务端各自加载，音效这部分只读客户端的值。

**新增 [SpreadingSoundInstanceRangeMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/SpreadingSoundInstanceRangeMixin.java)**，已注册进 `cgccmachinemaxclient.mixins.json`。

## 为什么钉在 `SpreadingSoundInstance` 上

从 SparkCore 1.0.1023 字节码核实：`maxRange` 在构造时由 `soundEvent.getRange(1.0f)` 赋值（两个构造器各一处，接收者就是 MM 传入的那个 SoundEvent），而它同时决定两件事——

- `spark_core$calculateVolume` 的音量：`volume × (1 − min(dist/maxRange, 1))²`；
- `tick()` 里声源点的存活：`spreadDistance > maxRange` 就丢弃该点（`spreadDistance` 按声速推进，`hasReachedListener` 要求 `dist ≤ spreadDistance`）。

所以它就是**可听半径本身**，改它既扩大半径又保留衰减曲线，近处音量不变。这也是为什么 B 在 3600 格外时客户端连 `SoundEngine.play` 都没进过（波前根本没推到那儿）。

只重定向这一处调用，`SoundEvent.getRange` 本身不动 —— 因此 `LocalSoundRange`（变速箱/手刹/轮刹那批 16 格音效）完全不受影响，两条机制彼此独立。

## 缩放范围（按你的答复）

**放大到 `vehicleSoundDistance`**：`machine_max:subsystem.*`（引擎/电机的启停音 + 各转速工作音）与 `machine_max:part.collision.terrain.tire_*`（轮胎摩擦、打滑）。

**保持原距离**：`part.collision.terrain.hard_*`（碰撞）、`part.torn_apart`（撕裂）、`hit_box.*`（命中）、`item.*`（撬棍/焊枪/喷漆等拆装工具）——这几类在数据里本来就是 64/48/32，现在原样保留；车辆附近的玩家照常能听到。

有一处我替你做了判断，请确认：**子系统破坏音也排除了**（官方包里是 `machine_max:subsystem.baisc.glass.break`，声明 32 格，比如玻璃件被打碎）。它是 `subsystem.*` 但不是"载具运转"信息，按你说的"听不到那么远的碎裂声"保留原距离。实现上是一句 `!path.endsWith(".break")`——这是个命名约定判断，如果你的内容包把破坏音起成别的名字，它会漏进来；不想要这条排除、或想换成别的规则，说一声我改。

另外提一句：配置里原本就有 `concurrentSoundLimitEnabled` / `maxConcurrentSounds` 两个字段，注释写着"当前未实现"，读写分支是注释掉的。我没动它们。

## 我没能验证的部分

**运行时行为我无法在此环境实测**——客户端 mixin 要在真实客户端里才加载。请两台客户端换上 [cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar](neoforge-compat/build/libs/cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar)（00:49），A 开远一点，在 B 的客户端日志里确认：

1. `Mixing sound.SpreadingSoundInstanceRangeMixin ... into cn.solarmoon.spark_core.sound.SpreadingSoundInstance` —— 必须出现；
2. `Mixin apply for mod cgccmachinemax failed` —— 必须为 0 条（这条注入失败在本项目只打 WARN 不崩，务必搜一次）；
3. 听感：A 驾驶时 B 在 300 格左右应能听到引擎与轮胎摩擦，且**随距离递减**、到 `vehicleSoundDistance × 16` 格处归零；B 靠近到 64 格内的音量应与改动前一致。

改动未提交，等你 review。

# 

```
检查一下，如果只有服务端设置了，客户端使用最新的jar是否仍然能正确计算距离，而不是使用本地的配置文件（不权威)？
```

# 

先核实这一点。

核实完了，**结论是：现在客户端完全不会用服务端的值。**

## 检查结果

- 音效距离是在客户端读的：`SpreadingSoundInstanceRangeMixin`（client mixin）里直接读 `CgccMMConfig.vehicleSoundDistance`。
- `CgccMMConfig.load(...)` 在 [CgccMachineMax.java:37](core/src/main/java/dev/xcolorful/cgccompat/machinemax/CgccMachineMax.java:37) 用 `FMLPaths.CONFIGDIR.get()` 调用 —— 每个端只读**自己那边的** config 目录。
- 全项目没有任何配置同步：唯一的网络代码是三个 `core/mixin/network/*` mixin 在改 MachineMax 自己的广播，兼容模组自己没有 payload 注册（没有 `PayloadRegistrar` / `RegisterPayloadHandlersInterface`）。

所以只设服务端时：客户端读自己的 `config/cgccmachinemax.json`；该键不存在时按默认值补写 `true` / `20`。**服务端把 `vehicleSoundDistance` 改成 40，客户端的音效仍旧按 20 播放。**

这里有个容易误判的点：你现在的默认值和你的预期恰好一致（两边默认都是 20），所以"看起来是对的"是巧合，不是同步生效。

## 对照：`renderDistance` 为什么不一样

现有的 `renderDistance` 是**混合**的：`ModEntities.modifyVehicleRenderDistance()` 在 common setup 里跑——在专用服务端上改的是服务端那份 `EntityType.clientTrackingRange`（所以同步范围确实由服务端决定 ✔），而客户端的 `PartEntityRendererMixin` 只用自己的值决定渲染裁剪。也就是说"服务端配置管服务端行为"在这个项目里已有先例，客户端行为（渲染裁剪、音效）一直是本地值。

要做成服务端权威，需要新增一条服务端→客户端的同步：

## 方案（若你确认要做）

1. 新增一个 `CustomPacketPayload`，两个字段就是 `modifyVehicleSoundDistance` + `vehicleSoundDistance`；
2. 在 `neoforge-compat` 用 `RegisterPayloadHandlersEvent` 注册 `playToClient`（标 `optional()`，避免没装兼容的客户端被踢）；
3. 服务端在**配置阶段**（NeoForge 的 `RegisterConfigurationTasksEvent`）下发，玩家进入世界前就生效，没有"进服瞬间按默认值播"的竞态；
4. 客户端收到后覆盖内存里的两个字段（**不写回本地文件**，保留文件作为单机/LAN 与默认值的记录）；
5. 客户端 mixin 不变，只是读到的值来自服务端。

语义：连了带兼容的服务端 → 服务端说了算（这也符合"竞技性信息音效要各端一致"）；单机/连没装兼容的服务端 → 用本地值。

要我按这个做吗？如果你更希望保留玩家自选（比如性能考虑），那就维持现状，我在配置注释里写清"仅对本客户端生效"。

# 

```
需要，防止客户端没改或者乱改，需要发包通知客户端设置内存值
```

# 

需要服务端权威的同步。先把项目的跨平台注册约定摸清——它可能已经有网络/事件注册的封装。

这是我需要判断的关键点：CGC 的 `NetworkHandler.registerMessages()` 是写死的列表，`adapter` 是 private，**没有给第三方模组留注册入口**。先看 MachineMax 自己是怎么注册 payload 的，作为模板。

CGC 没给第三方留注册入口，所以要走 NeoForge 原生注册。先读 NeoForge 源码确认 API 细节。

# 

```
CGC的NetworkHandler没给接口，但是其使用的interface的实现(在neoforge包下）是直接能参考的
```

# 

CGC 的 `registerMessage` 用的是 `event.registrar(modId)`——**非 optional**，而 NeoForge 的规定是「连接中缺少任何非 optional 的 payload 就拒绝连接」。也就是说走 CGC 的 registrar 注册我们自己的包，会让"装了兼容的服务端 + 没装兼容的客户端"直接连不上。先确认几个 API。

`bus` 在 1.21.1 已废弃、由事件类型自动判定（默认 GAME，但 mod bus 事件也会被正确挂上）——所以裸 `@EventBusSubscriber` 两种事件都能收到。现在实现。

服务端权威的同步做完了，编译通过。关于你提的"参考 NeoNetworkAdapter 的实现"——我正是照它的写法做的，但**没有**走 CGC 的 `registerMessage`，原因在下面。

## 新增/改动

| 文件 | 作用 |
|---|---|
| [VehicleSoundConfigPayload.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/network/VehicleSoundConfigPayload.java) | S2C 包，携带 `modifyVehicleSoundDistance` + `vehicleSoundDistance` |
| [NeoVehicleSoundConfigSync.java](neoforge-compat/src/main/java/dev/xcolorful/cgccompat/machinemax/neoforge/init/NeoVehicleSoundConfigSync.java) | 注册 payload（mod bus）+ 玩家登录时下发（game bus） |
| [CgccMMConfig.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/config/CgccMMConfig.java) | 新增 `applyVehicleSoundDistanceFromServer(modify, distance)`：覆盖内存值，带下限钳制 |
| [SpreadingSoundInstanceRangeMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/sound/SpreadingSoundInstanceRangeMixin.java) | 不变——它读的还是 `CgccMMConfig`，只是值现在来自服务端 |

流程：客户端连上就能收到一次，值覆盖内存；本地 `config/cgccmachinemax.json` 不写回（所以客户端那边的值完全不参与，改坏也没用）。

## 为什么没走 CGC 的 `registerMessage`

`NeoNetworkAdapter.INSTANCE.registerMessage(...)` 确实是可以直接调的（public static，`IMessage` 也是 public API），但它的 registrar 是：

```java
final PayloadRegistrar registrar = event.registrar(INSTANCE.modId);   // 没有 .optional()
```

而 NeoForge 的规则是（`PayloadRegistrar#optional` 的注释原文）：

> If any non-optional payloads are missing during a connection attempt, the connection will fail.

也就是说走这条路，**装了兼容的服务端会让没装兼容的客户端直接连不上**——对一个本该可选的兼容模组来说这个代价太大。另外这种方式包的 id 会落在 `customgun:` 命名空间、版本号用 CGC 的，而且要求我这个包在 CGC 那个 `RegisterPayloadHandlersEvent` 处理器跑之前排进队列，多一层时序依赖。

所以我是**照它的实现结构**（record + `TYPE` + `STREAM_CODEC` + `RegisterPayloadHandlersEvent` + `PayloadRegistrar`，与 MM 的 `MMPayloadRegistry`/`ControlPreferencePayload` 同构）注册在兼容模组自己的命名空间下，并且用了 `.optional()`：

- 包 id：`cgccmachinemax:vehicle_sound_config`
- `optional()` → 客户端没装兼容也不会被拒绝连接
- 下发前再查一次 `player.connection.hasChannel(TYPE)`，对方没这个通道就不发，不会报错

**如果你希望这类同步统一走 CGC 的网络层**，那需要 CGC 侧开个口子：给 `NetworkHandler` 一个第三方注册入口、并允许按包声明 optional。那是改 CGC 本体，你说一声我去做。

## 新增的平台 API（按你的规矩报备）

`RegisterPayloadHandlersEvent` / `PayloadRegistrar#optional()` / `PacketDistributor.sendToPlayer` / `PlayerEvent.PlayerLoggedInEvent` / `ICommonPacketListener#hasChannel`。这些都是 NeoForge 标准 API，且 MM 与 CGC 都在用同款，我认为不需要额外兼容包装——需要的话我可以包一层。

顺带一个核实结果：`@EventBusSubscriber` 的 `bus` 属性在 1.21.1 已废弃，总线由事件类型自动判定（默认值是 GAME，但 mod bus 事件照样能挂上，MM 就是这么用的），所以我把注册和下发放在同一个类里。

## 需要你实测的验收

这次只改服务端就有意义了，验证方法正好就是你要的场景：

1. **只改服务端**的 `config/cgccmachinemax.json`，把 `vehicleSoundDistance` 设成 `40`（640 格），重启。
2. 客户端**不动本地文件**（或干脆删掉它），连上后测听感：应该在 300+ 格就能听到引擎/轮胎摩擦，且在 640 格左右归零。
3. 客户端日志里搜 `cgccmachinemax`，应当**没有** payload 相关报错；若有 `Failed to send payload`／`unregistered payload` 之类，把它发我。

产物：[cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar](neoforge-compat/build/libs/cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar)（00:59）。改动未提交。

# 

```
我在README里本来就写了要双端都安装，且需要保证客户端有应用效果，所以这个message也是利用来防止客户端不一致的手段，给我修改
```

# 

明白——既然是强制双端安装，这个包就用作一致性校验。先确认 README 的措辞和 NeoForge 非 optional 包的实际拒绝行为。

README 的两端安装要求确认了（第 12/51 行），NeoForge 的非 optional 行为也确认了：协商失败时会向客户端发 `ModdedNetworkSetupFailedPayload`（列出缺失通道）并以 `multiplayer.disconnect.incompatible` 断开。开始改。

改完了。

## 改动

只改了一个文件：[NeoVehicleSoundConfigSync.java](neoforge-compat/src/main/java/dev/xcolorful/cgccompat/machinemax/neoforge/init/NeoVehicleSoundConfigSync.java)

- **去掉 `.optional()`** —— `event.registrar(PAYLOAD_VERSION)`（`"1.0.0"`），包变成非 optional；
- **删掉 `player.connection.hasChannel(...)` 那道判断** —— 协商阶段已经保证了通道存在，留着是死代码；
- 类注释改成说明它的第二重身份：一致性校验。

编译通过，产物里 `optional` / `hasChannel` 引用数都是 0。

## 这为什么真的能拦住不一致的客户端

查了 NeoForge 21.1.219 源码，不是"只打日志"：

- `NetworkRegistry` 在配置阶段做 payload 协商，失败时向客户端发 `ModdedNetworkSetupFailedPayload(failureReasons)`（**客户端能在断开界面看到缺失/不匹配的通道清单**），然后以 `multiplayer.disconnect.incompatible` 断开；
- 非 optional 的包缺失 → 协商失败；payload 版本字符串不一致 → 同样被拒绝。

所以现在的效果是：**没装本兼容的客户端连不进来**，装了但 payload 版本不同的也连不进来 —— 不会出现"连得进来、却收不到服务端下发的配置"这种更难查的状态。这与 CGC 自身的做法一致（`NeoNetworkAdapter` 用 `event.registrar(modId)` 也是非 optional，所以缺 CGC 的客户端本来就已经被拒了），另外 vanilla 客户端在服务器有必需 payload 时也会被拒——这一点在你装上 CGC/MM 的服务器上原本就成立，不是这次新引入的。

## 两点需要你知道

1. **`PAYLOAD_VERSION` 现在是一条契约**：以后改这个包的编解码或处理逻辑（比如加字段），必须同步上调这个字符串，否则新旧客户端会静默互通。我把这条规则写在常量注释里了。如果你希望"任何兼容模组版本差异都直接拒绝"，可以把它换成运行时读到的 mod 版本（`ModList` 取 `0.0.12.1` 之类），代价是每次发版所有客户端都得更新——要的话我改。
2. **服务端无法验证客户端"确实应用了"**：包走 TCP 必达，且 `applyOnClient()` 不会失败，所以正常流程下不存在漏应用；但如果要防"改过的客户端收到包后不执行"，那需要客户端回一个确认包（C2S）由服务端校验，那是另一套机制，现在没做。

## 验收

1. **双端一致性**：把某个客户端的 `cgccmachinemax-*.jar` 换成旧版或删掉，连接服务端 → 应在断开界面看到包含 `cgccmachinemax:vehicle_sound_config` 的缺失通道列表。
2. **配置权威**：客户端本地文件保持默认（或改乱），只在**服务端**把 `vehicleSoundDistance` 设成 `40` 重启，客户端连上后应在 ~640 格处才听不到引擎/轮胎摩擦。

产物：[cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar](neoforge-compat/build/libs/cgccmachinemax-0.0.12.1-neoforge-1.21.1.jar)（01:02）。运行时我这边测不了，上面两条要你实际跑一遍。改动仍未提交。
