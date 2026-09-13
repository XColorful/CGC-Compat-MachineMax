package dev.xcolorful.cgccompat.machinemax.core.util;

import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.vehicle.VehicleEntity;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 部件移除兼容的启动自检。
 * <p>
 * 移除兼容建立在"所有外部移除入口都会汇入 {@code Entity#remove(RemovalReason)}"之上，
 * 兼容模组靠覆写该方法接管。如果 MachineMax（或原版）之后在部件实体自身上加了移除相关的方法/重载，
 * 说明这条假设可能不再成立、兼容可能被绕过 —— 与其静默失效，不如直接崩掉提醒更新兼容模组。
 * <p>
 * 排除掉我们自己覆写的 {@code remove(RemovalReason)}：那正是本兼容模组加上去的。
 * 如果 MachineMax 自己声明了同签名的 {@code remove(RemovalReason)}，Mixin 在加载时就会因为方法冲突而报错，
 * 不需要在这里重复检测。
 */
public final class PartEntityRemovalGuard {

    /**
     * 与移除实体相关的入口方法名
     */
    private static final Set<String> REMOVAL_METHODS = Set.of("remove", "discard", "kill", "setRemoved");

    private PartEntityRemovalGuard() {
    }

    public static void check() {
        _check(MMPartEntity.class, true);
        _check(VehicleEntity.class, false);
    }

    private static void _check(Class<?> type, boolean skipOwnOverride) {
        for (Method method : type.getDeclaredMethods()) {
            if (!REMOVAL_METHODS.contains(method.getName())) continue;
            if (skipOwnOverride && _isOwnOverride(method)) continue;

            throw new IllegalStateException(String.format(
                    "MachineMax removal API changed: %s now declares %s. "
                            + "The part entity removal compat in CGC Compat: MachineMax relies on all removals "
                            + "funnelling through Entity#remove(RemovalReason) and must be updated.",
                    type.getName(), method));
        }
    }

    private static boolean _isOwnOverride(Method method) {
        return method.getName().equals("remove")
                && method.getParameterCount() == 1
                && method.getParameterTypes()[0] == RemovalReason.class;
    }
}
