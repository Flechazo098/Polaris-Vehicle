package com.flechazo.polaris_vehicle.vehicle.collision;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class OrientedBoundingBox {
    private static final double SOLID_EPSILON = 1.0E-7;
    private static final double SWEEP_EPSILON = 1.0E-7;
    private static final double SHALLOW_GROUND_CONTACT_DEPTH = 1.0E-3;

    private final double centerX;
    private final double centerY;
    private final double centerZ;
    private final double axisXx;
    private final double axisXy;
    private final double axisXz;
    private final double axisYx;
    private final double axisYy;
    private final double axisYz;
    private final double axisZx;
    private final double axisZy;
    private final double axisZz;
    private final double extentX;
    private final double extentY;
    private final double extentZ;

    OrientedBoundingBox(
            double centerX,
            double centerY,
            double centerZ,
            double axisXx,
            double axisXy,
            double axisXz,
            double axisYx,
            double axisYy,
            double axisYz,
            double axisZx,
            double axisZy,
            double axisZz,
            double extentX,
            double extentY,
            double extentZ
    ) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.axisXx = axisXx;
        this.axisXy = axisXy;
        this.axisXz = axisXz;
        this.axisYx = axisYx;
        this.axisYy = axisYy;
        this.axisYz = axisYz;
        this.axisZx = axisZx;
        this.axisZy = axisZy;
        this.axisZz = axisZz;
        this.extentX = nonNegativeFinite(extentX, "extentX");
        this.extentY = nonNegativeFinite(extentY, "extentY");
        this.extentZ = nonNegativeFinite(extentZ, "extentZ");
    }

    public static OrientedBoundingBox axisAligned(AABB bounds) {
        return axisAligned(
                (bounds.minX + bounds.maxX) * 0.5,
                (bounds.minY + bounds.maxY) * 0.5,
                (bounds.minZ + bounds.maxZ) * 0.5,
                (bounds.maxX - bounds.minX) * 0.5,
                (bounds.maxY - bounds.minY) * 0.5,
                (bounds.maxZ - bounds.minZ) * 0.5
        );
    }

    public static OrientedBoundingBox axisAligned(
            double centerX,
            double centerY,
            double centerZ,
            double extentX,
            double extentY,
            double extentZ
    ) {
        return new OrientedBoundingBox(
                centerX, centerY, centerZ,
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0,
                extentX, extentY, extentZ
        );
    }

    public static OrientedBoundingBox transformed(
            Vec3 center,
            Vec3 halfExtents,
            Matrix4f transform
    ) {

        Vector3f transformedCenter = transform.transformPosition(
                new Vector3f((float) center.x, (float) center.y, (float) center.z)
        );
        Vector3f transformedAxisX = transform.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F))
                .normalize();
        Vector3f transformedAxisY = transform.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F))
                .normalize();
        Vector3f transformedAxisZ = transform.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F))
                .normalize();
        return new OrientedBoundingBox(
                transformedCenter.x, transformedCenter.y, transformedCenter.z,
                transformedAxisX.x, transformedAxisX.y, transformedAxisX.z,
                transformedAxisY.x, transformedAxisY.y, transformedAxisY.z,
                transformedAxisZ.x, transformedAxisZ.y, transformedAxisZ.z,
                halfExtents.x, halfExtents.y, halfExtents.z
        );
    }

    public OrientedBoundingBox toWorld(Vec3 vehiclePosition, float vehicleYawDegrees) {
        double radians = -Math.toRadians(vehicleYawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return rotateAndTranslateY(cos, sin, vehiclePosition);
    }

    public OrientedBoundingBox toWorld(Vec3 vehiclePosition, Quaternionf vehicleRotation) {
        Quaternionf rotation = normalized(vehicleRotation);
        Vec3 rotatedCenter = rotate(new Vec3(centerX, centerY, centerZ), rotation, false)
                .add(vehiclePosition);
        Vec3 rotatedAxisX = rotate(new Vec3(axisXx, axisXy, axisXz), rotation, false);
        Vec3 rotatedAxisY = rotate(new Vec3(axisYx, axisYy, axisYz), rotation, false);
        Vec3 rotatedAxisZ = rotate(new Vec3(axisZx, axisZy, axisZz), rotation, false);
        return new OrientedBoundingBox(
                rotatedCenter.x, rotatedCenter.y, rotatedCenter.z,
                rotatedAxisX.x, rotatedAxisX.y, rotatedAxisX.z,
                rotatedAxisY.x, rotatedAxisY.y, rotatedAxisY.z,
                rotatedAxisZ.x, rotatedAxisZ.y, rotatedAxisZ.z,
                extentX, extentY, extentZ
        );
    }

    public OrientedBoundingBox relativeTo(Vec3 vehiclePosition, float vehicleYawDegrees) {
        double radians = Math.toRadians(vehicleYawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double translatedX = centerX - vehiclePosition.x;
        double translatedY = centerY - vehiclePosition.y;
        double translatedZ = centerZ - vehiclePosition.z;
        return new OrientedBoundingBox(
                rotateX(translatedX, translatedZ, cos, sin),
                translatedY,
                rotateZ(translatedX, translatedZ, cos, sin),
                rotateX(axisXx, axisXz, cos, sin), axisXy, rotateZ(axisXx, axisXz, cos, sin),
                rotateX(axisYx, axisYz, cos, sin), axisYy, rotateZ(axisYx, axisYz, cos, sin),
                rotateX(axisZx, axisZz, cos, sin), axisZy, rotateZ(axisZx, axisZz, cos, sin),
                extentX, extentY, extentZ
        );
    }

    public OrientedBoundingBox relativeTo(Vec3 vehiclePosition, Quaternionf vehicleRotation) {
        Quaternionf rotation = normalized(vehicleRotation);
        Vec3 localCenter = rotate(new Vec3(
                centerX - vehiclePosition.x,
                centerY - vehiclePosition.y,
                centerZ - vehiclePosition.z
        ), rotation, true);
        Vec3 localAxisX = rotate(new Vec3(axisXx, axisXy, axisXz), rotation, true);
        Vec3 localAxisY = rotate(new Vec3(axisYx, axisYy, axisYz), rotation, true);
        Vec3 localAxisZ = rotate(new Vec3(axisZx, axisZy, axisZz), rotation, true);
        return new OrientedBoundingBox(
                localCenter.x, localCenter.y, localCenter.z,
                localAxisX.x, localAxisX.y, localAxisX.z,
                localAxisY.x, localAxisY.y, localAxisY.z,
                localAxisZ.x, localAxisZ.y, localAxisZ.z,
                extentX, extentY, extentZ
        );
    }

    public boolean intersects(OrientedBoundingBox other) {
        double centerDifferenceX = other.centerX - centerX;
        double centerDifferenceY = other.centerY - centerY;
        double centerDifferenceZ = other.centerZ - centerZ;
        for (int candidateAxis = 0; candidateAxis < 15; candidateAxis++) {
            double axisX;
            double axisY;
            double axisZ;
            if (candidateAxis < 3) {
                axisX = axisComponent(candidateAxis, 0);
                axisY = axisComponent(candidateAxis, 1);
                axisZ = axisComponent(candidateAxis, 2);
            } else if (candidateAxis < 6) {
                int otherAxis = candidateAxis - 3;
                axisX = other.axisComponent(otherAxis, 0);
                axisY = other.axisComponent(otherAxis, 1);
                axisZ = other.axisComponent(otherAxis, 2);
            } else {
                int crossIndex = candidateAxis - 6;
                int thisAxis = crossIndex / 3;
                int otherAxis = crossIndex % 3;
                double thisX = axisComponent(thisAxis, 0);
                double thisY = axisComponent(thisAxis, 1);
                double thisZ = axisComponent(thisAxis, 2);
                double otherX = other.axisComponent(otherAxis, 0);
                double otherY = other.axisComponent(otherAxis, 1);
                double otherZ = other.axisComponent(otherAxis, 2);
                axisX = thisY * otherZ - thisZ * otherY;
                axisY = thisZ * otherX - thisX * otherZ;
                axisZ = thisX * otherY - thisY * otherX;
            }

            if (separatedOnAxis(
                    centerDifferenceX,
                    centerDifferenceY,
                    centerDifferenceZ,
                    axisX,
                    axisY,
                    axisZ,
                    other.projectionRadius(axisX, axisY, axisZ)
            )) {
                return false;
            }
        }
        return true;
    }

    public boolean intersects(AABB bounds) {
        return intersects(
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ
        );
    }

    /**
     * Earliest normalized time in {@code [0, 1]} at which this moving box
     * reaches {@code obstacle}. Returns positive infinity when the sweep is
     * clear.
     */
    public double sweepFraction(OrientedBoundingBox obstacle, Vec3 movement) {
        if (movement.lengthSqr() <= SWEEP_EPSILON * SWEEP_EPSILON) {
            return intersects(obstacle) ? 0.0 : Double.POSITIVE_INFINITY;
        }
        if (isShallowGroundContactForHorizontalMovement(obstacle, movement)) {
            return Double.POSITIVE_INFINITY;
        }
        if (intersects(obstacle)) {
            return initialOverlapBlocksMovement(obstacle, movement)
                    ? 0.0
                    : Double.POSITIVE_INFINITY;
        }

        double entryTime = Double.NEGATIVE_INFINITY;
        double exitTime = Double.POSITIVE_INFINITY;
        double centerDifferenceX = obstacle.centerX - centerX;
        double centerDifferenceY = obstacle.centerY - centerY;
        double centerDifferenceZ = obstacle.centerZ - centerZ;

        for (int candidateAxis = 0; candidateAxis < 15; candidateAxis++) {
            double axisX;
            double axisY;
            double axisZ;
            if (candidateAxis < 3) {
                axisX = axisComponent(candidateAxis, 0);
                axisY = axisComponent(candidateAxis, 1);
                axisZ = axisComponent(candidateAxis, 2);
            } else if (candidateAxis < 6) {
                int obstacleAxis = candidateAxis - 3;
                axisX = obstacle.axisComponent(obstacleAxis, 0);
                axisY = obstacle.axisComponent(obstacleAxis, 1);
                axisZ = obstacle.axisComponent(obstacleAxis, 2);
            } else {
                int crossIndex = candidateAxis - 6;
                int thisAxis = crossIndex / 3;
                int obstacleAxis = crossIndex % 3;
                double thisX = axisComponent(thisAxis, 0);
                double thisY = axisComponent(thisAxis, 1);
                double thisZ = axisComponent(thisAxis, 2);
                double obstacleX = obstacle.axisComponent(obstacleAxis, 0);
                double obstacleY = obstacle.axisComponent(obstacleAxis, 1);
                double obstacleZ = obstacle.axisComponent(obstacleAxis, 2);
                axisX = thisY * obstacleZ - thisZ * obstacleY;
                axisY = thisZ * obstacleX - thisX * obstacleZ;
                axisZ = thisX * obstacleY - thisY * obstacleX;
            }

            double axisLengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;
            if (axisLengthSquared <= SWEEP_EPSILON * SWEEP_EPSILON) {
                continue;
            }
            double projectedRadius = projectionRadius(axisX, axisY, axisZ)
                    + obstacle.projectionRadius(axisX, axisY, axisZ)
                    - SWEEP_EPSILON * Math.sqrt(axisLengthSquared);
            projectedRadius = Math.max(0.0, projectedRadius);
            double projectedDistance = centerDifferenceX * axisX
                    + centerDifferenceY * axisY
                    + centerDifferenceZ * axisZ;
            double projectedSpeed = movement.x * axisX
                    + movement.y * axisY
                    + movement.z * axisZ;

            if (Math.abs(projectedSpeed) <= SWEEP_EPSILON) {
                if (Math.abs(projectedDistance) > projectedRadius) {
                    return Double.POSITIVE_INFINITY;
                }
                continue;
            }
            double firstTime = (projectedDistance - projectedRadius) / projectedSpeed;
            double secondTime = (projectedDistance + projectedRadius) / projectedSpeed;
            if (firstTime > secondTime) {
                double swap = firstTime;
                firstTime = secondTime;
                secondTime = swap;
            }
            entryTime = Math.max(entryTime, firstTime);
            exitTime = Math.min(exitTime, secondTime);
            if (entryTime > exitTime) {
                return Double.POSITIVE_INFINITY;
            }
        }

        if (exitTime < 0.0 || entryTime > 1.0) {
            return Double.POSITIVE_INFINITY;
        }
        if (entryTime < 0.0 && exitTime <= SWEEP_EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.clamp(entryTime, 0.0, 1.0);
    }

    /**
     * A horizontal sweep across adjacent block tops can touch the top face and
     * a side edge at the same zero-time instant. The vertical contact manifold
     * is sufficient to keep the vehicle outside the block, so that edge must
     * not be treated as a wall. Actual walls and steps overlap the vehicle by
     * much more than this tiny recovery depth on the world-Y axis.
     */
    private boolean isShallowGroundContactForHorizontalMovement(
            OrientedBoundingBox obstacle,
            Vec3 movement
    ) {
        if (Math.abs(movement.y) > SWEEP_EPSILON
                || movement.horizontalDistanceSqr() <= SWEEP_EPSILON * SWEEP_EPSILON) {
            return false;
        }

        double centerDifferenceY = obstacle.centerY - centerY;
        if (centerDifferenceY >= 0.0) {
            return false;
        }
        double verticalRadius = projectionRadius(0.0, 1.0, 0.0)
                + obstacle.projectionRadius(0.0, 1.0, 0.0);
        double verticalPenetration = verticalRadius - Math.abs(centerDifferenceY);
        return verticalPenetration >= -SHALLOW_GROUND_CONTACT_DEPTH
                && verticalPenetration <= SHALLOW_GROUND_CONTACT_DEPTH;
    }

    /**
     * An existing contact only blocks motion directed farther into its minimum
     * translation axis. This lets a vehicle slide along or move away from a
     * wall, and prevents a tiny ground penetration from locking X/Z motion.
     */
    private boolean initialOverlapBlocksMovement(
            OrientedBoundingBox obstacle,
            Vec3 movement
    ) {
        double centerDifferenceX = obstacle.centerX - centerX;
        double centerDifferenceY = obstacle.centerY - centerY;
        double centerDifferenceZ = obstacle.centerZ - centerZ;
        double minimumPenetration = Double.POSITIVE_INFINITY;
        double contactNormalX = 0.0;
        double contactNormalY = 0.0;
        double contactNormalZ = 0.0;

        for (int candidateAxis = 0; candidateAxis < 15; candidateAxis++) {
            double axisX;
            double axisY;
            double axisZ;
            if (candidateAxis < 3) {
                axisX = axisComponent(candidateAxis, 0);
                axisY = axisComponent(candidateAxis, 1);
                axisZ = axisComponent(candidateAxis, 2);
            } else if (candidateAxis < 6) {
                int obstacleAxis = candidateAxis - 3;
                axisX = obstacle.axisComponent(obstacleAxis, 0);
                axisY = obstacle.axisComponent(obstacleAxis, 1);
                axisZ = obstacle.axisComponent(obstacleAxis, 2);
            } else {
                int crossIndex = candidateAxis - 6;
                int thisAxis = crossIndex / 3;
                int obstacleAxis = crossIndex % 3;
                double thisX = axisComponent(thisAxis, 0);
                double thisY = axisComponent(thisAxis, 1);
                double thisZ = axisComponent(thisAxis, 2);
                double obstacleX = obstacle.axisComponent(obstacleAxis, 0);
                double obstacleY = obstacle.axisComponent(obstacleAxis, 1);
                double obstacleZ = obstacle.axisComponent(obstacleAxis, 2);
                axisX = thisY * obstacleZ - thisZ * obstacleY;
                axisY = thisZ * obstacleX - thisX * obstacleZ;
                axisZ = thisX * obstacleY - thisY * obstacleX;
            }

            double axisLengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;
            if (axisLengthSquared <= SWEEP_EPSILON * SWEEP_EPSILON) {
                continue;
            }
            double inverseAxisLength = 1.0 / Math.sqrt(axisLengthSquared);
            double projectedDistance = (centerDifferenceX * axisX
                    + centerDifferenceY * axisY
                    + centerDifferenceZ * axisZ) * inverseAxisLength;
            double projectedRadius = (projectionRadius(axisX, axisY, axisZ)
                    + obstacle.projectionRadius(axisX, axisY, axisZ))
                    * inverseAxisLength;
            double penetration = projectedRadius - Math.abs(projectedDistance);
            if (penetration >= minimumPenetration) {
                continue;
            }

            minimumPenetration = penetration;
            double direction = projectedDistance < 0.0 ? -inverseAxisLength : inverseAxisLength;
            contactNormalX = axisX * direction;
            contactNormalY = axisY * direction;
            contactNormalZ = axisZ * direction;
        }

        double movementIntoContact = movement.x * contactNormalX
                + movement.y * contactNormalY
                + movement.z * contactNormalZ;
        return movementIntoContact > SWEEP_EPSILON;
    }

    boolean intersects(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        double centerDifferenceX = (minX + maxX) * 0.5 - centerX;
        double centerDifferenceY = (minY + maxY) * 0.5 - centerY;
        double centerDifferenceZ = (minZ + maxZ) * 0.5 - centerZ;
        double otherExtentX = (maxX - minX) * 0.5;
        double otherExtentY = (maxY - minY) * 0.5;
        double otherExtentZ = (maxZ - minZ) * 0.5;
        for (int candidateAxis = 0; candidateAxis < 15; candidateAxis++) {
            double axisX;
            double axisY;
            double axisZ;
            if (candidateAxis < 3) {
                axisX = axisComponent(candidateAxis, 0);
                axisY = axisComponent(candidateAxis, 1);
                axisZ = axisComponent(candidateAxis, 2);
            } else if (candidateAxis < 6) {
                int otherAxis = candidateAxis - 3;
                axisX = otherAxis == 0 ? 1.0 : 0.0;
                axisY = otherAxis == 1 ? 1.0 : 0.0;
                axisZ = otherAxis == 2 ? 1.0 : 0.0;
            } else {
                int crossIndex = candidateAxis - 6;
                int thisAxis = crossIndex / 3;
                int otherAxis = crossIndex % 3;
                double thisX = axisComponent(thisAxis, 0);
                double thisY = axisComponent(thisAxis, 1);
                double thisZ = axisComponent(thisAxis, 2);
                axisX = switch (otherAxis) {
                    case 0 -> 0.0;
                    case 1 -> -thisZ;
                    default -> thisY;
                };
                axisY = switch (otherAxis) {
                    case 0 -> thisZ;
                    case 1 -> 0.0;
                    default -> -thisX;
                };
                axisZ = switch (otherAxis) {
                    case 0 -> -thisY;
                    case 1 -> thisX;
                    default -> 0.0;
                };
            }
            double otherProjectedRadius = otherExtentX * Math.abs(axisX)
                    + otherExtentY * Math.abs(axisY)
                    + otherExtentZ * Math.abs(axisZ);
            if (separatedOnAxis(
                    centerDifferenceX,
                    centerDifferenceY,
                    centerDifferenceZ,
                    axisX,
                    axisY,
                    axisZ,
                    otherProjectedRadius
            )) {
                return false;
            }
        }
        return true;
    }

    public AABB enclosingAabb() {
        double radiusX = Math.abs(axisXx) * extentX
                + Math.abs(axisYx) * extentY
                + Math.abs(axisZx) * extentZ;
        double radiusY = Math.abs(axisXy) * extentX
                + Math.abs(axisYy) * extentY
                + Math.abs(axisZy) * extentZ;
        double radiusZ = Math.abs(axisXz) * extentX
                + Math.abs(axisYz) * extentY
                + Math.abs(axisZz) * extentZ;
        return new AABB(
                centerX - radiusX,
                centerY - radiusY,
                centerZ - radiusZ,
                centerX + radiusX,
                centerY + radiusY,
                centerZ + radiusZ
        );
    }

    public Vec3 center() {
        return new Vec3(centerX, centerY, centerZ);
    }

    public double projectedRadius(Vec3 axis) {
        return projectionRadius(axis.x, axis.y, axis.z);
    }

    public Vec3 axisX() {
        return new Vec3(axisXx, axisXy, axisXz);
    }

    public Vec3 axisY() {
        return new Vec3(axisYx, axisYy, axisYz);
    }

    public Vec3 axisZ() {
        return new Vec3(axisZx, axisZy, axisZz);
    }

    public Vec3 halfExtents() {
        return new Vec3(extentX, extentY, extentZ);
    }

    /**
     * One of the eight corners; the low three bits select the positive axes.
     */
    public Vec3 corner(int cornerIndex) {
        if (cornerIndex < 0 || cornerIndex >= 8) {
            throw new IllegalArgumentException("cornerIndex must be in [0, 7]");
        }
        double signX = (cornerIndex & 1) == 0 ? -1.0 : 1.0;
        double signY = (cornerIndex & 2) == 0 ? -1.0 : 1.0;
        double signZ = (cornerIndex & 4) == 0 ? -1.0 : 1.0;
        return new Vec3(
                centerX + axisXx * extentX * signX
                        + axisYx * extentY * signY
                        + axisZx * extentZ * signZ,
                centerY + axisXy * extentX * signX
                        + axisYy * extentY * signY
                        + axisZy * extentZ * signZ,
                centerZ + axisXz * extentX * signX
                        + axisYz * extentY * signY
                        + axisZz * extentZ * signZ
        );
    }

    public boolean hasVolume() {
        return extentX > SOLID_EPSILON
                && extentY > SOLID_EPSILON
                && extentZ > SOLID_EPSILON;
    }

    double centerCoordinate(int axis) {
        return switch (axis) {
            case 0 -> centerX;
            case 1 -> centerY;
            case 2 -> centerZ;
            default -> throw new IllegalArgumentException("axis must be 0, 1, or 2");
        };
    }

    private double projectionRadius(double projectionAxisX, double projectionAxisY, double projectionAxisZ) {
        return extentX * Math.abs(
                axisXx * projectionAxisX
                        + axisXy * projectionAxisY
                        + axisXz * projectionAxisZ
        ) + extentY * Math.abs(
                axisYx * projectionAxisX
                        + axisYy * projectionAxisY
                        + axisYz * projectionAxisZ
        ) + extentZ * Math.abs(
                axisZx * projectionAxisX
                        + axisZy * projectionAxisY
                        + axisZz * projectionAxisZ
        );
    }

    private boolean separatedOnAxis(
            double centerDifferenceX,
            double centerDifferenceY,
            double centerDifferenceZ,
            double projectionAxisX,
            double projectionAxisY,
            double projectionAxisZ,
            double otherProjectedRadius
    ) {
        double axisLengthSquared = projectionAxisX * projectionAxisX
                + projectionAxisY * projectionAxisY
                + projectionAxisZ * projectionAxisZ;
        if (axisLengthSquared <= SOLID_EPSILON * SOLID_EPSILON) {
            return false;
        }
        double projectedDistance = Math.abs(
                centerDifferenceX * projectionAxisX
                        + centerDifferenceY * projectionAxisY
                        + centerDifferenceZ * projectionAxisZ
        );
        double projectedRadius = projectionRadius(
                projectionAxisX,
                projectionAxisY,
                projectionAxisZ
        ) + otherProjectedRadius;
        double axisTolerance = SOLID_EPSILON * Math.sqrt(axisLengthSquared);
        return projectedDistance > projectedRadius + axisTolerance;
    }

    private double axisComponent(int axis, int component) {
        return switch (axis * 3 + component) {
            case 0 -> axisXx;
            case 1 -> axisXy;
            case 2 -> axisXz;
            case 3 -> axisYx;
            case 4 -> axisYy;
            case 5 -> axisYz;
            case 6 -> axisZx;
            case 7 -> axisZy;
            case 8 -> axisZz;
            default -> throw new IllegalArgumentException("axis and component must be in [0, 2]");
        };
    }

    private OrientedBoundingBox rotateAndTranslateY(double cos, double sin, Vec3 translation) {
        return new OrientedBoundingBox(
                rotateX(centerX, centerZ, cos, sin) + translation.x,
                centerY + translation.y,
                rotateZ(centerX, centerZ, cos, sin) + translation.z,
                rotateX(axisXx, axisXz, cos, sin), axisXy, rotateZ(axisXx, axisXz, cos, sin),
                rotateX(axisYx, axisYz, cos, sin), axisYy, rotateZ(axisYx, axisYz, cos, sin),
                rotateX(axisZx, axisZz, cos, sin), axisZy, rotateZ(axisZx, axisZz, cos, sin),
                extentX, extentY, extentZ
        );
    }

    // Matches Minecraft Vec3#yRot: x' = x cos + z sin, z' = z cos - x sin.
    private static double rotateX(double x, double z, double cos, double sin) {
        return x * cos + z * sin;
    }

    private static double rotateZ(double x, double z, double cos, double sin) {
        return z * cos - x * sin;
    }

    private static Quaternionf normalized(Quaternionf requestedRotation) {
        Quaternionf result = new Quaternionf(requestedRotation);
        float lengthSquared = result.lengthSquared();
        if (!Float.isFinite(lengthSquared) || lengthSquared < 1.0E-8F) {
            throw new IllegalArgumentException("vehicleRotation must be a finite quaternion");
        }
        return result.normalize();
    }

    private static Vec3 rotate(Vec3 vector, Quaternionf rotation, boolean inverse) {
        double qx = inverse ? -rotation.x() : rotation.x();
        double qy = inverse ? -rotation.y() : rotation.y();
        double qz = inverse ? -rotation.z() : rotation.z();
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

    private static double nonNegativeFinite(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
        return value;
    }
}
