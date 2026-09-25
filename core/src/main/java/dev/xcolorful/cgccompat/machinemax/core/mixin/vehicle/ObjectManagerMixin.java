package dev.xcolorful.cgccompat.machinemax.core.mixin.vehicle;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.core.util.DetachedPartDiscard;
import io.github.sweetzonzi.machine_max.common.vehicle.ObjectManager;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 脱落的部件不保留，详见 {@link DetachedPartDiscard}。
 * <p>
 * {@code addSpiltVehicle} 是"部件刚被从载具上撕裂出去、变成独立载具"的唯一入口，
 * 与造成撕裂的原因无关（子弹、碰撞等都会走这里）。
 * <p>
 * {@code removeVehicle} 上的日志是排查用的：物理层崩溃（libbulletjme 拆刚体时段错误）只可能发生在
 * 拆载具前后，把每次整车移除连同剩余部件数记下来，崩溃时才能回看是不是紧跟在一次批量拆除之后。
 */
@Mixin(ObjectManager.class)
public abstract class ObjectManagerMixin {

    @Inject(method = "addSpiltVehicle", at = @At("TAIL"))
    private static void cgcc$registerDetachedVehicle(VehicleCore spiltVehicle, CallbackInfo ci) {
        DetachedPartDiscard.register(spiltVehicle);
    }

    @Inject(method = "onPostTick", at = @At("TAIL"))
    private static void cgcc$discardDetachedVehicles(LevelTickEvent.Post event, CallbackInfo ci) {
        DetachedPartDiscard.onLevelPostTick(event);
    }

    @Inject(method = "removeVehicle", at = @At("HEAD"))
    private static void cgcc$logWholeVehicleRemoval(VehicleCore vehicle, CallbackInfo ci) {
        CgccMachineMax.LOGGER.debug("整车移除 {}（剩余 {} 个部件）",
                vehicle.uuid, vehicle.partMap.size());
    }
}
