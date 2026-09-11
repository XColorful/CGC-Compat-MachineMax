package dev.xcolorful.cgccompat.machinemax.neoforgeclient;

import dev.xcolorful.cgccompat.machinemax.client.CgccMachineMaxClient;

public class CgccMachineMaxNeoforgeClient {

    protected static boolean initialized;

    public static void init() {
        if (initialized) return;

        CgccMachineMaxClient.init();

        initialized = true;
    }
}
