package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Actual narrow-phase terrain for one wheel, including suspended-step gaps.
 */
public final class VehicleWheelTerrainSurface {
    public record Ownership(BulletVehicleController controller, int wheelIndex) {
    }

    private final Ownership ownership;
    private PhysicsRigidBody body;
    private long revision = -1;
    private int centerX = Integer.MIN_VALUE, centerZ = Integer.MIN_VALUE;
    private int anchorX, anchorZ, anchorY;
    private float[] geometry = new float[0];
    private int meshBuildCount;
    private PhysicsSpace world;
    private final Set<BlockPos> coveredBlocks = new HashSet<>();
    private final Set<BlockPos> rampBlocks = new HashSet<>();

    VehicleWheelTerrainSurface(BulletVehicleController controller, int wheelIndex) {
        ownership = new Ownership(controller, wheelIndex);
    }

    void update(PhysicsSpace world, VehicleClimbHeightField field, Vector3f center,
                double reference, double maximumStepHeight) {
        if (!field.hasConnectedHeightVariation()) {
            if (body != null) remove();
            return;
        }
        int x = (int) Math.floor(center.x), z = (int) Math.floor(center.z);
        if (this.world == world && field.revision() == revision
                && x == centerX && z == centerZ) return;
        // A field revision only says the sampling window moved. It does not
        // mean the terrain under a wheel changed. Keep this world's anchored
        // patch, including native manifolds, until the wheel nears its edge.
        boolean keepAnchor = this.world == world && body != null
                && Math.abs(center.x - (anchorX + 0.5F)) < 2.0F
                && Math.abs(center.z - (anchorZ + 0.5F)) < 2.0F;
        if (this.world != world) remove();
        if (!keepAnchor) {
            anchorX = x;
            anchorZ = z;
            anchorY = (int) Math.floor(center.y);
        }
        this.world = world;
        revision = field.revision();
        centerX = x;
        centerZ = z;
        if (!Double.isFinite(reference) || maximumStepHeight <= 0) {
            remove();
            return;
        }
        Set<BlockPos> nextCovered = new HashSet<>();
        Set<BlockPos> nextRamps = new HashSet<>();
        ArrayList<Vector3f> vertices = new ArrayList<>();
        // Ordinary flat ground keeps its original terrain body. Replacing it
        // with four per-wheel triangle grids caused needless internal edges,
        // duplicate contacts and manifold loss even during normal driving.
        float[][] heights = new float[29][29];
        for (float[] row : heights) Arrays.fill(row, Float.NaN);
        for (int blockX = -3; blockX < 4; blockX++) {
            for (int blockZ = -3; blockZ < 4; blockZ++) {
                float bx = anchorX + blockX, bz = anchorZ + blockZ;
                var raw = field.rawSurfaceAt(bx + 0.5F, bz + 0.5F);
                if (raw == null || !field.connectedSupport(raw.blockPos())
                        || !needsRamp(field, raw, bx, bz, reference, maximumStepHeight)) continue;
                nextCovered.add(raw.blockPos());
                nextRamps.add(raw.blockPos());
                // Include the whole selected column, since suppression of an
                // original block contact also applies to the entire column.
                for (int qx = 0; qx < 4; qx++)
                    for (int qz = 0; qz < 4; qz++) {
                        int ix = (blockX + 3) * 4 + qx, iz = (blockZ + 3) * 4 + qz;
                        float x0 = bx + qx * 0.25F, z0 = bz + qz * 0.25F;
                        float a = sampleHeight(heights, ix, iz, field, reference, maximumStepHeight);
                        float b = sampleHeight(heights, ix + 1, iz, field, reference, maximumStepHeight);
                        float c = sampleHeight(heights, ix, iz + 1, field, reference, maximumStepHeight);
                        float d = sampleHeight(heights, ix + 1, iz + 1, field, reference, maximumStepHeight);
                        if (!Float.isFinite(a) || !Float.isFinite(b) || !Float.isFinite(c) || !Float.isFinite(d))
                            continue;
                        Vector3f p00 = new Vector3f(x0 - anchorX, a - anchorY, z0 - anchorZ);
                        Vector3f p10 = new Vector3f(x0 + 0.25F - anchorX, b - anchorY, z0 - anchorZ);
                        Vector3f p01 = new Vector3f(x0 - anchorX, c - anchorY, z0 + 0.25F - anchorZ);
                        Vector3f p11 = new Vector3f(x0 + 0.25F - anchorX, d - anchorY, z0 + 0.25F - anchorZ);
                        vertices.add(p00);
                        vertices.add(p01);
                        vertices.add(p10);
                        vertices.add(p10);
                        vertices.add(p01);
                        vertices.add(p11);
                    }
            }
        }
        float[] nextGeometry = new float[vertices.size() * 3 + 3];
        nextGeometry[0] = anchorX;
        nextGeometry[1] = anchorY;
        nextGeometry[2] = anchorZ;
        for (int i = 0; i < vertices.size(); i++) {
            Vector3f vertex = vertices.get(i);
            nextGeometry[3 + i * 3] = vertex.x;
            nextGeometry[4 + i * 3] = vertex.y;
            nextGeometry[5 + i * 3] = vertex.z;
        }
        coveredBlocks.clear();
        coveredBlocks.addAll(nextCovered);
        rampBlocks.clear();
        rampBlocks.addAll(nextRamps);
        if (Arrays.equals(geometry, nextGeometry)) return;
        if (body != null && world.contains(body)) world.removeCollisionObject(body);
        body = null;
        geometry = nextGeometry;
        if (vertices.isEmpty()) return;
        int[] indices = IntStream.range(0, vertices.size()).toArray();
        MeshCollisionShape shape = new MeshCollisionShape(true,
                new IndexedMesh(vertices.toArray(Vector3f[]::new), indices));
        shape.setMargin(0.001F);
        body = new PhysicsRigidBody(shape, 0);
        body.setPhysicsLocation(new Vector3f(anchorX, anchorY, anchorZ));
        body.setCollisionGroup(CollisionGroups.TERRAIN);
        body.setCollideWithGroups(CollisionGroups.PHYSICS_BODY);
        body.setUserObject(ownership);
        body.setFriction(1);
        body.setRestitution(0);
        world.addCollisionObject(body);
        meshBuildCount++;
    }

