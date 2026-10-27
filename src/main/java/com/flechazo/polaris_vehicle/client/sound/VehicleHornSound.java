package com.flechazo.polaris_vehicle.client.sound;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.client.VehicleKeyMappings;
import com.flechazo.polaris_vehicle.mixin.client.SoundEngineAccessor;
import com.flechazo.polaris_vehicle.mixin.client.SoundManagerAccessor;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleDefinitions;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Electric dual-tone horns. Each file is the seamless sustain of one supplied
 * synthesized horn; its two tones sit roughly a minor third apart, and every
 * maker's pair is a slightly different dissonance. The convertible, rally car
 * and SUV use alarm2, the electric small cars their own "alarm elect" horn and
 * the bus alarm1_2, a deep air horn. Karts and formula cars carry no horn.
 *
 * <p>Horns supplied as a phrase (start / sustain loop / tail) play through
 * {@link VehicleHornPhrase}: the attack, the sustain for as long as H is held,
 * then the horn's own ending.</p>
 */
@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleHornSound {
    /**
     * alarm2 (1): 362 + 440 Hz, for the convertible, rally car and SUV.
     */
    private static final ResourceLocation HIGH = PolarisVehicle.id("vehicle.horn.high");
    /**
     * alarm elect: about 377 + 483 Hz, for the electric elderly car, three-wheeler and two-seater.
     */
    private static final ResourceLocation ELECTRIC = PolarisVehicle.id("vehicle.horn.electric");
    /**
     * alarm1_2: about 150 + 191 Hz with strong harmonics, a deep air horn for the bus.
     */
    private static final ResourceLocation BUS = PolarisVehicle.id("vehicle.horn.bus");
    private static final Map<ResourceLocation, ResourceLocation> BY_VEHICLE = Map.of(
            VehicleDefinitions.ELDERLY_CAR_ID, ELECTRIC,
            VehicleDefinitions.SMALL_CAR_ID, ELECTRIC,
            VehicleDefinitions.TWO_SEAT_CAR_ID, ELECTRIC,
            VehicleDefinitions.CONVERTIBLE_ID, HIGH,
            VehicleDefinitions.RALLY_CAR_ID, HIGH,
            VehicleDefinitions.SUV_ID, HIGH,
            VehicleDefinitions.SMALL_BUS_ID, BUS
    );

    static final Map<ResourceLocation, String> PHRASES = Map.of(HIGH, "high", ELECTRIC, "electric", BUS, "bus");
    private static final Map<UUID, Instance> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    static ResourceLocation soundFor(ResourceLocation vehicleType) {
        return BY_VEHICLE.get(vehicleType);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level != activeLevel) {
            ACTIVE.values().forEach(Instance::finish);
            ACTIVE.clear();
            activeLevel = level;
        }
        if (level == null) return;
        var soundManager = client.getSoundManager();
        ACTIVE.values().removeIf(horn -> horn.isStopped() || !soundManager.isActive(horn));
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof AbstractVehicleEntity vehicle && !vehicle.isRemoved() && sounding(vehicle)) {
                start(vehicle);
            }
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (VehicleKeyMappings.HORN.matches(event.getKey(), event.getScanCode())) followLocalDriver();
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Post event) {
        if (VehicleKeyMappings.HORN.matchesMouse(event.getButton())) followLocalDriver();
    }

    /**
     * Every frame: catch any key change the input events missed, and top up the
     * horn channels' short queues, which the sound engine otherwise only
     * refills once per game tick.
     */
    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (ACTIVE.isEmpty() && !VehicleKeyMappings.HORN.isDown()) return;
        followLocalDriver();
        var engine = ((SoundManagerAccessor) Minecraft.getInstance().getSoundManager()).polaris$soundEngine();
        var channels = ((SoundEngineAccessor) engine).polaris$instanceToChannel();
        for (Instance horn : ACTIVE.values()) {
            var channel = channels.get(horn);
            if (channel != null && horn.phrase != null) channel.execute(Channel::updateStream);
        }
    }

    private static void followLocalDriver() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.level != activeLevel
                || !(client.player.getVehicle() instanceof AbstractVehicleEntity vehicle)
                || !vehicle.isDriver(client.player)) return;
        boolean on = sounding(vehicle);
        Instance current = ACTIVE.get(vehicle.getUUID());
        if (current != null && !current.releasing()) {
            current.held = on;
        } else if (on) {
            start(vehicle);
        }
    }

    private static void start(AbstractVehicleEntity vehicle) {
        Instance current = ACTIVE.get(vehicle.getUUID());
        if (current != null && !current.releasing()) return;
        Instance horn = new Instance(vehicle);
        ACTIVE.put(vehicle.getUUID(), horn);
        Minecraft.getInstance().getSoundManager().play(horn);
    }

    private static boolean sounding(AbstractVehicleEntity vehicle) {
        if (soundFor(vehicle.vehicleTypeId()) == null) return false;
        Minecraft client = Minecraft.getInstance();
        if (vehicle.isDriver(client.player)) {
            return client.screen == null && VehicleKeyMappings.HORN.isDown();
        }
        return vehicle.hornActive();
    }

    private static final class Instance extends AbstractTickableSoundInstance implements VehicleEnginePcmSound {
        private static final int MINIMUM_TICKS = 4;
        private static final float RELEASE_PER_TICK = 0.34F;
        private final AbstractVehicleEntity vehicle;
        private final String phrase;
        private volatile boolean held = true;
        private volatile VehicleHornPcmStream stream;
        private int age;

        Instance(AbstractVehicleEntity vehicle) {
            super(SoundEvent.createVariableRangeEvent(soundFor(vehicle.vehicleTypeId())),
                    SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.phrase = PHRASES.get(soundFor(vehicle.vehicleTypeId()));
            looping = phrase == null;
            delay = 0;
            volume = 1.0F;
            pitch = 1.0F;
            attenuation = Attenuation.LINEAR;
            updatePosition();
        }

        @Override
        public boolean usesEnginePcmStream() {
            return phrase != null;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(
                SoundBufferLibrary buffers,
                Sound sound, boolean looping) {
            if (phrase == null) return super.getStream(buffers, sound, looping);
            return VehicleHornPcmBank.INSTANCE.get(phrase).<AudioStream>thenApply(parts -> {
                var created = new VehicleHornPcmStream(parts.play(), parts.sampleRate(), () -> held);
                stream = created;
                return created;
            }).whenComplete((created, failure) -> {
                if (failure != null) stop();
            });
        }

        boolean releasing() {
            var current = stream;
            return phrase != null && current != null && current.phrase().releasing();
        }

        @Override
        public void tick() {
            if (vehicle.isRemoved()) {
                stop();
                return;
            }
            updatePosition();
            if (phrase != null) {
                held = sounding(vehicle);
                return;
            }
            age++;
            if (age < MINIMUM_TICKS || sounding(vehicle)) {
                volume = 1.0F;
                return;
            }
            volume -= RELEASE_PER_TICK;
            if (volume <= 0.0F) {
                volume = 0.0F;
                stop();
            }
        }

        void finish() {
            stop();
        }

        private void updatePosition() {
            x = vehicle.getX();
            y = vehicle.getY() + vehicle.getBbHeight() * 0.45;
            z = vehicle.getZ();
        }
    }
}
