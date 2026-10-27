package com.flechazo.polaris_vehicle.vehicle.physics.bullet;

import com.flechazo.polaris_vehicle.sparkcore.physics.body.CollisionGroups;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.joints.motors.RotationMotor;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.mathd.Vec3d;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Independently simulated wheels, connected to the chassis by Bullet spring
 * constraints. All mutations belong to the physics PRE phase.
 *
 * <p>The model wheel center describes the loaded ride height. Joint travel is
 * measured from that center, with negative Y representing suspension droop.
 * The spring equilibrium includes the static gravity preload, so installing
 * real suspension does not lower the vehicle by its static sag a second time.
 * This follows Machine-Max's independent wheel/connector structure; engine
 * power is delivered by the rolling motor rather than a separate ray force.</p>
 */
public final class VehicleBulletWheelAssembly {
    private static final double GRAVITY = 9.81;
    private final VehiclePhysicsProfile profile;
    private final PhysicsRigidBody chassis;
    private final Vec3 localCenterOfMass;
    private final PhysicsRigidBody[] bodies;
    private final New6Dof[] joints;
    private final double[] radii;
    private final double springStiffness;
    private final double preload;
    private final double compressionDamping;
    private final double reboundDamping;
    private PhysicsSpace world;

    public VehicleBulletWheelAssembly(
            VehiclePhysicsProfile profile,
            PhysicsRigidBody chassis,
            Vec3 localCenterOfMass,
            VehiclePhysicsInput input
    ) {
        this.profile = profile;
        this.chassis = chassis;
        this.localCenterOfMass = Objects.requireNonNull(localCenterOfMass);
        bodies = new PhysicsRigidBody[profile.wheels().size()];
        joints = new New6Dof[bodies.length];
        radii = new double[bodies.length];
        double sprungMass = (profile.massKilograms() - wheelMass(profile) * bodies.length)
                / bodies.length;
        springStiffness = VehicleDynamics.springStiffness(
                sprungMass, profile.suspensionNaturalFrequencyHertz());
        preload = VehicleDynamics.staticLoadCompression(sprungMass, springStiffness, GRAVITY);
        compressionDamping = VehicleDynamics.dampingCoefficient(
                sprungMass, springStiffness, profile.compressionDampingRatio());
        reboundDamping = VehicleDynamics.dampingCoefficient(
                sprungMass, springStiffness, profile.reboundDampingRatio());
        refresh(input);
    }

    /**
     * Total profile mass is shared by the chassis and installed wheels.
     */
    public static float chassisMass(VehiclePhysicsProfile profile, VehiclePhysicsInput input) {
        long installed = input.installedWheels().stream().filter(Boolean::booleanValue).count();
        return (float) (profile.massKilograms() - installed * wheelMass(profile));
    }

    private static float wheelMass(VehiclePhysicsProfile profile) {
        return (float) Math.min(45.0, profile.massKilograms() * 0.03);
    }

    public int wheelCount() {
        return bodies.length;
    }

    /**
     * Null means the corresponding wheel is not installed.
     */
    public PhysicsRigidBody body(int index) {
        return bodies[index];
    }

    public New6Dof joint(int index) {
        return joints[index];
    }

    public Vector3f center(int index) {
        PhysicsRigidBody wheel = bodies[index];
        if (wheel != null) {
            return wheel.getPhysicsLocation(null);
        }
        return rotate(chassis.getPhysicsRotation(null), localMount(index))
                .addLocal(chassis.getPhysicsLocation(null));
    }

    /**
     * Same length convention as the renderer: restLength at normal ride height.
     */
    public double suspensionLength(int index) {
        PhysicsRigidBody wheel = bodies[index];
        if (wheel == null) {
            return profile.suspensionRestLength() + profile.suspensionMaxDroop();
        }
        Vector3f delta = VehicleContactMath.relativePosition(wheel, chassis);
        Vector3f chassisUp = rotate(chassis.getPhysicsRotation(null), Vector3f.UNIT_Y);
        double travel = delta.dot(chassisUp) - localMount(index).y;
        return Math.clamp(profile.suspensionRestLength() - travel,
                0.0, profile.suspensionRestLength() + profile.suspensionMaxDroop());
    }