    int meshBuildCount() {
        return meshBuildCount;
    }

    private float sampleHeight(float[][] heights, int x, int z, VehicleClimbHeightField field,
                               double reference, double step) {
        if (Float.isNaN(heights[x][z])) heights[x][z] = roundedSupportHeight(field,
                anchorX - 3 + x * 0.25F, anchorZ - 3 + z * 0.25F, reference, step);
        return heights[x][z];
    }

    private static boolean needsRamp(VehicleClimbHeightField field, VehicleClimbHeightField.Surface raw,
                                     float x, float z, double reference, double step) {
        if (field.nativeRamp(raw.blockPos())) {
            // A sloping plane itself is not a stair. Check both sides of its
            // boundaries; offset ramps have vertical seams that need bridging.
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                float ex = x + .5F + direction.getStepX() * .5F;
                float ez = z + .5F + direction.getStepZ() * .5F;
                var inside = field.rawSurfaceAt(ex - direction.getStepX() * .001F, ez - direction.getStepZ() * .001F);
                var outside = field.rawSurfaceAt(ex + direction.getStepX() * .001F, ez + direction.getStepZ() * .001F);
                if (inside != null && outside != null && field.connectedSupport(outside.blockPos())) {
                    double jump = Math.abs(inside.height() - outside.height());
                    if (jump > .01 && jump <= step + .04) return true;
                }
            }
            return false;
        }
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++) {
                float h = field.supportHeight(x + dx, z + dz, reference, step);
                if (Float.isFinite(h) && h > raw.height() + 0.001F) return true;
            }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var next = field.rawSurfaceAt(x + 0.5F + direction.getStepX(), z + 0.5F + direction.getStepZ());
            if (next != null && field.connectedSupport(next.blockPos())
                    && Math.abs(next.height() - raw.height()) > 0.001F
                    && Math.abs(next.height() - raw.height()) <= step + 0.04) return true;
        }
        return false;
    }

    boolean active() {
        return body != null && body.isInWorld();
    }

    private static float roundedSupportHeight(VehicleClimbHeightField field, float x, float z,
                                              double reference, double maxStep) {
        float base = field.supportHeight(x, z, reference, maxStep);
        float total = base * 4, weights = 4;
        for (int axis = 0; axis < 4; axis++) {
            float dx = axis == 0 ? 0.75F : axis == 1 ? -0.75F : 0;
            float dz = axis == 2 ? 0.75F : axis == 3 ? -0.75F : 0;
            float h = field.supportHeight(x + dx, z + dz, reference, maxStep);
            if (Float.isFinite(h)) {
                total += h;
                weights++;
            }
        }
        var raw = field.rawSurfaceAt(x, z);
        float lowerBound = raw != null && field.connectedSupport(raw.blockPos())
                ? raw.height() : Float.NEGATIVE_INFINITY;
        // Symmetric convolution rounds the ends of a constant-slope ramp.
        // Clamp to actual raw tops, so rounding cannot carve a wheel path
        // inside a visible block.
        return Math.max(Math.max(lowerBound, base), total / weights);
    }

    boolean covers(BlockPos block) {
        return active() && coveredBlocks.contains(block);
    }

    boolean replacesChassisContact(BlockPos block) {
        return active() && rampBlocks.contains(block);
    }

    void remove() {
        if (body != null && world != null && world.contains(body)) world.removeCollisionObject(body);
        body = null;
        world = null;
        coveredBlocks.clear();
        rampBlocks.clear();
        geometry = new float[0];
        revision = -1;
    }

    /**
     * Both the production overlap filter and native regression use this exact policy.
     */
    public static boolean canCollide(PhysicsCollisionObject a, PhysicsCollisionObject b) {
        if (a.getUserObject() instanceof Ownership(BulletVehicleController controller1, int index)) {
            return controller1.wheelTerrainEnabled()
                    && controller1.wheelBody(index) == b;
        }
        if (b.getUserObject() instanceof Ownership(BulletVehicleController controller, int wheelIndex)) {
            return controller.wheelTerrainEnabled()
                    && controller.wheelBody(wheelIndex) == a;
        }
        return true;
    }
}
