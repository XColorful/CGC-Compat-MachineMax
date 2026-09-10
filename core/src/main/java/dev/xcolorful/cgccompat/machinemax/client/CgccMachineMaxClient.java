package dev.xcolorful.cgccompat.machinemax.client;

public class CgccMachineMaxClient {

    protected static boolean initialized;

    public static void init() {
        if (initialized) return;

        initialized = true;
    }
}
