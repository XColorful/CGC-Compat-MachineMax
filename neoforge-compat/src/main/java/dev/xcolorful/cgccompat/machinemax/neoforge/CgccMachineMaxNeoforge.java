package dev.xcolorful.cgccompat.machinemax.neoforge;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.neoforgeclient.CgccMachineMaxNeoforgeClient;
import dev.xcolorful.customgun.CustomGun;
import dev.xcolorful.customgun.core.api.common.McSide;
import net.neoforged.fml.common.Mod;

@Mod(CgccMachineMax.MOD_ID)
public class CgccMachineMaxNeoforge {

    public CgccMachineMaxNeoforge() {
        McSide mcSide = CustomGun.getMcSide();

        CgccMachineMax.init();

        if (mcSide == McSide.CLIENT) {
            CgccMachineMaxNeoforge._CgccMachineMaxNeoforgeClient.init();
        }
    }

    private static class _CgccMachineMaxNeoforgeClient {
        public static void init() {
            CgccMachineMaxNeoforgeClient.init();
        }
    }
}
