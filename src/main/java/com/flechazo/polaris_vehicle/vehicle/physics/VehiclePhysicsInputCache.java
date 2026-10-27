package com.flechazo.polaris_vehicle.vehicle.physics;

import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleTypeParameters;
import com.flechazo.polaris_vehicle.vehicle.physics.bullet.*;

import java.util.List;
import java.util.Map;

public final class VehiclePhysicsInputCache {
    private VehicleDefinition definition;
    private VehicleInstanceData data;
    private VehicleTypeParameters parameters;
    private VehicleWheelTerrain terrain;
    private double slipstream;
    private VehiclePerformance performance;
    private Map<VehiclePartSlot, InstalledPart> parts;
    private VehiclePhysicsProfile wheelProfile;
    private double gripMultiplier;
    private List<Boolean> installed;
    private List<Double> grips;
    private List<Double> radii;
    private VehicleSteeringCurve curve;
    private VehiclePhysicsInput input;

    public VehiclePhysicsInput resolve(VehicleDefinition definition, VehicleInstanceData data,
                                       VehicleTypeParameters parameters, VehiclePhysicsProfile profile,
                                       VehicleWheelTerrain terrain, double slipstream, int throttle, int steering,
                                       boolean brake, boolean attended, VehicleSteeringResponse response) {
        boolean tuningChanged = !parameters.equals(this.parameters);
        if (performance == null || this.definition != definition || this.data != data
                || tuningChanged || !terrain.equals(this.terrain) || slipstream != this.slipstream) {
            performance = VehiclePerformanceCalculator.calculate(definition, data, terrain, slipstream);
        }
        double multiplier = parameters.physics().tireGripMultiplier();
        if (installed == null || !data.parts().equals(parts) || !profile.equals(wheelProfile)
                || multiplier != gripMultiplier) {
            installed = profile.wheels().stream().map(mount -> data.part(mount.slot()).isDefined()).toList();
            grips = VehicleWheelGrip.coefficients(profile, data).stream().map(grip -> grip * multiplier).toList();
            radii = VehicleWheelContactGeometry.radii(profile, data);
            parts = data.parts();
            wheelProfile = profile;
            gripMultiplier = multiplier;
        }
        if (curve == null || tuningChanged) {
            curve = new VehicleSteeringCurve(parameters.physics().steeringLimitStartRatio(),
                    parameters.physics().minimumSteeringScale());
        }
        this.definition = definition;
        this.data = data;
        this.parameters = parameters;
        this.terrain = terrain;
        this.slipstream = slipstream;
        if (input == null || input.throttle() != throttle || input.steering() != steering
                || input.brake() != brake || input.attended() != attended
                || !input.steeringResponse().equals(response)
                || !input.performance().equals(performance) || !input.installedWheels().equals(installed)
                || !input.wheelGripCoefficients().equals(grips) || !input.wheelRadii().equals(radii)
                || !input.steeringCurve().equals(curve)) {
            input = new VehiclePhysicsInput(throttle, steering, brake, attended,
                    curve, performance, installed, grips, radii, response);
        }
        return input;
    }
}
