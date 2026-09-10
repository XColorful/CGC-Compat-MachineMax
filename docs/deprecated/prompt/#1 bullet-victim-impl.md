> #1 提示词

# 

```
现在需要研究MachineMax的设计，以及实现CGC枪射物对MachineMax的出伤兼容
- CGC的枪射物(GunProjectile)是先进行射线检测判断命中，然后不走neoforge的projectile hit事件，手动处理
- MachineMax有mixin projectile来set hit part，但可能永远是慢一拍的（枪射物的射线检测会先执行），目前MachineMax具体设计尚不清晰，需要研究

- 当前IDEA打开了CGC和CGC compat两个窗口，可供浏览源码
- MachineMax的源码在CGC compat窗口浏览
- 当前主窗口是CGC，但是代码修改要写到CGC compat里

辅助快速浏览：
- CGC枪射物攻击判定在dev.xcolorful.customgun.core.projectile.impact包下的几个类
- 目测MachineMax的Entity和handler集中在io.github.sweetzonzi.machine_max.common.entity包下的几个类

设计时注意：
- mixin可以保持简洁，将逻辑代理到dev.xcolorful.cgccompat.machinemax.core.projectile.impact._ProjectileHit（跟CGC的_ProjectileHit相似的代理方式）
- CGC的射线检测获取到实体，但可能不一定是目标的，同时CGC子弹可以有多穿透数，所以判定时不提前预测之后会不会打到别的实体，只以CGC射线检测的为准，当带入MachineMax的碰撞箱判定没通过的时候，返回false来表述没消耗掉穿透数（不打该部位）

现在开始调查并完成，应该只需要修改mixin和_ProjectileHit两个类
```

# 

I'll research both codebases to understand MachineMax's design and how CGC's gun projectile hit detection works, then implement the compatibility layer. Let me start by exploring the structure.

I now have a complete understanding of both codebases. Let me verify my understanding is complete before implementing, then write the two files.

Key findings:
- **CGC**: `GunProjectile extends Projectile` and does its own raycast in `ProjectilePhysicsManager.physicTick` (ray = `position() → position() + deltaMovement()`), then `impactTick` → `IBulletVictimEntity.cgc$onProjectileImpact` on the hit entity. The compat's `MMPartEntityMixin` implements `IBulletVictimEntity`.
- **MachineMax**: `PartHitHandler` (NeoForge `ProjectileImpactEvent`) does a physics raycast against `WorldSnapshot` to find the exact `SubPart` + `HitBox` + hit point/normal, then `MMPartEntity.hurt()` routes to `subPart.onHurt(PartDamageData, damage)`.

Now writing the implementation:

Now let me verify the code compiles. Let me check for problems in both files via the IDE.

The method is `triangleIndex()` (no `get` prefix). Let me fix it.

Both files are implemented and the build passes. Here's what I found and did.

## MachineMax's design (researched from the decompiled jar)

- **`MMPartEntity`** ([MMPartEntity.class](libs/MachineMax-1.21.1-1.0.1-beta.1.jar)) is a `VehicleEntity` wrapping a physics `SubPart`. Its `hurt()` reads hit info off the projectile via `IProjectileMixin` (MachineMax's `ProjectileMixin`, which adds `machine_Max$hitBox`/`hitPoint`/`hitNormal`/`hitSubPart` to every `Projectile`) and forwards to `subPart.onHurt(PartDamageData, damage)`.
- **`PartHitHandler`** does the actual hit detection. Its `ProjectileImpactEvent` handler raycasts the physics world (`SparkLevel.getPhysicsLevel(level).getWorld().getWorldSnapshot().rayTest(start, end)`) along the projectile's path to find the first non-wheel, active-`HitBox` `SubPart`, then sets those four fields on the projectile. There's also an `EntityTickEvent.Pre` handler that does the same raycast one tick early — that's the "always one step behind" path you described.
- **CGC's `GunProjectile extends Projectile`** but does its own raycast (`ProjectilePhysicsManager.physicTick`) and calls `IBulletVictimEntity.cgc$onProjectileImpact` manually, never firing NeoForge's projectile-hit events — so `PartHitHandler` never runs correctly for it.

## What I changed (the two files)

**[MMPartEntityMixin.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/mixin/entity/MMPartEntityMixin.java)** — `cgc$onProjectileImpact` now just delegates to `_ProjectileHit`.

**[_ProjectileHit.java](core/src/main/java/dev/xcolorful/cgccompat/machinemax/core/projectile/impact/_ProjectileHit.java)** — `onProjectileHitEntity`:
1. Reconstructs CGC's exact ray (`gunProjectile.position()` → `+ deltaMovement()`, since impact runs before `physicMove`).
2. Raycasts the physics `WorldSnapshot`, finding the first non-wheel, active-`HitBox` `SubPart`.
3. If no active hitbox, or the hit `SubPart` isn't `partEntity.subPart`, returns **false** (doesn't consume penetration).
4. Otherwise computes damage (CGC distance-falloff + headshot), builds `PartDamageData` with the CGC `BULLET` damage source, and calls `subPart.onHurt(data, damage)` → returns **true**.