    /**
     * Install/remove/resize wheels atomically on the physics thread.
     */
    public void refresh(VehiclePhysicsInput input) {
        for (int index = 0; index < bodies.length; index++) {
            boolean installed = input.installedWheels().get(index);
            double radius = input.wheelRadii().get(index);
            if (bodies[index] != null && (!installed || radii[index] != radius)) {
                removeWheel(index);
            }
            if (installed && bodies[index] == null) {
                createWheel(index, radius);
                if (world != null) {
                    addWheel(index);
                }
            }
            if (bodies[index] != null) {
                // Native contact friction supplies both propulsion and lateral
                // resistance; no ray traction force is added a second time.
                bodies[index].setFriction((float) input.wheelGripCoefficients().get(index).doubleValue());
            }
        }
        float requiredMass = chassisMass(profile, input);
        if (chassis.getMass() != requiredMass) {
            chassis.setMass(requiredMass);
        }
    }

    public void addToWorld(PhysicsSpace world) {
        Objects.requireNonNull(world);
        if (this.world != null && this.world != world) {
            throw new IllegalStateException("Wheels are already attached to another physics world");
        }
        this.world = world;
        for (int index = 0; index < bodies.length; index++) {
            if (bodies[index] != null) {
                addWheel(index);
            }
        }
    }

    public void removeFromWorld() {
        if (world == null) {
            return;
        }
        for (int index = 0; index < bodies.length; index++) {
            removeWheelFromWorld(index);
        }
        world = null;
    }

    /**
     * Reset all wheel transforms only for an explicit vehicle teleport.
     */
    public void teleportPose() {
        for (int index = 0; index < bodies.length; index++) {
            if (bodies[index] != null) {
                resetWheelPose(index);
            }
        }
    }

    /**
     * Kinematic transport/assembly mode must not retain dynamic constraints.
     */
    public void setKinematic(boolean kinematic) {
        for (int index = 0; index < bodies.length; index++) {
            PhysicsRigidBody wheel = bodies[index];
            if (wheel == null) {
                continue;
            }
            New6Dof joint = joints[index];
            if (kinematic && world != null && joint.getPhysicsSpace() != null) {
                world.removeJoint(joint);
            }
            wheel.setKinematic(kinematic);
            if (!kinematic && world != null && joint.getPhysicsSpace() == null
                    && !chassis.isKinematic()) {
                world.addJoint(joint);
            }
        }
    }

    /**
     * Radians; positive direction is the joint's local +Y rotation.
     */
    public void steer(int index, double angle) {
        New6Dof joint = joints[index];
        if (joint == null || !profile.wheels().get(index).steerable()) {
            return;
        }
        RotationMotor motor = joint.getRotationMotor(1);
        if (Math.abs(angle) < 1E-5) {
            // A centred rack is an actual locked steering constraint. Leaving
            // a high-force servo chasing floating-point angular noise allows
            // toe drift at reduced physics frequency and large world coords.
            joint.set(MotorParam.LowerLimit, 4, 0);
            joint.set(MotorParam.UpperLimit, 4, 0);
            motor.setMotorEnabled(false);
            return;
        }
        float limit = (float) Math.min(Math.toRadians(75),
                Math.toRadians(profile.maximumSteeringAngleDegrees()) * 1.7);
        joint.set(MotorParam.LowerLimit, 4, -limit);
        joint.set(MotorParam.UpperLimit, 4, limit);
        motor.setMotorEnabled(true);
        motor.setServoEnabled(true);
        motor.set(MotorParam.ServoTarget, (float) angle);
        motor.set(MotorParam.TargetVelocity, 4.0F);
        motor.set(MotorParam.MaxMotorForce,
                (float) (profile.massKilograms() * GRAVITY / bodies.length * radii[index]));
        if (bodies[index] != null) {
            bodies[index].activate();
        }
    }

