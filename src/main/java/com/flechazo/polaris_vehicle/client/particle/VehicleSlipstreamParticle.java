package com.flechazo.polaris_vehicle.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class VehicleSlipstreamParticle extends TextureSheetParticle {
    /**
     * Fraction of the vehicle's velocity the disturbed air keeps for a moment.
     */
    private static final double AIR_DRAG_ALONG = 0.12;
    private final Vector3f backward;
    private final float length;
    private final float width;
    private final float peakAlpha;

    VehicleSlipstreamParticle(ClientLevel level, double x, double y, double z, Vec3 vehicleVelocity,
                              int lifetimeTicks, float strength) {
        super(level, x, y, z);
        Vec3 horizontal = new Vec3(vehicleVelocity.x, 0, vehicleVelocity.z);
        double perTick = horizontal.length();
        backward = perTick > 1.0E-6
                ? new Vector3f((float) (-horizontal.x / perTick), 0, (float) (-horizontal.z / perTick))
                : new Vector3f(0, 0, -1);
        // Overlap consecutive ticks' streaks so the trail reads as continuous.
        length = (float) Math.max(0.6, perTick * 1.6);
        width = 0.05F + 0.04F * strength;
        peakAlpha = 0.30F + 0.35F * strength;
        xd = vehicleVelocity.x * AIR_DRAG_ALONG;
        yd = 0;
        zd = vehicleVelocity.z * AIR_DRAG_ALONG;
        friction = 0.82F;
        gravity = 0;
        hasPhysics = false;
        lifetime = Math.max(3, lifetimeTicks);
        rCol = gCol = bCol = 1.0F;
        alpha = peakAlpha;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        // Appear quickly, then thin out towards the far end of the wake.
        alpha = peakAlpha * Mth.clamp(life * 6F, 0F, 1F) * (1F - life * life);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cameraPosition = camera.getPosition();
        float headX = (float) (Mth.lerp(partialTicks, xo, x) - cameraPosition.x());
        float headY = (float) (Mth.lerp(partialTicks, yo, y) - cameraPosition.y());
        float headZ = (float) (Mth.lerp(partialTicks, zo, z) - cameraPosition.z());
        Vector3f head = new Vector3f(headX, headY, headZ);
        Vector3f tail = new Vector3f(backward).mul(length).add(head);
        // Ribbon width perpendicular to both the streak and the view direction.
        Vector3f side = new Vector3f(backward).cross(new Vector3f(head).add(tail).mul(0.5F));
        if (side.lengthSquared() < 1.0E-8F) return;
        side.normalize().mul(width * 0.5F);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        int light = getLightColor(partialTicks);
        vertex(buffer, head.x + side.x, head.y + side.y, head.z + side.z, u0, v0, light);
        vertex(buffer, head.x - side.x, head.y - side.y, head.z - side.z, u0, v1, light);
        vertex(buffer, tail.x - side.x, tail.y - side.y, tail.z - side.z, u1, v1, light);
        vertex(buffer, tail.x + side.x, tail.y + side.y, tail.z + side.z, u1, v0, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double velocityX, double packed, double velocityZ) {
            int lifetime = (int) Math.floor(packed);
            float strength = (float) Mth.clamp(packed - lifetime, 0, 1);
            var particle = new VehicleSlipstreamParticle(level, x, y, z, new Vec3(velocityX, 0, velocityZ),
                    lifetime, strength);
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
