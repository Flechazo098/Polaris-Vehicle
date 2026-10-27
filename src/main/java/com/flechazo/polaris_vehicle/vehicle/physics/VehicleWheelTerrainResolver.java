package com.flechazo.polaris_vehicle.vehicle.physics;

import com.flechazo.polaris_vehicle.registry.ModBlockTags;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsProfile;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleWheelContactGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class VehicleWheelTerrainResolver {
    private static final double CONTACT_SKIN = 0.06;
    private static final int MAXIMUM_DOWNWARD_BLOCK_SEARCH = 2;

    private VehicleWheelTerrainResolver() {
    }

    public static VehicleWheelTerrain resolve(AbstractVehicleEntity vehicle) {
        VehiclePhysicsProfile profile = VehiclePhysicsProfile.require(vehicle.vehicleTypeId());
        Quaternionf rotation = vehicle.vehicleBodyRotation();
        List<WheelTerrain> result = new ArrayList<>(profile.wheels().size());
        for (int index = 0; index < profile.wheels().size(); index++) {
            var mount = profile.wheels().get(index);
            Vec3 local = mount.wheelCenter();
            Vector3f rotated = rotation.transform(new Vector3f(
                    (float) local.x,
                    (float) local.y,
                    (float) local.z
            ));
            Vec3 contact = vehicle.physicalWheelContactPoint(index)
                    .map(point -> point.add(0.0, -CONTACT_SKIN, 0.0))
                    .orElseGet(() -> vehicle.position().add(
                            rotated.x,
                            rotated.y - VehicleWheelContactGeometry.radius(
                                    profile,
                                    vehicle.vehicleData(),
                                    mount
                            ) - CONTACT_SKIN,
                            rotated.z
                    ));
            result.add(classifyGround(BlockPos.containing(contact), vehicle.level()::getBlockState));
        }
        return new VehicleWheelTerrain(result);
    }

    static WheelTerrain classifyGround(BlockPos initial, Function<BlockPos, BlockState> blockStateAt) {
        BlockPos.MutableBlockPos cursor = initial.mutable();
        for (int depth = 0; depth <= MAXIMUM_DOWNWARD_BLOCK_SEARCH; depth++) {
            BlockState state = blockStateAt.apply(cursor);
            if (!state.isAir()) {
                if (state.is(ModBlockTags.RACING_TIRE_SAND_BLOCKS)) {
                    return WheelTerrain.SAND;
                }
                if (state.is(ModBlockTags.RACING_TIRE_GRASS_BLOCKS)) {
                    return state.is(ModBlockTags.SOFT_GROUND_BLOCKS) ? WheelTerrain.SOFT_GRASS : WheelTerrain.GRASS;
                }
                if (state.is(ModBlockTags.RACING_TIRE_GRAVEL_BLOCKS)) {
                    return WheelTerrain.GRAVEL;
                }
                if (state.is(ModBlockTags.ROAD_BLOCKS)) {
                    return WheelTerrain.ROAD;
                }
                if (state.is(ModBlockTags.SOFT_GROUND_BLOCKS)) {
                    return WheelTerrain.SOFT;
                }
                return WheelTerrain.NORMAL;
            }
            cursor.move(0, -1, 0);
        }
        return WheelTerrain.NORMAL;
    }
}
