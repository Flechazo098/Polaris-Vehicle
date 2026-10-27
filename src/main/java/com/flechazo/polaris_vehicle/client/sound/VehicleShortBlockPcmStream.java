package com.flechazo.polaris_vehicle.client.sound;

public interface VehicleShortBlockPcmStream {
    default float blockSeconds() {
        return 0.025F;
    }

    default int queuedBlocks() {
        return 4;
    }
}
