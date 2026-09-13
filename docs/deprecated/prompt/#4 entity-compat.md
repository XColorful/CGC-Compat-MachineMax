> #4 提示词

# 

```
现在有以下问题：
- MachineMax的载具实体在距离一段不是很远的距离后就不渲染
- CGC有对声音做并发限制（dev.xcolorful.customgun.client.config.SoundConfig），防止同时播放的数量太多导致MC声音系统完全没声；而目前测试下似乎MachineMax的实体数量一多就必定会导致声音系统无声（需要到无载具实体的位置，退出存档重进来解决）
- 枪射物命中MachineMax的载具部位后，会脱落；kill @e无法移除载具实体（例如一些自动清理实体的小游戏模组会失效）

- 注意当前打开的主窗口是CGC，而修改的代码需要写在CGC-Compat-MachineMax，以及浏览MachineMax的源码

由以上说明，需要完成以下内容：
- CGC的实体渲染范围在dev.xcolorful.customgun.core.init.registry.ModEntities#GUN_PROJECTILE里定义clientTrackingRange，并配合dev.xcolorful.customgun.client.renderer.entity.GunProjectileRenderer#shouldRender来绕过渲染距离判定；目前看到io.github.sweetzonzi.machine_max.common.registry.MMEntities#PART_ENTITY里似乎没有定义，检查是否可以mixin注入来修改或者其他更好的方式
- 在CgccMachineMax定义的配置目录下的json文件，用一个JsonObject，里面加几个键值对：一个bool用来让枪射物把载具part打下来后立即消失（有助于解决音频并发问题）；一个bool和int用来开启和限制MachineMax的并发音频数量；一个bool用来控制kill @e/Entity#discard()等正常移除实体的方式是否能生效
- 声音并发限制暂时不完成，先完成枪射物打下part后立即消失，以及移除实体的配置的功能，我看效果再决定要不要加声音并发限制

现在开始，如果需要Mixin配置，按照已有的mixin配置继续补充即可。
```

# 

I'll start by exploring the current state of both projects to understand the structure and existing patterns.

Let me explore the compat project structure and locate MachineMax sources.

Let me read the existing compat code, docs, and mixin config in parallel.

Let me read the CGC side files and check for MachineMax sources.

Let me look for MachineMax sources and check how the compat project is built.

Let me inspect the MachineMax jar contents.

Vineflower is available. Let me decompile the key MachineMax classes.

Now let me examine the client side of MachineMax and the registry.

MachineMax is Kotlin. Let me examine the damage/removal flow and how parts become entities.

Let me examine the CGC projectile implementation and the compat mod's client entry.

Let me check the CGC projectile entity and the config patterns.

I now have a clear picture. Let me check the CGC config conventions before implementing.

I have enough context. Let me set up tasks and implement.

Now let me create the config class.

Now the projectile-disappear behavior in `_ProjectileHit`.

Now the entity-removal mixin.

Now the client render-distance mixin.

Let me verify the mods.toml mixin reference and try compiling.

Now let me compile to verify everything.

Compiled cleanly. Let me verify the full build and check the generated refmap.

Compilation succeeded (the failure is only the asset-download step of `build`). Let me confirm and inspect the generated refmap.

Let me make the config file always written in a stable, readable key order.

Let me verify the `discard` and `isRemoved` method signatures in the target Minecraft version.

All modules compile. Let me do a final review of the changed files.

Let me check the pre-existing uncommitted changes I noticed.

已完成两项功能并验证编译通过。下面是改动与结论。

## 改动清单（均在 `CGC-Compat-MachineMax`）

**1. 配置系统** — [CgccMachineMaxConfig.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/CgccMachineMaxConfig.java)（新增）  
在 `config/cgccmachinemax.json` 读写 `JsonObject`，字段缺失自动补默认值并按固定顺序回写：

```json
{
  "entityRemovalAllowed": false,
  "projectileDisappearOnPartHit": false,
  "concurrentSoundLimitEnabled": false,
  "maxConcurrentSounds": 8
}
```

后两个是给声音并发限制预留的，暂未消费。加载挂在 [CgccMachineMax.java:36](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/CgccMachineMax.java:36)（`init` 里），并加了 `reloadConfig()`。

**2. 枪射物命中 part 后立即消失** — [_ProjectileHit.java:102](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/projectile/impact/_ProjectileHit.java:102)  
在成功出伤后按配置 `discard()`。只在实际命中活动命中箱、真正出了伤时触发；CGC 的 `ProjectileImpactManager` 在每次命中后会检查 `isRemoved()` 并提前返回，所以同一 tick 内不会继续命中其它部件。

