package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.content.block.RampBlock;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups;
import com.flechazo.polaris_vehicle.sparkcore.physics.body.ManifoldPoint;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsWorld;
import com.flechazo.polaris_vehicle.util.MathUtil;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleBulletShapeFactory.VehicleBulletShape;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.AfMode;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleContactMath.*;

/**
 * One authoritative chassis with independently suspended, rolling Bullet wheels.
 *
 * <p>Suspension, normal support and tire traction all belong to the same Bullet
 * assembly. No ray wheel is permitted to report support for a different wheel
 * pose. Machine-Max's height-field technique is used under its author's MIT
 * authorization. Eligible stairs have actual private triangle surfaces, so
 * Bullet generates and preserves their contact anchors itself.</p>
 */
public final class BulletVehicleController {
    /**
     * About 3 deg/s: below this a released car counts as driving straight.
     */
    private static final double HEADING_HOLD_CAPTURE_YAW_RATE = 0.05;
    /**
     * Friction a 45-degree climb gets: tan 45 plus a margin to accelerate.
     * Much more lets a tire grab a riser's rounded edge on offset stairs.
     */
    private static final double CLIMB_FRICTION = 1.2;
    /**
     * A ramp block is one smooth slope with no riser to grab, so it gets a
     * wider margin: at 45 degrees 1.2 left about 1.4 m/s^2 in theory, and in
     * practice the unloaded front tire spun and cars slowed all the way up.
     */
    private static final double RAMP_CLIMB_FRICTION = 1.6;
    private static final double MAXIMUM_RAMP_FRICTION = 2.0;
    /**
     * How long after the last step contact a climb still counts as one staircase.
     */
    private static final double STEP_MEMORY_SECONDS = 0.5;
    /**
     * Strongest push back to the approach speed while crossing steps.
     */
    private static final double STEP_RESTORE_ACCELERATION = 12.0;
    /**
     * Time to win back lost step speed when the push is not saturated.
     */
    private static final double STEP_RESTORE_SECONDS = 0.15;
    /**
     * The push only restores hill-climbing speeds. Restoring 14-20 m/s over a
     * staircase threw cars off its crest; at those speeds the steps are taken
     * on momentum anyway.
     */
    private static final double STEP_RESTORE_MAXIMUM_SPEED = 8.0;
    /**
     * A chassis scraping a ramp slides; it is not a tire.
     */
    private static final float CHASSIS_RAMP_FRICTION = 0.2F;
    private final int entityId;
    private final UUID entityUuid;
    private final VehiclePhysicsProfile profile;
    private final VehicleBulletShape bulletShape;
    private final double maximumStepHeight;
    private final PhysicsRigidBody body;
    private final VehicleBulletWheelAssembly wheels;
    private final VehicleSteeringGeometry steeringGeometry;
    private final VehicleRolloverRecovery rolloverRecovery = new VehicleRolloverRecovery();
    private final VehicleCrestAdhesion crestAdhesion = new VehicleCrestAdhesion();
    private final VehiclePitchStability pitchStability = new VehiclePitchStability();
    private final double halfTrack;
    private double actualSteering;
    private double displaySteering;
    private final VehicleSteeringRack steeringRack = new VehicleSteeringRack();
    private double smoothedCornerAssist = 1;
    private double smoothedHighSpeedGrip = 1;
    private double smoothedCornerResponse = 1;
    private Vector3f neutralForward;
    private boolean reversingBrake;
    private final VehicleDriveResponse driveResponse = new VehicleDriveResponse();
    private final VehicleClimbHeightField climbField;
    private final VehicleClimbSupportReference[] supportReferences;
    private final double[] referenceYs;
    private final boolean[] contacts;
    private boolean chassisGroundContact;
    private boolean climbWheelFacingTerrain = true;
    private final boolean[] climbContacts;
    private final Vec3[] contactPoints;
    private final Vector3f[] contactNormals;
    private final VehicleWheelTerrainSurface[] terrainSurfaces;
    private final Set<BlockPos> chassisClimbBlocks = new HashSet<>();
    private final Set<BlockPos> nativeRampClimbBlocks = new HashSet<>();
    private final double[] supportAge;
    private final AtomicReference<VehiclePhysicsInput> input;
    private final AtomicReference<VehiclePhysicsSnapshot> snapshot = new AtomicReference<>();
    private int fieldX = Integer.MIN_VALUE;
    private int fieldZ = Integer.MIN_VALUE;
    private int fieldY = Integer.MIN_VALUE;
    private int fieldAge = 100;
    private boolean terrainEnabled;
    private volatile boolean added;
    private volatile boolean removed;
    private VehiclePhysicsInput appliedInput;
    private VehiclePhysicsInput restingInput;
    private double quietSeconds;
    private boolean resting;
    private volatile boolean sleeping;
    private final AtomicBoolean wakeRequested = new AtomicBoolean();
    private PhysicsWorld sampledWorld;
    private Function<BlockPos, BlockState> terrainSampler;
    /**
     * Tire friction on climbable surfaces never drops below what the body's pitch requires.
     */
    private double climbFrictionFloor;
    private double rampFrictionFloor;
    private double stepAge = Double.POSITIVE_INFINITY;
    private double stepApproachSpeed;