## Design decisions to confirm

Two things I had to decide without an explicit instruction, and want to flag for you:

1. **Armor-ignore isn't mapped.** CGC splits damage into normal (`BULLET`) vs armor-ignoring (`PIERCER`) via `armorIgnorePercent`. I pass the full damage with a single `BULLET` source and let MachineMax's own armor/RHA model (`hitBox.modifyPiercing/modifyDamage`) handle penetration — its default treats non-`bypasses_armor` sources as 50% penetration. If you want CGC's `armorIgnorePercent` to influence `modifyPiercing`, that's a follow-up.
2. **No CGC events/messages for parts.** I don't post `ProjectileHitEntityEvent`/kill/hit messages (parts have durability, not health, and a "kill feed" doesn't apply). The tracer/impact particle effect still works because CGC's `impactEffect` runs independently in `impactTick` after `processed` returns true.

Verification: the compat project builds clean (`build_project` → no problems), and I only touched those two files. The other pending changes in that repo (`cgccmachinemax.mixins.json`, `docs/`) were already there before I started — I left them untouched. I have not committed or pushed anything.

# 

```
你把目前研究到的写成体系文档，参考CGC文档体系下的渲染（./docs/architecture/client/render/）、修饰（./docs/architecture/core/entity/shooter/modifier/）
- Home要给出MachineMax原本设计的投掷物射线检测和出伤的这套流程的Mermaid
- 目录下需要有描述这个兼容模组目前的修改点，对这个流程的影响（带入CGC判定流程）

当别人浏览这个项目，需要通过文档快速了解这个体系，但仅描述体系不够，参考"#36 Render preparation.md"当时的需求，不过这个兼容模组只需要解释MachineMax的跟出伤相关的流程即可，不需要完整表述其tick物理等无关内容

文档编写的格式要求同已有文档：
- Home.md需要包含一个全局概览的Mermaid
- Markdown列表开头不使用加粗
- 对于有冒号的列表，冒号左边不使用加粗
- 表格语法里"|"之间跟文本不要有空格
- 合理设置标题层级，不得滥用二级标题，不得把标题当数字列表用
- 不需要提及CGC里哪些是TODO或待实现
- 不得大量复制代码到文档里，考虑使用文本描述或Mermaid图来表示关系，不得使用Class diagrams来规避“不得大量复制代码”的限制
- 不得通过“列出全部枚举”或“罗列堆砌代码实现部分”的方式来作说明

本地文档编写范围为./docs/architecture/core/projectile/
```

# 

I'll study the CGC documentation style first, then write the compat docs. Let me explore the reference docs.

I have a complete picture of the style and terminology. Now I'll write the three docs. The target directory is the compat repo's `docs/architecture/core/projectile/` (currently empty).

三个文档已写入兼容模组仓库的 `docs/architecture/core/projectile/`（该目录此前为空），未改动其他目录下的文档。

## 交付内容

