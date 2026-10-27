package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.content.block.FullRampBlock;
import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.flechazo.polaris_vehicle.content.block.UpperRampBlock;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevel;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.SectionSnapshot;
import com.jme3.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;
import java.util.function.Function;

/**
 * Small world-aligned terrain height field used only while a vehicle crosses
 * a legal Minecraft step.
 *
 * <p>The design follows the local-height-field technique used by Machine-Max:
 * sample collision-shape tops, smooth only downward from upper cells, then
 * solve a sphere/height-field contact iteratively. Adapted for Polaris with
 * the Machine-Max author's explicit permission to use the height-field code
 * under the MIT license.</p>
 */
final class VehicleClimbHeightField {
    private static final float EMPTY = Float.NEGATIVE_INFINITY;
    private static final int SMOOTHING_PASSES = 3;
    private static final float ORTHOGONAL_WEIGHT = 1.0F;
    private static final float DIAGONAL_WEIGHT = 0.5F;
    private static final float CLIMB_EPSILON = 0.04F;

    private enum SurfaceBand {
        RIGID,
        SOFT,
        OTHER,
        NONE
    }

    private final int radius;
    private final int size;
    private final float[][][] rawHeights;
    private final float[][][] smoothHeights;
    private final SurfaceBand[][] surfaceBands;
    private final float[][] sampledHeights;
    private final BlockPos[][] surfaceBlocks;
    private final RampCell[][] rampCells;
    private final boolean[][] gentleEdges;

    private record RampCell(int x, int z, double bottom, double rise, Direction highEdge) {
        float heightAt(float worldX, float worldZ) {
            double along = highEdge.getStepX() * (Math.clamp(worldX - x, 0, 1) - .5)
                    + highEdge.getStepZ() * (Math.clamp(worldZ - z, 0, 1) - .5) + .5;
            return (float) (bottom + rise * along);
        }
    }

    private final Set<BlockPos> climbableBlocks = new HashSet<>();
    private final Set<BlockPos> connectedSupports = new HashSet<>();
    private final Map<BlockPos, Double> nativeRampFloors = new HashMap<>();
    private final Set<BlockPos> connectionSeeds = new HashSet<>();
    private boolean connectivityDirty = true;
    private double connectedStep;
    private boolean connectedHeightVariation;
    private int rebuildCount;
    private int connectivityBuildCount;
    private int originX;
    private int originZ;
    private boolean valid;
    private long revision;

    VehicleClimbHeightField(int radius) {
        if (radius < 2) {
            throw new IllegalArgumentException("height-field radius must be at least two");
        }
        // Unlike Machine-Max's individual wheel bodies, a Polaris field is
        // shared by the whole chassis. Reserve a convolution halo so moving
        // its grid origin cannot alter an interior wheel's support height.
        this.radius = radius + SMOOTHING_PASSES;
        this.size = this.radius * 2 + 1;
        int bandCount = SurfaceBand.NONE.ordinal();
        this.rawHeights = new float[bandCount][size][size];
        this.smoothHeights = new float[bandCount][size][size];
        this.surfaceBands = new SurfaceBand[size][size];
        this.sampledHeights = new float[size][size];
        this.surfaceBlocks = new BlockPos[size][size];
        this.rampCells = new RampCell[size][size];
        this.gentleEdges = new boolean[size][size];
        clear();
    }

    void clear() {
        revision++;
        valid = false;
        climbableBlocks.clear();
        connectedSupports.clear();
        nativeRampFloors.clear();
        connectionSeeds.clear();
        connectivityDirty = true;
        connectedHeightVariation = false;
        for (int band = 0; band < rawHeights.length; band++) {
            for (int x = 0; x < size; x++) {
                Arrays.fill(rawHeights[band][x], EMPTY);
                Arrays.fill(smoothHeights[band][x], EMPTY);
            }
        }
        for (int x = 0; x < size; x++) {
            Arrays.fill(surfaceBands[x], SurfaceBand.NONE);
            Arrays.fill(sampledHeights[x], EMPTY);
            Arrays.fill(surfaceBlocks[x], null);
            Arrays.fill(rampCells[x], null);
            Arrays.fill(gentleEdges[x], false);
        }
    }

