[English](#English)

# 兼容模组修改点

> 兼容模组如何在 MachineMax 的摄像机接管与 CGC 的枪械摄像机之间做矫正，以及这些修改落在 CGC 的哪个原有功能位置。

## 修改点

兼容模组只改两个类，都遵循「与 CGC 原有功能对应包路径」的约定：

|修改点|包路径|对应 CGC 功能位置|作用|
|---|---|---|---|
|`GunCameraCompat`|`client.renderer.item.gun`|CGC `client.renderer.item.gun.GunCameraHelper`|非乘坐时还原摄像机朝向，抵消 MachineMax 的平滑覆盖|
|`GunProjectileMixin`|`core.mixin.entity.projectile`|CGC `core.entity.projectile.GunProjectile`|乘坐座位时把出弹位置矫正到座位视线位置|

## 摄像机朝向矫正（开镜回弹 / 后坐力）

`GunCameraCompat` 实现 CGC 的 `IEventHandler`，由 `CgccMachineMaxClient.init()` 通过 `CoreEventHandlers.register` 注册到 `COMPUTE_CAMERA_ANGLES_EVENT`，优先级取 `EventPriority.LOW`（比 MachineMax 的 NORMAL 低一级），在 MachineMax 与 CGC 后坐力之后运行：

1. 若摄像机实体的 `IEntityMixin#machine_Max$getControllingSubsystem()` 是 `SeatSubsystem`，说明玩家正在乘坐座位，直接放行 MachineMax 的接管。
2. 否则把 `event.setPitch/Yaw` 还原为 `entity.getViewXRot/YRot(partialTick)`，即玩家真实朝向；pitch 用 `Mth.clamp` 压回 `[-90, 90]`。

```java
if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
    return;
}
float partialTick = (float) event.getPartialTick();
event.setPitch(Mth.clamp(entity.getViewXRot(partialTick), -90.0F, 90.0F));
event.setYaw(entity.getViewYRot(partialTick));
```

CGC 后坐力通过 `LocalPlayer#setXRot` 修改玩家真实朝向，还原后即恢复为 CGC 单独存在时的表现，回弹与后坐力拖慢随之消失。后坐力可能把 pitch 顶出 `[-90, 90]`，这里 clamp 回原版范围，避免开镜压枪时镜头被顶到天上无法下拉。`roll` 不主动清零，避免抹掉 CGC 资源包 camera 动画的 roll。

## 出弹位置矫正（乘坐射击）

`GunProjectileMixin` 在 CGC `GunProjectile` 带射手参数的构造器末尾注入：若射手正乘坐 MachineMax 座位，则按 MachineMax `updateCameraPos` 的第一人称分支重算出弹位置并覆盖，同时同步 `shootPos`（距离衰减伤害基准）。

服务端没有渲染 partialTick，因此用 `getSeatPointWorldTransform()`（非插值版）与 `partialTick = 1.0` 的世界旋转：

```java
Quaternionf seatRot = new Quaternionf();
seat.getSubPart().getWorldPositionMatrix(1.0F).getNormalizedRotation(seatRot);
Vec3 seatPos = SparkMathKt.toVec3(seat.getSeatPointWorldTransform().getTranslation());
Vec3 worldOffset = SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
        PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.firstPersonOffset()),
        SparkMathKt.toBQuaternion(seatRot)));
// seatPos.add(worldOffset) → 覆盖出生点与 shootPos
```

# English

> How the compat mod corrects the interaction between MachineMax's camera takeover and CGC's gun camera, and which original CGC feature location each change maps to.

## Changes

The compat mod only changes two classes, both following the "mirror CGC's original feature package" convention:

|Change|Package|Corresponding CGC location|Effect|
|---|---|---|---|
|`GunCameraCompat`|`client.renderer.item.gun`|CGC `client.renderer.item.gun.GunCameraHelper`|Restores camera rotation when not seated, cancelling MachineMax's smoothing override|
|`GunProjectileMixin`|`core.mixin.entity.projectile`|CGC `core.entity.projectile.GunProjectile`|Corrects bullet spawn position to the seat view position while seated|

## Camera rotation correction (scope rebound / recoil)

`GunCameraCompat` implements CGC's `IEventHandler`, registered by `CgccMachineMaxClient.init()` via `CoreEventHandlers.register` for `COMPUTE_CAMERA_ANGLES_EVENT` at `EventPriority.LOW` (one level below MachineMax's NORMAL), running after MachineMax and CGC's recoil:

1. If the camera entity's `IEntityMixin#machine_Max$getControllingSubsystem()` is a `SeatSubsystem`, the player is seated — let MachineMax take over.
2. Otherwise restore `event.setPitch/Yaw` to `entity.getViewXRot/YRot(partialTick)`, i.e. the player's true orientation; clamp pitch back to `[-90, 90]` via `Mth.clamp`.

```java
if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
    return;
}
float partialTick = (float) event.getPartialTick();
event.setPitch(Mth.clamp(entity.getViewXRot(partialTick), -90.0F, 90.0F));
event.setYaw(entity.getViewYRot(partialTick));
```

CGC's recoil modifies the player's true orientation via `LocalPlayer#setXRot`; after restoring, it behaves as if CGC ran alone, and the rebound and damped recoil disappear. Recoil can push pitch beyond `[-90, 90]`; clamping it back keeps the camera from being pushed skyward when compensating while scoped. `roll` is deliberately left untouched to preserve CGC resource-pack camera animation roll.

## Bullet spawn position correction (shooting while seated)

`GunProjectileMixin` injects at the tail of CGC `GunProjectile`'s shooter-bearing constructor: if the shooter is riding a MachineMax seat, it recomputes the spawn position using MachineMax `updateCameraPos`'s first-person branch and overwrites it, also updating `shootPos` (the distance-falloff damage baseline).

The server has no render partialTick, so it uses `getSeatPointWorldTransform()` (non-lerped) with `partialTick = 1.0` world rotation:

```java
Quaternionf seatRot = new Quaternionf();
seat.getSubPart().getWorldPositionMatrix(1.0F).getNormalizedRotation(seatRot);
Vec3 seatPos = SparkMathKt.toVec3(seat.getSeatPointWorldTransform().getTranslation());
Vec3 worldOffset = SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
        PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.firstPersonOffset()),
        SparkMathKt.toBQuaternion(seatRot)));
// seatPos.add(worldOffset) → overwrite spawn position and shootPos
```
