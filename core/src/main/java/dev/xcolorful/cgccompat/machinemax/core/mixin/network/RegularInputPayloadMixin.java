package dev.xcolorful.cgccompat.machinemax.core.mixin.network;

import io.github.sweetzonzi.machine_max.network.payload.RegularInputPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 常规输入的回应只发给发起者本人。
 * <p>
 * MachineMax 的 {@code serverHandler} 会把整个包 {@code sendToPlayersInDimension} 广播给维度内所有玩家，
 * 各客户端再用 {@code handle} 处理它——而这个 {@code handle} 里除了「本地玩家的操控子系统」这类
 * 天然自限的分支外，还有一个作用于<b>本地玩家自己</b>的 {@code LEAVE_VEHICLE} 分支。
 * 后果是：任何玩家按下车键，维度内所有正在乘坐东西的玩家（座位、船、马都算）都会被自己的客户端
 * 本地卸下——表现为「一个人下车，两个人一起掉下来」。
 * <p>
 * 客户端需要这份回声来做本地表现（{@code CLUTCH}/{@code UP_SHIFT}/{@code DOWN_SHIFT}/
 * {@code HAND_BRAKE}/{@code TOGGLE_HAND_BRAKE} 要在本地座位上 {@code setRegularInputSignal}），
 * 所以只能把收件人收窄为发起者，不能取消回声。
 * <p>
 * 这里不额外加服务端校验：其余分支本来就要求发送者是所寻址系统的操控者
 * （{@code handleToggleLight} 走本地玩家的操控子系统，装配类分支只动发送者自己的装配缓存），
 * 唯一的漏洞就是那个广播本身，收窄即可。
 */
@Mixin(RegularInputPayload.class)
public abstract class RegularInputPayloadMixin {

    @Redirect(
            method = "serverHandler",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersInDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
            )
    )
    private static void cgcc$echoToSenderOnly(ServerLevel level, CustomPacketPayload payload, CustomPacketPayload[] rest,
                                             RegularInputPayload self, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer sender) {
            PacketDistributor.sendToPlayer(sender, payload);
        }
    }
}
