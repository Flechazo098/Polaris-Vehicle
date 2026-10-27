package com.flechazo.polaris_vehicle.vehicle.collision;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureBone;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureCube;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureDocument;
import com.flechazo.polaris_vehicle.vehicle.collision.data.BedrockStructureGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * Immutable bind-pose collision model shared by every entity of one vehicle
 * type. Cubes and the BVH remain in vehicle-local space.
 */
public final class VehicleCollisionModel {
    private static final double PIXELS_PER_BLOCK = 16.0;

    private final List<VehicleCollisionCube> cubes;
    private final StaticObbBvh bvh;
    private final StaticObbBvh physicsBvh;
    private final OrientedBoundingBox rootBox;
    private final OrientedBoundingBox physicsRootBox;
    private final AABB physicsLocalBounds;
    private final int solidCubeCount;
    private final int physicsCubeCount;
    private final Map<String, Vec3> locators;
    private final Map<String, String> boneParents;

    VehicleCollisionModel(List<VehicleCollisionCube> cubes, Map<String, Vec3> locators,
                          Map<String, String> boneParents) {
        this.cubes = List.copyOf(cubes);
        this.bvh = StaticObbBvh.build(this.cubes);
        List<VehicleCollisionCube> physicsCubes = this.cubes.stream()
                .filter(cube -> cube.participatesIn(
                        VehicleCollisionRole.PHYSICS.mask(),
                        true
                ))
                .toList();
        if (physicsCubes.isEmpty()) {
            throw new IllegalArgumentException("Vehicle model has no solid PHYSICS cubes");
        }
        this.physicsBvh = StaticObbBvh.build(physicsCubes);
        this.rootBox = OrientedBoundingBox.axisAligned(bvh.rootBounds());
        this.physicsLocalBounds = physicsBvh.rootBounds();
        this.physicsRootBox = OrientedBoundingBox.axisAligned(physicsLocalBounds);
        this.solidCubeCount = (int) this.cubes.stream()
                .filter(VehicleCollisionCube::solid)
                .count();
        this.physicsCubeCount = physicsCubes.size();
        this.locators = Map.copyOf(locators);
        this.boneParents = Map.copyOf(boneParents);
    }

    public static VehicleCollisionModel compile(BedrockStructureDocument document) {
        BedrockStructureGeometry geometry = document.primaryGeometry();
        Map<String, BedrockStructureBone> bonesByName = new LinkedHashMap<>();
        Map<String, List<BedrockStructureBone>> childrenByParent = new HashMap<>();
        List<BedrockStructureBone> roots = new ArrayList<>();
        for (BedrockStructureBone bone : geometry.bones()) {
            bonesByName.put(bone.name(), bone);
        }
        for (BedrockStructureBone bone : geometry.bones()) {
            bone.parent().fold(
                    () -> {
                        roots.add(bone);
                        return true;
                    },
                    parentName -> {
                        if (!bonesByName.containsKey(parentName)) {
                            throw new IllegalArgumentException(
                                    "Bone '" + bone.name() + "' references unknown parent '"
                                            + parentName + "'"
                            );
                        }
                        childrenByParent.computeIfAbsent(
                                parentName,
                                ignored -> new ArrayList<>()
                        ).add(bone);
                        return true;
                    }
            );
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Bedrock bone graph has no root");
        }

        List<VehicleCollisionCube> compiledCubes = new ArrayList<>();
        Map<String, Vec3> compiledLocators = new LinkedHashMap<>();
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        // GeoEntityRenderer rotates plain Entity models by 180 degrees before
        // our renderer applies -entityYaw. Including that bind transform here
        // keeps server collision coordinates aligned with visible geometry.
        Matrix4f entityModelTransform = new Matrix4f().rotateY((float) Math.PI);
        for (BedrockStructureBone root : roots) {
            compileBone(
                    root,
                    entityModelTransform,
                    VehicleCollisionRole.PHYSICS,
                    childrenByParent,
                    visiting,
                    visited,
                    compiledCubes,
                    compiledLocators
            );
        }
        if (visited.size() != geometry.bones().size()) {
            throw new IllegalArgumentException("Bedrock bone graph contains a cycle");
        }
        if (compiledCubes.isEmpty()) {
            throw new IllegalArgumentException("Bedrock structure contains no cubes");
        }
        Map<String, String> parents = new HashMap<>();
        for (BedrockStructureBone bone : geometry.bones()) {
            bone.parent().peek(parent -> parents.put(bone.name(), parent));
        }
        return new VehicleCollisionModel(compiledCubes, compiledLocators, parents);
    }

