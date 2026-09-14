package dev.xcolorful.cgccompat.machinemax.client.mixin.sound;

import cn.solarmoon.spark_core.sound.SpreadingSoundInstance;
import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 载具音效的传播距离。
 * <p>
 * SparkCore 的 {@code SpreadingSoundInstance} 在构造时把 {@code soundEvent.getRange(1.0f)} 记作
 * {@code maxRange}，这个值同时决定两件事：音量衰减的分母
 * （{@code volume * (1 - min(dist / maxRange, 1))^2}），以及声源波前能推进多远
 * （{@code tick} 里声源点的传播距离超过 maxRange 就会被丢弃）。因此在这里替换它，
 * 就能整体扩大可听半径，而衰减曲线、近处音量都保持不变。
 * <p>
 * 只作用于载具本体的持续音效：机械子系统工作音（引擎/电机等，{@code subsystem.*}）
 * 与轮胎摩擦音（{@code part.collision.terrain.tire_*}）。碰撞、命中、撕裂、破坏
 * 与拆装工具音仍按各自声明的短距离播放。
 * <p>
 * 走原版 {@code Level#playLocalSound} 的那批音效（变速箱/手刹/轮刹）不经过本类，
 * 由 {@link dev.xcolorful.cgccompat.machinemax.client.util.LocalSoundRange} 单独处理。
 */
@Mixin(SpreadingSoundInstance.class)
public abstract class SpreadingSoundInstanceRangeMixin {

    /**
     * 区块 → 方块
     */
    private static final float cgcc$BLOCKS_PER_CHUNK = 16.0F;

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/sounds/SoundEvent;getRange(F)F"
            )
    )
    private float cgcc$overrideVehicleSoundRange(SoundEvent sound, float volume) {
        if (CgccMMConfig.modifyVehicleSoundDistance && cgcc$isVehicleMachinerySound(sound)) {
            return CgccMMConfig.vehicleSoundDistance * cgcc$BLOCKS_PER_CHUNK;
        }
        return sound.getRange(volume);
    }

    /**
     * @return 该音效是否属于「载具本体持续音效」，即需要跟随本模组的可见范围一起放远的那一类
     */
    @Unique
    private static boolean cgcc$isVehicleMachinerySound(SoundEvent sound) {
        ResourceLocation id = sound.getLocation();
        if (!MachineMax.MOD_ID.equals(id.getNamespace())) return false;

        String path = id.getPath();
        // 机械子系统（引擎/电机等）的工作音与启停音；子系统被摧毁的破坏音不算“载具运转”信息，保持原距离
        if (path.startsWith("subsystem.")) return !path.endsWith(".break");
        // 轮胎与地面摩擦
        return path.startsWith("part.collision.terrain.tire");
    }
}
