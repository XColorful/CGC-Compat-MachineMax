package dev.xcolorful.cgccompat.machinemax;

import com.mojang.logging.LogUtils;
import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import org.slf4j.Logger;

import java.nio.file.Path;

public class CgccMachineMax {
    public static final String MOD_ID = "cgccmachinemax";
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static boolean initialized;

    /**
     * 配置文件名，位于平台的 config 目录下
     */
    public static final String FILE_NAME = MOD_ID + ".json";

    /**
     * 游戏根目录，用于解析配置中的相对路径
     */
    private static Path gameDirectory;

    /**
     * 模组配置文件的绝对路径
     */
    private static Path configFile;

    public static void init(Path gameDirectory,
                            Path configDirectory) {
        if (initialized) return;

        CgccMachineMax.gameDirectory = gameDirectory;
        CgccMachineMax.configFile = configDirectory.resolve(FILE_NAME);

        CgccMMConfig.load(CgccMachineMax.configFile);

        initialized = true;
    }

    /**
     * 重新从磁盘读取配置
     */
    public static void reloadConfig() {
        if (configFile == null) return;
        CgccMMConfig.load(configFile);
    }

    /**
     * @return 游戏根目录
     */
    public static Path gameDirectory() {
        return gameDirectory;
    }

    /**
     * @return 模组配置文件的绝对路径
     */
    public static Path configFile() {
        return configFile;
    }
}
