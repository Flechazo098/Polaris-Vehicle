package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class VehicleTireLayerSelection<L> {
    static final List<VehiclePartSlot> WHEEL_SLOTS = List.of(VehiclePartSlot.FRONT_LEFT_WHEEL,
            VehiclePartSlot.FRONT_RIGHT_WHEEL, VehiclePartSlot.FRONT_CENTER_WHEEL,
            VehiclePartSlot.REAR_LEFT_WHEEL, VehiclePartSlot.REAR_RIGHT_WHEEL);
    private final Map<ResourceLocation, Map<ResourceLocation, L>> index;
    private final L fallback;
    private final ResourceLocation[] tireIds = new ResourceLocation[WHEEL_SLOTS.size()];
    private ResourceLocation type;
    private int installedMask = -1;
    private List<L> selected = List.of();

    VehicleTireLayerSelection(Map<ResourceLocation, Map<ResourceLocation, L>> index, L fallback) {
        this.index = index;
        this.fallback = fallback;
    }

    List<L> select(ResourceLocation vehicleType, Map<VehiclePartSlot, InstalledPart> parts, int presence) {
        boolean changed = !vehicleType.equals(type) || presence != installedMask;
        for (int i = 0; i < WHEEL_SLOTS.size(); i++) {
            var part = (presence & (1 << i)) == 0 ? null : parts.get(WHEEL_SLOTS.get(i));
            var id = part == null ? null : part.partId();
            if (!Objects.equals(tireIds[i], id)) {
                changed = true;
                tireIds[i] = id;
            }
        }
        if (!changed) return selected;
        type = vehicleType;
        installedMask = presence;
        var byTire = index.getOrDefault(vehicleType, Map.of());
        var layers = new ArrayList<L>(WHEEL_SLOTS.size());
        for (var id : tireIds) {
            if (id == null) continue;
            L layer = byTire.getOrDefault(id, fallback);
            if (!layers.contains(layer)) layers.add(layer);
        }
        selected = List.copyOf(layers);
        return selected;
    }
}
