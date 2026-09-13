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
    private static final boolean DEFAULT_CONCURRENT_SOUND_LIMIT_ENABLED = false;
    private static final int DEFAULT_MAX_CONCURRENT_SOUNDS = 8;
    private static final boolean DEFAULT_MODIFY_RENDER_DISTANCE = true;
    private static final int DEFAULT_RENDER_DISTANCE = 20;

    /**
     * 渲染距离下限（区块）
     */
    private static final int MIN_RENDER_DISTANCE = 1;

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
     * 是否限制 MachineMax 的并发音频数量。当前未实现，仅保留配置项。
     */
    public static boolean concurrentSoundLimitEnabled = DEFAULT_CONCURRENT_SOUND_LIMIT_ENABLED;

    /**
     * 允许同时播放的 MachineMax 音频数量上限。当前未实现，仅保留配置项。
     */
    public static int maxConcurrentSounds = DEFAULT_MAX_CONCURRENT_SOUNDS;

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

    private static void _read(JsonReader reader) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case CgccMMConfigTag.ALLOW_ENTITY_REMOVAL -> entityRemovalAllowed = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.DISCARD_ON_DETACH -> discardOnDetach = JsonUtils.readBoolean(reader);
//                case CgccMMConfigTag.CONCURRENT_SOUND_LIMIT_ENABLED -> concurrentSoundLimitEnabled = JsonUtils.readBoolean(reader);
//                case CgccMMConfigTag.MAX_CONCURRENT_SOUNDS -> maxConcurrentSounds = Math.max(0, JsonUtils.readInt(reader));
                case CgccMMConfigTag.MODIFY_RENDER_DISTANCE -> modifyRenderDistance = JsonUtils.readBoolean(reader);
                case CgccMMConfigTag.RENDER_DISTANCE -> renderDistance = Math.max(MIN_RENDER_DISTANCE, JsonUtils.readInt(reader));
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
//                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.CONCURRENT_SOUND_LIMIT_ENABLED, concurrentSoundLimitEnabled);
//                    JsonUtils.writeInt(writer, CgccMMConfigTag.MAX_CONCURRENT_SOUNDS, maxConcurrentSounds);
                    JsonUtils.writeBoolean(writer, CgccMMConfigTag.MODIFY_RENDER_DISTANCE, modifyRenderDistance);
                    JsonUtils.writeInt(writer, CgccMMConfigTag.RENDER_DISTANCE, renderDistance);
                }
                writer.endObject();
            }
        } catch (IOException exception) {
            CgccMachineMax.LOGGER.error("Failed to write config file {}", configFile, exception);
        }
    }
}
