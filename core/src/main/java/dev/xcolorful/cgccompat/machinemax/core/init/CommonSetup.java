package dev.xcolorful.cgccompat.machinemax.core.init;

import dev.xcolorful.cgccompat.machinemax.core.init.registry.ModEntities;
import dev.xcolorful.cgccompat.machinemax.core.util.PartEntityRemovalGuard;

public class CommonSetup {

    private static final CommonSetup INSTANCE = new CommonSetup();
    public static CommonSetup get() {
        return INSTANCE;
    }
    private CommonSetup() {}

    public void onCommonSetup() {
        // 移除兼容的自检：MachineMax 的移除 API 变了就直接崩，提醒更新兼容模组
        PartEntityRemovalGuard.check();

        ModEntities.modifyVehicleRenderDistance();
    }
}
