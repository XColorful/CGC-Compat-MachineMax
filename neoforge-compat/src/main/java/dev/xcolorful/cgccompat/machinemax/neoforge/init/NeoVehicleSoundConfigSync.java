package dev.xcolorful.cgccompat.machinemax.neoforge.init;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import dev.xcolorful.cgccompat.machinemax.core.network.VehicleSoundConfigPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 载具音效配置的服务端 → 客户端同步。
 * <p>
 * 音效传播距离由客户端计算，客户端的配置文件既可能保持默认、也可能被改坏，因此服务端在玩家登录后
 * 把 {@link CgccMMConfig#modifyVehicleSoundDistance} / {@link CgccMMConfig#vehicleSoundDistance}
 * 下发一次，客户端以此覆盖内存值。
 * <p>
 * 本模组要求双端安装，所以这个包同时是一致性校验：注册为非 optional，客户端缺包或 payload
 * 版本不一致时在连接协商阶段就会被拒绝（客户端会看到缺失通道列表），不会出现"连得进来但收不到
 * 服务端配置"的情况。单机 / 集成服务端下发的是本地同一份配置，行为不变。
 */
@EventBusSubscriber(modid = CgccMachineMax.MOD_ID)
public class NeoVehicleSoundConfigSync {

    /**
     * payload 版本。仅在编解码或处理逻辑变化时上调：NeoForge 会拒绝 payload 版本不一致的连接。
     */
    private static final String PAYLOAD_VERSION = "1.0.0";

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 刻意不加 optional()：本模组要求双端安装，缺少此包（或版本不一致）的客户端
        // 应在连接协商阶段就被拒绝，而不是连进来却用不了服务端下发的配置。
        PayloadRegistrar registrar = event.registrar(PAYLOAD_VERSION);
        registrar.playToClient(VehicleSoundConfigPayload.TYPE, VehicleSoundConfigPayload.STREAM_CODEC,
                (payload, context) -> payload.applyOnClient());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PacketDistributor.sendToPlayer(player, new VehicleSoundConfigPayload(
                CgccMMConfig.modifyVehicleSoundDistance,
                CgccMMConfig.vehicleSoundDistance));
    }
}
