package dev.xcolorful.cgccompat.machinemax.core.config;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.core.api.config.CgccMMConfigTag;
import dev.xcolorful.customgun.core.util.JsonUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CGC Compat: MachineMax 的运行时配置。
 * <p>
 * 配置存放在平台的 config 目录下（{@link CgccMachineMax#FILE_NAME}）。
 * 读取采用流式解析，文件不存在或字段缺失时使用默认值，加载后按固定顺序回写，
 * 保证缺省的键也会出现在文件里供使用者编辑。
 */
public final class CgccMMConfig {

    // --------默认值--------

    private static final boolean DEFAULT_ALLOW_ENTITY_REMOVAL = true;
    private static final boolean DEFAULT_DISCARD_ON_DETACH = true;
    private static final boolean DEFAULT_ENABLE_SOUND_REFRESH = true;
    private static final int DEFAULT_SOUND_REFRESH_INTERVAL_TICKS = 1;
    private static final boolean DEFAULT_MODIFY_RENDER_DISTANCE = true;
    private static final int DEFAULT_RENDER_DISTANCE = 20;
    private static final boolean DEFAULT_MODIFY_VEHICLE_SOUND_DISTANCE = true;
    private static final int DEFAULT_VEHICLE_SOUND_DISTANCE = 20;

    /**
     * 渲染距离下限（区块）
     */
    private static final int MIN_RENDER_DISTANCE = 1;

    /**
     * 载具音效传播距离下限（区块）
     */
    private static final int MIN_VEHICLE_SOUND_DISTANCE = 1;

    /**
     * 音量自愈间隔下限（tick）
     */
    private static final int MIN_SOUND_REFRESH_INTERVAL_TICKS = 1;

    /**
     * 是否允许 {@code kill @e} / {@code Entity#discard()} 等主动手段移除部件实体。
     * <p>
     * {@code false} 时拦截这些移除，部件实体不会被主动清掉（小游戏模组的自动清理会失效）；
     * {@code true}（默认）时这些手段正常生效：连同该 part 一起从载具上摘除，
     * 避免 {@code SubPart#postTick} 把实体重建回来、也避免它留在载具存档里。
     */
    public static boolean entityRemovalAllowed = DEFAULT_ALLOW_ENTITY_REMOVAL;

    /**
     * {@code true} 时，部件因连接件被打断而从载具上脱落后立即消失，不保留脱离出去的部分。
     * <p>
     * MachineMax 的连接件被打断后会把脱落的部件分裂成一个新载具，其部件实体仍然存在、
     * 可继续被命中，这里在分裂发生时把脱离出去的部分整体移除。
     */
    public static boolean discardOnDetach = DEFAULT_DISCARD_ON_DETACH;

    /**
     * 是否启用音量自愈。
     * <p>
     * MachineMax 的载具音效（SparkCore {@code SpreadingSoundInstance}）每个实例占一个 OpenAL 声道，
     * 且没有并发上限；远处载具的持续音效会把声道池占满，新音效拿不到声道就被丢弃，表现成整个游戏突然静音。
     * 置 {@code true}（默认）时周期性重算并下发所有声道的音量，把听不见的声道停掉、释放回池子，
     * 等效于手动去声音设置里拨一下音量滑块。
     */
    public static boolean enableSoundRefresh = DEFAULT_ENABLE_SOUND_REFRESH;

    /**
     * 音量自愈的执行间隔，单位为 client tick。
     * <p>
     * {@code 1}（默认）表示每 tick 执行，恢复最快；调大可以降低开销，
     * 但静音后最长要等这么久才恢复，且这段窗口内起的新音效会一直丢失。
     */
    public static int soundRefreshIntervalTicks = DEFAULT_SOUND_REFRESH_INTERVAL_TICKS;

    /**
     * 是否修改部件实体的可见范围。
     * <p>
     * MachineMax 用客户端渲染距离截断部件渲染、且实体同步范围只有默认的 5 区块，
     * 载具稍远就整台消失；置 {@code true} 时按 {@link #renderDistance} 统一这两个范围。
     */
    public static boolean modifyRenderDistance = DEFAULT_MODIFY_RENDER_DISTANCE;

    /**
     * 部件实体的可见范围，单位为区块
     */
    public static int renderDistance = DEFAULT_RENDER_DISTANCE;

    /**
     * 是否修改载具音效的传播距离。
     * <p>
     * MachineMax 的引擎/电机工作音、轮胎摩擦音等经 SparkCore 的传播音效系统（{@code SpreadingSoundInstance}）
     * 播放，传播距离取音效自己声明的 range（内容包里多为 64 格），远小于本模组放宽后的可见范围。
     * 置 {@code true} 时按 {@link #vehicleSoundDistance} 统一这些音效的距离，衰减方式不变。
     * <p>
     * 碰撞、命中、撕裂、破坏与拆装工具音不受影响，保持各自声明的短距离。
     */
    public static boolean modifyVehicleSoundDistance = DEFAULT_MODIFY_VEHICLE_SOUND_DISTANCE;

    /**
     * 载具音效的传播距离，单位为区块
     */
    public static int vehicleSoundDistance = DEFAULT_VEHICLE_SOUND_DISTANCE;

    private CgccMMConfig() {
    }

    /**
     * 从配置目录加载配置；文件不存在时按默认值创建。
     *
     * @param configFile 配置文件的绝对路径
     */
    public static void load(Path configFile) {
        if (Files.exists(configFile)) {
            try (JsonReader reader = new JsonReader(Files.newBufferedReader(configFile, StandardCharsets.UTF_8))) {
                _read(reader);
            } catch (IOException | RuntimeException exception) {
                CgccMachineMax.LOGGER.error("Failed to read config file {}, using defaults", configFile, exception);
            }
        }

        _write(configFile);
    }

    /**
     * 用服务端下发的值覆盖载具音效配置（客户端侧，仅内存，不写回本地文件）。
     * <p>
     * 载具音效距离在客户端计算，客户端本地文件既可能没改也可能被改坏，
     * 因此以服务端的值为准，保证同一服务器内所有客户端一致。
     *
     * @param modify   服务端是否接管载具音效距离
     * @param distance 载具音效传播距离（区块）
     */
    public static void applyVehicleSoundDistanceFromServer(boolean modify, int distance) {
        modifyVehicleSoundDistance = modify;
        vehicleSoundDistance = Math.max(MIN_VEHICLE_SOUND_DISTANCE, distance);
    }

    private static void _read(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case CgccMMConfigTag.ALLOW_ENTITY_REMOVAL -> entityRemovalAllowed = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.DISCARD_ON_DETACH -> discardOnDetach = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.ENABLE_SOUND_REFRESH -> enableSoundRefresh = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.SOUND_REFRESH_INTERVAL_TICKS -> soundRefreshIntervalTicks = Math.max(MIN_SOUND_REFRESH_INTERVAL_TICKS, JsonUtils.readInt(reader));
                case CgccMMConfigTag.MODIFY_RENDER_DISTANCE -> modifyRenderDistance = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.RENDER_DISTANCE -> renderDistance = Math.max(MIN_RENDER_DISTANCE, JsonUtils.readInt(reader));
                case CgccMMConfigTag.MODIFY_VEHICLE_SOUND_DISTANCE -> modifyVehicleSoundDistance = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.VEHICLE_SOUND_DISTANCE -> vehicleSoundDistance = Math.max(MIN_VEHICLE_SOUND_DISTANCE, JsonUtils.readInt(reader));
                default -> reader.skipValue();
            }
        }
        reader.endObject();
    }

    /**
     * 把配置按固定顺序回写，便于使用者直接编辑。
     */
    private static void _write(Path configFile) {
        try {
            Path parent = configFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(configFile, StandardCharsets.UTF_8))) {
                writer.setIndent("\t");
                writer.beginObject(); {
                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.ALLOW_ENTITY_REMOVAL, entityRemovalAllowed);
                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.DISCARD_ON_DETACH, discardOnDetach);
                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.ENABLE_SOUND_REFRESH, enableSoundRefresh);
                    JsonUtils.writeInt(writer, CgccMMConfigTag.SOUND_REFRESH_INTERVAL_TICKS, soundRefreshIntervalTicks);
                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.MODIFY_RENDER_DISTANCE, modifyRenderDistance);
                    JsonUtils.writeInt(writer, CgccMMConfigTag.RENDER_DISTANCE, renderDistance);
                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.MODIFY_VEHICLE_SOUND_DISTANCE, modifyVehicleSoundDistance);
                    JsonUtils.writeInt(writer, CgccMMConfigTag.VEHICLE_SOUND_DISTANCE, vehicleSoundDistance);
                }
                writer.endObject();
            }
        } catch (IOException exception) {
            CgccMachineMax.LOGGER.error("Failed to write config file {}", configFile, exception);
        }
    }
}
