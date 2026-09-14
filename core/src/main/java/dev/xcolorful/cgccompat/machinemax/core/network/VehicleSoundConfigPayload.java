package dev.xcolorful.cgccompat.machinemax.core.network;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端把自己的载具音效配置下发给客户端。
 * <p>
 * 载具音效的传播距离是在客户端计算的（{@code SpreadingSoundInstance} 的音量衰减与波前推进），
 * 客户端自己的配置文件既可能没改、也可能被改坏，所以由服务端在玩家登录后覆盖一次，
 * 保证同一服务器上所有客户端听到的距离一致。
 * <p>
 * 收到后只覆盖内存值，不写回客户端本地文件（本地文件仍是单机 / LAN 与默认值的记录）。
 */
public record VehicleSoundConfigPayload(boolean modifyVehicleSoundDistance, int vehicleSoundDistance)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VehicleSoundConfigPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CgccMachineMax.MOD_ID, "vehicle_sound_config"));

    public static final StreamCodec<FriendlyByteBuf, VehicleSoundConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.modifyVehicleSoundDistance());
                buffer.writeVarInt(payload.vehicleSoundDistance());
            },
            buffer -> new VehicleSoundConfigPayload(buffer.readBoolean(), buffer.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 应用服务端下发的值（客户端侧）。
     */
    public void applyOnClient() {
        CgccMMConfig.applyVehicleSoundDistanceFromServer(modifyVehicleSoundDistance, vehicleSoundDistance);
    }
}
