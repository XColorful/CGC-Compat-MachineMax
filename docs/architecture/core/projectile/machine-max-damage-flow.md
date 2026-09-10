# MachineMax 投掷物出伤流程

> MachineMax 原本如何让一颗原版投掷物命中载具部件并造成伤害。本文只覆盖与出伤相关的流程，不涉及载具的 tick 物理与装配。

## 部件实体

MachineMax 的载具由多个 `SubPart`（物理刚体）组成，每个 `SubPart` 在 Minecraft 世界里对应一个 `MMPartEntity`（`VehicleEntity`）。这个实体用于承接命中与交互：

- `MMPartEntity` 的 `subPart` 字段指向它代表的物理子部件。
- 实体的原版碰撞箱由子部件物理包围盒按比例缩小得到，比真实的物理碰撞形状更粗略。

物理碰撞形状由各命中箱组成，比原版碰撞箱更精确。命中时先用原版碰撞箱粗筛出实体，再由物理射线检测精确定位命中箱。

## 命中检测

原版投掷物命中 `MMPartEntity` 时，NeoForge 触发 `ProjectileImpactEvent`。MachineMax 的 `PartHitHandler` 拦截该事件：

1. 把投掷物当作 `IProjectileMixin` 使用。MachineMax 通过 mixin 让所有 `Projectile` 都能携带命中信息。
2. 沿投掷物飞行路径对物理世界做射线检测。
3. 遍历命中结果，找到第一个命中活动命中箱、且非车轮滚动表面的 `SubPart`。
4. 命中的 `SubPart` 与实体自身一致时，把命中信息写入投掷物；否则取消事件，视为这次命中不成立。

命中信息包含四项：命中的 `SubPart`、`HitBox`、命中点与命中法线。

车轮命中箱由胎面球体与胎体圆柱两个形状构成：胎面球体用于滚动，判定时被跳过；胎体圆柱仍参与命中，轮胎因此可以被攻击。

## 出伤路由

事件通过后，原版沿自己的流程调用投掷物的 `onHit`，最终进入 `MMPartEntity.hurt`：

- 从投掷物读取命中信息。
- 校验命中的 `SubPart` 是否就是实体自身的 `subPart`。
- 匹配时用命中信息与伤害值构造 `PartDamageData`，调用 `subPart.onHurt`。

## 装甲与穿透模型

`SubPart.onHurt` 把一次命中转成实际损伤：

- 由命中箱的装甲厚度与命中角度算出等效装甲。
- 命中箱的穿透修改器决定穿透值。
- 穿透成功的伤害经命中箱的伤害修改器折算后计入耐久度，并向相邻连接件传递冲击。