    public List<VehicleCollisionCube> cubes() {
        return cubes;
    }

    /**
     * Hierarchy is authoritative; exported descendant names need not share a prefix.
     */
    public boolean isBoneInSubtrees(String boneName, Set<String> roots) {
        String current = boneName;
        while (current != null) {
            if (roots.contains(current)) return true;
            current = boneParents.get(current);
        }
        return false;
    }

    public Maybe<Vec3> locator(String name) {
        return Maybe.ofNullable(locators.get(name));
    }

    public int cubeCount() {
        return cubes.size();
    }

    public int solidCubeCount() {
        return solidCubeCount;
    }

    public int physicsCubeCount() {
        return physicsCubeCount;
    }

    public int bvhNodeCount() {
        return bvh.nodeCount();
    }

    public int physicsBvhNodeCount() {
        return physicsBvh.nodeCount();
    }

    public AABB localBounds() {
        return bvh.rootBounds();
    }

    public AABB physicsLocalBounds() {
        return physicsLocalBounds;
    }

    public AABB physicsWorldBounds(Vec3 vehiclePosition, float vehicleYawDegrees) {
        return physicsRootBox.toWorld(vehiclePosition, vehicleYawDegrees)
                .enclosingAabb();
    }

    public AABB physicsWorldBounds(Vec3 vehiclePosition, Quaternionf vehicleRotation) {
        return physicsRootBox.toWorld(vehiclePosition, vehicleRotation).enclosingAabb();
    }

    /**
     * Conservative broad phase containing the physics model at every yaw.
     */
    public AABB physicsRotationBounds(Vec3 vehiclePosition) {
        double maximumX = Math.max(
                Math.abs(physicsLocalBounds.minX),
                Math.abs(physicsLocalBounds.maxX)
        );
        double maximumZ = Math.max(
                Math.abs(physicsLocalBounds.minZ),
                Math.abs(physicsLocalBounds.maxZ)
        );
        double radius = Math.hypot(maximumX, maximumZ);
        return new AABB(
                vehiclePosition.x - radius,
                vehiclePosition.y + physicsLocalBounds.minY,
                vehiclePosition.z - radius,
                vehiclePosition.x + radius,
                vehiclePosition.y + physicsLocalBounds.maxY,
                vehiclePosition.z + radius
        );
    }

    public AABB worldBounds(Vec3 vehiclePosition, float vehicleYawDegrees) {
        return rootBox.toWorld(vehiclePosition, vehicleYawDegrees).enclosingAabb();
    }

    public AABB worldBounds(Vec3 vehiclePosition, Quaternionf vehicleRotation) {
        return rootBox.toWorld(vehiclePosition, vehicleRotation).enclosingAabb();
    }

    public boolean intersectsWorldAabb(
            Vec3 vehiclePosition,
            float vehicleYawDegrees,
            AABB worldBounds,
            boolean solidOnly
    ) {
        OrientedBoundingBox localQuery = OrientedBoundingBox.axisAligned(worldBounds)
                .relativeTo(vehiclePosition, vehicleYawDegrees);
        return bvh.intersects(localQuery, solidOnly);
    }

    public boolean intersectsWorldAabb(
            Vec3 vehiclePosition,
            Quaternionf vehicleRotation,
            AABB worldBounds,
            boolean solidOnly
    ) {
        OrientedBoundingBox localQuery = OrientedBoundingBox.axisAligned(worldBounds)
                .relativeTo(vehiclePosition, vehicleRotation);
        return bvh.intersects(localQuery, solidOnly);
    }

    public boolean intersectsWorldAabb(
            Vec3 vehiclePosition,
            float vehicleYawDegrees,
            AABB worldBounds,
            int roleMask,
            boolean solidOnly
    ) {
        OrientedBoundingBox localQuery = OrientedBoundingBox.axisAligned(worldBounds)
                .relativeTo(vehiclePosition, vehicleYawDegrees);
        if (solidOnly && roleMask == VehicleCollisionRole.PHYSICS.mask()) {
            return physicsBvh.intersects(localQuery, false);
        }
        return bvh.intersects(localQuery, roleMask, solidOnly);
    }

