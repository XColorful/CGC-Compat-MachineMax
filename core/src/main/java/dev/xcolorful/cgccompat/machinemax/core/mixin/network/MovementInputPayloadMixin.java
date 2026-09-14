package dev.xcolorful.cgccompat.machinemax.core.mixin.network;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.MovementInputPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 驾驶输入必须来自该座位上的乘坐者，且回应只发给发起者本人。
 * <p>
 * MachineMax 的 {@code serverHandler} 只按 {@code vehicleUUID/partUUID/subPartName/subSystemName}
 * 定位子系统并 {@code setMoveInputSignal}，<b>完全不看发送者是谁</b>——任何客户端只要报出别人驾驶位的
 * 名字就能驱动别人的车；而这些输入还会被 {@code sendToPlayersInDimension} 广播给维度内所有玩家，
 * 让每个客户端都拿着别人的驾驶输入去推进自己那份本地载具状态。
 * <p>
 * 服务端的权威状态是：只有 {@code SeatSubsystem#setPassenger} 会写
 * {@code machine_Max$setControllingSubsystem}，所以「发送者正操控的那个座位，就是本包寻址的座位」
 * 等价于「发送者确实坐在那个座位上」。不满足就丢弃该包，既不处理也不回声。
 * <p>
 * 判据用「座位名 + 部件/子部件/载具 UUID」而不是子系统对象本身：载具结构变化（零件被打掉、
 * 合并等）会重建子系统实例，而玩家手上的 {@code controllingSubsystem} 仍指向旧实例，
 * 按对象相等判断会把正常驾驶输入误杀。
 * <p>
 * 回声同样收窄为发起者本人：位姿由服务端权威下发（{@code SubPartSyncPayload} 每 tick 同步），
 * 旁观客户端不需要靠他人的驾驶输入推算本地状态；驱动方自己的本地预演仍由这份回声维持。
 */
@Mixin(MovementInputPayload.class)
public abstract class MovementInputPayloadMixin {

    @Inject(method = "serverHandler", at = @At("HEAD"), cancellable = true)
    private static void cgcc$onlyControllerMayDrive(MovementInputPayload payload, IPayloadContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer sender)) return;
        if (!cgcc$senderControlsAddressedSeat(payload, sender)) ci.cancel();
    }

    @Redirect(
            method = "serverHandler",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersInDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
            )
    )
    private static void cgcc$echoToSenderOnly(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload[] rest,
                                             MovementInputPayload self, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            PacketDistributor.sendToPlayer(sender, payload);
        }
    }

    @Unique
    private static boolean cgcc$senderControlsAddressedSeat(MovementInputPayload payload, ServerPlayer sender) {
        AbstractControllableSubsystem controller = ((IEntityMixin) sender).machine_Max$getControllingSubsystem();
        if (controller == null || !controller.name.equals(payload.subSystemName())) return false;
        if (!(controller.getOwner() instanceof SubPart seatSubPart)) return false;
        if (!seatSubPart.part.uuid.equals(payload.partUUID())) return false;

        return seatSubPart.name.equals(payload.subPartName())
                && seatSubPart.part.vehicle != null
                && seatSubPart.part.vehicle.uuid.equals(payload.vehicleUUID());
    }
}
