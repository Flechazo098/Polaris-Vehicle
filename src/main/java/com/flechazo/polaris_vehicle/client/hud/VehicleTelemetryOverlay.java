package com.flechazo.polaris_vehicle.client.hud;

import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinition;
import com.flechazo.polaris_vehicle.power.VehiclePowerDefinitions;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class VehicleTelemetryOverlay {
    private static final int MARGIN = 8;
    private static final int PADDING = 5;
    private static final int LINE_HEIGHT = 10;
    private static final int BACKGROUND = 0xA8000000;
    private static final int BORDER = 0x8060D8FF;
    private static final int TITLE_COLOR = 0x62DFFF;
    private static final int TEXT_COLOR = 0xF2F2F2;
    private static boolean enabled;
    private static UUID sampledVehicle;
    private static int sampledTick = -1;
    private static double sampledSpeed;
    private static double displayedAcceleration;

    public static boolean toggle() {
        enabled = !enabled;
        resetSample();
        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void render(GuiGraphics graphics, AbstractVehicleEntity vehicle) {
        if (!enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        double speed = vehicle.signedForwardSpeedMetersPerSecond();
        updateAcceleration(vehicle, speed);
        int energyCapacity = energyCapacity(vehicle);
        Component engineState = Component.translatable(vehicle.isEngineRunning()
                ? "hud.polaris_vehicle.telemetry.on"
                : "hud.polaris_vehicle.telemetry.off");
        Component steeringState = Component.translatable(switch (Integer.signum(vehicle.steeringInput())) {
            case -1 -> "hud.polaris_vehicle.telemetry.left";
            case 1 -> "hud.polaris_vehicle.telemetry.right";
            default -> "hud.polaris_vehicle.telemetry.center";
        });
        Component throttleState = Component.translatable(switch (Integer.signum(vehicle.throttleInput())) {
            case -1 -> "hud.polaris_vehicle.telemetry.reverse";
            case 1 -> "hud.polaris_vehicle.telemetry.forward";
            default -> "hud.polaris_vehicle.telemetry.idle";
        });
        List<Component> lines = List.of(
                Component.translatable("hud.polaris_vehicle.telemetry.title"),
                Component.translatable(
                        "hud.polaris_vehicle.telemetry.speed",
                        decimal(speed * 3.6),
                        decimal(speed)
                ),
                Component.translatable(
                        "hud.polaris_vehicle.telemetry.acceleration",
                        signedDecimal(displayedAcceleration)
                ),
                Component.translatable("hud.polaris_vehicle.telemetry.engine", engineState),
                Component.translatable(
                        "hud.polaris_vehicle.telemetry.fuel",
                        vehicle.fuel(),
                        vehicle.definition().fuelCapacity()
                ),
                Component.translatable(
                        "hud.polaris_vehicle.telemetry.energy",
                        vehicle.energy(),
                        energyCapacity
                ),
                Component.translatable("hud.polaris_vehicle.telemetry.throttle", throttleState),
                Component.translatable("hud.polaris_vehicle.telemetry.steering", steeringState)
        );

        int contentWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        int panelWidth = contentWidth + PADDING * 2;
        int panelHeight = lines.size() * LINE_HEIGHT + PADDING * 2 - 1;
        int x = graphics.guiWidth() - panelWidth - MARGIN;
        int y = MARGIN;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, BACKGROUND);
        graphics.fill(x, y, x + panelWidth, y + 1, BORDER);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, BORDER);
        graphics.fill(x, y, x + 1, y + panelHeight, BORDER);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, BORDER);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(
                    font,
                    lines.get(index),
                    x + PADDING,
                    y + PADDING + index * LINE_HEIGHT,
                    index == 0 ? TITLE_COLOR : TEXT_COLOR,
                    true
            );
        }
    }

    private static void updateAcceleration(AbstractVehicleEntity vehicle, double speed) {
        if (!vehicle.getUUID().equals(sampledVehicle)) {
            sampledVehicle = vehicle.getUUID();
            sampledTick = vehicle.tickCount;
            sampledSpeed = speed;
            displayedAcceleration = 0.0;
            return;
        }
        int elapsedTicks = vehicle.tickCount - sampledTick;
        if (elapsedTicks <= 0) {
            return;
        }
        double acceleration = (speed - sampledSpeed) * 20.0 / elapsedTicks;
        acceleration = Math.clamp(acceleration, -100.0, 100.0);
        displayedAcceleration += (acceleration - displayedAcceleration) * 0.35;
        sampledTick = vehicle.tickCount;
        sampledSpeed = speed;
    }

    private static int energyCapacity(AbstractVehicleEntity vehicle) {
        return vehicle.vehicleData().part(VehiclePartSlot.EXTRA_POWER_UNIT)
                .flatMap(part -> VehiclePowerDefinitions.find(part.partId())
                        .filter(VehiclePowerDefinition::isExtraPowerUnit))
                .fold(() -> 0, VehiclePowerDefinition::energyCapacity);
    }

    private static void resetSample() {
        sampledVehicle = null;
        sampledTick = -1;
        sampledSpeed = 0.0;
        displayedAcceleration = 0.0;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String signedDecimal(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }
}
