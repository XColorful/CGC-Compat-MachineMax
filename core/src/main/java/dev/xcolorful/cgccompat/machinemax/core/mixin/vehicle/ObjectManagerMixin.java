package dev.xcolorful.cgccompat.machinemax.core.mixin.vehicle;

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
}