    public BulletVehicleController(
            int entityId, UUID entityUuid, VehiclePhysicsProfile profile,
            VehicleBulletShape bulletShape, double maximumStepHeight,
            Vec3 initialModelOrigin, float initialYawDegrees,
            Vec3 initialMinecraftVelocity, VehiclePhysicsInput initialInput
    ) {
        this.entityId = entityId;
        this.entityUuid = Objects.requireNonNull(entityUuid);
        this.profile = profile;
        this.bulletShape = bulletShape;
        this.maximumStepHeight = Math.max(0, maximumStepHeight);
        validateInput(initialInput);
        input = new AtomicReference<>(initialInput);
        int count = profile.wheels().size();
        referenceYs = new double[count];
        Arrays.fill(referenceYs, Double.NaN);
        contacts = new boolean[count];
        climbContacts = new boolean[count];
        supportAge = new double[count];
        Arrays.fill(supportAge, Double.POSITIVE_INFINITY);
        contactPoints = new Vec3[count];
        Arrays.fill(contactPoints, Vec3.ZERO);
        contactNormals = new Vector3f[count];
        terrainSurfaces = new VehicleWheelTerrainSurface[count];
        supportReferences = new VehicleClimbSupportReference[count];
        for (int i = 0; i < count; i++) {
            supportReferences[i] = new VehicleClimbSupportReference();
            contactNormals[i] = Vector3f.UNIT_Y.clone();
            terrainSurfaces[i] = new VehicleWheelTerrainSurface(this, i);
        }
        double reach = profile.wheels().stream().mapToDouble(mount ->
                mount.wheelCenter().subtract(bulletShape.localCenterOfMass()).length()
        ).max().orElse(0);
        climbField = new VehicleClimbHeightField((int) Math.ceil(reach + 4));
        body = new PhysicsRigidBody(bulletShape.collisionShape(), (float) profile.massKilograms());
        body.setCollisionGroup(CollisionGroups.PHYSICS_BODY);
        body.setCollideWithGroups(CollisionGroups.PHYSICS_BODY | CollisionGroups.TERRAIN);
        body.setDamping(0.008F, 0.10F);
        body.setFriction(0.28F);
        body.setRestitution(0);
        body.setSleepingThresholds(0.04F, 0.04F);
        body.setCcdMotionThreshold(0.35F);
        body.setCcdSweptSphereRadius(bulletShape.ccdRadius());
        body.setUserObject(this);
        Quaternion rotation = new Quaternion().fromAngleNormalAxis(
                (float) -Math.toRadians(initialYawDegrees), Vector3f.UNIT_Y
        );
        body.setPhysicsRotation(rotation);
        neutralForward = rotate(rotation, Vector3f.UNIT_Z);
        Vector3f initialComOffset = rotate(rotation, bulletShape.localCenterOfMass());
        body.setPhysicsLocationDp(new Vec3d(initialModelOrigin.x + initialComOffset.x,
                initialModelOrigin.y + initialComOffset.y, initialModelOrigin.z + initialComOffset.z));
        body.setLinearVelocity(vector(initialMinecraftVelocity).multLocal(20));
        wheels = new VehicleBulletWheelAssembly(profile, body, bulletShape.localCenterOfMass(), initialInput);
        steeringGeometry = new VehicleSteeringGeometry(profile);
        halfTrack = (profile.wheels().stream().mapToDouble(w -> w.wheelCenter().x).max().orElse(0)
                - profile.wheels().stream().mapToDouble(w -> w.wheelCenter().x).min().orElse(0)) * 0.5;
        identifyWheels();
    }

    private void identifyWheels() {
        for (int i = 0; i < contacts.length; i++) {
            PhysicsRigidBody wheel = wheels.body(i);
            if (wheel != null) {
                wheel.setUserObject(this);
            }
        }
    }

    public PhysicsRigidBody body() {
        return body;
    }

    PhysicsRigidBody wheelBody(int index) {
        return wheels.body(index);
    }

    public VehiclePhysicsSnapshot latestSnapshot() {
        return snapshot.get();
    }

    public boolean added() {
        return added;
    }

    public boolean removed() {
        return removed;
    }

    public void markAdded() {
        if (!removed) added = true;
    }

    public void markRemoved() {
        removed = true;
        added = false;
    }

    public void addToWorld(PhysicsSpace world) {
        if (removed) return;
        world.setOverlapFilterEnabled(true);
        if (!world.contains(body)) world.addCollisionObject(body);
        wheels.addToWorld(world);
        markAdded();
    }

    public void removeFromWorld(PhysicsSpace world) {
        for (var surface : terrainSurfaces) surface.remove();
        wheels.removeFromWorld();
        if (world.contains(body)) world.removeCollisionObject(body);
    }

    public boolean ownsCollisionBody(PhysicsCollisionObject candidate) {
        return candidate == body || wheelIndex(candidate) >= 0;
    }

    private int wheelIndex(PhysicsCollisionObject candidate) {
        for (int i = 0; i < contacts.length; i++) {
            if (wheels.body(i) == candidate) return i;
        }
        return -1;
    }

    public void publishInput(VehiclePhysicsInput newInput) {
        validateInput(newInput);
        var previous = input.get();
        if (!newInput.equals(previous)) {
            input.set(newInput);
            wakeRequested.set(true);
            sleeping = false;
        }
    }

    public boolean sleeping() {
        return sleeping;
    }

    /**
     * Safe from the main thread; native mutation happens only in physics PRE.
     */
    public void requestWake() {
        wakeRequested.set(true);
        sleeping = false;
    }

    private boolean idleRest(VehiclePhysicsInput current, double dt) {
        boolean wake = wakeRequested.getAndSet(false);
        if (wake) {
            wheels.activate();
            fieldAge = 100;
        }
        if (sleeping && !wake && !body.isActive() && wheels.allSleeping()
                && current.equals(restingInput)) return true;
        boolean quiet = !current.attended() && current.throttle() == 0 && current.steering() == 0
                && !wake
                && body.getLinearVelocity(null).lengthSquared() < .04 * .04
                && body.getAngularVelocity(null).lengthSquared() < .04 * .04
                && wheels.quiet(.04 * .04)
                && rotate(body.getPhysicsRotation(null), Vector3f.UNIT_Y).y > .8
                && (hasGroundContact() || sleeping);
        if (!quiet || (resting && !current.equals(restingInput))) {
            resting = false;
            sleeping = false;
            quietSeconds = 0;
            return false;
        }
        quietSeconds += dt;
        if (!resting && quietSeconds >= .75) {
            resting = true;
            restingInput = current;
            // Retain passive parking brakes, springs and collision bodies.
            // No activate(), imposed pose or forced sleeping state is used.
            for (int i = 0; i < contacts.length; i++) {
                double torque = profile.massKilograms() * current.performance().brakingMetersPerSecondSquared()
                        / contacts.length * current.wheelRadii().get(i);
                wheels.setDrive(i, 0, torque, false);
            }
        }
        return resting;
    }

    private void validateInput(VehiclePhysicsInput value) {
        int count = profile.wheels().size();
        if (value.installedWheels().size() != count || value.wheelRadii().size() != count
                || value.wheelGripCoefficients().size() != count) {
            throw new IllegalArgumentException("Wheel input count differs from " + profile.vehicleType());
        }
    }

    public void prePhysicsStep(PhysicsWorld world, double timeStepSeconds) {
        if (sampledWorld != world) {
            sampledWorld = world;
            terrainSampler = pos -> {
                var block = world.getLevel().getTerrainManager().getBlockSnapshotAt(pos);
                return block == null ? null : block.getState();
            };
        }
        prePhysicsStep(world, timeStepSeconds, terrainSampler);
    }

