package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.part.InstalledPart;
import com.flechazo.polaris_vehicle.part.VehicleEngineDefinitions;
import com.flechazo.polaris_vehicle.part.VehiclePartSlot;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.*;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleEngineSoundManager {
    private static final Map<UUID, SoundGroup> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            stopAll();
            activeLevel = null;
            return;
        }
        if (level != activeLevel) {
            stopAll();
            activeLevel = level;
        }

        SoundManager soundManager = client.getSoundManager();
        Set<UUID> observed = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof AbstractVehicleEntity vehicle)
                    || vehicle.isRemoved()) {
                continue;
            }
            if (!vehicle.isEngineRunning()) continue;
            boolean started = vehicle.vehicleData().part(VehiclePartSlot.ENGINE).fold(
                    () -> false,
                    engine -> ensureSoundGroup(soundManager, vehicle, engine)
            );
            if (started) {
                observed.add(vehicle.getUUID());
            }
        }

        ACTIVE.entrySet().removeIf(entry -> {
            if (observed.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().finish();
            return true;
        });
    }

    private static boolean ensureSoundGroup(
            SoundManager soundManager,
            AbstractVehicleEntity vehicle,
            InstalledPart engine
    ) {
        if (!VehiclePartSlot.ENGINE.accepts(engine.category())) {
            return false;
        }
        if (!VehicleEngineDefinitions.isKnownEngine(engine)) {
            return false;
        }
        VehicleEngineSoundProfile profile = VehicleEngineSoundProfile.forVehicle(
                vehicle.definition(),
                engine.category()
        );
        if (profile.layered() && VehicleEnginePcmBank.INSTANCE.failed(profile)) return false;
        Entity listener = Minecraft.getInstance().getCameraEntity();
        if (profile.layered() && listener != null && listener.distanceToSqr(vehicle) > 32.0 * 32.0) {
            return false;
        }
        SoundGroup current = ACTIVE.get(vehicle.getUUID());
        if (current != null && current.matches(profile) && !current.isStopped()
                && current.engines().stream().allMatch(soundManager::isActive)) {
            return true;
        }
        if (current != null) {
            current.finish();
        }

        SoundGroup replacement = SoundGroup.create(vehicle, profile);
        ACTIVE.put(vehicle.getUUID(), replacement);
        replacement.play(soundManager);
        return true;
    }

    private static void stopAll() {
        ACTIVE.values().forEach(SoundGroup::finish);
        ACTIVE.clear();
    }

    private record SoundGroup(VehicleEngineSoundProfile profile, List<VehicleEngineSoundInstance> engines) {
        static SoundGroup create(AbstractVehicleEntity vehicle, VehicleEngineSoundProfile profile) {
            var mix = new VehicleEngineSoundMix(profile,
                    VehicleEngineSoundProfile.electricSemitoneOffset(vehicle.definition().id()));
            var loops = new ArrayList<VehicleEngineSoundInstance>(profile.loopCount());
            for (int layer = 0; layer < (profile.layered() ? 1 : profile.loopCount()); layer++) {
                loops.add(new VehicleEngineSoundInstance(vehicle, profile, mix, layer));
            }
            return new SoundGroup(profile, List.copyOf(loops));
        }

        boolean matches(VehicleEngineSoundProfile requestedProfile) {
            return profile == requestedProfile;
        }

        boolean isStopped() {
            return engines.stream().anyMatch(VehicleEngineSoundInstance::isStopped);
        }

        void play(SoundManager soundManager) {
            engines.forEach(soundManager::play);
        }

        void finish() {
            engines.forEach(VehicleEngineSoundInstance::finish);
        }
    }
}