    void rebuild(
            PhysicsLevel physicsLevel,
            int centerX,
            int centerZ,
            double baseSurfaceY,
            double maximumStepHeight
    ) {
        rebuild(centerX, centerZ, baseSurfaceY, maximumStepHeight, blockPos -> {
            SectionSnapshot.BlockSnapshot snapshot = physicsLevel.getTerrainManager()
                    .getBlockSnapshotAt(blockPos);
            return snapshot == null ? null : snapshot.getState();
        });
    }

    void rebuild(
            int centerX,
            int centerZ,
            double baseSurfaceY,
            double maximumStepHeight,
            Function<BlockPos, BlockState> blockStateAt
    ) {
        rebuild(centerX, centerZ, baseSurfaceY, maximumStepHeight, blockStateAt,
                new double[][]{{centerX + .5, centerZ + .5, baseSurfaceY}});
    }

    /**
     * @param supports each tire's {x, z, lowest point}; the surfaces it can
     *                 reach from there are preferred over anything above
     */
    void rebuild(
            int centerX,
            int centerZ,
            double baseSurfaceY,
            double maximumStepHeight,
            Function<BlockPos, BlockState> blockStateAt,
            double[][] supports
    ) {
        rebuildCount++;
        clear();
        if (!Double.isFinite(baseSurfaceY) || maximumStepHeight <= 0.0) {
            return;
        }
        originX = centerX - radius;
        originZ = centerZ - radius;
        int baseBlockY = (int) Math.floor(baseSurfaceY);
        // Continuous one-block stairs can rise by one block per sampled
        // column. The halo must also contain their upper surfaces, otherwise
        // truncation introduces the same boundary artifact in the Y scan.
        int maximumBlockY = baseBlockY + radius + 1;
        int minimumBlockY = baseBlockY - radius - 2;

        @SuppressWarnings("unchecked")
        List<Candidate>[][] candidates = new List[size][size];
        Candidate[][] topmost = new Candidate[size][size];
        for (int gridX = 0; gridX < size; gridX++) {
            for (int gridZ = 0; gridZ < size; gridZ++) {
                candidates[gridX][gridZ] = sampleColumn(blockStateAt, originX + gridX, originZ + gridZ,
                        minimumBlockY, maximumBlockY, topmost, gridX, gridZ);
            }
        }
        Candidate[][] chosen = chooseReachableSurfaces(candidates, supports, maximumStepHeight);
        for (int gridX = 0; gridX < size; gridX++) {
            for (int gridZ = 0; gridZ < size; gridZ++) {
                // A column no tire can reach keeps its uppermost solid block:
                // a wall stays a wall.
                Candidate surface = chosen[gridX][gridZ] != null ? chosen[gridX][gridZ] : topmost[gridX][gridZ];
                if (surface != null) commit(surface, gridX, gridZ);
            }
        }
        for (int band = 0; band < rawHeights.length; band++) {
            smoothBand(rawHeights[band], smoothHeights[band]);
        }
        valid = true;
    }

    void updateClimbableBlocks(
            double[] supportMinimumYs,
            double maximumStepHeight,
            AABB scanBounds
    ) {
        climbableBlocks.clear();
        if (!valid || maximumStepHeight <= 0.0) {
            return;
        }
        markCurrentLegalSteps(supportMinimumYs, maximumStepHeight, scanBounds);
    }

    private record Candidate(BlockPos pos, BlockState state, VoxelShape shape, RampCell ramp, double top) {
    }

