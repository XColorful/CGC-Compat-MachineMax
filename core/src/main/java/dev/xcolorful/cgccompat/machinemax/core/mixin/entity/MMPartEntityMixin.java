package dev.xcolorful.cgccompat.machinemax.core.mixin.entity;

import dev.xcolorful.cgccompat.machinemax.core.projectile.impact._ProjectileHit;
import dev.xcolorful.customgun.core.api.entity.IBulletVictimEntity;
import dev.xcolorful.customgun.core.api.entity.IGunProjectile;
import dev.xcolorful.customgun.core.api.projectile.physics.IProjectilePhysicsRuntime;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MMPartEntity.class)
public class MMPartEntityMixin implements IBulletVictimEntity {

    // --------IBulletVictimEntityImpact--------

    @Override
    public boolean cgc$onProjectileImpact(IProjectilePhysicsRuntime.EntityHitResult entityHitResult,
                                          IGunProjectile iGunProjectile, Entity gunProjectile) {
        return _ProjectileHit.onProjectileHitEntity(entityHitResult, iGunProjectile, gunProjectile);
    }

    // --------IBulletVictimKnockback--------

    @Override
    public void cgc$resetKnockbackStrength() {
    }

    @Override
    public float cgc$getKnockbackStrength() {
        return 0;
    }

    @Override
    public void cgc$setKnockbackStrength(float strength) {
    }
}
