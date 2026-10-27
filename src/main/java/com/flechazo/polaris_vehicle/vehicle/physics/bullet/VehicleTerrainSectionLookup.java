package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.PhysicsChunkSection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Bounds-safe lookup against the exact section which generated a contact.
 */
public final class VehicleTerrainSectionLookup {
    private VehicleTerrainSectionLookup() {
    }

    public static boolean contains(SectionPos section, BlockPos position) {
        return SectionPos.blockToSectionCoord(position.getX()) == section.x()
                && SectionPos.blockToSectionCoord(position.getY()) == section.y()
                && SectionPos.blockToSectionCoord(position.getZ()) == section.z();
    }

    @Nullable
    public static BlockState stateAt(PhysicsChunkSection section, BlockPos position) {
        // Spark 1.0.1031 SectionSnapshot#getRelativePos clamps each coordinate
        // to 0..15. At a top/side edge this would report an in-section block as
        // an out-of-section obstacle. Returning null allows the controller to
        // resolve the real supporting block from its sampled climb field.
        return contains(section.getSectionPos(), position) ? section.getBlockState(position) : null;
    }
}