    /**
     * Angular velocity in radians/s; torque in Nm, rolling around local +X.
     */
    public void setDrive(int index, double targetAngularVelocity, double maximumTorque) {
        setDrive(index, targetAngularVelocity, maximumTorque, true);
    }

    void setDrive(int index, double targetAngularVelocity, double maximumTorque, boolean wake) {
        New6Dof joint = joints[index];
        if (joint == null) {
            return;
        }
        RotationMotor motor = joint.getRotationMotor(0);
        motor.setServoEnabled(false);
        motor.setMotorEnabled(maximumTorque > 0.0);
        motor.set(MotorParam.TargetVelocity, (float) targetAngularVelocity);
        motor.set(MotorParam.MaxMotorForce, (float) Math.max(0.0, maximumTorque));
        if (wake && maximumTorque > 0.0) {
            bodies[index].activate();
            chassis.activate();
        }
    }

    boolean allSleeping() {
        for (var wheel : bodies) if (wheel != null && wheel.isActive()) return false;
        return true;
    }

    boolean quiet(double limitSquared) {
        for (var wheel : bodies) {
            if (wheel != null && (wheel.getLinearVelocity(null).lengthSquared() > limitSquared
                    || wheel.getAngularVelocity(null).lengthSquared() > limitSquared)) return false;
        }
        return true;
    }

    void activate() {
        chassis.activate();
        for (var wheel : bodies) if (wheel != null) wheel.activate();
    }

    /**
     * Keep asymmetric compression/rebound damping inside the native solver.
     */
    public void updateDamping() {
        updateDamping(1.0F / 120.0F);
    }

    public void updateDamping(float timeStep) {
        Vector3f up = rotate(chassis.getPhysicsRotation(null), Vector3f.UNIT_Y);
        Vector3f angularVelocity = chassis.getAngularVelocity(null);
        for (int index = 0; index < bodies.length; index++) {
            PhysicsRigidBody wheel = bodies[index];
            if (wheel == null) {
                continue;
            }
            boolean hardLanding = wheel.getLinearVelocity(null).y < -2.0F;
            wheel.setCcdMotionThreshold((float) (radii[index] * (hardLanding ? 0.1 : 0.5)));
            wheel.setCcdSweptSphereRadius((float) (hardLanding
                    ? Math.max(0.01, radii[index] - 0.001) : radii[index] * 0.8));
            Vector3f offset = VehicleContactMath.relativePosition(wheel, chassis);
            Vector3f attachmentVelocity = angularVelocity.cross(offset)
                    .addLocal(chassis.getLinearVelocity(null));
            double compressionVelocity = wheel.getLinearVelocity(null)
                    .subtractLocal(attachmentVelocity).dot(up);
            double damping = compressionVelocity >= 0.0 ? compressionDamping : reboundDamping;
            double nativeDamping = configureSpring(index, timeStep, damping);
            // Spring2 applies its damping as an explicit impulse. The light
            // wheel's reduced mass then forces a severe coefficient clamp at
            // 60 Hz, even though terrain supports the wheel and the damper is
            // intended to slow the much heavier sprung chassis. A zero-speed
            // motor supplements the safe small-signal Spring2 damping during
            // actual suspension impacts/rebound. Its zero target prevents that
            // impulse from reversing relative velocity and creating energy.
            // Leave tiny travel noise to the spring's original damper, so a
            // near-static wheel does not gain another active solver row merely
            // because contact rounding changed by a fraction of a millimetre.
            double additionalForce = Math.max(0.0, damping - nativeDamping)
                    * Math.max(0.0, Math.abs(compressionVelocity) - 0.15);
            New6Dof joint = joints[index];
            joint.getTranslationMotor().setServoEnabled(1, false);
            joint.getTranslationMotor().setMotorEnabled(1, additionalForce > 0.0);
            joint.set(MotorParam.TargetVelocity, 1, 0.0F);
            joint.set(MotorParam.MaxMotorForce, 1,
                    (float) additionalForce);
        }
    }