    /**
     * Same production path used by the native integration tests.
     */
    void prePhysicsStep(PhysicsSpace world, double dt, Function<BlockPos, BlockState> terrain) {
        if (!added || removed || !body.isInWorld()) return;
        // Contact callbacks receive only their originating section. Keep the
        // cached whole-world lookup for the wheel centre across section seams.
        terrainSampler = terrain;
        VehiclePhysicsInput current = input.get();
        if (appliedInput == null || !current.installedWheels().equals(appliedInput.installedWheels())
                || !current.wheelRadii().equals(appliedInput.wheelRadii())
                || !current.wheelGripCoefficients().equals(appliedInput.wheelGripCoefficients())) {
            wheels.refresh(current);
            identifyWheels();
        }
        appliedInput = current;
        double pitchSine = Math.min(0.999, Math.abs(rotate(body.getPhysicsRotation(null), Vector3f.UNIT_Z).y));
        double pitchTangent = pitchSine / Math.sqrt(1 - pitchSine * pitchSine);
        climbFrictionFloor = Math.min(CLIMB_FRICTION, CLIMB_FRICTION * pitchTangent);
        rampFrictionFloor = Math.min(MAXIMUM_RAMP_FRICTION, RAMP_CLIMB_FRICTION * pitchTangent);
        if (idleRest(current, dt)) return;
        wheels.updateDamping((float) dt);
        updateTerrain(world, current, terrain);
        Quaternion rotation = body.getPhysicsRotation(null);
        Vector3f forward = rotate(rotation, Vector3f.UNIT_Z);
        Vector3f velocity = body.getLinearVelocity(null);
        double forwardSpeed = velocity.dot(forward);
        double speedLimit = Math.max(current.performance().maxSpeedMetersPerSecond(),
                current.performance().reverseSpeedMetersPerSecond());
        // right=+1, left=-1. Bullet's +Y rotation about forward +Z points left.
        double rack = steeringRack.update(current.steering(), forwardSpeed, hasGroundContact(),
                current.steeringResponse(), dt);
        double steer = -Math.toRadians(profile.maximumSteeringAngleDegrees()) * rack
                * current.steeringCurve().scale(forwardSpeed, speedLimit);
        double grip = 0;
        int installed = 0;
        for (int i = 0; i < contacts.length; i++)
            if (current.installedWheels().get(i)) {
                grip += current.wheelGripCoefficients().get(i);
                installed++;
            }
        int cornerSupports = 0;
        double minimumNormalY = 1;
        for (int i = 0; i < contacts.length; i++) {
            if (contacts[i] && current.installedWheels().get(i)) {
                cornerSupports++;
                minimumNormalY = Math.min(minimumNormalY, contactNormals[i].y);
            }
        }
        double assistTarget = VehicleCorneringAssist.multiplier(forwardSpeed, rack,
                rotate(rotation, Vector3f.UNIT_Y).y, minimumNormalY, cornerSupports, installed);
        smoothedCornerAssist = MathUtil.exponentialApproach(smoothedCornerAssist, assistTarget, dt, 0.12);
        double highSpeedTarget = VehicleCorneringAssist.highSpeedMultiplier(forwardSpeed, rack,
                rotate(rotation, Vector3f.UNIT_Y).y, minimumNormalY, cornerSupports, installed);
        smoothedHighSpeedGrip = MathUtil.exponentialApproach(smoothedHighSpeedGrip, highSpeedTarget, dt, 0.12);
        // The high-speed bonus raises the grip only; the tire response keeps
        // following the base assist so fast corners do not get twitchier.
        double cornerAssist = smoothedCornerAssist * smoothedHighSpeedGrip;
        // Blend response with steering intent rather than abruptly adding a
        // 2.5x response above a binary input/terrain threshold.
        double responseTarget = VehicleCorneringAssist.responseMultiplier(rack,
                rotate(rotation, Vector3f.UNIT_Y).y, minimumNormalY, cornerSupports, installed);
        smoothedCornerResponse = MathUtil.exponentialApproach(smoothedCornerResponse, responseTarget, dt, 0.12);
        double cornerResponse = Math.max(smoothedCornerAssist * smoothedCornerAssist, smoothedCornerResponse);
        actualSteering = steeringGeometry.assistedAngle(steer, forwardSpeed,
                grip / Math.max(1, installed) * cornerAssist);
        // The assist eases in over ~0.12 s, so the grip-limited angle climbs
        // over several ticks at speed. Show the angle it settles at instead:
        // the wheels then follow the rack, a single step with instant steering.
        displaySteering = steeringGeometry.assistedAngle(steer, forwardSpeed,
                grip / Math.max(1, installed) * assistTarget * highSpeedTarget);
        long drivenCount = IntStream.range(0, contacts.length)
                .filter(i -> current.installedWheels().get(i) && profile.wheels().get(i).driven()).count();
        double gradeGravity = 0;
        int supportedCount = 0;
        for (int i = 0; i < contacts.length; i++) {
            if (!contacts[i]) continue;
            Vector3f tangent = forward.subtract(contactNormals[i].mult(forward.dot(contactNormals[i])));
            if (tangent.lengthSquared() > 1E-6) {
                gradeGravity += 9.81 * tangent.normalize().y;
                supportedCount++;
            }
        }
        gradeGravity = Math.max(0, gradeGravity / Math.max(1, supportedCount));
        double opposingSpeed = current.throttle() * forwardSpeed;
        if (Math.abs(current.throttle()) < 1E-4 || opposingSpeed >= -0.10) reversingBrake = false;
        else if (opposingSpeed < -1.25) reversingBrake = true;
        Vector3f angularVelocity = body.getAngularVelocity(null);
        Vector3f bodyUp = rotate(rotation, Vector3f.UNIT_Y);
        if (supportedCount >= 2 && bodyUp.y > 0.8F && Math.abs(current.steering()) < 0.05
                && Math.abs(actualSteering) < 0.01) {
            // A centred rack retains its release heading with bounded physical
            // yaw torque. Terrain gaps do not reset that reference; turning or
            // rolling over releases it. Never constrain position or a road lane.
            double inverseInertia = body.getInverseInertiaLocal(null).y;
            Vector3f heading = new Vector3f(forward.x, 0, forward.z).normalize();
            double yawRate = angularVelocity.dot(bodyUp);
            // Just after a turn the body still rotates. Capturing the heading
            // then and stopping that rotation reads as a hand twisting the car
            // back against the turn. Apply nothing until the tires have let
            // its rotation settle, then capture and hold the straight line.
            // Once captured, bumps still do not reset the reference.
            if (neutralForward == null && Math.abs(yawRate) <= HEADING_HOLD_CAPTURE_YAW_RATE) {
                neutralForward = heading;
            }
            if (neutralForward != null && inverseInertia > 1E-9) {
                double error = Math.atan2(heading.z * neutralForward.x - heading.x * neutralForward.z,
                        heading.dot(neutralForward));
                double response = -Math.expm1(-80 * dt) / dt;
                double torque = (error * 400 - yawRate * response) / inverseInertia;
                double limit = profile.massKilograms() * 9.81 * halfTrack;
                body.applyTorque(bodyUp.mult((float) Math.clamp(torque, -limit, limit)));
            }
        } else if (Math.abs(current.steering()) >= 0.05 || bodyUp.y <= 0.25F) neutralForward = null;
        boolean brakeCommand = current.brake() || !current.attended() || reversingBrake;
        double throttleCommand = current.performance().canDrive() && !brakeCommand ? current.throttle() : 0;
        var driveCommand = driveResponse.update(throttleCommand, brakeCommand, forwardSpeed,
                current.performance().maxSpeedMetersPerSecond(), current.performance().reverseSpeedMetersPerSecond(),
                current.performance().accelerationMetersPerSecondSquared(),
                current.performance().brakingMetersPerSecondSquared(),
                current.performance().accelerationRampSeconds(), dt);
        for (int i = 0; i < contacts.length; i++) {
            if (wheels.body(i) == null) continue;
            // Lateral response is supplied once, by the finite tire patch
            // below. Retain native rolling friction but do not also solve an
            // isotropic sideways sphere friction impulse at a mesh edge.
            wheels.body(i).setAnisotropicFriction(new Vector3f(bodyUp.y > 0.5F ? 0 : 1, 1, 1), AfMode.basic);
            supportAge[i] = contacts[i] ? 0 : supportAge[i] + dt;
            var wheelTarget = steeringGeometry.target(i, actualSteering);
            // Spring2's relative Euler angle is A minus B: servo targets
            // have the opposite sign from the desired world wheel heading.
            wheels.steer(i, -wheelTarget.steeringAngle());
            if (contacts[i] && bodyUp.y > 0.5F && supportedCount > 0) {
                // The spherical native tire provides longitudinal rolling
                // contact but has no finite lateral contact patch. Supply a
                // passive response at the actual wheel mount, including yaw
                // velocity there; never impose a world heading or lane.
                Vector3f offset = relativePosition(wheels.body(i), body);
                Vector3f pointVelocity = velocity.add(angularVelocity.cross(offset));
                Vector3f lateralForce = VehicleLateralGrip.force(rotation, wheelTarget.steeringAngle(),
                        contactNormals[i], pointVelocity, profile.massKilograms() / supportedCount,
                        current.wheelGripCoefficients().get(i) * cornerAssist,
                        // Extra corner grip needs a faster tire response as well:
                        // otherwise slower motors can cruise in the transition
                        // band with persistent slip despite unused load capacity.
                        // The force still obeys the same per-tire load limit.
                        profile.lateralGripResponse() * cornerResponse, dt);
                body.applyForce(lateralForce, offset);
            }
            boolean brake = brakeCommand;
            double throttle = driveCommand.throttle();
            double radius = current.wheelRadii().get(i);
            double targetSpeed = throttle >= 0 ? current.performance().maxSpeedMetersPerSecond()
                    : current.performance().reverseSpeedMetersPerSecond();
            double torque;
            double targetOmega;
            if (brake) {
                targetOmega = driveCommand.speedTarget() * wheelTarget.roadSpeedScale() / radius;
                torque = profile.massKilograms() * current.performance().brakingMetersPerSecondSquared()
                        * driveCommand.brake() / contacts.length * radius;
            } else if (profile.wheels().get(i).driven() && Math.abs(throttle) > 1E-4) {
                targetOmega = driveCommand.speedTarget() * wheelTarget.roadSpeedScale() / radius;
                double availableAcceleration = Math.abs(forwardSpeed) > targetSpeed + 0.1
                        ? Math.min(3.0, current.performance().brakingMetersPerSecondSquared())
                        : current.performance().accelerationMetersPerSecondSquared() + gradeGravity;
                torque = profile.massKilograms() * availableAcceleration
                        * Math.abs(throttle) / Math.max(1, drivenCount) * radius;
            } else {
                targetOmega = 0;
                torque = profile.rollingResistanceCoefficient() * profile.massKilograms()
                        * 9.81 / contacts.length * radius;
            }
            // A free-spinning airborne wheel has no tractive load. Do not
            // inject the full engine's reaction torque into the unsupported
            // chassis; real traction torque returns on landing. A short
            // grace covers one contact-manifold replacement during mesh PRE.
            if (supportAge[i] > 0.10) torque = 0;
            wheels.setDrive(i, -targetOmega, torque);
        }
        Vector3f supportingNormal = new Vector3f();
        int rollSupports = 0;
        for (int i = 0; i < contacts.length; i++) {
            if (contacts[i] && contactNormals[i].y > .5F) {
                supportingNormal.addLocal(contactNormals[i]);
                rollSupports++;
            }
        }
        if (rollSupports > 0) {
            Vector3f stability = VehicleRollStability.torque(rotation, angularVelocity,
                    body.getInverseInertiaLocal(null), supportingNormal.divideLocal(rollSupports), rollSupports, dt);
            if (stability.lengthSquared() > 0) body.applyTorque(stability);
        }
        Vector3f recoveryTorque = rolloverRecovery.update(dt, current.attended(), current.steering(),
                hasGroundContact(), rotation, velocity, body.getAngularVelocity(null),
                body.getInverseInertiaLocal(null), profile.massKilograms(), halfTrack);
        if (recoveryTorque.lengthSquared() > 0) body.applyTorque(recoveryTorque);
        // Rise per horizontal metre along the heading. The heading must be
        // level: the chassis' own forward is pitched up the slope, which read
        // a 45-degree ramp as a 35-degree one, and crest adhesion then pulled
        // down about 0.6 g as "excess" climb all the way up.
        double groundSlope = 0;
        int slopeContacts = 0;
        double headingLength = Math.hypot(forward.x, forward.z);
        for (int i = 0; i < contacts.length; i++) {
            if (contacts[i] && contactNormals[i].y > 0.25F && headingLength > 1E-6) {
                groundSlope += Math.max(0,
                        -(contactNormals[i].x * forward.x + contactNormals[i].z * forward.z)
                                / headingLength / contactNormals[i].y);
                slopeContacts++;
            }
        }
        Vector3f crestForce = crestAdhesion.force(dt, current.attended(), hasGroundContact(),
                rotation, velocity, groundSlope / Math.max(1, slopeContacts), profile.massKilograms());
        if (crestForce.lengthSquared() > 0) body.applyCentralForce(crestForce);
        Vector3f crestTorque = crestAdhesion.dampingTorque(rotation, body.getAngularVelocity(null),
                body.getInverseInertiaLocal(null));
        if (crestTorque.lengthSquared() > 0) body.applyTorque(crestTorque);
        boolean frontSupported = false, rearSupported = false;
        for (int i = 0; i < contacts.length; i++) {
            if (!contacts[i] || !current.installedWheels().get(i)) continue;
            if (profile.wheels().get(i).wheelCenter().z > bulletShape.localCenterOfMass().z) frontSupported = true;
            else rearSupported = true;
        }
        Vector3f pitchTorque = pitchStability.torque(dt, current.attended(), frontSupported, rearSupported,
                chassisGroundContact, rotation, velocity, body.getAngularVelocity(null),
                body.getInverseInertiaLocal(null));
        if (pitchTorque.lengthSquared() > 0) body.applyTorque(pitchTorque);
        double speed = velocity.length();
        if (speed > 1E-4) body.applyCentralForce(velocity.mult(
                (float) (-profile.aerodynamicDragCoefficient() * speed)));
        keepStepMomentum(dt, current, forward, forwardSpeed, bodyUp, throttleCommand, driveCommand.speedTarget());
        Arrays.fill(contacts, false);
        chassisGroundContact = false;
        Arrays.fill(climbContacts, false);
    }

