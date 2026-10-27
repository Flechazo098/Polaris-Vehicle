package com.flechazo.polaris_vehicle.manufacturing;

public enum VehicleManufacturingCategory {
    VEHICLE("screen.polaris_vehicle.vehicle_manufacturing_table.page.vehicle"),
    PART("screen.polaris_vehicle.vehicle_manufacturing_table.page.part"),
    TIRE("screen.polaris_vehicle.vehicle_manufacturing_table.page.tire"),
    PAINT("screen.polaris_vehicle.vehicle_manufacturing_table.page.paint"),
    TOOL("screen.polaris_vehicle.vehicle_manufacturing_table.page.tool"),
    BLOCK("screen.polaris_vehicle.vehicle_manufacturing_table.page.block"),
    OTHER("screen.polaris_vehicle.vehicle_manufacturing_table.page.other");

    private final String translationKey;

    VehicleManufacturingCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return translationKey;
    }
}
