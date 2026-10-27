package com.flechazo.polaris_vehicle.vehicle.entity;

import com.flechazo.polaris_vehicle.vehicle.data.VehicleMaintenanceState;

final class VehicleMaintenanceControls {

    static boolean lowersJack(VehicleMaintenanceState state, boolean emptyHand, boolean sneaking) {
        return state == VehicleMaintenanceState.JACKED && emptyHand && sneaking;
    }

    static boolean liftsVehicle(VehicleMaintenanceState state, boolean emptyHand, boolean sneaking) {
        return state == VehicleMaintenanceState.NORMAL && emptyHand && sneaking;
    }
}
