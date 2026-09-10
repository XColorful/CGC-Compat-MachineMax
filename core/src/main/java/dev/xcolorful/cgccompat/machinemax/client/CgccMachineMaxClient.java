package dev.xcolorful.cgccompat.machinemax.client;

import dev.xcolorful.cgccompat.machinemax.client.event.custom.ClientEventHandlers;
import dev.xcolorful.customgun.CustomGun;

public class CgccMachineMaxClient {

    protected static boolean initialized;

    public static void init() {
        if (initialized) return;

        ClientEventHandlers.registerAll(CustomGun.getEventRegister());
        initialized = true;
    }
}
