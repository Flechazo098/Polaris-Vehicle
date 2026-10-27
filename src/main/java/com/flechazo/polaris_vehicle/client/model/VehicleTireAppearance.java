package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record VehicleTireAppearance(
        ResourceLocation vehicleType,
        ResourceLocation tireType,
        ResourceLocation model,
        ResourceLocation texture,
        Map<String, Set<VehiclePartSlot>> boneSlots,
        Map<String, Float> steeringBones,
        Map<String, BonePose> bonePoses
) {
    public VehicleTireAppearance {
        Objects.requireNonNull(vehicleType, "vehicleType");
        Objects.requireNonNull(tireType, "tireType");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(texture, "texture");
        if (!boneSlots.keySet().containsAll(steeringBones.keySet())
                || !boneSlots.keySet().containsAll(bonePoses.keySet())) {
            throw new IllegalArgumentException("Tire appearance metadata references an unknown bone");
        }
    }

    public record BonePose(
            float x,
            float y,
            float z,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {
        public BonePose {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                    || !Float.isFinite(scaleX) || scaleX <= 0.0F
                    || !Float.isFinite(scaleY) || scaleY <= 0.0F
                    || !Float.isFinite(scaleZ) || scaleZ <= 0.0F) {
                throw new IllegalArgumentException("Invalid tire bone pose");
            }
        }

        public static BonePose uniform(float x, float y, float z, float scale) {
            return new BonePose(x, y, z, scale, scale, scale);
        }
    }
}