    public boolean intersectsWorldAabb(
            Vec3 vehiclePosition,
            Quaternionf vehicleRotation,
            AABB worldBounds,
            int roleMask,
            boolean solidOnly
    ) {
        OrientedBoundingBox localQuery = OrientedBoundingBox.axisAligned(worldBounds)
                .relativeTo(vehiclePosition, vehicleRotation);
        if (solidOnly && roleMask == VehicleCollisionRole.PHYSICS.mask()) {
            return physicsBvh.intersects(localQuery, false);
        }
        return bvh.intersects(localQuery, roleMask, solidOnly);
    }

    public int broadPhaseCandidateCount(
            Vec3 vehiclePosition,
            float vehicleYawDegrees,
            AABB worldBounds
    ) {
        OrientedBoundingBox localQuery = OrientedBoundingBox.axisAligned(worldBounds)
                .relativeTo(vehiclePosition, vehicleYawDegrees);
        return bvh.candidateCount(localQuery);
    }

    public double sweepFractionAgainstWorldAabb(
            Vec3 vehiclePosition,
            float vehicleYawDegrees,
            AABB worldObstacle,
            Vec3 worldMovement,
            boolean solidOnly
    ) {
        OrientedBoundingBox localObstacle = OrientedBoundingBox.axisAligned(worldObstacle)
                .relativeTo(vehiclePosition, vehicleYawDegrees);
        Vec3 localMovement = worldMovement.yRot((float) Math.toRadians(vehicleYawDegrees));
        return bvh.sweepFraction(localObstacle, localMovement, solidOnly);
    }

    public double sweepFractionAgainstWorldAabb(
            Vec3 vehiclePosition,
            float vehicleYawDegrees,
            AABB worldObstacle,
            Vec3 worldMovement,
            int roleMask,
            boolean solidOnly
    ) {
        OrientedBoundingBox localObstacle = OrientedBoundingBox.axisAligned(worldObstacle)
                .relativeTo(vehiclePosition, vehicleYawDegrees);
        Vec3 localMovement = worldMovement.yRot((float) Math.toRadians(vehicleYawDegrees));
        if (solidOnly && roleMask == VehicleCollisionRole.PHYSICS.mask()) {
            return physicsBvh.sweepFraction(localObstacle, localMovement, false);
        }
        return bvh.sweepFraction(localObstacle, localMovement, roleMask, solidOnly);
    }

    /**
     * Sweeps the complete, arbitrarily rotated vehicle body against a
     * world-space axis-aligned box. This is the quaternion counterpart of the
     * yaw-only overload used by the block resolver and is primarily used for
     * entities walking into or falling onto a pitched/rolled vehicle.
     */
    public double sweepFractionAgainstWorldAabb(
            Vec3 vehiclePosition,
            Quaternionf vehicleRotation,
            AABB worldObstacle,
            Vec3 worldMovement,
            int roleMask,
            boolean solidOnly
    ) {
        Quaternionf normalizedRotation = normalizedQuaternion(vehicleRotation);
        OrientedBoundingBox localObstacle = OrientedBoundingBox.axisAligned(worldObstacle)
                .relativeTo(vehiclePosition, normalizedRotation);
        Vec3 localMovement = inverseRotate(worldMovement, normalizedRotation);
        if (solidOnly && roleMask == VehicleCollisionRole.PHYSICS.mask()) {
            return physicsBvh.sweepFraction(localObstacle, localMovement, false);
        }
        return bvh.sweepFraction(localObstacle, localMovement, roleMask, solidOnly);
    }

