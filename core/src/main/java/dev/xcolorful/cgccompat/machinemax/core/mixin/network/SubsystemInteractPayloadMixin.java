package dev.xcolorful.cgccompat.machinemax.core.mixin.network;

import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.network.payload.SubsystemInteractPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * 互动包的回声只发给发起者本人。
 * <p>
 * MachineMax 的 {@code serverHandler} 处理完互动后会把整个包
 * {@code sendToPlayersInDimension} 广播给维度内所有玩家，而各客户端的
 * {@code clientHandler} 会拿<b>自己的本地玩家</b>重放一次互动：
 * {@code InteractBox#interact} → {@code SeatSubsystem#onInteract} → {@code setPassenger(本地玩家)}。
 * 于是任何人坐上座位，维度内每个玩家的客户端都会把自己塞进同一个座位
 * （{@code machine_Max$setControllingSubsystem}），镜头随之被拉进车里，
 * 载具的位置音效又正好在听者耳边，表现为「远处的玩家听到载具声音、视角被传送进车」。
 * <p>
 * 客户端需要这份回声来完成自己的入座与镜头接管（客户端不本地预演互动），所以不能直接取消回声，
 * 只能把收件人从「全维度」收窄为「发起者」。
 * <p>
 * 另外，服务端若是<b>因为座位已被他人占用</b>而拒绝了这次互动，发起者的客户端就不该再乐观地
 * 把自己塞进去——否则会出现「坐进已被占用的驾驶位、并以外来者的身份驱动该车」。这种情形必须连
 * 发起者也不回声。判定沿用服务端座位自身的权威状态（{@code occupied}/{@code passenger}），
 * 不新增同步字段。
 */
@Mixin(SubsystemInteractPayload.class)
public abstract class SubsystemInteractPayloadMixin {

    @Redirect(
            method = "serverHandler",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersInDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
            )
    )
    private static void cgcc$echoToSenderOnly(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload[] rest,
                                             SubsystemInteractPayload self, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender)) return;
        if (cgcc$refusedByOccupiedSeat(self, sender)) return;
        PacketDistributor.sendToPlayer(sender, payload);
    }

    /**
     * 该互动是否被服务端「座位已被他人占用」拒绝。
     * <p>
     * 互动框的目标里只要有一个座位已被<b>别人</b>占据，服务端的 {@code SeatSubsystem#onInteract}
     * 就会什么都不做；此时客户端也必须什么都不做。
     * <p>
     * 解析不出互动框（载具/部件/子部件/互动框任一缺失）或互动框没有已解析的座位目标时一律返回
     * false，即维持原有行为——宁可放过，也不要误伤正常互动。
     */
    @Unique
    private static boolean cgcc$refusedByOccupiedSeat(SubsystemInteractPayload payload, ServerPlayer sender) {
        InteractBox interactBox = cgcc$resolveInteractBox(payload);
        if (interactBox == null) return false;

        for (Map<String, ISignalReceiver> channel : interactBox.targets.values()) {
            for (ISignalReceiver receiver : channel.values()) {
                if (receiver instanceof SeatSubsystem seat
                        && seat.isOccupied()
                        && seat.getPassenger() != sender) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static InteractBox cgcc$resolveInteractBox(SubsystemInteractPayload payload) {
        VehicleCore vehicle = ObjectManager.serverAllVehicles.get(payload.vehicleUUID());
        if (vehicle == null) return null;

        Part part = vehicle.partMap.get(payload.partUUID());
        if (part == null) return null;

        SubPart subPart = part.subParts.get(payload.subPartName());
        if (subPart == null) return null;

        return subPart.interactBoxes.get(payload.interactBoxName());
    }
}
