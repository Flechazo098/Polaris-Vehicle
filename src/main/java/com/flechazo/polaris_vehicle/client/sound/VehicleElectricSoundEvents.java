package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleElectricSoundEvents {
    @SubscribeEvent
    public static void onSourceStarted(PlaySoundSourceEvent event) {
        if (event.getSound().getLocation().equals(VehicleEngineSoundProfile.ELECTRIC.soundEvent())
                && event.getChannel() instanceof VehicleFilteredSoundChannel channel) {
            channel.polaris_vehicle$enableElectricFiltering(event.getSound().getPitch());
        }
    }
}
