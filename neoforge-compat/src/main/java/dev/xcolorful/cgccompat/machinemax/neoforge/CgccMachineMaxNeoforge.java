package dev.xcolorful.cgccompat.machinemax.neoforge;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.neoforgeclient.CgccMachineMaxNeoforgeClient;
import dev.xcolorful.customgun.core.api.common.McSide;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

@Mod(CgccMachineMax.MOD_ID)
public class CgccMachineMaxNeoforge {

    public CgccMachineMaxNeoforge() {
        Dist dist = FMLLoader.getDist();
        McSide mcSide = dist.isClient() ? McSide.CLIENT : McSide.DEDICATED_SERVER;

        CgccMachineMax.init(FMLPaths.GAMEDIR.get(),
                FMLPaths.CONFIGDIR.get());

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
