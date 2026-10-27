package com.flechazo.polaris_vehicle.content.menu;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinition;

public enum AssemblyTableUiSlot {
    EXTRA_POWER(22, 20, "extra_power", "extra_power",
            Maybe.some(VehiclePartSlot.EXTRA_POWER_UNIT)),
    ENGINE(22, 51, "motor", "engine", Maybe.some(VehiclePartSlot.ENGINE)),
    ENERGY_RECOVERY(22, 82, "energy_recovery", "energy_recovery",
            Maybe.some(VehiclePartSlot.ENERGY_RECOVERY_UNIT));

    public static final int FRAME_SIZE = 30;
    public static final int ITEM_OFFSET = 7;

    private final int x;
    private final int y;
    private final String textureName;
    private final String translationSuffix;
    private final Maybe<VehiclePartSlot> partSlot;

    AssemblyTableUiSlot(
            int x,
            int y,
            String textureName,
            String translationSuffix,
            Maybe<VehiclePartSlot> partSlot
    ) {
        this.x = x;
        this.y = y;
        this.textureName = textureName;
        this.translationSuffix = translationSuffix;
        this.partSlot = partSlot;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int itemX() {
        return x + ITEM_OFFSET;
    }

    public int itemY() {
        return y + ITEM_OFFSET;
    }

    public String textureName() {
        return textureName;
    }

    public String translationKey() {
        return "screen.polaris_vehicle.assembly_table." + translationSuffix;
    }

    public Maybe<VehiclePartSlot> partSlot() {
        return partSlot;
    }

    public boolean isImplementedFor(VehicleDefinition definition) {
        return partSlot
                .map(definition.supportedSlots()::contains)
                .orElse(false);
    }
    public boolean isVisibleFor(VehicleDefinition definition) {
        return isImplementedFor(definition);
    }

    public static Maybe<AssemblyTableUiSlot> forPart(VehiclePartSlot slot) {
        for (AssemblyTableUiSlot uiSlot : values()) {
            if (uiSlot.partSlot.filter(slot::equals).isDefined()) {
                return Maybe.some(uiSlot);
            }
        }
        return Maybe.none();
    }
}
