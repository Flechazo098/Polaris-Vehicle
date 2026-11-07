package com.flechazo.polaris_vehicle.api.collision;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.hkt.business.data.Chain;
import com.flechazo.polaris_vehicle.vehicle.collision.OrientedBoundingBox;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionCube;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureDocument;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockBonePose;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/** Immutable native-free geometry shared by vanilla block queries and the physics terrain builder. */
public record BlockCollisionGeometry(Chain<OrientedBoundingBox> boxes, VoxelShape shape) {
    private static final int RESOLUTION = 32;

    public static BlockCollisionGeometry compile(BedrockStructureDocument document, Direction facing) {
        return compile(document, facing, name -> BedrockBonePose.IDENTITY);
    }

    public static BlockCollisionGeometry compile(BedrockStructureDocument document, Direction facing, Function<String, BedrockBonePose> pose) {
        // Matches GeoBlockRenderer: translate to block centre, then rotate NORTH/SOUTH/WEST/EAST.
        float angle = switch (facing) {
            case SOUTH -> (float) Math.PI;
            case WEST -> (float) (Math.PI / 2);
            case EAST -> (float) (-Math.PI / 2);
            default -> 0;
        };
        var transform = new Matrix4f().translate(0.5F, 0, 0.5F).rotateY(angle);
        var boxes = Chain.from(VehicleCollisionModel.compile(document, transform, pose).cubes())
                .filter(VehicleCollisionCube::solid).map(VehicleCollisionCube::box);
        VoxelShape shape = boxes.map(BlockCollisionGeometry::voxelize).foldLeft(Shapes.empty(), Shapes::or).optimize();
        return new BlockCollisionGeometry(boxes, shape);
    }

    private static VoxelShape voxelize(OrientedBoundingBox box) {
        AABB bounds = box.enclosingAabb();
        // Exact for axis-aligned cubes. Rotated cubes use a conservative 1/32-block vanilla approximation.
        return Maybe.some(box).filter(value -> aligned(value.axisX()) && aligned(value.axisY()) && aligned(value.axisZ()))
                .map(ignored -> Shapes.create(bounds)).orElseGet(() -> {
                    int x0 = (int) Math.floor(bounds.minX * RESOLUTION), x1 = (int) Math.ceil(bounds.maxX * RESOLUTION);
                    int y0 = (int) Math.floor(bounds.minY * RESOLUTION), y1 = (int) Math.ceil(bounds.maxY * RESOLUTION);
                    int z0 = (int) Math.floor(bounds.minZ * RESOLUTION), z1 = (int) Math.ceil(bounds.maxZ * RESOLUTION);
                    return Pathway.listPathRange(x0, x1).foldLeft(Shapes.empty(), (xs, x) -> Shapes.or(xs,
                            Pathway.listPathRange(y0, y1).foldLeft(Shapes.empty(), (ys, y) -> Shapes.or(ys,
                                    Pathway.listPathRange(z0, z1)
                                            .map(z -> new AABB(x / 32.0, y / 32.0, z / 32.0, (x + 1) / 32.0, (y + 1) / 32.0, (z + 1) / 32.0))
                                            .filter(cell -> box.intersects(cell.deflate(1.0e-8)))
                                            .map(Shapes::create).foldLeft(Shapes.empty(), Shapes::or))))).optimize();
                });
    }

    private static boolean aligned(net.minecraft.world.phys.Vec3 axis) {
        return Math.max(Math.max(Math.abs(axis.x), Math.abs(axis.y)), Math.abs(axis.z)) > 1 - 1.0e-6;
    }
}
