> #3 提示词

# 

```
现在需要继续解决CGC跟MachineMax的枪射物相关的兼容性问题

目前测试：
- 全自动枪械，一发弹夹30发打在载具上，客户端提示只有10次伤害，但是每打一发等一下再打就不会减少
- 疑似是载具有自己设置无敌时间

- 当前IDEA打开了CGC本体、CGC compat两个窗口，可供浏览源码
- MachineMax的源码在CGC compat窗口浏览
- 当前主窗口是CGC，但是代码修改要写到CGC compat里

辅助快速浏览：
- CGC攻击实体时取消无敌时间的逻辑在dev.xcolorful.customgun.core.projectile.impact._ProjectileHit#_executeProjectileHitEntity，CGC compat目前的dev.xcolorful.cgccompat.machinemax.core.projectile.impact._ProjectileHit还没看见有使用invulnerableTime这个字段

你需要检查载具实体是否使用这个字段，或者MachineMax有其他的机制，修改CGC Compat _ProjectileHit的实现

现在开始调查并完成修复。
```

# 

I'll start by exploring the CGC Compat `_ProjectileHit` and the MachineMax vehicle sources.

Let me locate the actual paths.

Now let me find MachineMax's `SubPart` and the hurt/invulnerability logic.

MachineMax is a dependency jar. Let me inspect it through IDEA MCP.

Let me decompile the key MachineMax classes.

MMPartEntity extends `VehicleEntity`. The compat bypasses `hurt()` and calls `subPart.onHurt` directly, so the gate must be inside `SubPart.onHurt`. Let me examine that decompiled class.

`onHurt` funnels into `accumulateDamage`. That's a likely rate limiter.

`accumulateDamage` isn't gated, but it sets `hurtTime = hurtDuration = 3`. 30 shots → 10 hits is exactly a 1-in-3 pattern. Let me find who reads `hurtTime`.

Only the client renderer reads `hurtTime`, so it's not the server-side gate. Let me see how the compat hooks into CGC core.

Let me trace where `cgc$onProjectileImpact` is invoked in CGC core.

Now let me find how `entityHitResults` are produced — that's where a per-tick/per-entity gate would live.

Let me inspect `HitBox` and the server config for any damage-rate gate.

Let me read the full physics manager to see when impact runs relative to projectile movement.

Let me list MachineMax's mixins — a damage cooldown could be injected there.

Let me check the tick ordering (physicTick → impactTick → physicMove) in CGC.

Let me check MachineMax's client-side damage feedback.

Let me check `VehicleCore` for activation/deactivation of physics bodies.

Let me re-search for `hurtTime` getter usages (my earlier grep missed `getHurtTime`).

Let me inspect SparkCore's physics snapshot API — that's what the compat ray runs against.

Let me read the projectile process manager and the GunProjectile tick to see when processing runs.

Major find — MachineMax has a **global per-tick projectile sweep** (`PartHitHandler.onProjectileTick`) that also runs for CGC bullets. Let me check how `GunProjectile` implements `onHit`/`onHitEntity`.

Let me check CGC's mixins on Projectile and the vanilla `Projectile.onHitEntity` contract.

Let me check the CGC worktree's modified docs — they may hold notes on this issue.

Let me look for runtime logs from the user's test.

Let me check the Molang bindings — `HitBox.active` comes from a Molang condition, which could depend on hurt state.

Let me check CGC's client render config — it matched the hitmarker search.

Interesting — CGC has a **damage counter HUD** with a 2000ms reset. Let me find how it's fed.

Let me check how the client renders the hit flash (the only place `hurtTime` is read).

Let me check whether SparkCore itself has a hurt-cooldown for physics hosts.

Let me read the compat's client event handlers — I haven't checked those yet.

Let me do a broad scan of MachineMax for any identifier suggesting a damage cooldown or interval.

`BlueprintAttachment` has a `hitRpCooldown` and subscribes to `SubPartDamageEvent` — let me inspect it.

Let me check CGC's `impactEffect` — it runs whenever the compat returns true, so it may be the client feedback channel.

I've traced the whole chain. Before I change code, two findings and one question — the fix differs fundamentally depending on which is true.

**On `invulnerableTime` — the vehicle does not use it.** Three independent checks:

