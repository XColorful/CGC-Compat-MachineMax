package dev.xcolorful.cgccompat.machinemax.core.util;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * 载具破坏方块的拦截判定。
 * <p>
 * MachineMax 只在 {@code CollisionHandler#applyBlockDamage} 里销毁载具撞到的方块，拦截放在这里
 * 等价于把方块当成基岩那样的不可破坏方块：方块保留、载具照常碰撞。
 */
public final class BlockDestructionGuard {

    private BlockDestructionGuard() {
    }

    /**
     * 是否拦截载具对方块的破坏。
     */
    public static boolean isBlockDestructionBlocked(Level level) {
        if (CgccMMConfig.forceDisableGriefing) return true;
        if (!CgccMMConfig.followMobGriefing) return false;
        return !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }
}