|文件|内容|
|---|---|
|[Home.md](docs/architecture/core/projectile/Home.md)|入口。给出 MachineMax 原本「投掷物射线检测 → 命中信息写入 → 出伤路由 → 装甲穿透」这条流程的全局 Mermaid，加出伤主线概述与导航|
|[machine-max-damage-flow.md](docs/architecture/core/projectile/machine-max-damage-flow.md)|MachineMax 原本的投掷物出伤流程：部件实体表示、`PartHitHandler` 命中检测、`MMPartEntity.hurt` 出伤路由、`SubPart.onHurt` 装甲/穿透模型|
|[cgc-compat.md](docs/architecture/core/projectile/cgc-compat.md)|兼容模组的修改点（`MMPartEntityMixin` + `_ProjectileHit`）、带入 CGC 判定流程后的行为（含 Mermaid）、与 MachineMax 原本流程的对应表|

## 关键设计表述

- **Home 的 Mermaid** 按你说的「投掷物射线检测和出伤这套流程」组织，以 `ProjectileImpactEvent` → 物理射线检测 → `subPart` 匹配判定 → `IProjectileMixin` 写入 → `hurt` → `onHurt` 为主线，并把「未命中活动命中箱 / 子部件不匹配 → 取消事件」作为旁路。
- **cgc-compat.md** 明确了两处差异（CGC 不走 `ProjectileImpactEvent`、不经过 MachineMax 装甲模型）和对应的修改点，并强调「判定只针对当前实体，不预测后续命中其他实体；返回 `false` 时由 CGC 的命中循环处理后续实体」，对应你给的设计约束。
- 术语沿用了 CGC 文档的既有译名（枪射物 / 投掷物 / 受弹实体 / 穿透数），MachineMax 侧用「部件实体 / 子部件 / 命中箱」。

## 格式核对

- Home 含全局 Mermaid；列表无加粗、冒号左无加粗；表格 `|` 间无空格；标题层级只用 `#`/`##`，未用标题当编号；无代码复制、无 Class diagram、无枚举罗列；未提 CGC 的 TODO/待实现。

两点需要你确认：

1. **语言**：我按 `client/render` 那套写成了纯中文。你仓库里 `docs/architecture/Home.md`、`core/Home.md` 和 CGC 的 `core` 文档是双语（含 `[English](#English)`）的——如果这套 `core/projectile` 也要双语，我可以补上英文部分。
2. **父级导航**：按你「编写范围仅 `./docs/architecture/core/projectile/`」的约束，我没有改动 `docs/architecture/core/Home.md`，所以目前父级导航还没挂上这套新文档的链接。需要的话我可以补一行入口。

# 

```
你刚才表述的修改可能跟我想要的不一样，我重新表述需求，你检查刚才的实现以及可能需要调整相应文档：
- CGC枪射物即使命中轮胎也应该打轮胎，MachineMax应该是支持攻击轮胎的，如果MachineMax本身的逻辑就是保护轮胎，那么忽略这个，否则命中轮胎时不应该给他特判跳过
- 如果整个载具是一个实体，即CGC射线检测只会获取到一个的话，应该检查是否有命中哪个部位，除非完全碰不到；如果整个载具是多个实体，但是MachineMax要求出伤给在载具main part的话，那么打中子part时应该要能正确给伤害，而不是忽略掉
- 正常来说，命中哪就应该对哪造成损伤，除非这个地方已经判定为消失了
```

# 

The user is correcting my design. Let me investigate MachineMax's actual wheel/entity/main-part structure before adjusting the code and docs.

I've now confirmed the key facts. Let me summarize what I found before making the fix:

1. **Entity structure**: each `SubPart` gets its own `MMPartEntity` (`SubPart.refreshPartEntity()` → `new MMPartEntity(level, this)` + `addFreshEntity`). So a vehicle is **multiple entities**, one per subPart.
    
2. **Wheels**: a wheel hitbox creates **two** child shapes — a sphere (the rolling "tread" surface, `isWheelSurface=true`) and a cylinder (the tire body, `isWheelSurface=false`). MachineMax's `PartHitHandler` skips only the tread sphere (`isWheel && isWheelSurface`) but **does** hit the tire body cylinder. So tires are attackable; the skip is not "tire protection".
    
