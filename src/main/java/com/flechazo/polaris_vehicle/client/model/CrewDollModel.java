package com.flechazo.polaris_vehicle.client.model;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.CrewDollItem;
import com.flechazo.polaris_vehicle.item.CrewDollVariant;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class CrewDollModel extends GeoModel<CrewDollItem> {
    private static final ResourceLocation ANIMATION = PolarisVehicle.id("animations/item/crew_doll.animation.json");
    private static final ResourceLocation DUCK_ANIMATION = PolarisVehicle.id("animations/item/crew_doll_yellow_duck.animation.json");

    @Override
    public ResourceLocation getModelResource(CrewDollItem animatable) {
        return PolarisVehicle.id("geo/item/" + animatable.variant().modelName() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CrewDollItem animatable) {
        return PolarisVehicle.id("textures/item/crew_doll/" + animatable.variant().textureName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(CrewDollItem animatable) {
        return animatable.variant() == CrewDollVariant.YELLOW_DUCK ? DUCK_ANIMATION : ANIMATION;
    }

}
