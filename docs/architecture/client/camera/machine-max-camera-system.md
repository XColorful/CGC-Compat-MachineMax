[English](#English)

# MachineMax 摄像机体系

> MachineMax 原本如何接管玩家的摄像机位置与朝向。本文只覆盖与 CGC 摄像机冲突相关的流程，不涉及载具的装配与物理 tick。

## 组成

MachineMax 的摄像机体系集中在三个类与三个 Mixin 上，包路径如下：

|类|包路径|职责|
|---|---|---|
|`CameraController`|`io.github.sweetzonzi.machine_max.client.input`|订阅摄像机相关事件，执行位置 / 朝向 / 距离 / FOV 覆盖|
|`CameraMixin`|`io.github.sweetzonzi.machine_max.mixin`|在 `Camera.setup` 末尾发 `ComputeCameraPosEvent` 并应用其结果|
|`MouseHandlerMixin`|`io.github.sweetzonzi.machine_max.mixin`|在 `turnPlayer` 前把鼠标增量喂给 `CameraController.turnCamera`|
|`RawInputHandler`|`io.github.sweetzonzi.machine_max.client.input`|维护 `freeCam` 标志，乘坐时吞掉原版输入|
|`ComputeCameraPosEvent`|`io.github.sweetzonzi.machine_max.client.event`|携带摄像机位置的 `ViewportEvent` 子类|

## 摄像机位置

`CameraMixin` 在 `Camera.setup()` 调用 `setPosition` **之后**注入：先发 `ComputeCameraPosEvent`，再用事件里的 `cameraPos` 重新 `setPosition`。

`CameraController.updateCameraPos` 订阅该事件，仅当摄像机实体正乘坐 `SeatSubsystem` 时改写位置：

- 非第一人称且 `focusOnCenter()`：改为「载具中心插值坐标 + 旋转后的 thirdPersonOffset」。
- 否则（第一人称）：改为「座位 locator 的插值世界坐标 + 旋转后的 firstPersonOffset」。

```java
Transform transform = seat.getOwner().getSubPart()
        .getLerpedLocatorWorldTransform(seat.attr.locator, new Transform().setTranslation(0, 1.1f, 0), partialTick);
event.setCameraPos(SparkMathKt.toVec3(transform.getTranslation())
        .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.firstPersonOffset()),
                SparkMathKt.toBQuaternion(seatRot)))));
```

`seatRot` 取自 `seat.getSubPart().getWorldPositionMatrix(partialTick).getNormalizedRotation(...)`。

## 摄像机朝向与平滑

`CameraController` 维护一组静态状态：`aimPitch/Yaw/Roll`（目标朝向）、`targetViewPitch/Yaw/Roll`（EMA 中间值）、`pitch/Yaw/Roll`（实际平滑值）。

`updateCameraRot` 订阅 `ViewportEvent.ComputeCameraAngles`，每帧执行：

```java
float lerp = 0.25f;
pitch = (1 - lerp) * pitch + lerp * targetViewPitch;
yaw   = (1 - lerp) * yaw   + lerp * targetViewYaw;
roll  = (1 - lerp) * roll  + lerp * targetViewRoll;

if (subsystem instanceof SeatSubsystem seat) {
    if (type.isFirstPerson() || ControlPreference.shouldFollowPose(seat)) {
        // 座位姿态四元数反解欧拉角 → event.setPitch/Yaw/Roll，然后 break
    }
}
// 关键：下面这个覆盖在“未乘坐”时也会走到
event.setPitch(pitch);
event.setYaw(yaw);
event.setRoll(roll);
```

`aimPitch/Yaw` 有两个来源：`MouseHandlerMixin.beforeTurnPlayer` 调 `turnCamera` 累加鼠标增量，以及 `updateCameraRot` 非座位分支每帧用 `entity.getViewXRot/YRot(partialTick)` 覆盖。非座位分支随后执行：

```java
aimPitch = entity.getViewXRot(partialTick);
aimYaw  = entity.getViewYRot(partialTick);
targetViewPitch = 0.9f * targetViewPitch + 0.1f * aimPitch;
targetViewYaw   = 0.9f * targetViewYaw   + 0.1f * aimYaw;
```

因此**即使玩家没有乘坐载具**，摄像机朝向也被一份 EMA + lerp 双重平滑值覆盖，滞后于玩家真实朝向。这是 CGC 开镜回弹与后坐力被拖慢的根源。

## 乘客与座位的实体关系

乘坐 MachineMax 座位时，乘客是原版 `MMPartEntity` 的骑乘者（`startRiding`），但第一人称摄像机位置按上文的座位 locator 计算，而非乘客实体坐标 + 眼睛高度。乘客实体上通过 `IEntityMixin#machine_Max$getControllingSubsystem()` 挂着一个 `SeatSubsystem`，双端一致，可用于服务端还原座位视线位置。

## 与 CGC 的冲突点

|MachineMax 行为|对 CGC 的影响|
|---|---|
|`updateCameraRot` 无条件平滑覆盖朝向|开镜后鼠标移动回弹、后坐力被拖慢压平|
|`CameraMixin` 在座位里改写位置|乘客实体坐标 ≠ 视线位置，服务端出弹位置偏移|

# English

> How MachineMax takes over the player's camera position and rotation. This document only covers the flows that conflict with CGC's camera, not vehicle assembly or physics ticks.

## Composition

MachineMax's camera system is concentrated in three classes and three mixins:

|Class|Package|Responsibility|
|---|---|---|
|`CameraController`|`io.github.sweetzonzi.machine_max.client.input`|Subscribes to camera events; overrides position / rotation / distance / FOV|
|`CameraMixin`|`io.github.sweetzonzi.machine_max.mixin`|Fires `ComputeCameraPosEvent` at the end of `Camera.setup` and applies its result|
|`MouseHandlerMixin`|`io.github.sweetzonzi.machine_max.mixin`|Feeds mouse delta to `CameraController.turnCamera` before `turnPlayer`|
|`RawInputHandler`|`io.github.sweetzonzi.machine_max.client.input`|Maintains `freeCam`; swallows vanilla input while seated|
|`ComputeCameraPosEvent`|`io.github.sweetzonzi.machine_max.client.event`|A `ViewportEvent` subclass carrying the camera position|

## Camera position

`CameraMixin` injects **after** `Camera.setup()` calls `setPosition`: it fires `ComputeCameraPosEvent`, then re-calls `setPosition` with the event's `cameraPos`.

`CameraController.updateCameraPos` subscribes and rewrites the position only while the camera entity is riding a `SeatSubsystem`:

- Not first person and `focusOnCenter()`: "vehicle-center interpolated coordinate + rotated thirdPersonOffset".
- Otherwise (first person): "seat locator interpolated world coordinate + rotated firstPersonOffset".

```java
Transform transform = seat.getOwner().getSubPart()
        .getLerpedLocatorWorldTransform(seat.attr.locator, new Transform().setTranslation(0, 1.1f, 0), partialTick);
event.setCameraPos(SparkMathKt.toVec3(transform.getTranslation())
        .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.firstPersonOffset()),
                SparkMathKt.toBQuaternion(seatRot)))));
```

`seatRot` comes from `seat.getSubPart().getWorldPositionMatrix(partialTick).getNormalizedRotation(...)`.

## Camera rotation and smoothing

`CameraController` maintains a set of static state: `aimPitch/Yaw/Roll` (target), `targetViewPitch/Yaw/Roll` (EMA intermediate), `pitch/Yaw/Roll` (actual smoothed value).

`updateCameraRot` subscribes to `ViewportEvent.ComputeCameraAngles` and runs every frame:

```java
float lerp = 0.25f;
pitch = (1 - lerp) * pitch + lerp * targetViewPitch;
yaw   = (1 - lerp) * yaw   + lerp * targetViewYaw;
roll  = (1 - lerp) * roll  + lerp * targetViewRoll;

if (subsystem instanceof SeatSubsystem seat) {
    if (type.isFirstPerson() || ControlPreference.shouldFollowPose(seat)) {
        // seat pose quaternion → Euler angles → event.setPitch/Yaw/Roll, then break
    }
}
// Key: this override also runs when NOT seated
event.setPitch(pitch);
event.setYaw(yaw);
event.setRoll(roll);
```

`aimPitch/Yaw` has two sources: `MouseHandlerMixin.beforeTurnPlayer` calls `turnCamera` to accumulate the mouse delta, and `updateCameraRot`'s non-seat branch overwrites it each frame from `entity.getViewXRot/YRot(partialTick)`. The non-seat branch then does:

```java
aimPitch = entity.getViewXRot(partialTick);
aimYaw  = entity.getViewYRot(partialTick);
targetViewPitch = 0.9f * targetViewPitch + 0.1f * aimPitch;
targetViewYaw   = 0.9f * targetViewYaw   + 0.1f * aimYaw;
```

So **even when the player is not riding a vehicle**, the camera rotation is overridden by a double (EMA + lerp) smoothed value that lags behind the player's true orientation. This is the root of CGC's scope rebound and damped recoil.

## Passenger–seat entity relationship

While riding a MachineMax seat, the passenger is a vanilla rider of `MMPartEntity` (`startRiding`), but the first-person camera position is computed from the seat locator as above, not from the passenger entity coordinate + eye height. The passenger entity carries a `SeatSubsystem` via `IEntityMixin#machine_Max$getControllingSubsystem()` on both sides, which can be used to reconstruct the seat view position on the server.

## Conflict points with CGC

|MachineMax behavior|Impact on CGC|
|---|---|
|`updateCameraRot` unconditionally smooths/overrides rotation|Scope rebound when moving; recoil damped/flattened|
|`CameraMixin` rewrites position while seated|Passenger entity coordinate ≠ view position; server bullet spawn offset|