- `MMPartEntity extends VehicleEntity` (not `LivingEntity`), and the compat bypasses `MMPartEntity.hurt` entirely, calling `subPart.onHurt(...)` directly ([_ProjectileHit.java:102](app://localhost/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/projectile/impact/_ProjectileHit.java#L102)) — so no vanilla hurt/invulnerability path is on this route at all.
- A full constant-pool scan of `MachineMax-1.21.1-1.0.1-beta.1.jar` finds `invulnerableTime` in exactly one class: `WeldingTorchItem`. SparkCore has an `AttackSystem.ignoreInvulnerableTime`, but MachineMax's part damage doesn't go through it.

**MachineMax has no server-side damage cooldown.** `DestroyableObject.accumulateDamage` unconditionally queues (`if (damage > 0)`), and `handleAccumulatedDamage` drains the whole queue every `preTick`. The only 3-tick timer in the damage path is `hurtTime = hurtDuration = 3`, and it is read **only** by `PartEntityRenderer` as a white-flash overlay (`OverlayTexture.pack(min(hurtTime,15),10)`) — it does not gate damage. (The other cooldown I found, `BlueprintAttachment.HIT_RP_COOLDOWN = 5`, gates research-point rewards only.)

So `hurtDuration = 3` is the one 3-tick constant in the system, which is suspicious given 30 → 10 is exactly 1-in-3. The remaining drop points in the compat chain that _could_ lose real damage are: the CGC entity trace not listing the part (AABB clip / latency-history box), the compat's own physics ray finding no matching active hitbox (`return false`), `SubPartDamageEvent.Pre` being cancelled (e.g. `BasicSubsystem` when `passDamage=false`), `subPartDamage` underflowing to 0 so `accumulateDamage` silently drops it, or the part being destroyed/removed.

One more thing worth knowing: the compat sends **no** CGC hit events or messages (documented decision), so CGC's hitmarker/hit sound never fires on the vehicle — all visible feedback is MachineMax's white flash / hit sound / sparks, or the durability value itself.

I need to know which of those you're actually counting before I pick a fix.

That's the key detail — a left-side HUD showing a hit count. Let me find what feeds it.

Found the likely culprit — MachineMax has an RP-reward HUD hint. Let me confirm how it's rendered and throttled.

Let me confirm the exact HUD line labels for the RP reasons.

我找到了。**这不是无敌时间，也不是伤害丢失——你看到的是 MachineMax 的研发点（RP）提示 HUD，第一行是「命中 +N」，它被 MachineMax 自己的 5 tick 奖励冷却限流了。**

## 证据链

**1. 那个 HUD 是 `ResearchPointHud`**

`ResearchPointHud.render` 画在 `guiWidth/4, guiHeight/2`（屏幕左半边、垂直居中），`ITEM_HEIGHT = 11`，按 `RpAddReason` **每种原因一行**，文本是 `reason + " +N"`。它的 lang key 全在 `hud.hint.machine_max.*` 下——正好对应你说的「提示」：

```
hud.hint.machine_max.rp_add_reason.hit = 命中
hud.hint.machine_max.rp_add_reason.part_damage = 伤害部件
hud.hint.machine_max.rp_add_reason.subsystem_damage = 伤害子系统
```

所以第一行是 `命中 +10`。

**2. 「命中」这一行被限流到 5 tick 一次**

`BlueprintAttachment.onSubPartHit`（订阅 `SubPartDamageEvent.Pre`）：

```java
private static final int HIT_RP_COOLDOWN = 5;
public static void onSubPartHit(SubPartDamageEvent.Pre event) {
    ...
    if (research.hitRpCooldown <= 0) {
        research.givRp(player, 1, RpAddReason.HIT);
        research.hitRpCooldown = 5;      // ← 下一次命中要等 5 tick
    }
}
```

`hitRpCooldown` 在 `BlueprintAttachment.onEntityTick(EntityTickEvent.Post)` 里每 tick 减 1。**不管这一 tick 打中几个部位、几发子弹，命中奖励每 5 tick 最多 +1。**

**3. 算术正好对上**

30 发全自动步枪，射速较快（约 1.5 tick/发）→ 弹夹打完约 45 tick → `45 / 5 ≈ 10` 次奖励。而「每打一发等一下再打」时每发间隔 > 5 tick，每发都能拿到一次 → 30。这精确解释了 30→10 和「隔开发射就不会减少」。

**4. 伤害本身没有任何限流**

- `SubPart.onHurt` → `accumulateDamage` 只要 `damage > 0` 就无条件入队，`handleAccumulatedDamage` 每个 `preTick` 把队列全部结算。
- 伤害走的是另一个奖励口：`SubPartDamageEvent.Post` → `givRp(player, (int)damage, RpAddReason.PART_DAMAGE)`，**没有冷却**，对应第二行「伤害部件 +N」。

所以「伤害部件」那一行和部件耐久才是伤害的真实读数——快速连射和隔开发射应该是同一个值。

## 顺带回答你的原问题

`invulnerableTime` 与这条链路完全无关，除了之前的三个检查（`MMPartEntity extends VehicleEntity` 不是 `LivingEntity`、compat 直接调 `subPart.onHurt` 绕过 `hurt()`、全 jar 扫描只有 `WeldingTorchItem` 用到该字段）之外，我还确认了 MachineMax 自己的投掷物通路对 CGC 子弹是**空转**的：`PartHitHandler.onProjectileTick` → `manualProjectileHit` → `Projectile.onHit` → `onHitEntity`，而原版 `Projectile.onHitEntity` 是空实现、`GunProjectile` 也没覆写。所以 compat 的 `_ProjectileHit` 确实是唯一的出伤口，不存在双重计数，也说明「命中次数」不可能来自这里。

## 结论

[_ProjectileHit.java](app://localhost/CGC-Compat-MachineMax/core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/projectile/impact/_ProjectileHit.java) 不需要改——`HIT_RP_COOLDOWN` 是 MachineMax 防止刷研发点的刻意设计，不是 compat 的正确性问题。我没有动任何代码。

两点需要你判断：

1. **验证方式**：对照「伤害部件 +N」那一行或部件耐久，快速连射 vs 隔开发射应当一致。如果你希望我加一个临时计数器到 compat `_ProjectileHit`（统计 30 发里各退出路径各走了多少次），我可以加上，跑一次就能把 30/30 钉死。
2. **另一个潜在真问题**：如果「伤害部件」值本身偏低（每发被装甲折算到不足 1 点，`(int)damage` 甚至为 0），那是之前设计记录里就留下的未决点——CGC 的 `armorIgnorePercent` 没有映射到 MachineMax 的穿透模型，默认按「非穿甲源」处理会被 `(pen/armor)^factor` 大幅衰减。这条要看具体枪械/载具数据才能定，跟这次的「10 次」无关。要我查这条的话，告诉我测试用的枪和载具。
