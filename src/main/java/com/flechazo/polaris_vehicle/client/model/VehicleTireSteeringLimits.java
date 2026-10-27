package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.util.CodecUtil;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleAssets;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehiclePhysicsProfile;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleSteeringGeometry;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleSteeringRack;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.VehicleSteeringResponse;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mesh/chassis clearance caps generated, intersected with authored turn endpoints.
 */
public final class VehicleTireSteeringLimits implements ResourceManagerReloadListener {
    public static final VehicleTireSteeringLimits INSTANCE = new VehicleTireSteeringLimits();
    private static final Codec<Map<ResourceLocation, Double>> CLEARANCES = Codec.unboundedMap(
            ResourceLocation.CODEC, Codec.doubleRange(Double.MIN_VALUE, Math.nextDown(90.0)));
    private volatile Map<ResourceLocation, Double> clearances = Map.of();
    private final Map<ResourceLocation, VehicleSteeringGeometry> geometry = new ConcurrentHashMap<>();

    private record CalibrationKey(ResourceLocation type, double physicalLockRadians) {
    }

    private final Map<CalibrationKey, Double> lockFractions = new ConcurrentHashMap<>();

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        var resource = PolarisVehicle.id("vehicle_tire_steering_limits.json");
        Pathway.tryRun(() -> {
            try (var reader = resources.getResourceOrThrow(resource).openAsReader()) {
                clearances = CodecUtil.readJson(CLEARANCES, reader);
            }
        }).peekFailure(ignored -> clearances = Map.of());
        lockFractions.clear();
    }

    public double maximumDegrees(ResourceLocation type, double authoredDegrees) {
        return Math.min(Math.abs(authoredDegrees), clearances.getOrDefault(type, Math.abs(authoredDegrees)));
    }

    /**
     * Use the same Ackermann angles as Bullet, without multiplying speed attenuation twice.
     */
    public float roadWheelAngle(ResourceLocation type, VehiclePartSlot slot, float centralRenderAngle, float authoredDegrees) {
        var profile = VehiclePhysicsProfile.requireBuiltin(type);
        var steering = geometry.get(type);
        if (steering == null) {
            steering = new VehicleSteeringGeometry(profile);
            geometry.put(type, steering);
        }
        for (int i = 0; i < profile.wheels().size(); i++) {
            if (profile.wheels().get(i).slot() != slot) continue;
            // The synced central render angle is positive-right. Geometry uses
            // Bullet's positive-left convention, which is Gecko's bone-Y sign here.
            double wheelAngle = steering.target(i, -centralRenderAngle).steeringAngle();
            double limit = Math.toRadians(maximumDegrees(type, authoredDegrees));
            return (float) (Math.clamp(wheelAngle, -limit, limit) * Math.signum(authoredDegrees));
        }
        return 0;
    }

    /**
     * Full animation lock occurs when the last steerable road wheel reaches its model stop.
     */
    public float visualProgress(ResourceLocation type, float centralAngle) {
        double progress = 1;
        boolean found = false;
        var slots = VehicleAssets.wheelBoneSlots(type);
        for (var bone : VehicleAssets.steeringBones(type).entrySet()) {
            for (var slot : slots.get(bone.getKey())) {
                double limit = Math.toRadians(maximumDegrees(type, bone.getValue()));
                if (limit <= 0) continue;
                progress = Math.min(progress, Math.abs(roadWheelAngle(type, slot, centralAngle, bone.getValue())) / limit);
                found = true;
            }
        }
        return found ? (float) Math.copySign(Math.clamp(progress, 0, 1), centralAngle) : 0;
    }

    /**
     * Invert Ackermann and rack easing so the preference measures visible lock, at rest.
     */
    public double visualLockRackFraction(ResourceLocation type, double physicalLockRadians) {
        return lockFractions.computeIfAbsent(new CalibrationKey(type, physicalLockRadians),
                key -> calculateVisualLockRackFraction(key.type(), key.physicalLockRadians()));
    }

    private double calculateVisualLockRackFraction(ResourceLocation type, double physicalLockRadians) {
        double required = 0;
        for (float sign : new float[]{-1, 1}) {
            double low = 0, high = physicalLockRadians;
            for (int i = 0; i < 48; i++) {
                double middle = (low + high) * .5;
                if (Math.abs(visualProgress(type, (float) (sign * middle))) < 1 - 1E-7) low = middle;
                else high = middle;
            }
            required = Math.max(required, (low + high) * .5 / physicalLockRadians);
        }
        return VehicleSteeringRack.positionForOutput(required);
    }

    public VehicleSteeringResponse responseForVisibleLock(ResourceLocation type, double physicalLockRadians,
                                                          VehicleSteeringResponse preference) {
        if (preference.instant()) return preference;
        double fraction = visualLockRackFraction(type, physicalLockRadians);
        return new VehicleSteeringResponse(false,
                Math.clamp(preference.lockSeconds() / Math.max(.05, fraction), .05, 40), preference.returnSeconds());
    }
}
