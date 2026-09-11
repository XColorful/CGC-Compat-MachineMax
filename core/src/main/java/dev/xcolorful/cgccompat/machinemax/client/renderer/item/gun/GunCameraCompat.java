package dev.xcolorful.cgccompat.machinemax.client.renderer.item.gun;

import dev.xcolorful.customgun.client.api.event.IComputeCameraAnglesEvent;
import dev.xcolorful.customgun.core.api.event.EventPriority;
import dev.xcolorful.customgun.core.api.event.EventType;
import dev.xcolorful.customgun.core.api.event.IEvent;
import dev.xcolorful.customgun.core.api.event.IEventHandler;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * 对 CGC {@code GunCameraHelper} 的 MachineMax 兼容修正。
 * <p>
 * MachineMax 的 {@code CameraController#updateCameraRot} 会在每次
 * {@code ViewportEvent.ComputeCameraAngles} 里用一份平滑值覆盖摄像机朝向，
 * 而且这个覆盖在玩家<b>并未乘坐载具</b>时也会执行。这份平滑值会滞后于玩家真实朝向，
 * 从而干扰 CGC 的两处摄像机逻辑：
 * <ul>
 *     <li>开镜放大 FOV 后，鼠标移动会让摄像机"回弹"（平滑值追赶真实朝向）；</li>
 *     <li>CGC 后坐力是直接改 {@code LocalPlayer#setXRot} 实现的，平滑覆盖会把它拖慢、压平。</li>
 * </ul>
 * 本处理器以比 MachineMax（NORMAL）低一级的 {@link EventPriority#LOW} 运行，把摄像机朝向
 * 还原为玩家真实朝向（与 CGC 不装 MachineMax 时一致），仅当玩家正在乘坐 MachineMax 座位时
 * 才放行 MachineMax 的摄像机接管。
 */
public class GunCameraCompat implements IEventHandler {
    private static class GunCameraCompatHolder {
        private static final GunCameraCompat INSTANCE = new GunCameraCompat();
    }
    public static GunCameraCompat get() {
        return GunCameraCompatHolder.INSTANCE;
    }
    protected GunCameraCompat() {}

    @Override public String getEventHandlerName() {
        return this.getClass().getName();
    }
    @Override
    public void handleEvent(EventType eventType, IEvent event) {
        if (eventType == EventType.COMPUTE_CAMERA_ANGLES_EVENT) {
            onComputeCameraAngles((IComputeCameraAnglesEvent) event);
        } else {
            onReceiveWrongEvent(eventType);
        }
    }

    private void onComputeCameraAngles(IComputeCameraAnglesEvent event) {
        Entity entity = event.getCamera().getEntity();
        if (entity == null) {
            return;
        }

        // 玩家正坐在 MachineMax 载具里时，交给 MachineMax 自己接管（第一/第三人称的座位视角）。
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
            return;
        }

        // 非乘坐状态：还原为玩家真实朝向，抵消 MachineMax 的平滑覆盖。
        // 后坐力直接改 LocalPlayer#setXRot，可能把 pitch 顶出 [-90, 90]，这里 clamp 回原版范围，
        // 与 CGC 不装 MachineMax 时的表现一致（否则开镜压枪会被顶到天上无法下拉）。
        float partialTick = (float) event.getPartialTick();
        event.setPitch(Mth.clamp(entity.getViewXRot(partialTick), -90.0F, 90.0F));
        event.setYaw(entity.getViewYRot(partialTick));
    }
}