3. **The real bug**: my `if (hitSubPart != subPart) return false` finds the _front-most_ physics hit and requires it to match. For a multi-entity vehicle with pierce, the bullet hits A then B, but the physics ray always returns the front-most (A) — so B's callback sees A (≠ B) and B never gets damaged.
    

The correct logic: for the current entity's `subPart`, find whether the physics ray hits _that_ subPart (active hitbox), and damage it.

The code is fixed and compiles. Now let me update the docs to reflect the corrected logic.

I've completed the investigation and fix. Here's what I found and changed.

## 调查结论

1. **实体结构**：载具是**多个实体**。每个 `SubPart` 通过 `SubPart.refreshPartEntity()` 创建并持有自己的 `MMPartEntity`（`new MMPartEntity(level, this)` + `addFreshEntity`）。`VehicleCore.refreshAllPartEntities()` 会为每个 `SubPart` 补建实体。
    
2. **没有 "main part" 出伤**：`SubPart.onHurt` 直接把损伤计入**该子部件自身**的耐久度，再按 `vehicleDamageRate` 折算到载具整体血量。`Part.rootSubPart` 只是坐标/变换的根（质量最大的子部件），不是出伤目标。所以「命中哪个子部件就对哪个子部件出伤」就是正确语义，不存在需要转发到 main part 的路径。
    
3. **轮胎可被攻击**：一个 `wheel` 命中箱会生成**两个**子形状——胎面球体（`isWheelSurface=true`，用于滚动）和胎体圆柱（`isWheelSurface=false`）。MachineMax 的 `PartHitHandler` 只跳过「胎面球体」（`isWheel && isWheelSurface`），**胎体圆柱仍然命中**。所以这不是「保护轮胎」，轮胎是可攻击的。
    

## 修复的 bug

我之前的实现是「找到**最前面**的活动命中箱，要求它 == 实体自身的 subPart，否则 return false」。对多实体载具 + 穿透数时这是错的：子弹依次打 A、B，但物理射线每次都返回最前面的 A，于是 B 的回调看到 A（≠B）→ 永远 return false → **B 被打不到**。

修正后 `_ProjectileHit` 的逻辑改为：在物理射线结果里**找属于当前实体 `subPart` 的活动命中箱**（`candidate != subPart` 直接跳过，而非找最前面再比对）。这样：

- 命中 A → 打 A；命中 B → 打 B（每个子部件各打一次）。
- A 的 AABB 是假阳性（物理形状不在路径上）→ 找不到 → return false。
- 命中轮胎 → 跳过胎面球体、命中胎体圆柱 → 轮胎被打到。
- 已销毁的子部件物理体被移除 → 找不到 → return false。

代码编译无错（`get_file_problems` 为空；完整 Gradle 构建因 NeoGradle 过慢超时，非代码错误）。

## 文档同步

- [machine-max-damage-flow.md](app://localhost/epitaxy/docs/architecture/core/projectile/machine-max-damage-flow.md)：把「非车轮表面」改为「非车轮滚动表面」，并补一句「车轮由胎面球体 + 胎体圆柱构成，胎面球体被跳过、胎体仍可命中，轮胎因此可被攻击」。
- [cgc-compat.md](app://localhost/epitaxy/docs/architecture/core/projectile/cgc-compat.md)：Mermaid 去掉「与实体自身 subPart 一致」的判定分支，改为「找属于该实体 subPart 的命中」；步骤 3/4/5 同步改成「在命中结果里找属于当前实体 subPart 的活动命中箱」，并明确「没找到才 return false」。

一个需要你确认的语义点：**车轮胎面球体**我仍按 MachineMax 原样跳过（`isWheel && isWheelSurface`）。如果你想要「连胎面球体也直接命中」（不跳过、命中球面而不是胎体圆柱），告诉我，我把这个条件也去掉——两者损伤落到同一个 `HitBox`，差别只在命中点/法线取的是球面还是圆柱面。
