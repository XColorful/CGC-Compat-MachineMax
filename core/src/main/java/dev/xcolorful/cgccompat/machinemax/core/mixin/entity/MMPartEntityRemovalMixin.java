package dev.xcolorful.cgccompat.machinemax.core.mixin.entity;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 让 {@code Entity#discard()}、{@code kill @e}（走 {@code Entity#kill} →
 * {@code remove(KILLED)}）、以及其它模组直接调用的 {@code remove(reason)} 对 MachineMax 部件实体生效。
 * <p>
 * 用<b>覆写</b>而不是 {@code @Inject}：{@code discard()} 和 {@code remove()} 都声明在 {@code Entity} 上，
 * 挂在 {@code MMPartEntity} 上做注入时 Mixin 能解析到继承来的目标方法、校验不报错，但注入永远写不进
 * {@code MMPartEntity}（那个类里没有该方法体），是静默失效。覆写则是真的往 {@code MMPartEntity} 里加方法。
 * <p>
 * 只需要覆写 {@code remove(RemovalReason)} 一个方法：{@code Entity#discard()} 与 {@code Entity#kill()}
 * 最终都汇入 {@code remove(reason)}，不会漏。{@code discard()} / {@code setRemoved()} 虽然是 final 没法覆写，
 * 但也因此不需要 —— 前者走 {@code remove()}，后者是区块卸载在用的（已经验证
 * {@code PersistentEntitySectionManager} 直接调 {@code setRemoved(UNLOADED_TO_CHUNK)} 绕过 {@code remove()}，
 * 所以区块卸载不受影响）。
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
@Mixin(MMPartEntity.class)
public abstract class MMPartEntityRemovalMixin extends Entity {

    public MMPartEntityRemovalMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (cgcc$handleExternalRemoval()) return;
        super.remove(reason);
    }

    /**
     * 处理一次移除请求。
     *
     * @return 是否已处理（true 时跳过 {@code super.remove}）
     */
    @Unique
    private boolean cgcc$handleExternalRemoval() {
        MMPartEntity self = (MMPartEntity) (Object) this;

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
                // removePart 内部经 SubPart#destroy 已经把这个实体移除了（那一层 subPart 已为 null，
                // 会走回 super.remove），所以这里直接跳过外层调用，避免 setRemoved 被走第二遍
                vehicle.removePart(part);
                return true;
            }
        }
        return false;
    }
}
