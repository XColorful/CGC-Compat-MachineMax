package dev.xcolorful.cgccompat.machinemax.client.mixin.renderer;

import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import io.github.sweetzonzi.machine_max.client.render.renderer.PartEntityRenderer;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 放宽 MachineMax 部件实体的渲染距离。
 * <p>
 * {@code PartEntityRenderer#shouldRender} 用客户端渲染距离（默认 16 区块）截断部件渲染，
 * 载具离得稍远就整台消失。启用 {@link CgccMMConfig#modifyRenderDistance} 后改成只做视锥体裁剪，
 * 实际可见范围由同步范围（{@link CgccMMConfig#renderDistance}）决定，与 CGC
 * {@code GunProjectileRenderer#shouldRender} 的处理一致。
 */
@Mixin(PartEntityRenderer.class)
public abstract class PartEntityRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void cgcc$shouldRenderWithoutDistanceLimit(MMPartEntity partEntity,
                                                       Frustum frustum,
                                                       double camX, double camY, double camZ,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!CgccMMConfig.modifyRenderDistance) return;
        // subPart 为空时保持原实现（不渲染）
        if (partEntity.subPart == null) return;

        AABB aabb = partEntity.getBoundingBoxForCulling().inflate(0.5);
        if (aabb.hasNaN() || aabb.getSize() == 0.0) {
            aabb = new AABB(partEntity.getX() - 2.0, partEntity.getY() - 2.0, partEntity.getZ() - 2.0,
                    partEntity.getX() + 2.0, partEntity.getY() + 2.0, partEntity.getZ() + 2.0);
        }
        cir.setReturnValue(frustum.isVisible(aabb));
    }
}
