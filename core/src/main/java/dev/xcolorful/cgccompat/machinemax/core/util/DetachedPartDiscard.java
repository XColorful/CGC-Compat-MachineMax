package dev.xcolorful.cgccompat.machinemax.core.util;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
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
 * 移除走 MachineMax 自己的整车销毁入口 {@code ObjectManager#removeVehicle}，它最终调用
 * {@code VehicleCore#onRemoveFromLevel} 一次性拆掉全部部件。<b>不能</b>改成逐个部件置
 * {@code Part#destroyed} 让 {@code VehicleCore#preTick} 走 {@code removePart}：{@code removePart}
 * 每拆一个部件都会跑一次 {@code partNetSpiltCheck()}，只要剩余部件不再连通就会再分裂出新载具并
 * {@code addSpiltVehicle}，于是"丢弃整个载具"退化成反复分裂—再丢弃的级联，期间不断新建
 * {@code VehicleCore}、迁移部件与子系统、拆除并重建刚体与关节。{@code onRemoveFromLevel} 没有这个回路。
 * <p>
 * 延后到本 tick 结束（{@code LevelTickEvent.Post}）仍然必要：{@code addSpiltVehicle} 是在构造
 * {@code ConnectorDetachPayload} 的实参时被调用的，同步移除会让移除包早于分裂包发出，客户端状态分叉。
 */
public final class DetachedPartDiscard {

    private static final Queue<VehicleCore> PENDING = new ConcurrentLinkedQueue<>();

    /** 同一 tick 内分裂载具的登记数，仅用于暴露拆分级联 */
    private static int spiltThisTick = 0;

    private DetachedPartDiscard() {
    }

    /**
     * 分裂载具加入世界时登记，延后到本 tick 的 {@link LevelTickEvent.Post} 再移除。
     */
    public static void register(VehicleCore spiltVehicle) {
        //统计放在配置判断之前：即使关掉开关，也要能看出分裂频率
        spiltThisTick++;
        CgccMachineMax.LOGGER.debug("分裂载具 {}（{} 个部件）已登记，本 tick 第 {} 个",
                spiltVehicle.uuid, spiltVehicle.partMap.size(), spiltThisTick);

        if (!CgccMMConfig.discardOnDetach) return;
        // 只在服务端决定，客户端跟随服务端发出的移除包，避免两端配置不一致时状态分叉
        if (!(spiltVehicle.level instanceof ServerLevel)) return;

        PENDING.add(spiltVehicle);
    }

    /**
     * 在服务端 level tick 结束时把登记过的分裂载具整体移除。
     */
    public static void onLevelPostTick(LevelTickEvent.Post event) {
        int spilt = spiltThisTick;
        spiltThisTick = 0;
        // 一次撕裂正常只产生一个分裂载具；同一 tick 出现多个说明发生了拆分级联
        if (spilt > 2) {
            CgccMachineMax.LOGGER.warn("同一 tick 内登记了 {} 个分裂载具，疑似拆分级联", spilt);
        }

        if (PENDING.isEmpty()) return;
        if (event.getLevel().isClientSide()) return;

        VehicleCore spiltVehicle;
        while ((spiltVehicle = PENDING.poll()) != null) {
            if (spiltVehicle.isRemoved) continue;
            ObjectManager.removeVehicle(spiltVehicle);
        }
    }
}
