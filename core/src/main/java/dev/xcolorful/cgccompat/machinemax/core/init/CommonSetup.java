package dev.xcolorful.cgccompat.machinemax.core.init;

import dev.xcolorful.cgccompat.machinemax.core.init.registry.ModEntities;

public class CommonSetup {

    private static final CommonSetup INSTANCE = new CommonSetup();
    public static CommonSetup get() {
        return INSTANCE;
    }
    private CommonSetup() {}

    public void onCommonSetup() {
        ModEntities.modifyVehicleRenderDistance();
    }
}
