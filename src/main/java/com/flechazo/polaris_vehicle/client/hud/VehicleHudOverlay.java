package com.flechazo.polaris_vehicle.client.hud;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class VehicleHudOverlay {
    private static final int FRAME_WIDTH = 324;
    private static final int FRAME_HEIGHT = 49;
    private static final int FILL_WIDTH = 142;
    private static final int FILL_HEIGHT = 33;
    private static final int FUEL_FILL_X = 8;
    private static final int FUEL_FILL_Y = 16;
    private static final int SPEED_FILL_X = 174;
    private static final int SPEED_FILL_Y = 16;
    private static final int BOOST_FRAME_WIDTH = 83;
    private static final int BOOST_FRAME_HEIGHT = 10;
    private static final int BOOST_FILL_WIDTH = 77;
    private static final int BOOST_FILL_HEIGHT = 4;
    private static final int BOOST_BAR_SPACING = 111;

    private static final ResourceLocation FRAME = texture("frame");
    private static final ResourceLocation FUEL_FILL = texture("fuel_fill");
    private static final ResourceLocation SPEED_FILL = texture("speed_fill");
    private static final ResourceLocation BOOST_FRAME = texture("boost_frame");
    private static final ResourceLocation BOOST_FILL = texture("boost_fill");
    private static final ResourceLocation BOOST_FILL_FULL = texture("boost_fill_full");

    private static Maybe<UUID> trackedVehicle = Maybe.none();
    private static boolean engineWasRunning;
    private static float selfTestTicks = VehicleHudMath.SELF_TEST_TOTAL_TICKS;
    private static double displayedFuelRatio;
    private static double displayedSpeedRatio;
    private static double displayedSpeed;
    private static final double[] displayedBoostRatios = new double[2];

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui
                || !(minecraft.player instanceof LocalPlayer player)
                || !(player.getVehicle() instanceof AbstractVehicleEntity vehicle)
                || !vehicle.isDriver(player)) {
            resetTracking();
            return;
        }

        float frameDeltaTicks = Math.max(0.0F, deltaTracker.getRealtimeDeltaTicks());
        updateSelfTest(vehicle, frameDeltaTicks);
        boolean running = vehicle.isEngineRunning();
        int frameX = (graphics.guiWidth() - FRAME_WIDTH) / 2;
        int frameY = graphics.guiHeight() - FRAME_HEIGHT;

        graphics.blit(FRAME, frameX, frameY, 0.0F, 0.0F,
                FRAME_WIDTH, FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);

        if (running) {
            double fuelRatio = vehicle.definition().fuelCapacity() == 0
                    ? 0.0
                    : (double) vehicle.fuel() / vehicle.definition().fuelCapacity();
            double speed = Math.max(0.0, vehicle.horizontalSpeedMetersPerSecond());
            double targetFuelRatio = VehicleHudMath.selfTestGauge(fuelRatio, selfTestTicks);
            double targetSpeedRatio = VehicleHudMath.selfTestGauge(
                    Math.clamp(speed / 20.0, 0.0, 1.0),
                    selfTestTicks
            );
            displayedFuelRatio = VehicleHudMath.interpolateGauge(
                    displayedFuelRatio,
                    targetFuelRatio,
                    frameDeltaTicks
            );
            displayedSpeedRatio = VehicleHudMath.interpolateGauge(
                    displayedSpeedRatio,
                    targetSpeedRatio,
                    frameDeltaTicks
            );
            double numericTarget = selfTestTicks < VehicleHudMath.SELF_TEST_TOTAL_TICKS
                    ? targetSpeedRatio * 20.0 : speed;
            displayedSpeed = VehicleHudMath.interpolateSpeed(displayedSpeed, numericTarget, frameDeltaTicks);
            drawCroppedFromLeft(
                    graphics,
                    FUEL_FILL,
                    frameX + FUEL_FILL_X,
                    frameY + FUEL_FILL_Y,
                    FILL_WIDTH,
                    FILL_HEIGHT,
                    VehicleHudMath.fuelGauge(displayedFuelRatio)
            );
            drawCroppedFromRight(
                    graphics,
                    SPEED_FILL,
                    frameX + SPEED_FILL_X,
                    frameY + SPEED_FILL_Y,
                    FILL_WIDTH,
                    FILL_HEIGHT,
                    VehicleHudMath.speedGauge(displayedSpeedRatio * 20.0)
            );
            graphics.drawCenteredString(
                    minecraft.font,
                    String.format(Locale.ROOT, "%.1f", displayedSpeed),
                    frameX + FRAME_WIDTH / 2,
                    frameY + 10,
                    0xFFFFFF
            );
        }

        drawPartIndicators(graphics, vehicle, frameX, frameY, running);
        drawBoost(graphics, vehicle, frameX, frameY, running, frameDeltaTicks);
        VehicleTelemetryOverlay.render(graphics, vehicle);
    }

    private static void updateSelfTest(AbstractVehicleEntity vehicle, float deltaTicks) {
        boolean vehicleChanged = trackedVehicle.fold(
                () -> true,
                tracked -> !vehicle.getUUID().equals(tracked)
        );
        boolean running = vehicle.isEngineRunning();
        if (vehicleChanged) {
            trackedVehicle = Maybe.some(vehicle.getUUID());
            engineWasRunning = false;
            selfTestTicks = running ? 0.0F : VehicleHudMath.SELF_TEST_TOTAL_TICKS;
            displayedFuelRatio = 0.0;
            displayedSpeedRatio = 0.0;
            displayedSpeed = 0.0;
            resetBoostRatios();
        } else if (running && !engineWasRunning) {
            selfTestTicks = 0.0F;
            displayedFuelRatio = 0.0;
            displayedSpeedRatio = 0.0;
            displayedSpeed = 0.0;
        }
        if (running) {
            selfTestTicks = Math.min(
                    VehicleHudMath.SELF_TEST_TOTAL_TICKS,
                    selfTestTicks + Math.max(0.0F, deltaTicks)
            );
        }
        engineWasRunning = running;
    }

    private static void resetTracking() {
        trackedVehicle = Maybe.none();
        engineWasRunning = false;
        selfTestTicks = VehicleHudMath.SELF_TEST_TOTAL_TICKS;
        displayedFuelRatio = 0.0;
        displayedSpeedRatio = 0.0;
        displayedSpeed = 0.0;
        resetBoostRatios();
    }

    private static void drawPartIndicators(
            GuiGraphics graphics,
            AbstractVehicleEntity vehicle,
            int frameX,
            int frameY,
            boolean running
    ) {
        if (vehicle.definition().supportedSlots().contains(VehiclePartSlot.FRONT_CENTER_WHEEL)) {
            drawWheel(graphics, vehicle, VehiclePartSlot.FRONT_CENTER_WHEEL,
                    "front", frameX + 95, frameY + 27, running);
        } else {
            drawWheel(graphics, vehicle, VehiclePartSlot.FRONT_LEFT_WHEEL,
                    "front_left", frameX + 75, frameY + 27, running);
            drawWheel(graphics, vehicle, VehiclePartSlot.FRONT_RIGHT_WHEEL,
                    "front_right", frameX + 115, frameY + 27, running);
        }
        drawWheel(graphics, vehicle, VehiclePartSlot.REAR_LEFT_WHEEL,
                "rear_left", frameX + 194, frameY + 27, running);
        drawWheel(graphics, vehicle, VehiclePartSlot.REAR_RIGHT_WHEEL,
                "rear_right", frameX + 234, frameY + 27, running);

        int state = vehicle.vehicleData().part(VehiclePartSlot.ENGINE).fold(
                () -> 5,
                engine -> durabilityState(engine, running)
        );
        ResourceLocation texture = texture("engine_" + state);
        graphics.blit(
                texture,
                frameX + 147,
                frameY + (state == 5 ? 22 : 23),
                30,
                30,
                0.0F,
                0.0F,
                32,
                32,
                32,
                32
        );
    }

    private static void drawWheel(
            GuiGraphics graphics,
            AbstractVehicleEntity vehicle,
            VehiclePartSlot slot,
            String textureName,
            int x,
            int y,
            boolean running
    ) {
        if (!vehicle.definition().supportedSlots().contains(slot)) {
            return;
        }
        int state = vehicle.vehicleData().part(slot).fold(
                () -> 5,
                wheel -> durabilityState(wheel, running)
        );
        ResourceLocation texture = texture("wheel_" + textureName + "_" + state);
        graphics.blit(texture, x, y, 0.0F, 0.0F, 16, 22, 16, 22);
    }

    private static int durabilityState(InstalledPart part, boolean running) {
        return VehicleHudMath.durabilityState(
                part.durability(),
                part.maxDurability(),
                running
        );
    }

    private static void drawBoost(
            GuiGraphics graphics,
            AbstractVehicleEntity vehicle,
            int frameX,
            int frameY,
            boolean running,
            float frameDeltaTicks
    ) {
        boolean boostAvailable = vehicle.vehicleData().part(VehiclePartSlot.EXTRA_POWER_UNIT)
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(VehiclePowerDefinition::isExtraPowerUnit)
                        .filter(power -> power.energyCost() > 0 && power.energyCapacity() > 0)
                        .map(power -> new BoostDisplay(part, power)))
                .fold(
                        () -> false,
                        display -> {
                            drawBoostBars(
                                    graphics,
                                    vehicle,
                                    frameX,
                                    frameY,
                                    running,
                                    display,
                                    frameDeltaTicks
                            );
                            return true;
                        }
                );
        if (!boostAvailable) {
            resetBoostRatios();
        }
    }

    private static void drawBoostBars(
            GuiGraphics graphics,
            AbstractVehicleEntity vehicle,
            int frameX,
            int frameY,
            boolean running,
            BoostDisplay display,
            float frameDeltaTicks
    ) {
        InstalledPart part = display.part();
        VehiclePowerDefinition power = display.power();
        int bars = Math.clamp(
                (int) Math.ceil((double) power.energyCapacity() / power.energyCost()),
                1,
                2
        );
        int firstX = bars == 1
                ? frameX + (FRAME_WIDTH - BOOST_FRAME_WIDTH) / 2
                : frameX + 65;
        for (int visualIndex = 0; visualIndex < bars; visualIndex++) {
            int x = firstX + visualIndex * BOOST_BAR_SPACING;
            int y = frameY - 12;
            graphics.blit(BOOST_FRAME, x, y, 0.0F, 0.0F,
                    BOOST_FRAME_WIDTH, BOOST_FRAME_HEIGHT,
                    BOOST_FRAME_WIDTH, BOOST_FRAME_HEIGHT);
            int energySlice = visualIndex;
            double targetCharge = !running || !part.enabled() || part.isScrapped()
                    ? 0.0
                    : Math.clamp(
                    (double) (vehicle.energy() - energySlice * power.energyCost())
                    / power.energyCost(),
                    0.0,
                    1.0
            );
            displayedBoostRatios[visualIndex] = VehicleHudMath.interpolateGauge(
                    displayedBoostRatios[visualIndex],
                    targetCharge,
                    frameDeltaTicks
            );
            drawCroppedFromLeft(
                    graphics,
                    displayedBoostRatios[visualIndex] >= 0.9999
                            ? BOOST_FILL_FULL
                            : BOOST_FILL,
                    x + 3,
                    y + 3,
                    BOOST_FILL_WIDTH,
                    BOOST_FILL_HEIGHT,
                    displayedBoostRatios[visualIndex]
            );
        }
        for (int hiddenIndex = bars; hiddenIndex < displayedBoostRatios.length; hiddenIndex++) {
            displayedBoostRatios[hiddenIndex] = 0.0;
        }
    }

    private static void resetBoostRatios() {
        Arrays.fill(displayedBoostRatios, 0.0);
    }

    private static void drawCroppedFromLeft(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            double ratio
    ) {
        int visibleWidth = (int) Math.round(width * Math.clamp(ratio, 0.0, 1.0));
        if (visibleWidth <= 0) {
            return;
        }
        graphics.blit(texture, x, y, 0.0F, 0.0F,
                visibleWidth, height, width, height);
    }

    private static void drawCroppedFromRight(
            GuiGraphics graphics,
            ResourceLocation texture,
            int x,
            int y,
            int width,
            int height,
            double ratio
    ) {
        int visibleWidth = (int) Math.round(width * Math.clamp(ratio, 0.0, 1.0));
        if (visibleWidth <= 0) {
            return;
        }
        int hiddenWidth = width - visibleWidth;
        graphics.blit(
                texture,
                x + hiddenWidth,
                y,
                (float) hiddenWidth,
                0.0F,
                visibleWidth,
                height,
                width,
                height
        );
    }

    private static ResourceLocation texture(String name) {
        return PolarisVehicle.id("textures/gui/hud/" + name + ".png");
    }

    private record BoostDisplay(InstalledPart part, VehiclePowerDefinition power) {
    }
}
