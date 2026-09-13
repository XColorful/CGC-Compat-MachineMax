package dev.xcolorful.cgccompat.machinemax.core.mixin.entity;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 {@code kill @e} / {@code Entity#discard()} 等主动移除手段对 MachineMax 部件实体生效。
 * <p>
 * MachineMax 的 {@code SubPart#postTick} 会在实体被移除后立即重建 {@code MMPartEntity}
 * （只要对应 subPart 还在），因此小游戏模组的自动清理实体失效。这里在实体仍绑定着存活
 * subPart 时取消 {@link MMPartEntity#discard()}，避免它被重建。
 * <p>
 * 部件被摧毁时 {@code SubPart#destroy} 会先解绑 {@code subPart} 再移除实体，不受此取消
 * 影响，所以摧毁流程保持原样。
 */
@Mixin(MMPartEntity.class)
public abstract class MMPartEntityRemovalMixin {

    @Shadow
    public abstract boolean isRemoved();

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void cgcc$keepPartEntityOnDiscard(CallbackInfo ci) {
        if (CgccMMConfig.entityRemovalAllowed) return;

        MMPartEntity self = (MMPartEntity) (Object) this;
        if (self.subPart != null && !this.isRemoved()) {
            ci.cancel();
        }
    }
}