    /**
     * Every top face in the column with open space above it, highest first.
     * The first solid block from the top is only a wall's top; under a
     * ceiling, a bridge or an upper floor the drivable step lies further down.
     */
    private List<Candidate> sampleColumn(
            Function<BlockPos, BlockState> blockStateAt,
            int worldX,
            int worldZ,
            int minimumBlockY,
            int maximumBlockY,
            Candidate[][] topmost,
            int gridX,
            int gridZ
    ) {
        List<Candidate> result = new ArrayList<>(2);
        boolean openAbove = !solid(blockStateAt.apply(new BlockPos(worldX, maximumBlockY + 1, worldZ)));
        for (int worldY = maximumBlockY; worldY >= minimumBlockY; worldY--) {
            BlockPos blockPos = new BlockPos(worldX, worldY, worldZ);
            BlockState state = blockStateAt.apply(blockPos);
            if (!solid(state)) {
                openAbove = true;
                continue;
            }
            VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            RampCell ramp = null;
            if (state.getBlock() instanceof RampBlock) {
                boolean upper = state.getBlock() instanceof UpperRampBlock;
                boolean full = state.getBlock() instanceof FullRampBlock;
                ramp = new RampCell(worldX, worldZ, worldY + (upper ? .5 : 0),
                        full ? 1 : .5, state.getValue(RampBlock.FACING));
            }
            // Sample the authored plane, not its enclosing column maximum.
            double top = ramp == null ? worldY + shape.max(Direction.Axis.Y) : ramp.heightAt(worldX + .5F, worldZ + .5F);
            Candidate candidate = new Candidate(blockPos.immutable(), state, shape, ramp, top);
            if (topmost[gridX][gridZ] == null) topmost[gridX][gridZ] = candidate;
            if (openAbove) result.add(candidate);
            openAbove = false;
        }
        return result;
    }

    private static boolean solid(BlockState state) {
        return state != null && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    /**
     * Walks outward from the tires, one column at a time, onto the highest
     * top face within one legal step of its neighbour. Stairs therefore keep
     * climbing, while a ceiling or an upper floor several blocks higher is
     * never mistaken for the next tread.
     */
    private Candidate[][] chooseReachableSurfaces(List<Candidate>[][] candidates,
                                                  double[][] supports, double maximumStepHeight) {
        Candidate[][] chosen = new Candidate[size][size];
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        double reach = maximumStepHeight + CLIMB_EPSILON;
        for (double[] support : supports) {
            if (support == null || !Double.isFinite(support[2])) continue;
            int x = (int) Math.floor(support[0]) - originX, z = (int) Math.floor(support[1]) - originZ;
            if (x < 0 || x >= size || z < 0 || z >= size || chosen[x][z] != null) continue;
            Candidate best = null;
            for (Candidate candidate : candidates[x][z]) {
                double gap = Math.abs(candidate.top() - support[2]);
                if (gap <= reach + .5 && (best == null || gap < Math.abs(best.top() - support[2]))) best = candidate;
            }
            if (best != null) {
                chosen[x][z] = best;
                pending.add(new int[]{x, z});
            }
        }
        while (!pending.isEmpty()) {
            int[] cell = pending.removeFirst();
            double from = chosen[cell[0]][cell[1]].top();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int x = cell[0] + side.getStepX(), z = cell[1] + side.getStepZ();
                if (x < 0 || x >= size || z < 0 || z >= size || chosen[x][z] != null) continue;
                // Candidates are highest first: the first reachable one climbs.
                for (Candidate candidate : candidates[x][z]) {
                    if (Math.abs(candidate.top() - from) <= reach) {
                        chosen[x][z] = candidate;
                        pending.add(new int[]{x, z});
                        break;
                    }
                }
            }
        }
        return chosen;
    }

    private void commit(Candidate surface, int gridX, int gridZ) {
        BlockPos blockPos = surface.pos();
        if (surface.ramp() != null) {
            nativeRampFloors.put(blockPos, blockPos.getY() + surface.shape().min(Direction.Axis.Y));
            rampCells[gridX][gridZ] = surface.ramp();
        }
        float surfaceY = (float) surface.top();
        SurfaceBand band = classify(surface.state());
        surfaceBands[gridX][gridZ] = band;
        sampledHeights[gridX][gridZ] = surfaceY;
        surfaceBlocks[gridX][gridZ] = blockPos;
        rawHeights[band.ordinal()][gridX][gridZ] = surfaceY;
        smoothHeights[band.ordinal()][gridX][gridZ] = surfaceY;
    }

