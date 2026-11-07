package com.flechazo.polaris_vehicle.network;

import cc.sighs.oelib.network.api.INetworkContext;
import cc.sighs.oelib.network.api.INetworkPacket;
import cc.sighs.oelib.network.api.NetworkPacket;
import cc.sighs.oelib.network.api.Side;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.screen.DrivingLicenseScreen;
import com.flechazo.polaris_vehicle.license.DrivingLicenseGrade;
import com.flechazo.polaris_vehicle.license.DrivingLicenseState;

import java.util.UUID;

/** Opens a licence card on the client. */
@NetworkPacket(modId = PolarisVehicle.MODID, id = "driving_license_view", side = Side.CLIENT)
public record DrivingLicenseViewPacket(
        UUID owner,
        String name,
        int id,
        DrivingLicenseGrade grade,
        DrivingLicenseState state
) implements INetworkPacket<DrivingLicenseViewPacket> {
    @Override
    public void handle(INetworkContext context) {
        if (!context.isClientSide()) {
            return;
        }
        context.enqueueWork(() -> DrivingLicenseScreen.open(this));
    }
}
