package dev.xcolorful.cgccompat.machinemax.client.mixin.sound;

import dev.xcolorful.cgccompat.machinemax.client.util.LocalSoundRange;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.CarControllerSubsystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 手刹音效加距离门，见 {@link LocalSoundRange}。
 * <p>
 * 两处 {@code playLocalSound}（松开/拉上）都在 {@code onTick} 里，用 ordinal 分别指定：
 * 0 = 松开手刹，1 = 拉上手刹。
 */
@Mixin(CarControllerSubsystem.class)
public abstract class CarControllerSubsystemSoundMixin {

    @Redirect(
            method = "onTick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 0,
                    target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"
            )
    )
    private void cgcc$limitHandBrakeOffDistance(Level level, double x, double y, double z, SoundEvent sound,
                                               SoundSource source, float volume, float pitch, boolean distanceDelay) {
        cgcc$playIfAudible(level, x, y, z, sound, source, volume, pitch, distanceDelay);
    }

    @Redirect(
            method = "onTick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 1,
                    target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"
            )
    )
    private void cgcc$limitHandBrakeOnDistance(Level level, double x, double y, double z, SoundEvent sound,
                                              SoundSource source, float volume, float pitch, boolean distanceDelay) {
        cgcc$playIfAudible(level, x, y, z, sound, source, volume, pitch, distanceDelay);
    }

    @Unique
    private static void cgcc$playIfAudible(Level level, double x, double y, double z, SoundEvent sound,
                                          SoundSource source, float volume, float pitch, boolean distanceDelay) {
        if (LocalSoundRange.isAudibleAt(x, y, z, sound)) {
            level.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
        }
    }
}
