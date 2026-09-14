package dev.xcolorful.cgccompat.machinemax.core.util;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 脱落部件的移除。
 * <p>
 * 连接件被打断后，MachineMax 会把脱落的部分分裂成一个新载具（{@code ObjectManager#addSpiltVehicle}）。
 * 分裂载具连同它的部件会一直存在：{@code MMPartEntity} 虽然是 {@code noSave} 的瞬态实体，
 * 但真正的持久化载体是 {@code VehicleCore} / {@code Part} / {@code SubPart}，实体在
 * {@code SubPart#postTick} 里会被重新建回来，重新加载区块后也一样。所以只 discard 实体没有用，
 * 必须把整个分裂载具移除。
 * <p>
 * 做法是把该分裂载具的部件全部置 {@code Part#destroyed}，由它自己的 {@code VehicleCore#preTick}
 * 走正常流程 {@code removePart} —— 与 MachineMax 正常摧毁部件同路径，兼容端不再同步拆刚体/关节。
 * <p>
 * 标记延后到本 tick 结束（{@code LevelTickEvent.Post}）：{@code addSpiltVehicle} 是在构造
 * {@code ConnectorDetachPayload} 的实参时被调用的，延后既让标记落在分裂流程之外，实际移除又发生在
 * 下一 tick 的 {@code preTick}，移除包自然晚于 {@code ConnectorDetachPayload} 发出。
 */
public final class DetachedPartDiscard {

    private static final Queue<VehicleCore> PENDING = new ConcurrentLinkedQueue<>();

    private DetachedPartDiscard() {
    }

    /**
     * 分裂载具加入世界时登记，延后到本 tick 的 {@link LevelTickEvent.Post} 再移除。
     */
    public static void register(VehicleCore spiltVehicle) {
        if (!CgccMMConfig.discardOnDetach) return;
        // 只在服务端决定，客户端跟随服务端发出的移除包，避免两端配置不一致时状态分叉
        if (!(spiltVehicle.level instanceof ServerLevel)) return;

        PENDING.add(spiltVehicle);
    }

    /**
     * 在服务端 level tick 结束时把登记过的分裂载具整体移除。
     */
    public static void onLevelPostTick(LevelTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        if (event.getLevel().isClientSide()) return;

        VehicleCore spiltVehicle;
        while ((spiltVehicle = PENDING.poll()) != null) {
            if (spiltVehicle.isRemoved) continue;

            // 只标记已摧毁，移除交给分裂载具自己的 VehicleCore#preTick，
            // 避免在这里同步拆刚体/关节（与 MachineMax 正常摧毁部件同路径）。
            for (Part part : spiltVehicle.partMap.values()) {
                part.destroyed = true;
            }
        }
    }
}
