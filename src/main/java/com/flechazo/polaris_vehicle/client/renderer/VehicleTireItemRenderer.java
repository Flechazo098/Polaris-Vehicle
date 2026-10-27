package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.model.ModelBounds;
import com.flechazo.polaris_vehicle.client.model.VehicleTireItemModel;
import com.flechazo.polaris_vehicle.item.VehicleTireItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

public final class VehicleTireItemRenderer extends StaticGeoItemRenderer<VehicleTireItem> {
    private static final String VISIBLE_WHEEL = "1";

    public VehicleTireItemRenderer() {
        super(new VehicleTireItemModel());
    }

    @Override
    protected void prepareModel(VehicleTireItem animatable, BakedGeoModel model) {
        for (GeoBone root : model.topLevelBones()) {
            selectWheel(root);
        }
    }

    @Override
    protected ModelBounds measureBounds(BakedGeoModel model) {
        return ModelBounds.ofBone(model, VISIBLE_WHEEL, 45.0F, -45.0F);
    }

    private static void selectWheel(GeoBone bone) {
        for (GeoBone child : bone.getChildBones()) {
            if (VISIBLE_WHEEL.equals(child.getName())) {
                child.setHidden(false);
                child.setChildrenHidden(false);
                child.setPosX(child.getPivotX());
                child.setPosY(-child.getPivotY());
                child.setPosZ(-child.getPivotZ());
            } else if ("2".equals(child.getName())
                    || "3".equals(child.getName())
                    || "4".equals(child.getName())) {
                child.setHidden(true);
            }
        }
    }
}
