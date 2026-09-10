package dev.xcolorful.cgccompat.machinemax.core.projectile.impact;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.Vector3f;
import dev.xcolorful.customgun.core.api.entity.IGunProjectile;
import dev.xcolorful.customgun.core.api.minecraft.damage.CustomDamageType;
import dev.xcolorful.customgun.core.api.projectile.physics.IProjectilePhysicsRuntime;
import dev.xcolorful.customgun.core.init.registry.ModDamageTypes;
import dev.xcolorful.customgun.core.resource.data.data.gun.bullet.damage._DistanceDamageData;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * CGC 枪射物对 MachineMax 部件实体的出伤兼容。
 * <p>
 * CGC 枪射物不走 NeoForge 的 projectile hit 事件, 而是以自己的射线检测结果手动调用
 * {@code IBulletVictimEntity#cgc$onProjectileImpact}。这里复刻 MachineMax
 * {@code PartHitHandler} 的物理射线判定: 沿枪射物本 tick 位移做物理射线检测, 只有当射线
 * 实际命中了该实体对应 subPart 的活动碰撞箱时才出伤。
 */
public class _ProjectileHit {

    /**
     * @return 是否算作"已处理" (消耗穿透数); 碰撞箱判定没通过时返回 false (不打该部位)
     */
    public static boolean onProjectileHitEntity(IProjectilePhysicsRuntime.EntityHitResult entityHitResult,
                                                IGunProjectile iGunProjectile, Entity gunProjectile) {
        Entity victimEntity = entityHitResult.entity();
        if (!(victimEntity instanceof MMPartEntity partEntity)) {
            return false;
        }
        SubPart subPart = partEntity.subPart;
        if (subPart == null) {
            return false;
        }

        // --------MachineMax 碰撞箱判定--------
        // 以 CGC 射线检测为准, 沿枪射物本 tick 位移做物理射线检测, 找第一个命中
        // 活动碰撞箱(非车轮)的 SubPart
        Vec3 startPos = gunProjectile.position();
        Vec3 deltaMovement = gunProjectile.getDeltaMovement();
        if (deltaMovement.lengthSqr() < 1.0E-6) {
            return false;
        }
        Vector3f start = PhysicsHelperKt.toBVector3f(startPos);
        Vector3f end = PhysicsHelperKt.toBVector3f(startPos.add(deltaMovement));

        SubPart hitSubPart = null;
        HitBox hitBox = null;
        Vector3f hitPoint = null;
        Vector3f hitNormal = null;

        List<PhysicsRayTestResult> results = SparkLevel.getPhysicsLevel(gunProjectile.level())
                .getWorld().getWorldSnapshot().rayTest(start, end);
        for (PhysicsRayTestResult result : results) {
            PhysicsHost owner = PhysicsBodyExtensionKt.getOwner(result.getCollisionObject());
            if (!(owner instanceof SubPart candidate)) {
                continue;
            }
            if (candidate.isWheel(result.triangleIndex()) && candidate.isWheelSurface(result.triangleIndex())) {
                continue;
            }
            HitBox candidateHitBox = candidate.getHitBox(result.triangleIndex());
            if (!candidateHitBox.isActive()) {
                continue;
            }
            hitSubPart = candidate;
            hitBox = candidateHitBox;
            hitPoint = start.add(end.subtract(start).mult(result.getHitFraction()));
            hitNormal = result.getHitNormalLocal(null);
            break;
        }

        // 没命中活动碰撞箱, 或命中的是别的 subPart -> 不消耗穿透数
        if (hitSubPart == null || hitSubPart != subPart) {
            return false;
        }

        // --------出伤--------
        float damage = _calculateDamage(entityHitResult.hitPos(), iGunProjectile, gunProjectile);
        if (entityHitResult.headshot()) {
            damage *= iGunProjectile.getHeadshotMultiplier(gunProjectile);
        }

        DamageSource source = _createDamageSource(gunProjectile);
        Vector3f worldContactSpeed = PhysicsHelperKt.toBVector3f(gunProjectile.getDeltaMovement().scale(20.0))
                .subtract(subPart.body.getLinearVelocity(null));
        PartDamageData data = new PartDamageData(source, null, hitNormal, worldContactSpeed, hitPoint, hitBox);

        subPart.onHurt(data, damage);
        return true;
    }

    private static float _calculateDamage(Vec3 hitPos, IGunProjectile iGunProjectile, Entity gunProjectile) {
        @Nullable List<_DistanceDamageData> damageCalculation = iGunProjectile.getDamageCalculation(gunProjectile);
        if (damageCalculation == null) return 0;

        @Nullable Vec3 shootPos = iGunProjectile.getShootPos(gunProjectile);
        double shootDistance = shootPos != null ? hitPos.distanceTo(shootPos) : 0;
        float damage = 0;
        for (_DistanceDamageData damageData : damageCalculation) {
            damage = damageData.getDamage();
            // 取第一个 >= shootDistance 时伤害值
            if (shootDistance < damageData.getDistance()) {
                break;
            }
        }
        return damage;
    }

    private static DamageSource _createDamageSource(Entity gunProjectile) {
        @Nullable Entity causingEntity = gunProjectile instanceof Projectile projectile ? projectile.getOwner() : null;
        if (causingEntity == null) causingEntity = gunProjectile;
        RegistryAccess registryAccess = gunProjectile.level().registryAccess();
        return ModDamageTypes.createDamage(registryAccess, CustomDamageType.BULLET, gunProjectile, causingEntity);
    }
}
