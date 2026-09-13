package dev.xcolorful.cgccompat.machinemax.core.mixin.vehicle;

import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import dev.xcolorful.cgccompat.machinemax.core.util.BlockDestructionGuard;
import io.github.sweetzonzi.machine_max.common.vehicle.collision.CollisionHandler;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 载具破坏方块受 mobGriefing 约束，详见 {@link BlockDestructionGuard}。
 * <p>
 * {@code applyBlockDamage} 是载具销毁地形方块的唯一入口，其调用条件里的破坏速度判定
 * （{@code getDestroySpeed >= 0}）本来就把基岩这类不可破坏方块挡在外面。因此在这里取消整段处理，
 * 与"方块不可破坏"是同一路径：方块保留，载具的正常碰撞不受影响。
 * <p>
 * 回调必须照抄目标方法的完整参数表：Mixin 只接受完整签名，写前缀参数会以
 * {@code InvalidInjectionException} 失败，且只记 WARN、不崩游戏，表现为静默失效。
 */
@Mixin(CollisionHandler.class)
public abstract class CollisionHandlerMixin {

    @Inject(method = "applyBlockDamage", at = @At("HEAD"), cancellable = true)
    private void cgcc$interceptBlockDestruction(PhysicsRigidBody other, PhysicsChunkSection terrain,
                                                BlockPos blockPos, BlockState blockState, HitBox hitBox,
                                                Vector3f normal, Vector3f worldContactPoint, Vector3f contactVel,
                                                float partMass, float blockRestitution, float slipRatio,
                                                long manifoldPointId, CallbackInfo ci) {
        if (BlockDestructionGuard.isBlockDestructionBlocked(terrain.getChunk().getLevel())) {
            ci.cancel();
        }
    }
}
