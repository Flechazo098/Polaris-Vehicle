package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.vehicle.collision.OrientedBoundingBox;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionRole;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds one shared native compound shape for each loaded bind-pose model.
 */
public final class VehicleBulletShapeFactory {
    private static final float CHILD_MARGIN = 0.01F;
    private static final Map<VehicleCollisionModel, VehicleBulletShape> CACHE =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private VehicleBulletShapeFactory() {
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    public static VehicleBulletShape getOrCreate(
            VehicleCollisionModel model,
            VehiclePhysicsProfile profile
    ) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(model, ignored -> build(model, profile));
        }
    }

    private static VehicleBulletShape build(
            VehicleCollisionModel model,
            VehiclePhysicsProfile profile
    ) {
        AABB bounds = model.physicsLocalBounds();
        double loadedCenterX = profile.wheels().stream()
                .mapToDouble(mount -> mount.wheelCenter().x)
                .average()
                .orElseThrow();
        double loadedCenterZ = profile.wheels().stream()
                .mapToDouble(mount -> mount.wheelCenter().z)
                .average()
                .orElseThrow();
        Vec3 centerOfMass = new Vec3(
                loadedCenterX,
                bounds.minY + bounds.getYsize() * profile.centerOfMassHeightRatio(),
                loadedCenterZ
        );
        CompoundCollisionShape compound = new CompoundCollisionShape(model.physicsCubeCount());
        Set<String> wheelBones = VehicleAssets.wheelBoneSlots(profile.vehicleType()).keySet();
        model.cubes().stream()
                .filter(cube -> cube.participatesIn(VehicleCollisionRole.PHYSICS.mask(), true))
                // Machine-Max represents wheel hit boxes with dedicated
                // spheres. Keeping the authored wheel cubes as chassis boxes
                // as well would create two overlapping tire contacts and make
                // the box corner catch on every stair riser.
                .filter(cube -> !model.isBoneInSubtrees(cube.boneName(), wheelBones))
                .forEach(cube -> addCube(compound, cube.box(), centerOfMass));
        compound.setMargin(CHILD_MARGIN);
        double minimumHalfExtent = Math.min(
                Math.min(bounds.getXsize(), bounds.getYsize()),
                bounds.getZsize()
        ) * 0.5;
        float ccdRadius = (float) Math.clamp(minimumHalfExtent * 0.35, 0.08, 0.35);
        return new VehicleBulletShape(compound, centerOfMass, ccdRadius);
    }

    public static boolean isWheelGeometryBone(String boneName, Set<String> wheelRootBones) {
        for (String wheelRoot : wheelRootBones) {
            if (boneName.equals(wheelRoot)
                    || boneName.startsWith(wheelRoot + "_")) {
                return true;
            }
        }
        return false;
    }

    private static void addCube(
            CompoundCollisionShape compound,
            OrientedBoundingBox box,
            Vec3 centerOfMass
    ) {
        Vec3 extents = box.halfExtents();
        BoxCollisionShape child = new BoxCollisionShape(
                (float) extents.x,
                (float) extents.y,
                (float) extents.z
        );
        child.setMargin(CHILD_MARGIN);
        Vec3 center = box.center();
        Vec3 axisX = box.axisX();
        Vec3 axisY = box.axisY();
        Vec3 axisZ = box.axisZ();
        Matrix3f rotation = new Matrix3f(
                (float) axisX.x, (float) axisY.x, (float) axisZ.x,
                (float) axisX.y, (float) axisY.y, (float) axisZ.y,
                (float) axisX.z, (float) axisY.z, (float) axisZ.z
        );
        compound.addChildShape(
                child,
                new Vector3f(
                        (float) (center.x - centerOfMass.x),
                        (float) (center.y - centerOfMass.y),
                        (float) (center.z - centerOfMass.z)
                ),
                rotation
        );
    }

    /**
     * Native shape plus the model-origin-to-center-of-mass transform.
     */
    public static final class VehicleBulletShape {
        private final CompoundCollisionShape collisionShape;
        private final Vec3 localCenterOfMass;
        private final float ccdRadius;

        VehicleBulletShape(
                CompoundCollisionShape collisionShape,
                Vec3 localCenterOfMass,
                float ccdRadius
        ) {
            this.collisionShape = collisionShape;
            this.localCenterOfMass = localCenterOfMass;
            this.ccdRadius = ccdRadius;
        }

        public CompoundCollisionShape collisionShape() {
            return collisionShape;
        }

        public Vec3 localCenterOfMass() {
            return localCenterOfMass;
        }

        public float ccdRadius() {
            return ccdRadius;
        }
    }
}
