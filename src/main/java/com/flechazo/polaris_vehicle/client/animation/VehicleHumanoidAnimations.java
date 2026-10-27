package com.flechazo.polaris_vehicle.client.animation;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.VehicleFirstPersonView;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleSeatLayout;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

import java.lang.Math;
import java.util.*;

import static com.flechazo.polaris_vehicle.client.animation.VehicleHumanoidParser.*;
import static com.flechazo.polaris_vehicle.util.AnimationUtil.blendPose;
import static com.flechazo.polaris_vehicle.util.AnimationUtil.turnPose;

public final class VehicleHumanoidAnimations implements ResourceManagerReloadListener {
    public static final VehicleHumanoidAnimations INSTANCE = new VehicleHumanoidAnimations();

    private static final ResourceLocation RESOURCE = PolarisVehicle.id("animations/entity/humanoid.animation.json"
    );
    private static final ResourceLocation FIRST_PERSON_RESOURCE = PolarisVehicle.id("animations/entity/first_person_driver.animation.json"
    );
    private static final ResourceLocation FIRST_PERSON_GEOMETRY = PolarisVehicle.id("geo/entity/first_person_driver.geo.json"
    );
    private static final String FIRST_PERSON_ANIMATION = "animation.model.idle";
    private static final float DRIVER_EYE_HEIGHT = 6.0F / 16.0F;
    private static final float DRIVER_EYE_FRONT_PADDING = 0.005F;
    private static final HumanoidAnimationPose NEUTRAL_DRIVER_POSE = new HumanoidAnimationPose(Map.of());

    private volatile Map<String, HumanoidAnimationPose> animations = Map.of();
    private volatile HumanoidAnimationPose firstPersonPose = new HumanoidAnimationPose(Map.of());
    private volatile FirstPersonSkeleton firstPersonSkeleton = FirstPersonSkeleton.EMPTY;
    private volatile Map<ResourceLocation, DriverDummy> driverDummies = Map.of();
    private volatile Map<ResourceLocation, Map<String, HumanoidAnimationPose>> controlPoses = Map.of();
    private volatile Map<ResourceLocation, String> riderVehicleFrames = Map.of();

