package dev.xcolorful.cgccompat.machinemax;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class CgccMachineMax {
    public static final String MOD_ID = "cgccmachinemax";
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static boolean initialized;

    public static void init() {
        if (initialized) return;

        initialized = true;
    }
}