    private void markCurrentLegalSteps(
            double[] supportMinimumYs,
            double maximumStepHeight,
            AABB scanBounds
    ) {
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                float height = sampledHeights[x][z];
                BlockPos blockPos = surfaceBlocks[x][z];
                if (Float.isFinite(height)
                        && blockPos != null
                        && isLegalFromAnySupport(
                        height,
                        supportMinimumYs,
                        maximumStepHeight
                )
                        && blockPos.getX() + 1.0 >= scanBounds.minX
                        && blockPos.getX() <= scanBounds.maxX
                        && blockPos.getZ() + 1.0 >= scanBounds.minZ
                        && blockPos.getZ() <= scanBounds.maxZ) {
                    climbableBlocks.add(blockPos);
                }
            }
        }
    }

    private static boolean isLegalFromAnySupport(
            double surfaceY,
            double[] supportMinimumYs,
            double maximumStepHeight
    ) {
        for (double supportMinimumY : supportMinimumYs) {
            if (!Double.isFinite(supportMinimumY)) {
                continue;
            }
            if (isLegalFromSupport(
                    surfaceY,
                    supportMinimumY,
                    maximumStepHeight
            )) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rechecks a union-selected step against one physical wheel. The global
     * climbable set is intentionally the union of every installed wheel so
     * that chassis contacts can be cleared around the whole vehicle, but a
     * low rear wheel must not borrow a high front wheel's permission to skip
     * two stair tiers at once.
     */
    boolean isLegalForSupport(
            BlockPos blockPos,
            double supportMinimumY,
            double maximumStepHeight
    ) {
        if (!valid || !Double.isFinite(supportMinimumY)) {
            return false;
        }
        int gridX = blockPos.getX() - originX;
        int gridZ = blockPos.getZ() - originZ;
        if (gridX < 0 || gridX >= size || gridZ < 0 || gridZ >= size
                || !blockPos.equals(surfaceBlocks[gridX][gridZ])) {
            return false;
        }
        float surfaceY = sampledHeights[gridX][gridZ];
        return Float.isFinite(surfaceY)
                && isLegalFromSupport(
                surfaceY,
                supportMinimumY,
                maximumStepHeight
        );
    }

    private static boolean isLegalFromSupport(
            double surfaceY,
            double supportMinimumY,
            double maximumStepHeight
    ) {
        double lowerReference = Math.floor(supportMinimumY) - 0.1;
        double highestLegalSurface = supportMinimumY
                + maximumStepHeight + CLIMB_EPSILON;
        return surfaceY > lowerReference && surfaceY <= highestLegalSurface;
    }

    private static SurfaceBand classify(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return SurfaceBand.RIGID;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return SurfaceBand.SOFT;
        }
        return SurfaceBand.OTHER;
    }

    private void smoothBand(float[][] raw, float[][] output) {
        float[][] source = new float[size][size];
        float[][] destination = new float[size][size];
        copy(raw, source);
        for (int pass = 0; pass < SMOOTHING_PASSES; pass++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    float center = source[x][z];
                    if (!Float.isFinite(center)) {
                        destination[x][z] = EMPTY;
                        continue;
                    }
                    float weightedDrop = 0.0F;
                    float totalWeight = 0.0F;
                    for (int deltaX = -1; deltaX <= 1; deltaX++) {
                        for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                            if (deltaX == 0 && deltaZ == 0) {
                                continue;
                            }
                            int sampleX = Math.clamp(x + deltaX, 0, size - 1);
                            int sampleZ = Math.clamp(z + deltaZ, 0, size - 1);
                            float neighbor = source[sampleX][sampleZ];
                            if (!Float.isFinite(neighbor) || neighbor > center) {
                                continue;
                            }
                            float weight = deltaX == 0 || deltaZ == 0
                                    ? ORTHOGONAL_WEIGHT
                                    : DIAGONAL_WEIGHT;
                            weightedDrop += (neighbor - center) * weight;
                            totalWeight += weight;
                        }
                    }
                    float smoothed = totalWeight > 0.0F
                            ? center + weightedDrop / totalWeight
                            : center;
                    destination[x][z] = Math.min(smoothed, raw[x][z]);
                }
            }
            float[][] swap = source;
            source = destination;
            destination = swap;
        }
        copy(source, output);
    }

    private static void copy(float[][] source, float[][] destination) {
        for (int x = 0; x < source.length; x++) {
            System.arraycopy(source[x], 0, destination[x], 0, source[x].length);
        }
    }

    boolean isClimbableBlock(BlockPos blockPos) {
        return valid && climbableBlocks.contains(blockPos);
    }

    Surface rawSurfaceAt(float worldX, float worldZ) {
        int x = (int) Math.floor(worldX) - originX;
        int z = (int) Math.floor(worldZ) - originZ;
        if (!valid || x < 0 || x >= size || z < 0 || z >= size
                || surfaceBlocks[x][z] == null) {
            return null;
        }
        var ramp = rampCells[x][z];
        return new Surface(surfaceBlocks[x][z], ramp == null ? sampledHeights[x][z] : ramp.heightAt(worldX, worldZ));
    }

    record Surface(BlockPos blockPos, float height) {
    }

    long revision() {
        return revision;
    }

    void updateConnectedSupports(Vector3f[] wheelCenters, double[] references, double stepHeight) {
        Set<BlockPos> seeds = new HashSet<>();
        ArrayDeque<Surface> pending = new ArrayDeque<>();
        for (int i = 0; i < wheelCenters.length; i++) {
            Vector3f center = wheelCenters[i];
            if (center == null) continue;
            Surface seed = rawSurfaceAt((float) Math.floor(center.x) + .5F, (float) Math.floor(center.z) + .5F);
            if (seed != null && seed.height() <= references[i] + stepHeight + CLIMB_EPSILON
                    && seeds.add(seed.blockPos())) pending.add(seed);
        }
        // Wheel height/contact noise inside the same columns cannot change
        // this graph. Recompute only when the sampled field or eligible seeds change.
        if (!connectivityDirty && connectedStep == stepHeight && connectionSeeds.equals(seeds)) return;
        connectivityBuildCount++;
        connectivityDirty = false;
        connectedStep = stepHeight;
        connectionSeeds.clear();
        connectionSeeds.addAll(seeds);
        Set<BlockPos> found = new HashSet<>(seeds);
        float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
        while (!pending.isEmpty()) {
            Surface source = pending.removeFirst();
            minimum = Math.min(minimum, source.height());
            maximum = Math.max(maximum, source.height());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos p = source.blockPos().relative(direction);
                Surface next = rawSurfaceAt(p.getX() + 0.5F, p.getZ() + 0.5F);
                if (next != null && Math.abs(next.height() - source.height()) <= stepHeight + CLIMB_EPSILON
                        && found.add(next.blockPos())) pending.add(next);
            }
        }
        connectedHeightVariation = maximum - minimum > .001F;
        if (!connectedSupports.equals(found)) {
            connectedSupports.clear();
            connectedSupports.addAll(found);
            updateGentleEdges();
            revision++;
        }
    }

    /**
     * Cache edge classification with connectivity, not in every mesh vertex sample.
     */
    private void updateGentleEdges() {
        for (int x = 0; x < size; x++)
            for (int z = 0; z < size; z++) {
                gentleEdges[x][z] = false;
                if (rampCells[x][z] != null || !connectedSupports.contains(surfaceBlocks[x][z])) continue;
                float top = sampledHeights[x][z];
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    int nx = x + side.getStepX(), nz = z + side.getStepZ();
                    if (nx < 0 || nx >= size || nz < 0 || nz >= size) continue;
                    float neighbor = sampledHeights[nx][nz];
                    double rise = top - neighbor;
                    if (Float.isFinite(neighbor) && rise > .001 && rise <= .125
                            && connectedSupports.contains(surfaceBlocks[nx][nz])) {
                        gentleEdges[x][z] = true;
                        break;
                    }
                }
            }
    }

    boolean connectedSupport(BlockPos position) {
        return connectedSupports.contains(position);
    }

    boolean nativeRamp(BlockPos position) {
        return nativeRampFloors.containsKey(position);
    }

    boolean hasConnectedHeightVariation() {
        return connectedHeightVariation;
    }

    int rebuildCount() {
        return rebuildCount;
    }

    int connectivityBuildCount() {
        return connectivityBuildCount;
    }

    /**
     * Native inclined hulls stay intact; authorize only nearby legal chassis-overhang contacts.
     */
    void collectNativeRampOverhangs(Set<BlockPos> target, AABB footprint, double[] references, double step) {
        target.clear();
        for (var entry : nativeRampFloors.entrySet()) {
            var p = entry.getKey();
            if (p.getX() + 1 < footprint.minX || p.getX() > footprint.maxX
                    || p.getZ() + 1 < footprint.minZ || p.getZ() > footprint.maxZ) continue;
            for (double reference : references) {
                if (Double.isFinite(reference) && entry.getValue() <= reference + step + CLIMB_EPSILON
                        && entry.getValue() + 1 >= reference - CLIMB_EPSILON) {
                    target.add(p);
                    break;
                }
            }
        }
    }

    /**
     * Conservative 45-degree support envelope of eligible block columns.
     * Distance is measured to the COLUMN, not its centre: a support surface
     * can approach a riser smoothly but can never cut below its actual top.
     * An over-height wall never participates in this envelope.
     */
    float supportHeight(float worldX, float worldZ, double referenceY, double stepHeight) {
        float result = EMPTY;
        double roundingLength = 0.75;
        // A rounded cone reaches the neighbouring tier's rounded cone only
        // after one full rounding length. Cutting it at half that distance
        // inserts a 0.09375 m ledge on EVERY one-block stair, even though each
        // individual cone is C1. Keep enough overlap for the envelopes to meet.
        double influence = Math.max(1, stepHeight) + roundingLength;
        int reach = (int) Math.ceil(influence);
        int gridX = (int) Math.floor(worldX) - originX;
        int gridZ = (int) Math.floor(worldZ) - originZ;
        for (int x = Math.max(0, gridX - reach - 1); x <= Math.min(size - 1, gridX + reach); x++) {
            for (int z = Math.max(0, gridZ - reach - 1); z <= Math.min(size - 1, gridZ + reach); z++) {
                float top = sampledHeights[x][z];
                if (!Float.isFinite(top) || !connectedSupports.contains(surfaceBlocks[x][z])) continue;
                float minX = originX + x, minZ = originZ + z;
                if (rampCells[x][z] == null && overHeightFaceToward(x, z, top, worldX - minX, worldZ - minZ,
                        stepHeight)) continue;
                var ramp = rampCells[x][z];
                if (ramp != null) top = ramp.heightAt(worldX, worldZ);
                double dx = Math.max(Math.max(minX - worldX, worldX - minX - 1), 0);
                double dz = Math.max(Math.max(minZ - worldZ, worldZ - minZ - 1), 0);
                double distance = Math.hypot(dx, dz);
                // Next tier can influence only one step-length around it.
                // Far surfaces cannot pre-lift a vehicle several blocks away.
                if (distance > influence + 1E-4) continue;
                // C1 upper rounding: zero derivative at a plateau's edge,
                // continuously reaches slope 1, then continues linearly.
                // Unlike averaging downward it always stays above the cone
                // and therefore cannot cut into the source block corner.
                // A 1/16-block lip otherwise gets only ~0.30m of run-up:
                // at 28m/s a whole wheel crosses that curve in one solver
                // step, launching one axle before the roll damper can react.
                // Spread small road seams over a longer, still local curve.
                // Full steps and authored ramps retain their existing slope.
                double localRounding = gentleEdges[x][z] ? 4.0 : roundingLength;
                double drop = distance < localRounding
                        ? distance * distance / (2 * localRounding)
                        : distance - localRounding * 0.5;
                result = Math.max(result, (float) (top - drop));
            }
        }
        return Float.isFinite(result) ? result : Float.NaN;
    }

    /**
     * Whether the column's cone would reach the point across a riser no tire
     * may climb. Connectivity chains along equal tops: a tunnel wall one step
     * above the next tread joins the graph there and then runs, level, beside
     * lower treads it towers over. Its cone lifted a rear wheel 0.8 block
     * through the side of the tunnel, the surface jumped past the wheel's
     * centre and swallowed it. A staircase keeps its pre-lift: every tier's
     * face toward the tires stands one legal step above the tier below.
     *
     * @param localX query X relative to the column's minimum corner
     * @param localZ query Z relative to the column's minimum corner
     */
    private boolean overHeightFaceToward(int x, int z, float top, float localX, float localZ, double stepHeight) {
        int sideX = localX < 0 ? -1 : localX > 1 ? 1 : 0;
        int sideZ = localZ < 0 ? -1 : localZ > 1 ? 1 : 0;
        if (overHeightRiser(x + sideX, z, top, sideX != 0, stepHeight)
                || overHeightRiser(x, z + sideZ, top, sideZ != 0, stepHeight)) return true;
        if (sideX == 0 || sideZ == 0) return false;
        // Beyond a corner the cone crosses the diagonal column. On a diagonal
        // staircase that column lies two tiers down, but one legal step below
        // both side columns, so the corner is approached along either edge.
        // Beside a tunnel wall one side column is the wall itself, towering
        // over the diagonal tread: the corner is part of the wall.
        int diagonalX = x + sideX, diagonalZ = z + sideZ;
        return overHeightRiser(diagonalX, diagonalZ, top, true, stepHeight)
                && (overHeightRiser(diagonalX, diagonalZ, sampledHeights[diagonalX][z], true, stepHeight)
                || overHeightRiser(diagonalX, diagonalZ, sampledHeights[x][diagonalZ], true, stepHeight));
    }

    private boolean overHeightRiser(int x, int z, float top, boolean facing, double stepHeight) {
        if (!facing || x < 0 || x >= size || z < 0 || z >= size) return false;
        float below = sampledHeights[x][z];
        return Float.isFinite(below) && top - below > stepHeight + CLIMB_EPSILON;
    }

    BlockPos findClimbableBlock(Vector3f contactPoint, float allowance) {
        if (!valid) {
            return null;
        }
        for (BlockPos blockPos : climbableBlocks) {
            if (contactPoint.x >= blockPos.getX() - allowance
                    && contactPoint.x <= blockPos.getX() + 1.0F + allowance
                    && contactPoint.y >= blockPos.getY() - allowance
                    && contactPoint.y <= blockPos.getY() + 1.0F + allowance
                    && contactPoint.z >= blockPos.getZ() - allowance
                    && contactPoint.z <= blockPos.getZ() + 1.0F + allowance) {
                return blockPos;
            }
        }
        return null;
    }

    boolean isNearClimbableColumn(float worldX, float worldZ, float allowance) {
        if (!valid || climbableBlocks.isEmpty()) {
            return false;
        }
        for (BlockPos blockPos : climbableBlocks) {
            if (worldX >= blockPos.getX() - allowance
                    && worldX <= blockPos.getX() + 1.0F + allowance
                    && worldZ >= blockPos.getZ() - allowance
                    && worldZ <= blockPos.getZ() + 1.0F + allowance) {
                return true;
            }
        }
        return false;
    }

    int climbableBlockCount() {
        return climbableBlocks.size();
    }

    float height(float worldX, float worldZ) {
        if (!valid) {
            return Float.NaN;
        }
        float gridX = worldX - (originX + 0.5F);
        float gridZ = worldZ - (originZ + 0.5F);
        int x0 = (int) Math.floor(gridX);
        int z0 = (int) Math.floor(gridZ);
        if (x0 < 0 || z0 < 0 || x0 >= size - 1 || z0 >= size - 1) {
            return Float.NaN;
        }
        SurfaceBand band = surfaceBands[x0][z0];
        if (band == SurfaceBand.NONE) {
            return Float.NaN;
        }
        float[][] field = smoothHeights[band.ordinal()];
        float h00 = field[x0][z0];
        float h10 = field[x0 + 1][z0];
        float h01 = field[x0][z0 + 1];
        float h11 = field[x0 + 1][z0 + 1];
        if (!Float.isFinite(h00) || !Float.isFinite(h10)
                || !Float.isFinite(h01) || !Float.isFinite(h11)) {
            return Float.NaN;
        }
        float fractionX = gridX - x0;
        float fractionZ = gridZ - z0;
        float bottom = h00 + (h10 - h00) * fractionX;
        float top = h01 + (h11 - h01) * fractionX;
        return bottom + (top - bottom) * fractionZ;
    }

    Vector3f normal(float worldX, float worldZ) {
        float sampleDistance = 0.5F;
        float left = height(worldX - sampleDistance, worldZ);
        float right = height(worldX + sampleDistance, worldZ);
        float back = height(worldX, worldZ - sampleDistance);
        float front = height(worldX, worldZ + sampleDistance);
        if (!Float.isFinite(left) || !Float.isFinite(right)
                || !Float.isFinite(back) || !Float.isFinite(front)) {
            return Vector3f.UNIT_Y.clone();
        }
        float gradientX = (right - left) / (sampleDistance * 2.0F);
        float gradientZ = (front - back) / (sampleDistance * 2.0F);
        return new Vector3f(-gradientX, 1.0F, -gradientZ).normalize();
    }

    Contact solveSphereContact(
            Vector3f sphereCenter,
            float sphereRadius,
            Vector3f initialContact
    ) {
        float contactX = initialContact.x;
        float contactZ = initialContact.z;
        Vector3f surfaceNormal = Vector3f.UNIT_Y.clone();
        for (int iteration = 0; iteration < 3; iteration++) {
            float surfaceHeight = height(contactX, contactZ);
            if (!Float.isFinite(surfaceHeight)) {
                return Contact.NONE;
            }
            surfaceNormal = normal(contactX, contactZ);
            contactX = sphereCenter.x - surfaceNormal.x * sphereRadius;
            contactZ = sphereCenter.z - surfaceNormal.z * sphereRadius;
        }
        float surfaceHeight = height(contactX, contactZ);
        if (!Float.isFinite(surfaceHeight)) {
            return Contact.NONE;
        }
        // The fixed-point iterations move X/Z after sampling the normal. The
        // accepted contact must use the normal at the final position rather
        // than the previous iteration's position.
        surfaceNormal = normal(contactX, contactZ);
        if (surfaceNormal.y <= 1.0E-4F) {
            return Contact.NONE;
        }
        Vector3f contactPoint = new Vector3f(contactX, surfaceHeight, contactZ);
        float penetration = sphereRadius
                - (sphereCenter.y - surfaceHeight) / surfaceNormal.y;
        return new Contact(contactPoint, surfaceNormal, penetration, true);
    }

    record Contact(
            Vector3f point,
            Vector3f normal,
            float penetration,
            boolean valid
    ) {
        private static final Contact NONE = new Contact(
                Vector3f.ZERO,
                Vector3f.UNIT_Y,
                Float.NaN,
                false
        );
    }
}
