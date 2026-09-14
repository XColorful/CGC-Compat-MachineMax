package dev.xcolorful.cgccompat.machinemax.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;

/**
 * 客户端侧的音效距离门。
 * <p>
 * MachineMax 有一部分音效（变速箱/离合、手刹、轮刹）走原版 {@code Level#playLocalSound}，
 * 传播距离完全交给原版的线性衰减。原版这套衰减在部分音频环境下衰减不到 0（远距离留有
 * 1/d 尾巴），结果是几千格外的玩家仍能听见这些"咔哒"声——正是联机时"所有人都能听到载具声音"的来源。
 * <p>
 * 这里按音效自己声明的传播范围做硬门限：MachineMax 的这些音效都由
 * {@code SoundEvent#createFixedRangeEvent(..., 16)} 或内容包里的 {@code range} 声明范围，
 * 门限跟着声明走，既恢复作者本意，又不会误伤内容包想放远的音效。
 */
public final class LocalSoundRange {

    private LocalSoundRange() {
    }

    /**
     * @return 该位置的声音是否值得在本机播放（本机玩家在音效声明的范围内）
     */
    public static boolean isAudibleAt(double x, double y, double z, SoundEvent sound) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        float range = sound.getRange(1.0F);
        if (range <= 0.0F) {
            // 未声明范围（原版按 16 处理，这里保持一致）
            range = 16.0F;
        }
        return player.distanceToSqr(x, y, z) <= (double) range * range;
    }
}

