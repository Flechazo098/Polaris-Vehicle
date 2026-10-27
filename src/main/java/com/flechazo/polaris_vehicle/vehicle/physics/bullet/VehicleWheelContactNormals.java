package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.simsilica.mathd.Vec3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

/**
 * Recognises a real flat collision face beneath a sphere, excluding slopes and risers.
 */
final class VehicleWheelContactNormals {
    private VehicleWheelContactNormals() {
    }

    static double flatTop(Vec3d center, double radius, double contactY,
                          Function<BlockPos, BlockState> terrain) {
        return flatTop(center, radius, contactY, terrain, null);
    }

    static double flatTop(Vec3d center, double radius, double contactY,
                          Function<BlockPos, BlockState> contactSection,
                          Function<BlockPos, BlockState> allSections) {
        BlockPos pos = BlockPos.containing(center.x, contactY - 0.015, center.z);
        BlockState state = contactSection.apply(pos);
        if (state == null && allSections != null) state = allSections.apply(pos);
        if (state == null || state.getBlock() instanceof RampBlock) return Double.NaN;
        for (var box : state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).toAabbs()) {
            double top = pos.getY() + box.maxY;
            double x = center.x - pos.getX(), z = center.z - pos.getZ();
            if (x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ
                    && Math.abs(contactY - top) < 0.015
                    && center.y - radius >= top - 0.015) return top;
        }
        return Double.NaN;
    }

}
