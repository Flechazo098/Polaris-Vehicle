package com.flechazo.polaris_vehicle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class VehicleKeyMappings {
    public static final String CATEGORY = "key.categories.polaris_vehicle";

    public static final KeyMapping ENGINE_TOGGLE = new KeyMapping(
            "key.polaris_vehicle.engine_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );
    public static final KeyMapping BRAKE = new KeyMapping(
            "key.polaris_vehicle.brake",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            CATEGORY
    );
    public static final KeyMapping BOOST = new KeyMapping(
            "key.polaris_vehicle.boost",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );
    public static final KeyMapping HORN = new KeyMapping(
            "key.polaris_vehicle.horn",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );
    public static final KeyMapping CAMERA_TOGGLE = new KeyMapping(
            "key.polaris_vehicle.camera_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );
    public static final KeyMapping COLLISION_DEBUG = new KeyMapping(
            "key.polaris_vehicle.collision_debug",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    );
    public static final KeyMapping VEHICLE_PARAMETERS = new KeyMapping(
            "key.polaris_vehicle.vehicle_parameters",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );
    public static final KeyMapping VEHICLE_TELEMETRY = new KeyMapping(
            "key.polaris_vehicle.vehicle_telemetry",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );
}