    private void createWheel(int index, double radius) {
        // A sphere's margin is its radius; setMargin is a logged no-op.
        SphereCollisionShape shape = new SphereCollisionShape((float) radius);
        PhysicsRigidBody wheel = new PhysicsRigidBody(shape, wheelMass(profile));
        bodies[index] = wheel;
        radii[index] = radius;
        wheel.setCollisionGroup(CollisionGroups.PHYSICS_BODY);
        wheel.setCollideWithGroups(CollisionGroups.PHYSICS_BODY | CollisionGroups.TERRAIN);
        wheel.setRestitution(0.0F);
        wheel.setDamping(0.01F, 0.01F);
        wheel.setRollingFriction((float) profile.rollingResistanceCoefficient());
        wheel.setSleepingThresholds(0.04F, 0.04F);
        // The wheel is itself a sphere. A swept radius of 80% permits nearly
        // 7 cm penetration for a 34 cm tire during a fast descending landing.
        // Tighten CCD during downward impacts in updateDamping, rather than
        // sweeping a full-radius sphere into every road seam while rolling.
        wheel.setCcdMotionThreshold((float) (radius * 0.5));
        wheel.setCcdSweptSphereRadius((float) (radius * 0.8));
        wheel.setKinematic(chassis.isKinematic());
        chassis.addToIgnoreList(wheel);
        for (PhysicsRigidBody otherWheel : bodies) {
            if (otherWheel != null && otherWheel != wheel) {
                wheel.addToIgnoreList(otherWheel);
            }
        }
        resetWheelPose(index);
        New6Dof joint = new New6Dof(chassis, wheel, localMount(index), Vector3f.ZERO,
                Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.XZY);
        joints[index] = joint;
        joint.setCollisionBetweenLinkedBodies(false);
        for (int axis = 0; axis < 6; axis++) {
            joint.set(MotorParam.LowerLimit, axis, 0.0F);
            joint.set(MotorParam.UpperLimit, axis, 0.0F);
            joint.set(MotorParam.StopErp, axis, 0.2F);
            joint.set(MotorParam.StopCfm, axis, 1.0E-5F);
        }
        joint.set(MotorParam.LowerLimit, 1, (float) -profile.suspensionMaxDroop());
        joint.set(MotorParam.UpperLimit, 1, (float) profile.suspensionRestLength());
        joint.set(MotorParam.Equilibrium, 1, (float) -preload);
        configureSpring(index, 1.0F / 120.0F, compressionDamping);
        joint.enableSpring(1, true);
        // Lower > upper means this degree of freedom is free in Bullet.
        joint.set(MotorParam.LowerLimit, 3, 1.0F);
        joint.set(MotorParam.UpperLimit, 3, -1.0F);
        if (profile.wheels().get(index).steerable()) {
            // The configured angle is the central steering-rack angle.
            // Ackermann's inside front wheel needs a larger angle to roll
            // around the same turn centre without scrubbing.
            float limit = (float) Math.min(Math.toRadians(75),
                    Math.toRadians(profile.maximumSteeringAngleDegrees()) * 1.7);
            joint.set(MotorParam.LowerLimit, 4, -limit);
            joint.set(MotorParam.UpperLimit, 4, limit);
            steer(index, 0.0);
        }
    }

    private void addWheel(int index) {
        PhysicsRigidBody wheel = bodies[index];
        if (!wheel.isInWorld()) {
            world.addCollisionObject(wheel);
        }
        if (!chassis.isKinematic() && !wheel.isKinematic()
                && joints[index].getPhysicsSpace() == null) {
            world.addJoint(joints[index]);
        }
    }

