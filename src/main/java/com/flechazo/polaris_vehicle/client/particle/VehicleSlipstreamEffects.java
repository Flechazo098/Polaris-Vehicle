package com.flechazo.polaris_vehicle.client.particle;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.registry.ModParticles;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.flechazo.polaris_vehicle.vehicle.physics.VehicleSlipstreamProfile;
import com.flechazo.polaris_vehicle.vehicle.physics.VehicleSlipstreamService;
import com.flechazo.polaris_vehicle.vehicle.physics.VehicleSlipstreamTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleSlipstreamEffects {
    private static final double VISIBLE_DISTANCE = 96;
    private static final RandomSource RANDOM = RandomSource.create();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.isPaused() || client.options.particles().get() == ParticleStatus.MINIMAL)
            return;
        Entity camera = client.getCameraEntity();
        if (camera == null) return;
        VehicleSlipstreamProfile profile = VehicleSlipstreamProfile.DEFAULT;
        boolean decreased = client.options.particles().get() == ParticleStatus.DECREASED;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof AbstractVehicleEntity vehicle) || vehicle.isRemoved()
                    || vehicle.distanceToSqr(camera) > VISIBLE_DISTANCE * VISIBLE_DISTANCE) continue;
            Vec3 velocity = new Vec3(vehicle.getX() - vehicle.xo, 0, vehicle.getZ() - vehicle.zo);
            double speed = velocity.horizontalDistance() * 20.0;
            int tier = tier(profile, speed);
            if (tier == 0) continue;
            emit(client, vehicle, velocity, speed, tier, profile, decreased);
        }
    }

    static int tier(VehicleSlipstreamProfile profile, double speed) {
        int tier = 0;
        for (VehicleSlipstreamTier candidate : profile.tiers()) if (speed > candidate.minimumFrontSpeed()) tier++;
        return tier;
    }

    private static void emit(Minecraft client, AbstractVehicleEntity vehicle, Vec3 velocity, double speed,
                             int tier, VehicleSlipstreamProfile profile, boolean decreased) {
        AABB body = VehicleStructureModelManager.CLIENT.find(vehicle.vehicleTypeId())
                .fold(() -> new AABB(-0.8, 0, -1.5, 0.8, 1.2, 1.5), VehicleCollisionModel::physicsLocalBounds);
        float strength = Math.min(0.99F, tier / (float) profile.tiers().size());
        // Streaks stay put in the air, so lingering wake/speed seconds spans the wake.
        double reach = VehicleSlipstreamService.wakeReach(
                profile, body.maxZ - body.minZ);
        int lifetime = (int) Math.round(reach / speed * 20.0);
        double packed = lifetime + strength;
        var rotation = vehicle.vehicleBodyRotation();
        int perCorner = tier + (decreased ? 0 : 1);
        if (decreased && RANDOM.nextBoolean()) perCorner = Math.max(1, perCorner - 1);
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < perCorner; i++) {
                // Rear bumper corner (+Z is the front), low on the body, with a little scatter.
                Vector3f local = new Vector3f(
                        (float) (side * body.maxX * (0.65 + 0.25 * RANDOM.nextDouble())),
                        (float) (body.minY + body.getYsize() * (0.2 + 0.35 * RANDOM.nextDouble())),
                        (float) (body.minZ - 0.1 - 0.3 * RANDOM.nextDouble()));
                Vector3f world = rotation.transform(local);
                client.particleEngine.createParticle(ModParticles.SLIPSTREAM.get(),
                        vehicle.getX() + world.x, vehicle.getY() + world.y, vehicle.getZ() + world.z,
                        velocity.x, packed, velocity.z);
            }
        }
    }
}