    /**
     * A tire meeting a one-block step hits a short, steep climb surface and
     * the impact took a car from 10.7 to 5.4 m/s on the first step; on a
     * natural hillside every following step did the same, so cars crawled
     * up at 3 m/s. A suspension would soak most of that up. While a car
     * driven forward crosses steps, push it back towards the speed it came in
     * with (or reached since), never past its own wheel target or 8 m/s.
     */
    private void keepStepMomentum(double dt, VehiclePhysicsInput current, Vector3f forward, double forwardSpeed,
                                  Vector3f bodyUp, double throttle, double speedTarget) {
        boolean stepping = false;
        for (int i = 0; i < climbContacts.length; i++) stepping |= climbContacts[i] && current.installedWheels().get(i);
        if (!stepping && stepAge > STEP_MEMORY_SECONDS) stepApproachSpeed = forwardSpeed;
        else stepApproachSpeed = Math.max(stepApproachSpeed, forwardSpeed);
        stepAge = stepping ? 0 : stepAge + dt;
        if (stepAge > STEP_MEMORY_SECONDS || throttle <= 1E-4 || bodyUp.y < 0.7F || !hasGroundContact()) return;
        double deficit = Math.min(Math.min(stepApproachSpeed, speedTarget), STEP_RESTORE_MAXIMUM_SPEED) - forwardSpeed;
        if (deficit <= 0) return;
        Vector3f heading = new Vector3f(forward.x, 0, forward.z);
        if (heading.lengthSquared() < 1E-6) return;
        double acceleration = Math.min(STEP_RESTORE_ACCELERATION, deficit / STEP_RESTORE_SECONDS) * throttle;
        body.applyCentralForce(heading.normalize().multLocal((float) (profile.massKilograms() * acceleration)));
    }

