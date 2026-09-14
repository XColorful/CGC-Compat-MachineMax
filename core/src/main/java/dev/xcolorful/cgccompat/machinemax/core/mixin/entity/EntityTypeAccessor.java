package dev.xcolorful.cgccompat.machinemax.core.mixin.entity;

import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link EntityType} 的 {@code clientTrackingRange}，用于放宽 MachineMax 部件实体的
 * 客户端同步范围（默认 {@code MobCategory.MISC} 只有 5 区块 / 80 格）。
 * <p>
 * MachineMax 在构建 {@code PART_ENTITY} 时没有调用 {@code clientTrackingRange}，
 * 该值是构建期写死的，只能在实体类型注册完成后改写。
 */
@Mixin(EntityType.class)
public interface EntityTypeAccessor {

    @Mutable
    @Accessor("clientTrackingRange")
    void cgcc$setClientTrackingRange(int range);
}
