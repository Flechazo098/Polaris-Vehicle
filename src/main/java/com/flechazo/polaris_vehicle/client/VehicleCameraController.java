package com.flechazo.polaris_vehicle.client;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.config.VehicleClientConfig;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleCameraController {
    private static Maybe<CameraType> previousCamera = Maybe.none();
    private static boolean wasDriving;
    private static boolean vehicleCameraEnabled;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        boolean driving = client.player != null
                && client.player.getVehicle() instanceof AbstractVehicleEntity vehicle
                && vehicle.isDriver(client.player);
        if (driving && !wasDriving) {
            previousCamera = Maybe.some(client.options.getCameraType());
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!driving && wasDriving) {
            previousCamera.ifPresent(client.options::setCameraType);
            previousCamera = Maybe.none();
        }

        while (VehicleKeyMappings.CAMERA_TOGGLE.consumeClick()) {
            if (!driving) {
                continue;
            }
            boolean firstPerson = !client.options.getCameraType().isFirstPerson();
            client.options.setCameraType(firstPerson ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
            client.player.displayClientMessage(Component.translatable(firstPerson
                    ? "message.polaris_vehicle.camera.first_person"
                    : "message.polaris_vehicle.camera.vehicle_centered"), true);
        }
        if (driving && client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
        vehicleCameraEnabled = driving && !client.options.getCameraType().isFirstPerson();
        wasDriving = driving;
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null
                && client.player.getVehicle() instanceof AbstractVehicleEntity vehicle
                && vehicle.isDriver(client.player)
                && vehicleCameraEnabled
                && event.getCamera().getEntity() == client.player) {
            event.setFOV(VehicleClientConfig.get().drivingCameraFovDegrees());
        }
    }

    public static boolean isVehicleCameraEnabled() {
        return wasDriving && vehicleCameraEnabled;
    }

}
