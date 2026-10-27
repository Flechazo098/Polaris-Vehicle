package com.flechazo.polaris_vehicle.client.hud;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleHotbarVisibility {

    @SubscribeEvent
    public static void beforeGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.HOTBAR.equals(event.getName())) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null
                && client.player.getVehicle() instanceof AbstractVehicleEntity) {
            event.setCanceled(true);
        }
    }
}
