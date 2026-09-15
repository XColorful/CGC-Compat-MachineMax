package dev.xcolorful.cgccompat.machinemax.client.event.custom;

import dev.xcolorful.cgccompat.machinemax.client.renderer.item.gun.GunCameraCompat;
import dev.xcolorful.cgccompat.machinemax.client.sound.SoundVolumeRefresher;
import dev.xcolorful.customgun.core.api.event.EventPriority;
import dev.xcolorful.customgun.core.api.event.EventType;
import dev.xcolorful.customgun.core.api.event.ICustomEventRegister;
import dev.xcolorful.customgun.core.event.custom.CoreEventHandlers;

public class ClientEventHandlers {

    public static void registerAll(ICustomEventRegister customEventRegister) {

        // 摄像机朝向兼容：比 MachineMax（NORMAL）低一级运行，还原 CGC 的摄像机朝向
        CoreEventHandlers.register(customEventRegister, GunCameraCompat.get(), EventType.COMPUTE_CAMERA_ANGLES_EVENT, EventPriority.LOW, false);
        // 音量自愈：载具音效占满声道导致整体静音时靠它清理，见 SoundVolumeRefresher
        CoreEventHandlers.register(customEventRegister, SoundVolumeRefresher.get(), EventType.PREPARE_CLIENT_TICK_EVENT, EventPriority.LOWEST, false);
    }
}
