package com.flechazo.polaris_vehicle_more_liveries.client;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle_more_liveries.PolarisVehicleMoreLiveries;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = PolarisVehicleMoreLiveries.MODID, value = Dist.CLIENT)
public final class SkinItemModels {

    @SubscribeEvent
    public static void registerSprites(ModelEvent.RegisterAdditional event) {
        for (String name : PolarisVehicleMoreLiveries.PAINTS.keySet()) {
            event.register(ModelResourceLocation.standalone(PolarisVehicleMoreLiveries.id("item/" + name + "_vehicle_paint_2d")));
        }
    }

    @SubscribeEvent
    public static void wrapPaintModels(ModelEvent.ModifyBakingResult event) {
        for (String name : PolarisVehicleMoreLiveries.PAINTS.keySet()) {
            var id = PolarisVehicleMoreLiveries.id(name + "_vehicle_paint");
            var inventoryId = ModelResourceLocation.inventory(id);
            var original = event.getModels().get(inventoryId);
            var sprite = event.getModels().get(ModelResourceLocation.standalone(id.withPath("item/" + id.getPath() + "_2d")));
            Pathway.nullable(original)
                    .zipWith(Pathway.nullable(sprite), SkinPaintItemModel::new)
                    .peek(model -> event.getModels().put(inventoryId, model));
        }
    }
}
