package dev.xcolorful.cgccompat.machinemax.core.init.registry;

import dev.xcolorful.cgccompat.machinemax.CgccMachineMax;
import dev.xcolorful.cgccompat.machinemax.core.config.CgccMMConfig;
import dev.xcolorful.cgccompat.machinemax.core.mixin.entity.EntityTypeAccessor;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import net.minecraft.world.entity.EntityType;

/**
 * 放宽 MachineMax 部件实体的客户端同步范围。
 * <p>
 * MachineMax 构建 {@code PART_ENTITY} 时没有调用 {@code clientTrackingRange}，
 * {@code MobCategory.MISC} 的默认值只有 5 区块（80 格），载具稍远就同步不到客户端。
 * 这里按 {@link CgccMMConfig#renderDistance} 与渲染范围对齐。
 */
public final class ModEntities {

    private ModEntities() {
    }

    public static void modifyVehicleRenderDistance() {
        if (!CgccMMConfig.modifyRenderDistance) return;

        try {
            EntityType<MMPartEntity> partEntityType = MMEntities.getPART_ENTITY().get();
            ((EntityTypeAccessor) partEntityType).cgcc$setClientTrackingRange(CgccMMConfig.renderDistance);
        } catch (RuntimeException exception) {
            CgccMachineMax.LOGGER.error("Failed to extend MachineMax part entity client tracking range", exception);
        }
    }
}
