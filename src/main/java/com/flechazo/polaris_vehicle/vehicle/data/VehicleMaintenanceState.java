package com.flechazo.polaris_vehicle.vehicle.data;

public enum VehicleMaintenanceState {
    NORMAL,
    CARRIED,
    JACKED,
    ASSEMBLY_TABLE,
    DISASSEMBLING,
    REMOVED;

    public boolean allowsMaintenance() {
        return this == JACKED || this == ASSEMBLY_TABLE;
    }

    public boolean blocksDriving() {
        return this != NORMAL;
    }

    public boolean allowsEngineOperation() {
        return this == NORMAL || this == JACKED;
    }

    public boolean allowsBoarding() {
        return this == NORMAL;
    }

    public boolean locksPose() {
        return this == JACKED || this == ASSEMBLY_TABLE;
    }
}