**3. kill @e / discard() 移除载具** — [MMPartEntityRemovalMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/MMPartEntityRemovalMixin.java)（新增）  
默认（`entityRemovalAllowed: false`）在实体仍绑定存活 subPart 时取消 `discard()`，覆盖 `kill @e` 和模组清理。关键点是 MachineMax 的 `SubPart#postTick` 会把被移除的 part 实体立刻重建，所以只在 `discard()` 处拦。部件被摧毁走的是 `SubPart#destroy` → 先解绑 `subPart` 再 `remove(DISCARDED)`，不受影响，摧毁流程保持原样。

**4. 部件渲染距离** — [PartEntityRendererMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/renderer/entity/PartEntityRendererMixin.java)（新增）  
已确认根因：`PartEntityRenderer#shouldRender` 里用 `gameRenderer.getRenderDistance()`（默认 16 区块）截断。mixin 改成只做视锥体裁剪，复刻 CGC `GunProjectileRenderer#shouldRender` 的写法（用 `getBoundingBoxForCulling()` + `inflate(0.5)`）。已在 [cgccmachinemax.mixins.json](app://localhost/epitaxy/core/src/main/resources/cgccmachinemax.mixins.json) 的 `client` 段注册。

## 两点需要你确认

**关于 `clientTrackingRange`**：`MMEntities.PART_ENTITY` 确实没设置，MobCategory.MISC 默认是 5 区块（80 格）的同步范围。但它是构建期写死的，且 `DeferredHolder.get()` 在 `Init` 前会抛异常——要改用 mixin 得在 register 时拦截访问器，或事后改 entity type 的 final 字段，都比渲染那处复杂。渲染距离放开后 80 格同步范围可能仍是短板，是否要我继续处理这块？

**关于"命中后脱落"**：我读到的效果是——子弹命中部位出伤，部位耐久掉光后 `SubPart#destroy` 移除该部件实体（`entity.remove(DISCARDED)`），载具上留缺口。想确认这是你说的"脱落"，还是你看到的是正常出伤时部件就异常掉下来？

The answers clarify two things: (1) discard should happen **after** the part detaches, not instead of it; (2) also handle `clientTrackingRange`. Let me investigate the part-detach API and the `EntityType` field.

Let me verify the exact current state of my earlier edit.

Let me verify `Part.isDestroyed()` visibility and the `Part`/vehicle accessors.

Now let me implement the `clientTrackingRange` fix. I'll add an accessor mixin for `EntityType` and set the part range during mod init.

The tracking range must be applied after entity registration. Let me wire it via the register event.

The builder just uses the vanilla default. Let me rewrite the tracking-range utility to hook the setup event.

Now let me compile and verify the `MMEntities` getter and accessor resolve.

Both modules compile. Let me verify the mixin targets against the actual Minecraft classes (the accessor field name is a runtime risk).

The decompile jar has Java sources. Let me extract and verify.

1.20.1's cache can't confirm 1.21.1 field names. Let me verify against the actual 1.21.1 source.

Let me check whether 1.21.1 decompiled sources exist anywhere locally.

I have the obfuscated 1.21.1 jar. Let me decompile it to confirm the real field name.

Found the official mappings. Let me confirm the field name.

Mappings confirm `int clientTrackingRange` exists on `EntityType`. Let me verify the remaining mixin targets.

All mixin targets verified against 1.21.1. Let me make the ordering intent explicit and recompile.

Build successful. Let me do a final review of all changes.

Let me double-check the original mixin package value to make sure my edit is correct.

That's correct — the package became the root prefix so both core and client mixins fit one config. Let me verify the final config and do a clean rebuild.

Let me run a clean rebuild to ensure nothing stale, then finalize.

Build clean and successful. Final summary of the improvements you asked for:

## 本轮新增（承接你确认的两点）

**1. `clientTrackingRange` 一起处理** — [EntityTypeAccessor.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/EntityTypeAccessor.java) + [PartEntityTrackingRange.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/PartEntityTrackingRange.java)

已用 1.21.1 官方 mappings 确认字段真实存在（`EntityType -> bsx:` 下的 `int clientTrackingRange -> bM`，Builder 的 `clientTrackingRange(int) -> a`）。做法：accessor mixin 把 `EntityType#clientTrackingRange` 的 `final` 去掉，在 `FMLCommonSetupEvent`（注册完成后）改成 20 区块 / 320 格，对齐 CGC 的 `GUN_PROJECTILE`。已确认 `MMEntities` 是 Kotlin `object` 上的 `@JvmStatic val PART_ENTITY`，对应 `MMEntities.getPART_ENTITY()`。

**2. 脱落 → 再 discard 的顺序** — [_ProjectileHit.java:104](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/projectile/impact/_ProjectileHit.java:104)

按你的说明调整了注释、明确顺序语义：`subPart.onHurt` 先把出伤走完（耐久耗尽的部件在载具 tick 里被 `Part.onTick` → `VehicleCore.removePart` → `SubPart.destroy` 移除脱落），之后才 `gunProjectile.discard()`。`discard()` 只作用于枪射物实体自身，不会波及载具，所以不存在误 discard 整台载具的风险。当前实现是唯一会 discard 的地方。

## 已确认的 Mixin 目标（对照 1.21.1 / MachineMax 字节码）

|Mixin|目标|核验|
|---|---|---|
|`EntityTypeAccessor`|`EntityType#clientTrackingRange`|官方 mappings 存在|
|`MMPartEntityRemovalMixin`|`Entity#discard()` / `isRemoved()`|`void discard() -> discard`、`boolean isRemoved() -> dJ`|
|`PartEntityRendererMixin`|`PartEntityRenderer#shouldRender(MMPartEntity, Frustum, double, double, double)`|javap 确认该重载存在|
|`MMPartEntityMixin`、`GunProjectileMixin`|原有|未改动|

## 全部改动清单

- 新增 [CgccMachineMaxConfig.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/CgccMachineMaxConfig.java)（`config/cgccmachinemax.json`，4 个键，缺失自动补默认、固定顺序回写）
- 改 [CgccMachineMax.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/CgccMachineMax.java:36)（`init` 里加载配置 + `reloadConfig()`）
- 改 [_ProjectileHit.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/projectile/impact/_ProjectileHit.java:104)（命中 part 后按配置 discard 枪射物）
- 新增 [MMPartEntityRemovalMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/MMPartEntityRemovalMixin.java)（按配置拦 `discard()`，默认拦，恢复 `kill @e` 的原有"无法清理"行为）
- 新增 [PartEntityRendererMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/renderer/entity/PartEntityRendererMixin.java)（去掉 `gameRenderer.getRenderDistance()` 截断，只做视锥裁剪）
- 新增 [EntityTypeAccessor.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/EntityTypeAccessor.java)、[PartEntityTrackingRange.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/PartEntityTrackingRange.java)
- 改 [cgccmachinemax.mixins.json](app://localhost/epitaxy/core/src/main/resources/cgccmachinemax.mixins.json)（`package` 提升为根包前缀，新增 3 条挂载；`client` 段注册渲染 mixin）
- 改 [CgccMachineMaxNeoforge.java](app://localhost/epitaxy/neoforge-compat/src/main/java/dev/xcolorful/cgccompat/machinemax/neoforge/CgccMachineMaxNeoforge.java)（接 `IEventBus`，监听 `FMLCommonSetupEvent`）

## 需要你注意的两点

1. **`entityRemovalAllowed` 默认 `false`（保持现有行为）**。置 `true` 后 `kill @e` / 模组清理的 `discard()` 才真正生效。注意 MachineMax 的 `SubPart#postTick` 会自愈重建实体，所以置 `true` 时"移除后重新生成"是原生行为，不是新引入的。
2. **只拦了 `Entity#discard()`**。`SubPart#destroy`（部件被摧毁时的正常移除）走的是 `entity.remove(DISCARDED)`，故意不受影响，摧毁流程保持原样。

我没有提交，改动都在工作区，等你确认效果。

## 关于声音并发限制

按你的意思先留着两个配置项（`concurrentSoundLimitEnabled` / `maxConcurrentSounds`）未消费。我看了一下 MachineMax 的发声点：`SubPart#onHurt` 会经 `SparkLevel.submitImmediateTask(POST, ...)` 调 `Level#playSound`，摧毁时还有 `SpreadingSoundHelper.playSpreadingSound`。等你测完这次的 `projectileDisappearOnPartHit` 效果，再决定要不要在客户端截这些 `playSound` 做计数限流。

# 

```
你的理解有问题：
- 我看你_ProjectileHit里的行为有问题，我的意思是如果枪射物命中载具part后，这个载具part会脱落的话，那么把这个part给discard，而不是把枪射物给discard（枪射物有自己的pierce计数）

以及，我对代码做了些修改，你还要接着改：
- 配置类已经移到CgccMMConfig，并且把load改成流式读取，CGC本体有JsonUtils给你用；CgccMachineMax.LOGGER调用写的日志要用英文log
- CgccMMConfigTag加一个是否启用渲染距离修改，再加一个渲染距离，即配置项加一个bool和int
```

# 

I misunderstood — discard the part, not the projectile. Let me read the current state of the moved config class and find CGC's `JsonUtils`.

Let me gather the current state of everything the user changed.

Let me kill that stale background grep and examine MachineMax's part-destroy semantics.

Let me read the current main class and find where `CommonSetup` is invoked.

The user restructured mixins. Let me read the current mixin configs and mods.toml.

Let me check how event-bus wiring exists anywhere, and confirm the maven dep situation.

Now I understand the structure. Let me implement the config changes first.

Let me read the current neoforge mod entrypoint.

Now let me wire `CommonSetup` (currently unreferenced) via the mod event bus.

Now let me remove the incorrect projectile discard from `_ProjectileHit`.

Config, tags, render-distance config, English logs, and `CommonSetup` wiring are done and compiling. Now I need to pin down the `脱落` semantics before implementing the part discard — I traced MachineMax's destroy lifecycle and there are two distinct moments, so let me confirm which one you mean.

The user wants: whenever a part detaches, discard it — regardless of bullet hits. Let me find the actual detach path, especially connector breakage.

`CommonSetup` wiring, config streaming, English logs, and render-distance config are done. For the discard feature I traced MachineMax's lifecycle and there are three distinct "detach" moments — let me confirm which one matches what you see in-game, so I hook the right place.

Let me find how a part (e.g. a wheel) can detach from the vehicle while still alive.

Found it: `AbstractConnector.handleAccumulatedImpact()` tears a part off the vehicle when a connector's integrity breaks. Let me see how the split-off parts are tracked so I discard only the detached piece.

Now I understand the mechanic. Let me verify the API surface for a safe hook on split-off parts.

Now I understand the real mechanic: a connector breaks → `VehicleCore.detachConnector` → the vehicle **splits**, `ObjectManager.addSpiltVehicle` creates a new vehicle for the torn-off piece, whose `SubPart.postTick` keeps its entity alive (so it stays shootable). That's the right hook. Let me implement it.

Let me verify the split VehicleCore populates `partMap` from the network.

Compiles. Let me do a final review of the whole change set.

改完了，`core` 与 `neoforge-compat` 都编译通过。逐条说明。

## 1. `_ProjectileHit`：去掉对枪射物的 discard

已删除 `gunProjectile.discard()` 及其配置判断 —— 枪射物保持自己的 pierce 计数，不做任何额外处理。

## 2. 「脱落后不保留」的实现 —— 关键发现

追了 MachineMax 的源码，脱落的真实机制是：

`AbstractConnector#handleAccumulatedImpact` 里，连接件吃的冲击 ≥ 自身 integrity 时调用 `subPart.part.vehicle.detachConnector(this)` → `VehicleCore#detachConnections` → `partNetSpiltCheck()` 把载具**分裂**成多个，脱落的部分经 `ObjectManager.addSpiltVehicle(newVehicle)` 变成一个新载具。

所以脱落不是"部件被移除"，而是"部件裂出去变成独立载具" —— 它的 `MMPartEntity` 还在，所以能继续被打、被击退。这也解释了为什么只 `discard()` 实体没用：`SubPart#postTick` 会 `refreshPartEntity()` 把实体重建回来。

落点：[ObjectManagerMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/vehicle/ObjectManagerMixin.java)（新增）挂在 `ObjectManager#addSpiltVehicle` 的 TAIL —— 这是"刚刚裂出去"的唯一入口，跟是不是子弹打的无关，符合你说的"不论是否是子弹命中的"。启用后对该分裂载具的每个 part 调 `VehicleCore#removePart`，走正常移除流程把 SubPart 标记 `isRemoved` 并移除实体，不会再被重建。只在服务端执行，客户端跟服务端的移除包。

配置键按你说的方向重命名为 `discardOnDetach`（"脱落后不保留"）。

## 3. 配置类改造

- [CgccMMConfigTag.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/api/config/CgccMMConfigTag.java)：补上你遗留的 `public static final String "";`（那是编译不过的），新增 `MODIFY_RENDER_DISTANCE`、`RENDER_DISTANCE`。
- [CgccMMConfig.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/config/CgccMMConfig.java)：`load` 改成 `JsonReader` 流式读取 + `JsonUtils` 读写；日志全部改英文。
- 最终配置项：`allowEntityRemoval`、`discardOnDetach`、`concurrentSoundLimitEnabled`、`maxConcurrentSounds`、`modifyRenderDistance`、`renderDistance`。

## 4. 渲染距离配置

`modifyRenderDistance`（bool，默认 `true`）+ `renderDistance`（int，默认 20，单位区块）同时驱动：

- [PartEntityRendererMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/client/mixin/renderer/PartEntityRendererMixin.java)：启用时绕过 MachineMax 的客户端渲染距离截断，只做视锥裁剪；不启用则完全不改。
- [PartEntityTrackingRange.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/PartEntityTrackingRange.java)：启用时把 `clientTrackingRange` 设成 `renderDistance`，否则不动（保持 5 区块的默认值）。

默认值选 `true`/`20` 是为了保持上一轮那个可见范围的修复默认生效。

## 5. 顺带修的一件事（需要你确认）

[CommonSetup.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/init/CommonSetup.java) 你建了但没人调用（死代码）。我在 [CgccMachineMaxNeoforge.java](app://localhost/epitaxy/neoforge-compat/src/main/java/dev/xcolorful/cgccompat/machinemax/neoforge/CgccMachineMaxNeoforge.java) 的构造里重新接回了 `IEventBus`，用 `modEventBus.addListener((FMLCommonSetupEvent e) -> CommonSetup.get().onCommonSetup())` 调它 —— 你之前把这段事件总线去掉了，如果是有意为之告诉我，我换别的方式。

## 两个需要你注意的点

**关于「discard」的强度**：我按"脱落后不保留"实现成了**整体移除该脱离部件**（`removePart` 会把它 destroy 掉，不论还剩多少血）。如果你其实只想让实体消失、让裂出去的载具继续存在，那做不到 —— 因为 `SubPart#postTick` 一定会把实体重建回来，除非把 SubPart 一起标记移除。

**风控**：`addSpiltVehicle` 里立刻 `removePart` 属于在分裂流程内再触发一次移除，我加了服务端限定和 `List.copyOf` 防并发，但这条路径我没法实机验证，建议你进游戏测一下打轮胎的表现（是否还会残留、是否有报错/卡顿）。

没有提交，改动都在工作区。

# 

```
现在实体渲染正常了，我已commit
目前的代码状态下，打胎还是不会立即清掉脱落的part
脱落的载具如果从主体上移除掉subPart，是否会引入副作用？是否还需要从脱落的part上读取什么信息来正常运行？如果是这样，就拦截postTick重建，并且要考虑当这个载具距离很远（区块被卸载），重新进入的时候是否会变回到载具上；如果不会变回去的话，假如载具part被拉得很远，是否会导致超远距离持续加载而不被清理？

假设脱离后的part处于未加载区块下，以这个标准来看主part的行为逻辑，如果还保留subPart，就拦截postTick重建，否则就直接移除subPart并discard
```

# 

Let me verify my architectural assumptions against the actual MachineMax persistence path before answering.

Important finding: `serverHandleSpilt` runs _before_ `ConnectorDetachPayload` is sent, so my `removePart` packets would be sent _ahead_ of the split packet. Let me check the client handlers to confirm the failure mode.

Let me check how the client handles the split packet (threading matters for the ordering fix).

Let me confirm `VehicleCore`'s tick method so I can defer the removal to the next tick (fixing packet order).

Let me verify the remaining API details before writing.

Now I'll implement the fix — defer the removal to `LevelTickEvent.Post` so `PartRemovePayload` is sent _after_ `ConnectorDetachPayload`.

编译通过。先回答你的四个判断，再说我改了什么。

## 分析结论

**1. 从主体移除 subPart 会不会有副作用？**  
不会有残留引用。`VehicleCore#removePart` 走的是 MachineMax 自己的移除流程：摘子系统 → `partNet.removeNode` → `part.destroy()`（停碰撞效果、销毁连接件、销毁交互箱、移除实体）→ 空载具就 `ObjectManager#removeVehicle` + `saveVehicles`。因为我们移除的是**整个脱离出去的部分**（不是主体载具的一块），没有任何连接件/子系统/部件还引用它。唯一"损失"是它不掉物品 —— 但 MachineMax 原生摧毁也不掉（`MMPartEntity#getDropItem` 返回空、`SubPart#destroy` 里没有 drop），所以和原生行为一致。

**2. 是否还需要从脱落的 part 读信息？**  
不需要。脱离部分被整体移除，之后没有任何逻辑再依赖它。  
反过来说，如果只**拦截 `postTick` 重建**：subPart 还在 → 物理刚体还在（隐形但仍会碰撞），而且它会被写进载具存档永久留着。这比移除更糟。

**3. 区块卸载后会不会变回载具上？**  
不会"变回"，但**只 discard 实体确实会被重建**。原因：`MMPartEntity` 是 `noSave()` 的瞬态实体；真正的持久化载体是 `VehicleCore`/`Part`/`SubPart`。区块卸载时实体随区块卸载，重新加载时 `SubPart#postTick` → `refreshPartEntity()` 把实体建回来。所以必须把 subPart 本身摘掉（`removePart` 会 `isRemoved=true` + 从 partNet/partMap 摘除 + 存档里也没了），重新进也不会回来。

**4. 拉得很远会不会一直加载不被清理？**  
不会。MachineMax 没有强加载（`updateVehicleChunk` 只是 `hasChunk` 查询，`inLoadedChunk=false` 就不 tick，没有 ticket）。但不清理的话，脱离部分会作为一个独立载具永久留在存档和内存里，你回去还能打它 —— 这就是要修的现象。

**5. 真正没生效的原因（查到的 bug）**  
`serverHandleSpilt` 是在**构造 `ConnectorDetachPayload` 实参时**被调用的，所以 `addSpiltVehicle` 里就地 `removePart` 发出的 `PartRemovePayload`，**比 `ConnectorDetachPayload` 先发**。客户端先收到移除包，但分裂载具还没创建，`PartRemovePayload#handle` 找不到载具 → 打日志放弃 → 客户端永远保留脱离部件。

我顺带确认了 NeoForge 的 `MainThreadPayloadHandler` 会把整个 handle 包进 `context.enqueueWork`，所以**只要把发送顺序调正，客户端就能正确应用**，不需要在客户端做额外处理。

## 改动

- [DetachedPartDiscard.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/util/DetachedPartDiscard.java)（新增）：分裂载具先入队，延后到本 level 的 `LevelTickEvent.Post` 再 `removePart`。此时 `ConnectorDetachPayload` 已经发出，`PartRemovePayload` 自然排在后面。
- [ObjectManagerMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/vehicle/ObjectManagerMixin.java)：挂在 `addSpiltVehicle`（登记）和 `onPostTick`（统一移除）。`addSpiltVehicle` 是"部件刚被撕裂成独立载具"的唯一入口，与造成撕裂的原因无关 —— 符合你说的"不论是否是子弹命中的"。
- 只在服务端决定移除，客户端跟随服务端下发的移除包，避免两端配置不一致时状态分叉。
- `discardOnDetach` 默认值改成 **`true`**，这样开箱即生效；不想要就改回 `false`。

服务端移除、客户端通过移除包同步，这条链路是权威的；`removePart` 过程中若又产生新的分裂，会再次入队，下一 tick 继续处理，最终收敛。

改动仍未提交，建议进游戏验证：打轮胎后脱离部件应立即消失，且退出重进不会回来。

# 

```
那照你这么说的话，如果allowEntityRemoval，是否会导致实体残留在存档里？是否可以默认启用allowEntityRemoval，但是把removeal时的残留给清理掉（如断subpart+不写存档）？
```

# 

Good question — let me verify how entities are actually removed on chunk unload, because that determines whether hooking `discard()` is even safe.

Let me verify what `kill @e` actually calls on an `MMPartEntity`.

Key finding: let me check whether `kill @e` even goes through `discard()`, and find internal callers.

Let me confirm `Part`'s field visibility before rewriting the removal mixin.

Now I have the full picture. Let me rewrite the removal mixin to cover `kill @e` (which goes through `remove(KILLED)`, not `discard()`) and to clean up the subPart.

编译通过。你这个方向是对的，但过程中我发现了一个之前漏掉的关键事实。

## 先纠正一点：`kill @e` 根本不走 `discard()`

查了 1.21.1 的 mappings 和 vanilla 源码：

- `KillCommand` → `entity.kill()` → `Entity#kill()` → `remove(RemovalReason.KILLED)` → `setRemoved(KILLED)`
- `Entity#discard()` → `remove(RemovalReason.DISCARDED)`
- 区块卸载 → `PersistentEntitySectionManager` 直接调 `setRemoved(UNLOADED_TO_CHUNK)`，**绕过** `discard()`/`remove()`
- `VehicleEntity`（1.21.1）没有覆写 `kill()`/`discard()`

所以我之前那个只挂 `discard()` 的 mixin **从来没覆盖 `kill @e`** —— 这就是为什么 `allowEntityRemoval` 对 `kill @e` 看起来没作用。

## 回答你的问题

**1. 会不会有实体残留在存档里？**  
不会。"残留"这个担心不成立：`MMPartEntity` 是 `noSave()` 的瞬态实体，**任何情况下都不进存档**。真正进存档的是 `VehicleCore`/`Part`/`SubPart`。  
所以开不开 `allowEntityRemoval`，实体都不会留在存档里。之前"实体又被重建回来"是 `SubPart#postTick` 干的，跟存档无关。

但反过来说：**只让 `discard()`/`remove()` 放行是不够的** —— 实体被移除了，`SubPart` 还在，`postTick` 下一 tick 就把实体建回来。所以"让移除生效"等价于"要连 subPart 一起摘掉"，也就是你说的"断 subpart + 不写存档"。

**2. 可以默认启用 + 清理残留吗？**  
可以，而且这是唯一能让 `kill @e` 真正生效的做法。已实现：

[MMPartEntityRemovalMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/MMPartEntityRemovalMixin.java)：

- 接管两条路径：`discard()`，以及 `/kill` 走的 `remove(KILLED)`。
- **不碰 `RemovalReason.DISCARDED`** —— 那是 MachineMax 自己在用（`SubPart#destroy` → `entity.remove(DISCARDED)`、`baseTick` 清理孤立实体、`readSpawnData`），碰了会破坏原生流程。`SubPart#destroy` 内部再调 `remove(DISCARDED)` 不会再触发本 mixin，所以没有递归。
- 启用（默认 `true`）时：连同该 part 一起 `VehicleCore#removePart` 摘除 → subPart 标 `isRemoved`、从 `partNet`/`partMap` 摘掉 → 实体不会被重建，载具存档里也没有它了。
- 只服务端执行；客户端不做载具结构改动，等服务端下发的 `PartRemovePayload`。这个方向很重要 —— 客户端在实体离开同步范围时会移除实体，若在客户端也摘 part，会误拆载具。
- 未启用时：拦截移除，部件实体保持不被主动清掉。

`allowEntityRemoval` 默认改成 `true`。理由是这跟原版语义一致（原版 `kill @e` 本来就能清掉船/矿车），MachineMax 部件"删不掉"才是异常。

## 需要你注意

- 默认 `true` 后，`/kill @e`（不带 type 过滤）会**拆掉世界里所有载具**。这正是小游戏自动清理想要的语义，但如果你只是想清掉落物，得写 `kill @e[type=!machine_max:part]` 之类。
- `discardOnDetach`（连接件撕裂后不保留）和 `allowEntityRemoval`（主动移除手段生效）现在是两个独立开关，互不干扰。

改动未提交。建议实机验证两点：`kill @e[type=machine_max:part]` 后部件是否彻底消失且重进不回来；以及打轮胎脱落的部分是否随 `discardOnDetach` 一起被清掉。

# 

```
其他模组代码可能不是用remove(KILLED)，而是用discard，需要确保别的模组利用discard来移除实体的时候，本兼容模组的config能让其生效来正常移除
也就是不管哪个reason，应该都走同样的清理逻辑
```

# 

Good catch — and I verified there's a safety property that makes this clean. Let me confirm the exact ordering in MachineMax's own destroy path first.

All three MachineMax-internal `remove(DISCARDED)` sites run with `subPart` already null. So `subPart != null` is a reliable discriminator — I can drop reason-based branching entirely.

Important refinement: after `removePart` the entity is already removed via `SubPart#destroy`, so I must cancel the outer call to avoid a double `setRemoved`.

按你说的改了：不再按 reason 分支，所有移除请求走同一套逻辑。

## 为什么可以安全地不看 reason

关键在于区分「外部主动移除」和「MachineMax 自己移除」不能靠 reason，而要靠 `subPart` 是否还绑着。我核对了 MachineMax 内部全部三处 `remove(DISCARDED)`，触发时 `subPart` 一定已经是 null：

|位置|场景|触发时 subPart|
|---|---|---|
|`SubPart#destroy`|部件被摧毁/正常移除|先 `entity.subPart = null` 再 `remove`|
|`MMPartEntity#baseTick`|无匹配部件的孤立实体，超 100 tick 清理|在 `if (subPart == null)` 分支里|
|`MMPartEntity#readSpawnData`|客户端收到 `hasPart == false` 的新实体|新 spawn，还没绑|

所以 `subPart != null` 就是「这是个还挂在载具上的活部件，有人在外部移除它」的可靠判据。这样任何模组用 `discard()`、`remove(任意 reason)`、`kill @e`（`kill()` → `remove(KILLED)`）都能命中同一套清理逻辑。

## 改动

[MMPartEntityRemovalMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/MMPartEntityRemovalMixin.java)：

- `discard()` 和 `remove(RemovalReason)` 都注入，**不再判断 reason**。
- `subPart == null || isRemoved()` → 放行（MachineMax 内部清理、重复移除）。
- `entityRemovalAllowed == false` → 一律拦截。
- `true`（默认）→ 服务端调 `VehicleCore#removePart` 把该 part 连 subPart 一起摘掉，**然后取消外层 remove**。
- 客户端不做载具结构改动，等服务端 `PartRemovePayload`。

有个细节值得说：清理后要**取消外层调用**，因为 `removePart` 内部经 `SubPart#destroy` 已经把实体移除了（`SubPart#destroy` 先把 `entity.subPart = null` 再 `remove(DISCARDED)`，此时我的守卫看到 subPart 已经是 null 就放行，不会再递归）。如果不取消，`setRemoved` 会被走第二遍，可能重复触发 level 的 `onRemove` 回调。

## 一个刻意的例外

只挂 `remove(RemovalReason)`，**没有挂 `setRemoved(RemovalReason)`**。因为区块卸载是 `PersistentEntitySectionManager` 直接调 `setRemoved(UNLOADED_TO_CHUNK)` 绕过 `remove()` 的，挂上去会导致区块卸载时误拆载具。所以如果某个模组不走 `discard()`/`remove()`/`kill()` 而是直接 `entity.setRemoved(...)`（很少见），这个 config 覆盖不到。

## 提醒

`entityRemovalAllowed` 默认 `true`，意味着 `kill @e`（不带 type 过滤）会拆掉世界里所有载具 —— 这和原版语义一致（原版 `kill @e` 本来就会清掉船/矿车），但如果你只想清掉落物，需要写成 `kill @e[type=!machine_max:part]`。

编译已通过，未提交。建议实机验证：用 `kill @e` 和某模组的 discard 清理各测一次，确认部件都没了且退出重进不回来。

# 

```
kill和其他模组的discard都没能移除，清掉之后还是会重建回来，还需要修改。
```

# 

The user reports it still rebuilds — so my hooks likely aren't firing. Let me verify the injection targets properly, starting with whether `VehicleEntity` overrides these methods.

Confirmed `VehicleEntity` doesn't override them — so they're inherited from `Entity`. Let me check whether MachineMax itself injects into inherited methods (precedent for whether Mixin supports it).

Let me settle the Mixin semantics definitively — I'll check the Mixin library's method-resolution behaviour.

Found the root cause. Let me confirm it and check `Entity`'s method names.

Root cause confirmed: `discard()`/`remove()` are declared in `Entity`, not `MMPartEntity`. Mixin resolves the inherited method into a target (so no validation error and no crash), but the injection can never be written into `MMPartEntity` — a silent no-op. The fix is to mix into `Entity` directly with an `instanceof` guard.

找到真正的原因了。

## 根因：Mixin 挂错了类

`discard()` 和 `remove(RemovalReason)` 都**声明在 `Entity` 上**，不在 `MMPartEntity` 上（`VehicleEntity` 也没覆写，我核对了 1.21.1 的 mappings：`kill() -> ap`、`discard() -> aq`、`remove(RemovalReason) -> a` 全部在 `Entity` 里）。

而 Mixin 的行为是：`MixinTargetContext.getTargetMethod` 用 `findMethodInHierarchy(..., SUPER_CLASSES_ONLY, ...)` **能在父类里找到**这个方法，所以目标数量 > 0、`defaultRequire: 1` 校验通过、**不报错也不崩溃**；但注入是往 `MMPartEntity` 里写方法体，而那个类根本没有这两个方法的方法体 —— 写不进去，**静默失效**。

这正好解释了你看到的现象：不崩溃，但 `kill @e` / `discard()` 都毫无作用，实体被移除后又被 `SubPart#postTick` 重建回来。

## 修复

- 新增 [EntityRemovalMixin.java](app://localhost/epitaxy/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/EntityRemovalMixin.java)：`@Mixin(Entity.class)`，注入 `discard()` 和 `remove(RemovalReason)`，用 `instanceof MMPartEntity` 过滤，只处理部件实体。
- 删除 `MMPartEntityRemovalMixin.java`，[cgccmachinemax.mixins.json](app://localhost/epitaxy/core/src/main/resources/cgccmachinemax.mixins.json) 换成 `entity.EntityRemovalMixin`。
- 清理逻辑不变：不按 reason 分支，靠 `subPart != null` 区分外部移除；启用时 `VehicleCore#removePart` 把 part 连 subPart 一起摘掉（既不会被重建，也不留在载具存档里），然后取消外层调用避免 `setRemoved` 走两遍；只在服务端做，客户端等服务端 `PartRemovePayload`。

我顺带把其余 mixin 都过了一遍，确认没有同类问题 —— `ObjectManager#addSpiltVehicle` / `onPostTick`、`PartEntityRenderer#shouldRender`、`EntityType#clientTrackingRange`、`GunProjectile#<init>` 都声明在各自的目标类里，注入是有效的。（也就是说 `discardOnDetach` 之前不生效是另一个原因 —— 那个是 `PartRemovePayload` 早于 `ConnectorDetachPayload` 发出的发送顺序 bug，已经用延后到 `LevelTickEvent.Post` 修掉了，和这次是两回事。）

## ⚠️ 你需要先改配置，否则这次修复看起来仍然"没用"

你机器上的 `config/cgccmachinemax.json` 是**早期版本生成的**，里面键已经存在、值是 `false`：

```json
"allowEntityRemoval": false
```

加载器只对**缺失的键**套用新默认值，已存在的键会用文件里的值。所以它仍然是 `false` —— 那样修复后的钩子会**拦截**移除，`kill @e` 会变成"完全没反应"（连消失再重建都不会有）。

请把该键改成 `true`，或者直接删掉 `config/cgccmachinemax.json` 让它按新默认（`true`）重新生成。

编译已通过，未提交。验证时建议看两点：`kill @e` 后部件是否彻底不在（且重进存档不回来），以及日志里有没有 `MachineMax` 的 `找不到载具` 之类的报错。