    record DriverDummy(FirstPersonSkeleton skeleton, Map<String, HumanoidAnimationPose> poses,
                              int steeringPoseDirection, float renderHeightOffset) {
        DriverDummy(FirstPersonSkeleton skeleton, Map<String, HumanoidAnimationPose> poses) {
            this(skeleton, poses, 1, 0);
        }

        DriverDummy(FirstPersonSkeleton skeleton, Map<String, HumanoidAnimationPose> poses, int direction) {
            this(skeleton, poses, direction, 0);
        }

        DriverDummy atHeadHeight(float height) {
            float original = skeleton.globalTransform("head", NEUTRAL_DRIVER_POSE).m31();
            return new DriverDummy(skeleton, poses, steeringPoseDirection, height - original);
        }

        HumanoidAnimationPose pose(float steering) {
            return blendPose(NEUTRAL_DRIVER_POSE,
                    turnPose(poses, steering * steeringPoseDirection < 0), Math.abs(steering));
        }

        Maybe<Matrix4f> boneTransform(String boneName, float steering) {
            return Maybe.ofNullable(skeleton.globalTransform(boneName, pose(steering)))
                    .map(matrix -> new Matrix4f().translation(0, renderHeightOffset, 0).mul(matrix));
        }

        Maybe<FirstPersonGeometry> geometry(String boneName) {
            return Maybe.ofNullable(skeleton.geometry(boneName));
        }

        Maybe<Vec3> eyeOffset(float steering,
                              Quaternionfc bodyRotation, Quaternionfc cameraRotation) {
            return boneTransform("head", steering).flatMap(head -> geometry("head").map(geometry ->
                    VehicleFirstPersonView.eyeOnHead(
                            VehicleFirstPersonView.lookingHeadTransform(head, bodyRotation, cameraRotation),
                            DRIVER_EYE_HEIGHT,
                            Math.min(geometry.frontFaceZ(), geometry("hat")
                                    .map(FirstPersonGeometry::frontFaceZ).orElse(geometry.frontFaceZ()))
                                    - DRIVER_EYE_FRONT_PADDING)));
        }

        List<VehicleFirstPersonView.ClearanceBox> bodyBoxes(float steering) {
            List<VehicleFirstPersonView.ClearanceBox> boxes = new ArrayList<>();
            for (String name : List.of("body", "rightArm", "leftArm", "rightLeg", "leftLeg")) {
                boneTransform(name, steering).ifPresent(transform ->
                        geometry(name).ifPresent(geometry -> boxes.addAll(geometry.clearanceBoxes(transform))));
            }
            return List.copyOf(boxes);
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        riderVehicleFrames = loadRiderVehicleFrames(resourceManager);
        animations = loadAnimations(resourceManager, RESOURCE);
        firstPersonPose = loadAnimations(resourceManager, FIRST_PERSON_RESOURCE)
                .getOrDefault(FIRST_PERSON_ANIMATION, new HumanoidAnimationPose(Map.of()));
        firstPersonSkeleton = loadFirstPersonSkeleton(resourceManager, FIRST_PERSON_GEOMETRY);
        Map<ResourceLocation, DriverDummy> loadedDummies = new LinkedHashMap<>();
        Map<ResourceLocation, Map<String, HumanoidAnimationPose>> loadedControls = new LinkedHashMap<>();
        for (var type : VehicleDefinitions.ALL.keySet()) {
            var steering = loadAnimations(resourceManager, PolarisVehicle.id("animations/vehicle/" + VehicleAssets.modelName(type) + ".animation.json"));
            loadedControls.put(type, steering);
            var geometry = loadFirstPersonSkeleton(resourceManager, VehicleAssets.firstPersonDriverGeometry(type));
            var poses = loadAnimations(resourceManager, VehicleAssets.firstPersonDriverAnimation(type));
            if (geometry.geometry("head") != null) {
                var controls = loadFirstPersonSkeleton(resourceManager, PolarisVehicle.id("geo/vehicle/" + VehicleAssets.controlModelName(type) + ".geo.json"), false);
                var dummy = alignedDriverDummy(geometry, poses, controls,
                        VehicleAssets.steeringControl(type),
                        turnPose(steering, VehicleAssets.authoredSteeringInput(type, 1) < 0));
                var seated = animations.get("animation.humanoid." + VehicleAssets.modelName(type));
                if (seated != null) {
                    float bodyHeight = (float) VehicleSeatLayout.require(type).seats().getFirst().y - .6F + 1.501F;
                    String frame = riderVehicleFrames.get(type);
                    if (frame != null) {
                        var pivot = geometry.globalTransform(frame, NEUTRAL_DRIVER_POSE);
                        if (pivot != null) bodyHeight = pivot.m31();
                    }
                    dummy = dummy.atHeadHeight(thirdPersonHeadHeight(seated, bodyHeight));
                }
                loadedDummies.put(type, dummy);
            }
        }
        driverDummies = Map.copyOf(loadedDummies);
        controlPoses = Map.copyOf(loadedControls);
    }

    /**
     * MC's ModelPart Y points down; head position is relative to the seated body.
     */
    static float thirdPersonHeadHeight(HumanoidAnimationPose seated, float bodyHeight) {
        var body = seated.bone("body").flatMap(HumanoidBoneTransform::position).orElse(AnimationVector.ZERO);
        var head = seated.bone("head").flatMap(HumanoidBoneTransform::position).orElse(body);
        return bodyHeight + (head.y() - body.y()) / 16;
    }

    /**
     * Optional whole-body frame, read from an authored bone rather than an offset constant.
     */
    public Maybe<Vec3> riderVehicleFramePivot(ResourceLocation type) {
        return Maybe.ofNullable(riderVehicleFrames.get(type)).flatMap(name ->
                Maybe.ofNullable(driverDummies.get(type)).flatMap(dummy -> dummy.boneTransform(name, 0))
                        .map(matrix -> {
                            var pivot = matrix.getTranslation(new Vector3f());
                            // Same Y(180) root as the vehicle/first-person renderer.
                            return new Vec3(-pivot.x, pivot.y, -pivot.z);
                        }));
    }

    static DriverDummy alignedDriverDummy(FirstPersonSkeleton skeleton, Map<String, HumanoidAnimationPose> poses,
                                          FirstPersonSkeleton controls, VehicleAssets.SteeringControl control,
                                          HumanoidAnimationPose authoredRightControl) {
        Matrix4f wheel = controls.globalTransform(control.bone(), NEUTRAL_DRIVER_POSE);
        HumanoidAnimationPose right = poses.get("animation.humanoid.turn_right");
        if (wheel == null || right == null) return new DriverDummy(skeleton, poses);
        AnimationVector rotation = authoredRightControl.bone(control.bone())
                .flatMap(HumanoidBoneTransform::rotation).map(VehicleHumanoidAnimations::convertRotation)
                .orElse(AnimationVector.ZERO);
        Vector3f axis = new Vector3f(rotation.x(), rotation.y(), rotation.z());
        if (axis.lengthSquared() < 1E-8F) return new DriverDummy(skeleton, poses);
        wheel.transformDirection(axis).normalize();
        Vector3f center = wheel.getTranslation(new Vector3f());
        double handSpin = 0;
        for (String name : List.of("rightArm", "leftArm")) {
            FirstPersonGeometry geometry = skeleton.geometry(name);
            if (geometry == null) continue;
            Vector3f neutral = skeleton.globalTransform(name, NEUTRAL_DRIVER_POSE)
                    .transformPosition(geometry.gripPoint()).sub(center);
            Vector3f turned = skeleton.globalTransform(name, right)
                    .transformPosition(geometry.gripPoint()).sub(center);
            handSpin += axis.dot(neutral.cross(turned));
        }
        return new DriverDummy(skeleton, poses, handSpin < 0 ? -1 : 1);
    }

    public HumanoidAnimationPose controlPoseFor(ResourceLocation type, float steering) {
        return blendPose(NEUTRAL_DRIVER_POSE,
                turnPose(controlPoses.getOrDefault(type, Map.of()),
                        VehicleAssets.authoredSteeringInput(type, steering) < 0), Math.abs(steering));
    }

    public Set<String> controlBonesFor(ResourceLocation type) {
        var poses = controlPoses.getOrDefault(type, Map.of());
        var names = new LinkedHashSet<>(turnPose(poses, true).bones().keySet());
        names.addAll(turnPose(poses, false).bones().keySet());
        return names;
    }

    public HumanoidAnimationPose firstPersonDriverPose() {
        return firstPersonPose;
    }

    public Maybe<Matrix4f> firstPersonBoneTransform(String boneName) {
        return Maybe.ofNullable(firstPersonSkeleton.globalTransform(boneName, firstPersonPose));
    }

    public Maybe<FirstPersonGeometry> firstPersonGeometry(String boneName) {
        return Maybe.ofNullable(firstPersonSkeleton.geometry(boneName));
    }

    public boolean hasDriverDummy(ResourceLocation vehicleType) {
        return driverDummies.containsKey(vehicleType);
    }

    public Maybe<Matrix4f> driverBoneTransform(ResourceLocation vehicleType, String boneName, float steering) {
        return Maybe.ofNullable(driverDummies.get(vehicleType))
                .flatMap(dummy -> dummy.boneTransform(boneName, steering));
    }

    public Maybe<FirstPersonGeometry> driverGeometry(ResourceLocation vehicleType, String boneName) {
        return Maybe.ofNullable(driverDummies.get(vehicleType))
                .flatMap(dummy -> dummy.geometry(boneName));
    }

    public Maybe<Vec3> driverEyeOffset(ResourceLocation vehicleType, float steering,
                                       Quaternionfc bodyRotation, Quaternionfc cameraRotation) {
        return Maybe.ofNullable(driverDummies.get(vehicleType))
                .flatMap(dummy -> dummy.eyeOffset(steering, bodyRotation, cameraRotation));
    }

    public List<VehicleFirstPersonView.ClearanceBox> driverBodyBoxes(ResourceLocation vehicleType, float steering) {
        return Maybe.ofNullable(driverDummies.get(vehicleType)).map(dummy -> dummy.bodyBoxes(steering))
                .orElse(List.of());
    }

    static AnimationVector convertPosition(AnimationVector bedrock) {
        return new AnimationVector(-bedrock.x(), bedrock.y(), bedrock.z());
    }

    static AnimationVector convertRotation(AnimationVector degrees) {
        return new AnimationVector(
                (float) Math.toRadians(-degrees.x()),
                (float) Math.toRadians(-degrees.y()),
                (float) Math.toRadians(degrees.z())
        );
    }

    public Maybe<HumanoidAnimationPose> poseFor(AbstractVehicleEntity vehicle) {
        return poseFor(vehicle, 0);
    }

    public Maybe<HumanoidAnimationPose> poseFor(AbstractVehicleEntity vehicle, float partialTick) {
        return riderPoseFor(vehicle.vehicleTypeId(), vehicle.steeringRenderProgress(partialTick));
    }

    public Maybe<HumanoidAnimationPose> riderPoseFor(ResourceLocation type, float steering) {
        String name = "animation.humanoid." + VehicleAssets.modelName(type);
        int direction = Maybe.ofNullable(driverDummies.get(type)).map(DriverDummy::steeringPoseDirection).orElse(1);
        var neutral = animations.get(name);
        if (neutral == null) return Maybe.none();
        var endpoint = animations.getOrDefault(name + (steering * direction < 0 ? "_turn_left" : "_turn_right"), neutral);
        return Maybe.some(blendPose(neutral, endpoint, Math.abs(steering)));
    }

    /**
     * Neutral vehicle-specific seating geometry used by non-driving riders.
     * Steering must never move a rear passenger's legs, so this deliberately
     * ignores the driver's left/right animation suffix.
     */
    public Maybe<HumanoidAnimationPose> seatedPassengerPoseFor(
            AbstractVehicleEntity vehicle
    ) {
        return Maybe.ofNullable(animations.get(
                "animation.humanoid." + VehicleAssets.modelName(vehicle.vehicleTypeId())
        ));
    }

    /**
     * Evaluates the supplied {@code animation.humanoid.ju} carry animation.
     * Molang trigonometric functions use degrees and {@code q.anim_time} is
     * seconds, hence the 20-tick conversion below.
     */
    public HumanoidAnimationPose carryPose(float ageInTicks) {
        double animationSeconds = ageInTicks / 20.0;
        float bodyWave = cosineDegrees(animationSeconds * 150.0);
        float armWave = cosineDegrees(animationSeconds * 150.0 + 20.0);
        return new HumanoidAnimationPose(Map.of(
                // The authored body rotation is zero; leaving it unset keeps
                // vanilla's crouch lean, which the crouched leg offsets assume.
                "body", transform(
                        Maybe.none(),
                        new AnimationVector(0.0F, -0.75F + bodyWave * 0.3F, 0.0F)
                ),
                "head", transform(
                        Maybe.none(),
                        new AnimationVector(0.0F, -1.0F + armWave * 0.3F, 0.0F)
                ),
                "rightArm", transform(
                        Maybe.some(new AnimationVector(-180.1179F, 1.0809F,
                                -12.4539F + armWave * 3.0F)),
                        new AnimationVector(0.0F, armWave * 0.5F, 0.0F)
                ),
                "leftArm", transform(
                        Maybe.some(new AnimationVector(-180.1179F, -1.0809F,
                                12.4539F - armWave * 3.0F)),
                        new AnimationVector(0.0F, armWave * 0.5F, 0.0F)
                )
        ));
    }

    private static HumanoidBoneTransform transform(
            Maybe<AnimationVector> rotation,
            AnimationVector position
    ) {
        return new HumanoidBoneTransform(
                rotation,
                Maybe.some(position)
        );
    }

    private static float cosineDegrees(double angleDegrees) {
        return (float) Math.cos(Math.toRadians(angleDegrees));
    }

    public record AnimationVector(float x, float y, float z) {
        public static final AnimationVector ZERO = new AnimationVector(0.0F, 0.0F, 0.0F);

        public static final Codec<AnimationVector> CODEC = Codec.FLOAT.listOf(3, 3).xmap(
                values -> new AnimationVector(values.getFirst(), values.get(1), values.get(2)),
                vector -> List.of(vector.x, vector.y, vector.z)
        );
    }

    public record HumanoidBoneTransform(
            Maybe<AnimationVector> rotation,
            Maybe<AnimationVector> position
    ) {
        public static final Codec<HumanoidBoneTransform> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                CodecUtil.maybeFieldCodec("rotation", AnimationVector.CODEC)
                        .forGetter(HumanoidBoneTransform::rotation),
                CodecUtil.maybeFieldCodec("position", AnimationVector.CODEC)
                        .forGetter(HumanoidBoneTransform::position)
        ).apply(instance, HumanoidBoneTransform::new));

    }

    public record HumanoidAnimationPose(Map<String, HumanoidBoneTransform> bones) {
        private static final Codec<Map<String, HumanoidBoneTransform>> BONES_CODEC = Codec.unboundedMap(
                Codec.STRING,
                HumanoidBoneTransform.CODEC
        );

        public static final Codec<HumanoidAnimationPose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BONES_CODEC.optionalFieldOf("bones", Map.of())
                        .forGetter(HumanoidAnimationPose::bones)
        ).apply(instance, HumanoidAnimationPose::new));

        public Maybe<HumanoidBoneTransform> bone(String name) {
            return Maybe.ofNullable(bones.get(name));
        }
    }

    record FirstPersonBone(
            String name,
            Maybe<String> parent,
            AnimationVector bindPosition,
            AnimationVector bindRotation,
            FirstPersonGeometry geometry
    ) {
    }

    /**
     * Same bind-pose-relative hierarchy as SimpleBedrockModel's BedrockModel,
     * BedrockAnimation and additive animation graph. Evaluates the authored static
     * arm and vehicle dummy poses; no external model/animation library is needed.
     * Reference: https://github.com/MCModderAnchor/SimpleBedrockModel
     */
    static final class FirstPersonSkeleton {
        static final FirstPersonSkeleton EMPTY = new FirstPersonSkeleton(Map.of());

        private final Map<String, FirstPersonBone> bones;

        FirstPersonSkeleton(Map<String, FirstPersonBone> bones) {
            this.bones = bones;
        }

        Matrix4f globalTransform(String name, HumanoidAnimationPose animation) {
            FirstPersonBone bone = bones.get(name);
            if (bone == null) {
                return null;
            }
            Matrix4f local = localTransform(bone, animation);
            return bone.parent().fold(() -> local,
                    parent -> globalTransform(parent, animation).mul(local));
        }

        FirstPersonGeometry geometry(String name) {
            FirstPersonBone bone = bones.get(name);
            return bone == null ? null : bone.geometry();
        }

        private static Matrix4f localTransform(FirstPersonBone bone,
                                               HumanoidAnimationPose animation) {
            HumanoidBoneTransform channel = animation.bones().get(bone.name());
            AnimationVector offset = channel == null
                    ? AnimationVector.ZERO
                    : channel.position().orElse(AnimationVector.ZERO);
            AnimationVector rotation = channel == null
                    ? AnimationVector.ZERO
                    : channel.rotation().orElse(AnimationVector.ZERO);
            AnimationVector delta = convertPosition(offset);
            AnimationVector animatedRotation = convertRotation(rotation);

            AnimationVector basePosition = bone.bindPosition();
            AnimationVector baseRotation = bone.bindRotation();
            Quaternionf quaternion = new Quaternionf().rotateZYX(
                    baseRotation.z() + animatedRotation.z(),
                    baseRotation.y() + animatedRotation.y(),
                    baseRotation.x() + animatedRotation.x()
            );
            return new Matrix4f()
                    .translate(
                            (basePosition.x() + delta.x()) / 16.0F,
                            (basePosition.y() + delta.y()) / 16.0F,
                            (basePosition.z() + delta.z()) / 16.0F
                    )
                    .rotate(quaternion);
        }
    }

    /**
     * Bedrock box-UV geometry rendered in Bedrock's Y-up convention.
     *
     * <p>A vanilla {@code ModelPart.Cube} assumes the opposite longitudinal
     * arm convention (minimum Y is the shoulder), which put the hand pixels on
     * the shoulder end of this exported Bedrock arm. Face order and UV layout
     * below follow SimpleBedrockModel's {@code BedrockCubeBox}.</p>
     * Reference: https://github.com/MCModderAnchor/SimpleBedrockModel
     */
    public static final class FirstPersonGeometry {
        private final List<FirstPersonCube> cubes;

        FirstPersonGeometry(List<FirstPersonCube> cubes) {
            this.cubes = cubes;
        }

        /**
         * Bedrock arms extend down from the shoulder; grip sits one pixel above the fingertips.
         */
        Vector3f gripPoint() {
            if (cubes.isEmpty()) return new Vector3f();
            FirstPersonCube cube = cubes.getFirst();
            return new Vector3f((cube.x() + cube.width() / 2) / 16,
                    (cube.y() + 1) / 16, (cube.z() + cube.depth() / 2) / 16);
        }

        /**
         * Frontmost head face relative to its bone pivot, in block units.
         */
        public float frontFaceZ() {
            return (float) cubes.stream().mapToDouble(cube ->
                            Math.min(cube.z(), cube.z() + cube.depth()) - cube.inflate())
                    .min().orElse(0) / 16.0F;
        }

        public List<VehicleFirstPersonView.ClearanceBox> clearanceBoxes(Matrix4f transform) {
            return cubes.stream().map(cube -> new VehicleFirstPersonView.ClearanceBox(new Matrix4f(transform),
                    new Vec3((cube.x() - cube.inflate()) / 16.0,
                            (cube.y() - cube.inflate()) / 16.0, (cube.z() - cube.inflate()) / 16.0),
                    new Vec3((cube.x() + cube.width() + cube.inflate()) / 16.0,
                            (cube.y() + cube.height() + cube.inflate()) / 16.0,
                            (cube.z() + cube.depth() + cube.inflate()) / 16.0))).toList();
        }

        public void render(
                PoseStack poseStack,
                VertexConsumer consumer,
                int packedLight,
                int packedOverlay
        ) {
            PoseStack.Pose pose = poseStack.last();
            for (FirstPersonCube cube : cubes) {
                cube.render(pose, consumer, packedLight, packedOverlay);
            }
        }
    }

    record FirstPersonCube(
            float x,
            float y,
            float z,
            float width,
            float height,
            float depth,
            int textureU,
            int textureV,
            boolean mirror,
            float inflate
    ) {
        private static final int[][] VERTEX_ORDER = {
                {0, 1, 5, 4}, // -Y
                {7, 6, 2, 3}, // +Y
                {3, 2, 1, 0}, // -Z
                {6, 7, 4, 5}, // +Z
                {7, 3, 0, 4}, // -X
                {2, 6, 5, 1}  // +X
        };
        private static final int[][] UV_ORDER = {
                {2, 3, 7, 6},
                {1, 2, 6, 7},
                {1, 2, 7, 8},
                {4, 5, 7, 8},
                {2, 4, 7, 8},
                {0, 1, 7, 8}
        };
        private static final int[][] MIRRORED_UV_ORDER = {
                {3, 2, 7, 6},
                {2, 1, 6, 7},
                {2, 1, 7, 8},
                {5, 4, 7, 8},
                {1, 0, 7, 8},
                {4, 2, 7, 8}
        };
        private static final Vector3f[] FACE_NORMALS = {
                new Vector3f(0.0F, -1.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, -1.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(-1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 0.0F, 0.0F)
        };

        private void render(
                PoseStack.Pose pose,
                VertexConsumer consumer,
                int packedLight,
                int packedOverlay
        ) {
            float x0 = (x - inflate) / 16.0F;
            float y0 = (y - inflate) / 16.0F;
            float z0 = (z - inflate) / 16.0F;
            float x1 = (x + width + inflate) / 16.0F;
            float y1 = (y + height + inflate) / 16.0F;
            float z1 = (z + depth + inflate) / 16.0F;
            Vector3f[] vertices = {
                    new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0),
                    new Vector3f(x1, y1, z0), new Vector3f(x0, y1, z0),
                    new Vector3f(x0, y0, z1), new Vector3f(x1, y0, z1),
                    new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)
            };
            for (Vector3f vertex : vertices) {
                pose.pose().transformPosition(vertex);
            }

            float[] uv = boxUv();
            int[][] faceUv = mirror ? MIRRORED_UV_ORDER : UV_ORDER;
            Matrix3f normalMatrix = pose.normal();
            for (int face = 0; face < VERTEX_ORDER.length; face++) {
                Vector3f normal = normalMatrix.transform(
                        new Vector3f(FACE_NORMALS[face])
                ).normalize();
                for (int corner = 0; corner < 4; corner++) {
                    Vector3f vertex = vertices[VERTEX_ORDER[face][corner]];
                    int uIndex = faceUv[face][corner == 0 || corner == 3 ? 1 : 0];
                    int vIndex = faceUv[face][corner <= 1 ? 2 : 3];
                    consumer.addVertex(vertex.x, vertex.y, vertex.z)
                            .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                            .setUv(uv[uIndex], uv[vIndex])
                            .setOverlay(packedOverlay)
                            .setLight(packedLight)
                            .setNormal(normal.x, normal.y, normal.z);
                }
            }
        }

        private float[] boxUv() {
            // The model declares 16x16 preview metadata but deliberately uses
            // vanilla player skin coordinates beginning at (40,16).
            float inverseTextureSize = 1.0F / 64.0F;
            float flooredWidth = (float) Math.floor(width);
            float flooredHeight = (float) Math.floor(height);
            float flooredDepth = (float) Math.floor(depth);
            return new float[]{
                    textureU * inverseTextureSize,
                    (textureU + flooredDepth) * inverseTextureSize,
                    (textureU + flooredDepth + flooredWidth) * inverseTextureSize,
                    (textureU + flooredDepth + flooredWidth * 2.0F) * inverseTextureSize,
                    (textureU + flooredDepth * 2.0F + flooredWidth * 2.0F) * inverseTextureSize,
                    (textureU + flooredDepth * 2.0F + flooredWidth * 3.0F) * inverseTextureSize,
                    textureV * inverseTextureSize,
                    (textureV + flooredDepth) * inverseTextureSize,
                    (textureV + flooredDepth + flooredHeight) * inverseTextureSize
            };
        }
    }

}
