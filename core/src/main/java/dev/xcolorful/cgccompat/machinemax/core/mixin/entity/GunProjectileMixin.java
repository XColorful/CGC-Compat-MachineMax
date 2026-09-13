package dev.xcolorful.cgccompat.machinemax.core.mixin.entity;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import dev.xcolorful.customgun.core.entity.projectile.GunProjectile;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 对 CGC {@code GunProjectile} 的 MachineMax 兼容修正（服务端出弹位置）。
 * <p>
 * CGC 生成子弹时用 {@code IShooterState#getShooterEyePos}（即玩家实体坐标 + 眼睛高度）
 * 作为出生点。当玩家坐在 MachineMax 座位里时，玩家实体实际是骑乘在 {@code MMPartEntity}
 * 上，其原版坐标并不等于 MachineMax 第一人称摄像机所在的位置（座位 locator 的世界坐标 +
 * firstPersonOffset）。这会导致服务端生成的子弹初始位置与客户端瞄准的视线位置不一致。
 * <p>
 * 这里在 {@code GunProjectile} 构造完成后，若射手正乘坐 MachineMax 座位，则按
 * MachineMax {@code CameraController#updateCameraPos} 的第一人称分支重新计算出生点并覆盖，
 * 同时同步 {@code shootPos}（距离衰减伤害以此为基准），使两者保持一致。
 */
@Mixin(GunProjectile.class)
public abstract class GunProjectileMixin {

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At("TAIL")
    )
    private void cgcc$adjustSpawnPosToSeat(CallbackInfo ci) {
        GunProjectile self = (GunProjectile) (Object) this;
        if (!(self.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        // 射手正在乘坐 MachineMax 座位时才需要矫正。
        if (!(((IEntityMixin) shooter).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat)) {
            return;
        }

        // 覆盖出生点；shootPos 是距离衰减伤害的基准，需要一并更新。
        Vec3 seatEyePos = cgcc$computeSeatEyePos(seat);
        self.setPos(seatEyePos);
        self.setShootPos(self, seatEyePos);
    }

    /**
     * 复刻 MachineMax {@code CameraController#updateCameraPos} 的第一人称分支：
     * 座位 locator 的世界坐标 + 旋转后的 firstPersonOffset。
     * <p>
     * 服务端没有渲染的 partialTick，这里用 {@code getSeatPointWorldTransform()}（非插值版）
     * 与 partialTick = 1.0 的世界旋转，与客户端当前 tick 的视线位置保持一致。
     */
    @Unique
    private static Vec3 cgcc$computeSeatEyePos(SeatSubsystem seat) {
        Quaternionf seatRot = new Quaternionf();
        seat.getSubPart().getWorldPositionMatrix(1.0F).getNormalizedRotation(seatRot);

        Vec3 seatPos = SparkMathKt.toVec3(seat.getSeatPointWorldTransform().getTranslation());
        Vec3 firstPersonOffset = seat.attr.staticAttribute.views.firstPersonOffset();
        Vec3 worldOffset = SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                PhysicsHelperKt.toBVector3f(firstPersonOffset),
                SparkMathKt.toBQuaternion(seatRot)
        ));

        return seatPos.add(worldOffset);
    }
}