    private void updateTerrain(PhysicsSpace world, VehiclePhysicsInput current, Function<BlockPos, BlockState> terrain) {
        if (maximumStepHeight <= 0 || !current.performance().canDrive() || !wheelTerrainEnabled()) {
            if (!terrainEnabled) return;
            terrainEnabled = false;
            chassisClimbBlocks.clear();
            nativeRampClimbBlocks.clear();
            climbField.clear();
            fieldAge = 100;
            for (var reference : supportReferences) reference.clear();
            Arrays.fill(referenceYs, Double.NaN);
            for (var surface : terrainSurfaces) surface.remove();
            return;
        }
        terrainEnabled = true;
        Vector3f location = body.getPhysicsLocation(null);
        double base = Double.POSITIVE_INFINITY;
        for (int i = 0; i < contacts.length; i++) {
            if (wheels.body(i) != null) base = Math.min(base, wheels.center(i).y - current.wheelRadii().get(i));
        }
        if (!Double.isFinite(base)) return;
        int x = (int) Math.floor(location.x), z = (int) Math.floor(location.z);
        int y = (int) Math.floor(base + .04);
        // A settled sphere oscillates a few micrometres around an integer top.
        // Neither that Y-floor flip nor crossing one X/Z cell invalidates the
        // halo-protected interior of this field. Keep a stable sampling window.
        if (fieldX == Integer.MIN_VALUE || Math.abs((long) x - fieldX) >= 2
                || Math.abs((long) z - fieldZ) >= 2 || base < fieldY - .25 || base > fieldY + 1.25
                || ++fieldAge >= 100) {
            double[][] supports = new double[contacts.length][];
            for (int i = 0; i < contacts.length; i++) {
                if (wheels.body(i) == null) continue;
                Vector3f center = wheels.center(i);
                supports[i] = new double[]{center.x, center.z, center.y - current.wheelRadii().get(i)};
            }
            climbField.rebuild(x, z, base, maximumStepHeight, terrain, supports);
            fieldX = x;
            fieldZ = z;
            fieldY = y;
            fieldAge = 0;
        }
        for (int i = 0; i < contacts.length; i++) {
            if (wheels.body(i) == null) {
                terrainSurfaces[i].remove();
                supportReferences[i].clear();
                referenceYs[i] = Double.NaN;
            } else {
                referenceYs[i] = supportReferences[i].resolve(climbField, wheels.center(i),
                        current.wheelRadii().get(i).floatValue(), maximumStepHeight, contacts[i]);
            }
        }
        Vector3f[] centers = new Vector3f[contacts.length];
        for (int i = 0; i < centers.length; i++) {
            if (wheels.body(i) != null) centers[i] = wheels.center(i);
        }
        climbField.updateConnectedSupports(centers, referenceYs, maximumStepHeight);
        for (int i = 0; i < centers.length; i++) {
            if (centers[i] != null)
                terrainSurfaces[i].update(world, climbField, centers[i], referenceYs[i], maximumStepHeight);
        }
        var bounds = body.boundingBox(null);
        Vector3f min = bounds.getMin(null), max = bounds.getMax(null);
        boolean recentlySupported = false;
        for (int i = 0; i < contacts.length; i++) recentlySupported |= contacts[i] || supportAge[i] <= .10;
        if (recentlySupported) climbField.collectNativeRampOverhangs(nativeRampClimbBlocks,
                new AABB(min.x, min.y, min.z, max.x, max.y, max.z).inflate(.05), referenceYs, maximumStepHeight);
        else nativeRampClimbBlocks.clear();
        if (!climbField.hasConnectedHeightVariation()) {
            chassisClimbBlocks.clear();
            return;
        }
        Vector3f velocity = body.getLinearVelocity(null);
        climbField.updateClimbableBlocks(referenceYs, maximumStepHeight,
                new AABB(min.x, min.y, min.z, max.x, max.y, max.z)
                        .expandTowards(velocity.x * 0.1, 0, velocity.z * 0.1).inflate(0.4));
        rebuildChassisClimbBlocks(min, max);
    }

