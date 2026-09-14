package dev.xcolorful.cgccompat.machinemax.client.mixin.sound;

import dev.xcolorful.cgccompat.machinemax.client.util.LocalSoundRange;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.GearboxSubsystem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 变速箱/离合音效加距离门，见 {@link LocalSoundRange}。
 * <p>
 * {@code GearboxSubsystem} 的 4 处 {@code playLocalSound} 各在独立的 lambda 里，
 * 因此这里可以对整个类做重定向。
 */
@Mixin(GearboxSubsystem.class)
public abstract class GearboxSubsystemSoundMixin {

    @Redirect(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"
            )
    )
    private void cgcc$limitSoundDistance(Level level, double x, double y, double z, SoundEvent sound,
                                        SoundSource source, float volume, float pitch, boolean distanceDelay) {
        if (LocalSoundRange.isAudibleAt(x, y, z, sound)) {
            level.playLocalSound(x, y, z, sound, source, volume, pitch, distanceDelay);
        }
    }
}
