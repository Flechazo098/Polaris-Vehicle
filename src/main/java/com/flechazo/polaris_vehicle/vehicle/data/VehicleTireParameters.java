package com.flechazo.polaris_vehicle.vehicle.data;

import cc.sighs.oelib.data.DataManager;
import cc.sighs.oelib.data.DataRegistry;
import cc.sighs.oelib.data.api.DataDriven;
import cc.sighs.oelib.data.api.DataValidator;
import cc.sighs.oelib.event.neoforge.DataReloadEvent;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehicleTireDefinitions;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@DataDriven(modid = "polaris_vehicle", folder = "tire_parameters", syncToClient = true,
        enableCache = false, validator = VehicleTireParameters.Validator.class)
public record VehicleTireParameters(int maxDurability) {
    public static final Codec<VehicleTireParameters> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.doubleRange(1, 1_000_000_000).comapFlatMap(value -> value == Math.rint(value)
                            ? DataResult.success(value.intValue())
                            : DataResult.error(() -> "Tire durability must be an integer"), Integer::doubleValue).fieldOf("max_durability")
                    .forGetter(VehicleTireParameters::maxDurability)).apply(instance, VehicleTireParameters::new));
    private static boolean registered;

    public VehicleTireParameters {
        if (maxDurability < 1 || maxDurability > 1_000_000_000)
            throw new IllegalArgumentException("Tire durability must be between 1 and 1000000000");
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        DataRegistry.register(VehicleTireParameters.class, CODEC);
        NeoForge.EVENT_BUS.addListener(
                (DataReloadEvent event) -> {
                    if (!event.isDataType(VehicleTireParameters.class)) return;
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (server != null) server.execute(() -> {
                        for (var level : server.getAllLevels())
                            for (var entity : level.getAllEntities())
                                if (entity instanceof AbstractVehicleEntity vehicle)
                                    vehicle.updateVehicleData(data -> data);
                    });
                });
    }

    public static VehicleTireParameters resolve(ResourceLocation id, int builtinMaximum) {
        if (registered) {
            var loaded = DataManager.getData(VehicleTireParameters.class, id);
            if (loaded != null) return loaded;
        }
        return new VehicleTireParameters(builtinMaximum);
    }

    public static Map<ResourceLocation, VehicleTireParameters> defaultsView() {
        var defaults = new LinkedHashMap<ResourceLocation, VehicleTireParameters>();
        VehicleTireDefinitions.ALL.forEach((id, tire) ->
                defaults.put(id, new VehicleTireParameters(tire.builtinMaxDurability())));
        return Collections.unmodifiableMap(defaults);
    }

    public static InstalledPart rescale(InstalledPart part, int maximum) {
        if (part.maxDurability() == maximum) return part;
        int remaining = (int) Math.round((double) part.durability() / part.maxDurability() * maximum);
        if (part.durability() > 0 && part.durability() < part.maxDurability())
            remaining = Math.min(remaining, maximum - 1);
        return new InstalledPart(part.partId(), part.category(), remaining, maximum, part.tightening(), part.enabled());
    }

    public static final class Validator implements DataValidator<VehicleTireParameters> {
        @Override
        public ValidationResult validate(VehicleTireParameters data, ResourceLocation id) {
            return VehicleTireDefinitions.ALL.containsKey(id) ? ValidationResult.success()
                    : ValidationResult.failure("Unknown tire " + id);
        }
    }
}