    private void rebuildChassisClimbBlocks(Vector3f min, Vector3f max) {
        chassisClimbBlocks.clear();
        ArrayDeque<VehicleClimbHeightField.Surface> pending = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (int i = 0; i < contacts.length; i++) {
            if (wheels.body(i) == null) continue;
            Vector3f center = wheels.center(i);
            var surface = climbField.rawSurfaceAt(center.x, center.z);
            if (surface != null && surface.height() <= referenceYs[i] + maximumStepHeight + 0.04
                    && visited.add(surface.blockPos())) pending.add(surface);
        }
        while (!pending.isEmpty()) {
            var source = pending.removeFirst();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos p = source.blockPos().relative(side);
                if (p.getX() + 1 < min.x - 0.4 || p.getX() > max.x + 0.4
                        || p.getZ() + 1 < min.z - 0.4 || p.getZ() > max.z + 0.4) continue;
                var next = climbField.rawSurfaceAt(p.getX() + 0.5F, p.getZ() + 0.5F);
                if (next == null || Math.abs(next.height() - source.height()) > maximumStepHeight + 0.04) continue;
                if (Math.abs(next.height() - source.height()) > 0.001F) {
                    chassisClimbBlocks.add(source.blockPos());
                    chassisClimbBlocks.add(next.blockPos());
                }
                if (visited.add(next.blockPos())) pending.add(next);
            }
        }
        // Once a wheel-authorized stair sequence actually intersects the
        // footprint, the chassis must not scrape the same supporting ground
        // beneath its low front/rear overhang while its wheels negotiate that
        // sequence. This remains a finite connected footprint, never a global
        // height mask; flat ground outside the staircase keeps full collision.
        if (!chassisClimbBlocks.isEmpty()) chassisClimbBlocks.addAll(visited);
    }

    int terrainRebuildCount() {
        return climbField.rebuildCount();
    }

    int terrainConnectivityBuildCount() {
        return climbField.connectivityBuildCount();
    }

    /**
     * A private climbing surface substitutes ground only while the suspension
     * faces it. Sideways/inverted vehicles rest on the authored chassis boxes,
     * which deliberately cannot collide with a wheel's private mesh.
     * Check the live pose, including inside callbacks: the previous PRE cache
     * may still describe upright stair supports after an impact rolls the body.
     */
    boolean wheelTerrainEnabled() {
        Quaternion rotation = body.getPhysicsRotation(null);
        float x = rotation.getX(), y = rotation.getY(), z = rotation.getZ(), w = rotation.getW();
        float upY = 1 - 2 * (x * x + z * z);
        float rightY = 2 * (x * y + z * w);
        // Measure roll in the chassis transverse plane: pure uphill pitch
        // lowers upY but does not put the car on its side. Once overturned,
        // keep original chassis support until it is substantially upright;
        // otherwise the height-field can erase those contacts midway through
        // landing and let the body dip under the terrain before it recovers.
        double transverseLength = Math.hypot(upY, rightY);
        double rollSine = transverseLength > 1E-6 ? Math.abs(rightY) / transverseLength : 1;
        if (upY <= 0.25F || rollSine > 0.906307787) climbWheelFacingTerrain = false; // 65 degrees
        else if (!climbWheelFacingTerrain && rollSine < 0.573576436) climbWheelFacingTerrain = true; // 35 degrees
        return climbWheelFacingTerrain;
    }

    boolean hasGroundContact() {
        if (chassisGroundContact) return true;
        for (boolean contact : contacts) if (contact) return true;
        return false;
    }

    /**
     * Covered wheel contacts and upright climbing chassis contacts may be discarded.
     */
    public boolean shouldSuppressTerrainContact(
            PhysicsCollisionObject vehicleBody, PhysicsCollisionObject terrainBody,
            long pointId, int vehicleIndex, int terrainIndex,
            Function<BlockPos, BlockState> terrain
    ) {
        if (terrainBody.getUserObject() instanceof VehicleWheelTerrainSurface.Ownership) return false;
        if (wheelIndex(vehicleBody) < 0 && vehicleBody != body) return false;
        // Buried faces are never terrain, whatever the vehicle's step assist.
        var world = withWorld(terrain);
        BlockPos worldContact = contactBlock(terrainBody, pointId, vehicleIndex, terrainIndex, world);
        if (worldContact != null && hiddenSideFace(worldContact, pointId, vehicleIndex, terrainIndex, world))
            return true;
        if (maximumStepHeight <= 0 || !wheelTerrainEnabled()) return false;
        BlockPos contact = contactBlock(terrainBody, pointId, vehicleIndex, terrainIndex, terrain);
        if (contact == null) return false;
        int wheel = wheelIndex(vehicleBody);
        if (wheel >= 0) return terrainSurfaces[wheel].active() && terrainSurfaces[wheel].covers(contact);
        return chassisClimbBlocks.contains(contact) || nativeRampClimbBlocks.contains(contact)
                || Arrays.stream(terrainSurfaces).anyMatch(surface -> surface.replacesChassisContact(contact));
    }

    /**
     * Each block is its own collision body, so the faces between neighbours
     * still collide. On a ramp staircase the support block under the next ramp
     * presents its riser exactly behind the previous ramp's high side: once the
     * chassis rests on (and is allowed into) that ramp, the buried riser stops
     * it dead. A side face hidden by its neighbour is not terrain, and neither
     * is the vertical edge between two hidden side faces.
     */
    private static boolean hiddenSideFace(BlockPos block, long pointId, int vehicleIndex, int terrainIndex,
                                          Function<BlockPos, BlockState> terrain) {
        Vector3f outward = new ManifoldPoint(pointId, vehicleIndex).getNormalWorld(new Vector3f()).negateLocal();
        if (Math.abs(outward.y) > 0.5F) return false;
        Vector3f point = new ManifoldPoint(pointId, terrainIndex).getPositionWorld(new Vector3f());
        boolean onSide = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            // Only faces the contact actually lies on and pushes away from.
            double plane = side.getAxis() == Direction.Axis.X
                    ? block.getX() + (side.getStepX() > 0 ? 1 : 0) : block.getZ() + (side.getStepZ() > 0 ? 1 : 0);
            double coordinate = side.getAxis() == Direction.Axis.X ? point.x : point.z;
            double push = side.getStepX() * outward.x + side.getStepZ() * outward.z;
            if (Math.abs(coordinate - plane) > 0.02 || push < 0.05) continue;
            if (!coveredBy(block.relative(side), side.getOpposite(), point.y, terrain)) return false;
            onSide = true;
        }
        return onSide;
    }

    /**
     * Whether {@code neighbour}'s face toward {@code face} is solid up to height {@code y}.
     */
    private static boolean coveredBy(BlockPos neighbour, Direction face, double y,
                                     Function<BlockPos, BlockState> terrain) {
        BlockState state = terrain.apply(neighbour);
        if (state == null) return false;
        double covered;
        if (state.getBlock() instanceof RampBlock ramp) {
            if (state.getValue(RampBlock.FACING) != face) return false;
            covered = ramp.lowHeight() + ramp.rise();
        } else if (Block.isFaceFull(
                state.getCollisionShape(EmptyBlockGetter.INSTANCE, neighbour), face)) {
            covered = 1;
        } else {
            return false;
        }
        return y <= neighbour.getY() + covered + 0.02;
    }

    /**
     * Generic contact adapter shared by Spark's callbacks and native tests.
     */
    public boolean processTerrainContact(
            PhysicsCollisionObject vehicleBody, PhysicsCollisionObject terrainBody,
            long id, int vehicleIndex, int terrainIndex,
            Function<BlockPos, BlockState> terrain
    ) {
        int index = wheelIndex(vehicleBody);
        if (index < 0 && vehicleBody != body) return false;
        ManifoldPoint vehiclePoint = new ManifoldPoint(id, vehicleIndex);
        ManifoldPoint terrainPoint = new ManifoldPoint(id, terrainIndex);
        Vector3f rawNormal = vehiclePoint.getNormalWorld(new Vector3f()).negateLocal();
        Vector3f point = terrainPoint.getPositionWorld(new Vector3f());
        if (terrainBody.getUserObject() instanceof VehicleWheelTerrainSurface.Ownership(
                BulletVehicleController controller, int wheelIndex
        )) {
            if (controller != this || wheelIndex != index) return false;
            if (!wheelTerrainEnabled()) return false;
            vehiclePoint.setCombinedFriction(climbFriction(input.get().wheelGripCoefficients().get(index)));
            vehiclePoint.setCombinedRestitution(0);
            if (rawNormal.y > 0.25F) recordSupport(index, point, rawNormal, true);
            // This contact was generated by Bullet against real triangles;
            // no virtual point/depth replacement or extra normal force exists.
            return true;
        }
        BlockPos block = contactBlock(terrainBody, id, vehicleIndex, terrainIndex, terrain);
        if (index >= 0 && rawNormal.y > 0.5F && wheelTerrainEnabled()) {
            Vec3d center = wheels.body(index).getPhysicsLocationDp(null);
            double radius = input.get().wheelRadii().get(index);
            double flatTop = VehicleWheelContactNormals.flatTop(center, radius, point.y, terrain, terrainSampler);
            if (Double.isFinite(flatTop)) {
                // Adjacent coplanar triangles are one road surface, not a sequence
                // of tire-sized edges. Correct its normal AND sphere/plane anchors;
                // changing the normal alone retains an off-axis lever arm.
                ManifoldPoints.setNormalWorldOnB(id,
                        vehicleIndex == 0 ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.mult(-1));
                rawNormal.set(Vector3f.UNIT_Y);
                Quaternion wheelRotation = wheels.body(index).getPhysicsRotation(null);
                Quaternion inverseWheel = new Quaternion(-wheelRotation.getX(), -wheelRotation.getY(),
                        -wheelRotation.getZ(), wheelRotation.getW());
                vehiclePoint.setLocalPoint(rotate(inverseWheel, new Vector3f(0, (float) -radius, 0)));
                Vec3d terrainLocation = terrainBody.getPhysicsLocationDp(null);
                Quaternion terrainRotation = terrainBody.getPhysicsRotation(null);
                Quaternion inverseTerrain = new Quaternion(-terrainRotation.getX(), -terrainRotation.getY(),
                        -terrainRotation.getZ(), terrainRotation.getW());
                terrainPoint.setLocalPoint(rotate(inverseTerrain, new Vector3f((float) (center.x - terrainLocation.x),
                        (float) (flatTop - terrainLocation.y), (float) (center.z - terrainLocation.z))));
                vehiclePoint.setDistance((float) (center.y - radius - flatTop));
                point.set((float) center.x, (float) flatTop, (float) center.z);
            }
        }
        var world = withWorld(terrain);
        BlockPos worldBlock = block != null ? block : contactBlock(terrainBody, id, vehicleIndex, terrainIndex, world);
        if (index >= 0 && worldBlock != null && hiddenSideFace(worldBlock, id, vehicleIndex, terrainIndex, world)) {
            vehiclePoint.setDistance(5000);
            vehiclePoint.setCombinedFriction(0);
            return true;
        }
        if (index >= 0) {
            boolean replacedBySurface = wheelTerrainEnabled() && block != null && terrainSurfaces[index].covers(block);
            if (!replacedBySurface && rawNormal.y > 0.25F) recordSupport(index, point, rawNormal, false);
            double grip = input.get().wheelGripCoefficients().get(index);
            BlockState state = worldBlock == null ? null : world.apply(worldBlock);
            boolean ramp = state != null
                    && state.getBlock() instanceof RampBlock;
            vehiclePoint.setCombinedFriction(ramp ? (float) Math.max(grip, rampFrictionFloor) : (float) grip);
            vehiclePoint.setCombinedRestitution(0);
        }
        if (index < 0 && block == null) {
            // Spark's section lookup cannot see past its own section; a
            // contact on a box ending at the section border still needs it.
            if (shouldSuppressTerrainContact(vehicleBody, terrainBody, id, vehicleIndex, terrainIndex, terrain)) {
                vehiclePoint.setDistance(5000);
                vehiclePoint.setCombinedFriction(0);
                return true;
            }
            if (worldBlock != null)
                slideOnRampSurface(worldBlock, id, vehicleIndex, point, rawNormal, vehiclePoint, world);
            return false;
        }
        if (block == null) return false;
        if (index >= 0 && wheelTerrainEnabled() && terrainSurfaces[index].active()
                && terrainSurfaces[index].covers(block)) {
            vehiclePoint.setDistance(5000);
            vehiclePoint.setCombinedFriction(0);
            return true;
        }
        if (index < 0) {
            if (shouldSuppressTerrainContact(vehicleBody, terrainBody, id, vehicleIndex, terrainIndex, terrain)) {
                vehiclePoint.setDistance(5000);
                vehiclePoint.setCombinedFriction(0);
                return true;
            }
            slideOnRampSurface(block, id, vehicleIndex, point, rawNormal, vehiclePoint, world);
            if (rawNormal.y > 0.25F && vehiclePoint.getDistance() <= 0.02F) chassisGroundContact = true;
            return false;
        }
        // Uncovered terrain remains a normal Bullet collision. Never keep a
        // second virtual-contact or ray-support path alongside the mesh.
        return false;
    }

    /**
     * A low nose entering a ramp run scrapes its surface. Consecutive ramp
     * blocks form one continuous slope, yet Bullet resolves a touch at their
     * seam against the hull's edge, a ledge that stops the car. A contact on
     * the sloped face is treated as that face, and the body slides up it
     * rather than gripping like a tire.
     */
    private static void slideOnRampSurface(BlockPos block, long id, int vehicleIndex, Vector3f point,
                                           Vector3f rawNormal, ManifoldPoint vehiclePoint,
                                           Function<BlockPos, BlockState> terrain) {
        Vector3f normal = rampFaceNormal(block, point, terrain);
        if (normal == null) return;
        ManifoldPoints.setNormalWorldOnB(id, vehicleIndex == 0 ? normal : normal.negate());
        rawNormal.set(normal);
        vehiclePoint.setCombinedFriction(CHASSIS_RAMP_FRICTION);
    }

    /**
     * The contact's own section first, then the physics thread's world snapshot.
     */
    private Function<BlockPos, BlockState> withWorld(Function<BlockPos, BlockState> section) {
        Function<BlockPos, BlockState> sampler = terrainSampler;
        if (sampler == null || sampler == section) return section;
        return pos -> {
            BlockState state = section.apply(pos);
            return state != null ? state : sampler.apply(pos);
        };
    }

    /**
     * The sloped face's outward normal when {@code point} lies on it, otherwise null.
     */
    private static Vector3f rampFaceNormal(BlockPos block, Vector3f point, Function<BlockPos, BlockState> terrain) {
        BlockState state = terrain.apply(block);
        if (state == null || !(state.getBlock() instanceof RampBlock ramp)) {
            return null;
        }
        Direction high = state.getValue(RampBlock.FACING);
        float top = block.getY() + 0.5F + RampCollisionGeometry.surfaceY(high,
                Math.clamp(point.x - block.getX() - 0.5F, -0.5F, 0.5F),
                Math.clamp(point.z - block.getZ() - 0.5F, -0.5F, 0.5F), ramp.lowHeight(), ramp.rise());
        if (Math.abs(point.y - top) > 0.03F) return null;
        return RampCollisionGeometry.surfaceNormal(high, ramp.rise());
    }

    /**
     * Stairs and ramp slopes are built to be driven up, but one block per block
     * is a 45-degree grade: holding still on it already takes a friction
     * coefficient of 1, and tires worn to 72% of their grip fall short halfway
     * up. On climbable surfaces the tire never has less friction than the
     * grade requires, with a margin to keep accelerating. The grade is the
     * body's own pitch: a single step edge, locally steeper than 45 degrees,
     * must not grab the tire like a gear tooth. Flat road keeps the tire's
     * own grip.
     */
    private float climbFriction(double grip) {
        return (float) Math.max(grip, climbFrictionFloor);
    }

    private void recordSupport(int index, Vector3f point, Vector3f normal, boolean climb) {
        if (!contacts[index] || normal.y > contactNormals[index].y) {
            contactPoints[index] = new Vec3(point.x, point.y, point.z);
            contactNormals[index].set(normal);
        }
        contacts[index] = true;
        climbContacts[index] |= climb;
    }

    private BlockPos contactBlock(
            PhysicsCollisionObject terrainBody, long id, int vehicleIndex, int terrainIndex,
            Function<BlockPos, BlockState> terrain
    ) {
        if (terrainBody.getUserObject() instanceof BlockPos pos) return pos;
        Vector3f p = new ManifoldPoint(id, terrainIndex).getPositionWorld(new Vector3f());
        Vector3f outward = new ManifoldPoint(id, vehicleIndex).getNormalWorld(new Vector3f()).negateLocal();
        BlockPos primary = BlockPos.containing(p.x - outward.x * 0.015,
                p.y - outward.y * 0.015, p.z - outward.z * 0.015);
        BlockState state = terrain.apply(primary);
        if (state != null && !state.getCollisionShape(EmptyBlockGetter.INSTANCE, primary).isEmpty()) return primary;
        return climbField.findClimbableBlock(p, 0.02F);
    }

    public void postPhysicsStep() {
        postPhysicsStep(true);
    }

    /**
     * Observe every solver substep, but publish only a completed Minecraft tick.
     */
    public void postPhysicsStep(boolean publishSnapshot) {
        if (!added || removed || !body.isInWorld()) return;
        if (!publishSnapshot) return;
        boolean nowSleeping = resting && !body.isActive() && wheels.allSleeping();
        if (sleeping && nowSleeping && !wakeRequested.get()) return;
        sleeping = nowSleeping && !wakeRequested.get();
        Vec3d position = body.getPhysicsLocationDp(null);
        Quaternion rotation = body.getPhysicsRotation(null).normalizeLocal();
        Vector3f offset = rotate(rotation, bulletShape.localCenterOfMass());
        Vector3f velocity = body.getLinearVelocity(null), angular = body.getAngularVelocity(null);
        List<Double> lengths = new ArrayList<>();
        List<Boolean> flags = new ArrayList<>();
        for (int i = 0; i < contacts.length; i++) {
            lengths.add(wheels.suspensionLength(i));
            flags.add(contacts[i]);
        }
        snapshot.set(new VehiclePhysicsSnapshot(new Vec3(position.x - offset.x, position.y - offset.y, position.z - offset.z),
                rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW(),
                new Vec3(velocity.x, velocity.y, velocity.z), new Vec3(angular.x, angular.y, angular.z),
                lengths, flags, Arrays.asList(contactPoints.clone()), displaySteering));
    }

    static boolean sameStepSurfaceHeight(double target, double surface) {
        return Double.isFinite(target) && Double.isFinite(surface) && Math.abs(target - surface) <= 1E-3;
    }

}