    private static Quaternionf normalizedQuaternion(Quaternionf rotation) {
        Quaternionf result = new Quaternionf(rotation);
        float lengthSquared = result.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-8F) {
            throw new IllegalArgumentException("vehicleRotation must be a finite quaternion");
        }
        return result.normalize();
    }

    private static Vec3 inverseRotate(Vec3 vector, Quaternionf rotation) {
        double qx = -rotation.x();
        double qy = -rotation.y();
        double qz = -rotation.z();
        double qw = rotation.w();
        double tx = 2.0 * (qy * vector.z - qz * vector.y);
        double ty = 2.0 * (qz * vector.x - qx * vector.z);
        double tz = 2.0 * (qx * vector.y - qy * vector.x);
        return new Vec3(
                vector.x + qw * tx + (qy * tz - qz * ty),
                vector.y + qw * ty + (qz * tx - qx * tz),
                vector.z + qw * tz + (qx * ty - qy * tx)
        );
    }

    private static void compileBone(
            BedrockStructureBone bone,
            Matrix4f parentTransform,
            VehicleCollisionRole parentRole,
            Map<String, List<BedrockStructureBone>> childrenByParent,
            Set<String> visiting,
            Set<String> visited,
            List<VehicleCollisionCube> output,
            Map<String, Vec3> locatorOutput
    ) {
        if (!visiting.add(bone.name())) {
            throw new IllegalArgumentException("Cycle at Bedrock bone '" + bone.name() + "'");
        }
        Matrix4f boneTransform = applyBedrockPivotRotation(
                new Matrix4f(parentTransform),
                bone.pivot(),
                bone.rotation()
        );
        VehicleCollisionRole role = VehicleCollisionRoles.classify(bone.name(), parentRole);
        bone.locators().forEach((name, rawPosition) -> {
            Vec3 resolvedPosition = rawPosition.lengthSqr() < 1.0E-12
                    && name.equalsIgnoreCase(bone.name())
                    ? bone.pivot()
                    : rawPosition;
            Vector3f transformed = boneTransform.transformPosition(new Vector3f(
                    (float) (-resolvedPosition.x / PIXELS_PER_BLOCK),
                    (float) (resolvedPosition.y / PIXELS_PER_BLOCK),
                    (float) (resolvedPosition.z / PIXELS_PER_BLOCK)
            ));
            Vec3 previous = locatorOutput.putIfAbsent(
                    name,
                    new Vec3(transformed.x, transformed.y, transformed.z)
            );
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate Bedrock locator: " + name);
            }
        });
        for (int cubeIndex = 0; cubeIndex < bone.cubes().size(); cubeIndex++) {
            BedrockStructureCube cube = bone.cubes().get(cubeIndex);
            double inflatePixels = cube.inflate().orElseGet(() -> bone.inflate().orElse(0.0));
            Vec3 size = cube.size();
            Vec3 center = new Vec3(
                    -(cube.origin().x + size.x * 0.5) / PIXELS_PER_BLOCK,
                    (cube.origin().y + size.y * 0.5) / PIXELS_PER_BLOCK,
                    (cube.origin().z + size.z * 0.5) / PIXELS_PER_BLOCK
            );
            Vec3 halfExtents = new Vec3(
                    Math.max(0.0, Math.abs(size.x) / (PIXELS_PER_BLOCK * 2.0)
                            + inflatePixels / PIXELS_PER_BLOCK),
                    Math.max(0.0, Math.abs(size.y) / (PIXELS_PER_BLOCK * 2.0)
                            + inflatePixels / PIXELS_PER_BLOCK),
                    Math.max(0.0, Math.abs(size.z) / (PIXELS_PER_BLOCK * 2.0)
                            + inflatePixels / PIXELS_PER_BLOCK)
            );
            Matrix4f cubeTransform = applyBedrockPivotRotation(
                    new Matrix4f(boneTransform),
                    cube.pivot(),
                    cube.rotation()
            );
            OrientedBoundingBox box = OrientedBoundingBox.transformed(
                    center,
                    halfExtents,
                    cubeTransform
            );
            output.add(new VehicleCollisionCube(
                    bone.name(),
                    cubeIndex,
                    box,
                    role
            ));
        }
        for (BedrockStructureBone child : childrenByParent.getOrDefault(bone.name(), List.of())) {
            compileBone(
                    child,
                    boneTransform,
                    role,
                    childrenByParent,
                    visiting,
                    visited,
                    output,
                    locatorOutput
            );
        }
        visiting.remove(bone.name());
        visited.add(bone.name());
    }

    private static Matrix4f applyBedrockPivotRotation(
            Matrix4f transform,
            Vec3 rawPivot,
            Vec3 rawRotationDegrees
    ) {
        float pivotX = (float) (-rawPivot.x / PIXELS_PER_BLOCK);
        float pivotY = (float) (rawPivot.y / PIXELS_PER_BLOCK);
        float pivotZ = (float) (rawPivot.z / PIXELS_PER_BLOCK);
        transform.translate(pivotX, pivotY, pivotZ);
        transform.rotateZ((float) Math.toRadians(rawRotationDegrees.z));
        transform.rotateY((float) Math.toRadians(-rawRotationDegrees.y));
        transform.rotateX((float) Math.toRadians(-rawRotationDegrees.x));
        transform.translate(-pivotX, -pivotY, -pivotZ);
        return transform;
    }
}