    private double configureSpring(int index, float timeStep, double damping) {
        double dt = Math.max(1.0E-4, timeStep);
        double wheelMass = bodies[index].getMass();
        double chassisMass = chassis.getMass();
        double effectiveMass = chassisMass * wheelMass / (chassisMass + wheelMass);
        // Spring2's optional conservative clamp silently substitutes another
        // stiffness inside the solver without updating the equilibrium preload.
        // Keep the sampled spring's k*dt^2/m + 2*c*dt/m < 4 stability
        // condition. A passive motor supplements the missing damping during
        // real impacts, without destabilising the spring's explicit update.
        double actualStiffness = Math.min(springStiffness,
                0.8 * 4.0 * effectiveMass / (dt * dt));
        double dampingLimit = Math.max(0.0,
                (4.0 - actualStiffness * dt * dt / effectiveMass)
                        * effectiveMass / (2.0 * dt) * 0.8);
        double nativeDamping = Math.min(damping, dampingLimit);
        double sprungMass = chassisMass / bodies.length;
        New6Dof joint = joints[index];
        joint.setStiffness(1, (float) actualStiffness, false);
        joint.setDamping(1, (float) nativeDamping, false);
        joint.set(MotorParam.Equilibrium, 1,
                (float) -VehicleDynamics.staticLoadCompression(sprungMass, actualStiffness, GRAVITY));
        return nativeDamping;
    }

    private void removeWheelFromWorld(int index) {
        New6Dof joint = joints[index];
        if (joint != null && joint.getPhysicsSpace() != null) {
            world.removeJoint(joint);
        }
        PhysicsRigidBody wheel = bodies[index];
        if (wheel != null && wheel.isInWorld()) {
            world.removeCollisionObject(wheel);
        }
    }

    private void removeWheel(int index) {
        PhysicsRigidBody wheel = bodies[index];
        if (world != null) {
            removeWheelFromWorld(index);
        }
        chassis.removeFromIgnoreList(wheel);
        for (PhysicsRigidBody otherWheel : bodies) {
            if (otherWheel != null && otherWheel != wheel) {
                wheel.removeFromIgnoreList(otherWheel);
            }
        }
        bodies[index] = null;
        joints[index] = null;
    }

    private void resetWheelPose(int index) {
        Quaternion rotation = chassis.getPhysicsRotation(null);
        Vector3f offset = rotate(rotation, localMount(index));
        PhysicsRigidBody wheel = bodies[index];
        wheel.setPhysicsRotation(rotation);
        Vec3d location = chassis.getPhysicsLocationDp(null);
        wheel.setPhysicsLocationDp(new Vec3d(location.x + offset.x, location.y + offset.y, location.z + offset.z));
        Vector3f mountVelocity = chassis.getAngularVelocity(null).cross(offset)
                .addLocal(chassis.getLinearVelocity(null));
        wheel.setLinearVelocity(mountVelocity);
        // A vehicle restored or teleported while moving already has rolling
        // tires. Starting each wheel at zero spin introduces a braking skid
        // while the new engine force is still building.
        Vector3f forward = rotate(rotation, Vector3f.UNIT_Z);
        Vector3f axle = rotate(rotation, Vector3f.UNIT_X);
        wheel.setAngularVelocity(chassis.getAngularVelocity(null)
                .addLocal(axle.mult((float) (mountVelocity.dot(forward) / radii[index]))));
        wheel.activate();
    }

    private Vector3f localMount(int index) {
        Vec3 mount = profile.wheels().get(index).wheelCenter().subtract(localCenterOfMass);
        return new Vector3f((float) mount.x, (float) mount.y, (float) mount.z);
    }

    /**
     * Spark's compact math dependency omits Quaternion#mult(Vector3f).
     */
    private static Vector3f rotate(Quaternion rotation, Vector3f vector) {
        float qx = rotation.getX();
        float qy = rotation.getY();
        float qz = rotation.getZ();
        float qw = rotation.getW();
        float tx = 2.0F * (qy * vector.z - qz * vector.y);
        float ty = 2.0F * (qz * vector.x - qx * vector.z);
        float tz = 2.0F * (qx * vector.y - qy * vector.x);
        return new Vector3f(
                vector.x + qw * tx + qy * tz - qz * ty,
                vector.y + qw * ty + qz * tx - qx * tz,
                vector.z + qw * tz + qx * ty - qy * tx);
    }
}
