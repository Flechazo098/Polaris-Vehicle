package com.flechazo.polaris_vehicle.registry;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
            Registries.SOUND_EVENT,
            PolarisVehicle.MODID
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_MANUAL = register(
            "tool.wrench.manual"
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_PNEUMATIC = register(
            "tool.wrench.pneumatic"
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> JACK = register("tool.jack");

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String path) {
        ResourceLocation id = PolarisVehicle.id(path);
        return SOUND_EVENTS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
