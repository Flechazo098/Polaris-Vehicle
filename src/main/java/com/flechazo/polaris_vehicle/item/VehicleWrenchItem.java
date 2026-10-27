package com.flechazo.polaris_vehicle.item;

public final class VehicleWrenchItem extends StaticGeoItem {
    private final boolean electric;

    public VehicleWrenchItem(Properties properties, boolean electric) {
        super(properties, electric ? "electric_wrench_model" : "wrench_model");
        this.electric = electric;
    }

    public boolean electric() {
        return electric;
    }

    @Override
    public boolean usesAuthoredHandDisplay() {
        return true;
    }
}
