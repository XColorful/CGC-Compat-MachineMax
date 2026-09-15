package dev.xcolorful.cgccompat.machinemax.client.sound;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import dev.xcolorful.customgun.client.util.ClientInputUtils;
import dev.xcolorful.customgun.core.api.event.EventType;
import dev.xcolorful.customgun.core.api.event.IEvent;
import dev.xcolorful.customgun.core.api.event.IEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;

/**
 * 载具音效占满 OpenAL 声道、导致游戏整体静音时的自愈。
 * <p>
 * MachineMax 的载具音效走 SparkCore 的 {@code SpreadingSoundInstance}，每个实例在
 * {@code SoundEngine} 里占一个 OpenAL 声道，而且没有并发上限
 * （{@code ClientSpreadingSoundPlayer} 只管登记，不做数量限制）。声道池由
 * {@code Library} 按设备能力分配，设备只报 30 个源时静态池只有
 * {@code clamp(30 - 5, 8, 255) = 25} 个。这些声道一旦被远处载具的持续音效占住，
 * 新的音效就在 {@code SoundEngine#play} 里拿不到声道而被静默丢弃，听感上就是整个游戏突然没声。
 * <p>
 * 手动打开「音乐和声音」拨一下任意一个非 MASTER 音量滑块能恢复，是因为
 * {@code Options#createSoundSliderOptionInstance} 里滑块的 {@code OptionInstance} 回调调用
 * {@code SoundManager#updateSourceVolume} → {@code SoundEngine#updateCategoryVolume}：
 * 它会对每一个活动声道重算音量，把算出来是 0 的声道 {@code stop()} 掉，
 * 这些声道在下一帧 {@code ChannelAccess#scheduleTick} 里被释放，声道池重新有空位。
 * （{@code SoundEngine#tickNonPaused} 只在<b>类别</b>音量为 0 时才停声道，
 * 不会清理「距离远到听不见」的载具音效，所以被占住的声道会一直留着。）
 * <p>
 * 本类就是把「拨滑块」这一下按 {@link CgccMMConfig#soundRefreshIntervalTicks} 自动执行，
 * 默认每 tick 一次。挂在 {@code ClientTickEvent.Pre} 上：这时候上一 tick 的声音引擎已经跑完，
 * 而本 tick 的 {@code SoundEngine#tick}（里面才做 {@code ChannelAccess#scheduleTick} 的声道释放）
 * 还没执行，所以停掉的声道能在同一 tick 内就被收回去。
 */
public class SoundVolumeRefresher implements IEventHandler {
    private static class SoundVolumeRefresherHolder {
        private static final SoundVolumeRefresher INSTANCE = new SoundVolumeRefresher();
    }
    public static SoundVolumeRefresher get() {
        return SoundVolumeRefresherHolder.INSTANCE;
    }
    protected SoundVolumeRefresher() {}

    /**
     * 距上次刷新的 client tick 数
     */
    private int tickCounter;

    @Override
    public String getEventHandlerName() {
        return this.getClass().getName();
    }

    @Override
    public void handleEvent(EventType eventType, IEvent event) {
        switch (eventType) {
            case PREPARE_CLIENT_TICK_EVENT -> onPrepareClientTick();
            default -> onReceiveWrongEvent(eventType);
        }
    }

    private void onPrepareClientTick() {
        if (!CgccMMConfig.enableSoundRefresh) return;
        if (++this.tickCounter < CgccMMConfig.soundRefreshIntervalTicks) return;
        this.tickCounter = 0;

        refreshVolume();
    }

    private void refreshVolume() {
        if (!ClientInputUtils.isInGameWorld()) return;

        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        Options options = minecraft.options;

        // MASTER：只把监听器增益（OpenAL 的 AL_GAIN）重新下发一次。
        // SoundManager#updateSourceVolume 在 MASTER 音量为 0 时会走「停掉全部音效」的分支，
        // 那种情况下音效本来就该静音，这里跳过，免得每 tick 触发一次。
        float master = options.getSoundSourceVolume(SoundSource.MASTER);
        if (master > 0.0F) {
            soundManager.updateSourceVolume(SoundSource.MASTER, master);
        }

        // 非 MASTER 类别会让 SoundEngine 重算并下发所有声道的音量（和传进去的值无关，
        // 它内部用的是每个音效自己的类别音量），所以挑哪个类别都一样，
        // 取 NEUTRAL 只是因为载具子系统音效用的是它。
        soundManager.updateSourceVolume(SoundSource.NEUTRAL, options.getSoundSourceVolume(SoundSource.NEUTRAL));
    }
}
