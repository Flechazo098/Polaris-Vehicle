package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.resources.ResourceLocation;

public final class FuelExtractorUiSpec {
    public static final ResourceLocation BACKGROUND = texture("fuel_extractor");
    public static final ResourceLocation HORIZONTAL_PROGRESS =
            texture("fuel_extractor_progress_horizontal");
    public static final ResourceLocation VERTICAL_PROGRESS =
            texture("fuel_extractor_progress_vertical");

    public static final int BACKGROUND_WIDTH = 195;
    public static final int BACKGROUND_HEIGHT = 227;
    public static final int HORIZONTAL_PROGRESS_WIDTH = 71;
    public static final int HORIZONTAL_PROGRESS_HEIGHT = 4;
    public static final int HORIZONTAL_PROGRESS_X = 25;
    public static final int HORIZONTAL_PROGRESS_Y = 66;
    public static final int VERTICAL_PROGRESS_WIDTH = 5;
    public static final int VERTICAL_PROGRESS_HEIGHT = 89;
    public static final int VERTICAL_PROGRESS_COUNT = 3;
    public static final int VERTICAL_PROGRESS_FIRST_X = 124;
    public static final int VERTICAL_PROGRESS_X_STEP = 15;
    public static final int VERTICAL_PROGRESS_Y = 25;

    public static int horizontalProgressWidth(double progress) {
        return scaled(progress, HORIZONTAL_PROGRESS_WIDTH);
    }

    public static int horizontalProgressWidth(int progress, int totalProgress) {
        return totalProgress <= 0
                ? 0
                : horizontalProgressWidth((double) progress / totalProgress);
    }

    public static int verticalProgressHeight(double progress) {
        return scaled(progress, VERTICAL_PROGRESS_HEIGHT);
    }

    public static int verticalProgressX(int segment) {
        if (segment < 0 || segment >= VERTICAL_PROGRESS_COUNT) {
            throw new IndexOutOfBoundsException("Fuel segment index: " + segment);
        }
        return VERTICAL_PROGRESS_FIRST_X + segment * VERTICAL_PROGRESS_X_STEP;
    }

    public static int verticalFuelSegmentHeight(int amountMl, int capacityMl, int segment) {
        if (segment < 0 || segment >= VERTICAL_PROGRESS_COUNT || capacityMl <= 0) {
            return 0;
        }
        long clampedAmount = Math.clamp(amountMl, 0L, capacityMl);
        long segmentStart = (long) capacityMl * segment / VERTICAL_PROGRESS_COUNT;
        long segmentEnd = (long) capacityMl * (segment + 1) / VERTICAL_PROGRESS_COUNT;
        long segmentCapacity = segmentEnd - segmentStart;
        long segmentAmount = Math.clamp(clampedAmount - segmentStart, 0L, segmentCapacity);
        return segmentCapacity <= 0
                ? 0
                : verticalProgressHeight((double) segmentAmount / segmentCapacity);
    }

    private static int scaled(double progress, int fullSize) {
        return (int) Math.round(Math.clamp(progress, 0.0, 1.0) * fullSize);
    }

    private static ResourceLocation texture(String name) {
        return PolarisVehicle.id("textures/gui/container/" + name + ".png"
        );
    }
}
