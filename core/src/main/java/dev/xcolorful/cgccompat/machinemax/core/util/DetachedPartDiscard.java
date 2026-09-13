package dev.xcolorful.cgccompat.machinemax.core.util;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 脱落部件的延迟移除。
 * <p>
 * 连接件被打断后，MachineMax 会把脱落的部分分裂成一个新载具（{@code ObjectManager#addSpiltVehicle}）。
 * 分裂载具连同它的部件会一直存在：{@code MMPartEntity} 虽然是 {@code noSave} 的瞬态实体，
 * 但真正的持久化载体是 {@code VehicleCore} / {@code Part} / {@code SubPart}，实体在
 * {@code SubPart#postTick} 里会被重新建回来，重新加载区块后也一样。所以只 discard 实体没有用，
 * 必须把这个分裂载具整体移除（{@code VehicleCore#removePart}）。
 * <p>
 * 移除必须延后到本 tick 结束（{@code LevelTickEvent.Post}）：{@code addSpiltVehicle} 是在构造
 * {@code ConnectorDetachPayload} 的实参时被调用的，如果就地移除，{@code PartRemovePayload}
 * 会先于描述分裂的 {@code ConnectorDetachPayload} 发出，客户端此时还没有这个分裂载具，
 * {@code PartRemovePayload#handle} 会找不到载具而丢弃该移除，导致客户端一直保留脱落的部件。
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

            for (Part part : List.copyOf(spiltVehicle.partMap.values())) {
                spiltVehicle.removePart(part);
            }
        }
    }
}
