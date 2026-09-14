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
