package com.flechazo.polaris_vehicle.item;

public interface StaticGeoAsset {
    String geckoAssetFolder();

    String geckoAssetName();

    default String geckoTextureFolder() {
        return geckoAssetFolder();
    }

    default String geckoTextureName() {
        return geckoAssetName();
    }
    default String geckoAnimationName() {
        return "static";
    }

    default boolean usesAuthoredHandPose() {
        return false;
    }

    default boolean usesAuthoredHandDisplay() {
        return false;
    }
}
