package dev.xcolorful.cgccompat.machinemax.core.mixin.entity;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 {@code Entity#discard()}、{@code kill @e}（走 {@code Entity#kill} →
 * {@code remove(KILLED)}）等移除手段对 MachineMax 部件实体生效。
 * <p>
 * 这里必须 @Mixin {@link Entity} 而不是 {@link MMPartEntity}：{@code discard()} / {@code remove()}
 * 都声明在 {@code Entity} 上，挂在 {@code MMPartEntity} 上时 Mixin 能解析到继承来的目标方法、
 * 校验不报错，但注入永远写不进 {@code MMPartEntity}（那个类里没有该方法体），是静默失效。
 * 所以这里挂在 {@code Entity} 上并用 {@code instanceof} 过滤，只处理部件实体。
 * <p>
 * 不按 {@link RemovalReason} 分支：不同模组用的 reason 不一样，一律走同一套清理逻辑。
 * 区分"外部主动移除"和"MachineMax 自己的移除"靠的是 {@code subPart} 是否还绑着 ——
 * MachineMax 内部三处 {@code remove(DISCARDED)} 触发时 {@code subPart} 一定已经是 null：
 * {@code SubPart#destroy} 先置 {@code entity.subPart = null} 再移除，
 * {@code MMPartEntity#baseTick} 的孤立实体清理在 {@code subPart == null} 分支里，
 * {@code MMPartEntity#readSpawnData} 的实体是新 spawn 的、还没有 subPart。
 * <p>
 * 只移除实体是不够的：{@code MMPartEntity} 是 {@code noSave} 的瞬态实体，真正的持久化载体是
 * {@code VehicleCore}/{@code Part}/{@code SubPart}，{@code SubPart#postTick} 会把实体重建回来。
 * 所以启用时要把该 part 一起从载具上摘除（{@code VehicleCore#removePart}），
 * 既不会重建，也不会留在载具存档里。
 */
@Mixin(Entity.class)
public abstract class EntityRemovalMixin {

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void cgcc$onDiscard(CallbackInfo ci) {
        if (cgcc$handleExternalRemoval()) ci.cancel();
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void cgcc$onRemove(RemovalReason reason, CallbackInfo ci) {
        if (cgcc$handleExternalRemoval()) ci.cancel();
    }

    /**
     * 处理一次移除请求。非部件实体一律放行。
     *
     * @return 是否拦截本次移除
     */
    @Unique
    private boolean cgcc$handleExternalRemoval() {
        if (!((Object) this instanceof MMPartEntity self)) return false;

        SubPart subPart = self.subPart;
        // MachineMax 自己清理（subPart 已摘掉）或已在移除中的，按原逻辑走
        if (subPart == null || self.isRemoved()) return false;

        // 未启用：拦截，保持部件实体不被主动清掉
        if (!CgccMMConfig.entityRemovalAllowed) return true;

        // 启用：把 part 一起摘掉。客户端不做载具结构改动，等服务端的 PartRemovePayload
        if (!self.level().isClientSide()) {
            Part part = subPart.part;
            VehicleCore vehicle = part.vehicle;
            if (vehicle != null && vehicle.partMap.containsKey(part.uuid)) {
                // removePart 内部经 SubPart#destroy 已经把这个实体移除了，
                // 取消外层调用避免 setRemoved 被走第二遍
                vehicle.removePart(part);
                return true;
            }
        }
        return false;
    }
}
