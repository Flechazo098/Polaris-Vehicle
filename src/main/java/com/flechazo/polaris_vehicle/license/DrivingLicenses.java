package com.flechazo.polaris_vehicle.license;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Every player's driving licence, stored with the overworld. */
@EventBusSubscriber(modid = PolarisVehicle.MODID)
public final class DrivingLicenses extends SavedData {
    private static final String DATA_NAME = PolarisVehicle.MODID + "_driving_licenses";
    private static final SavedData.Factory<DrivingLicenses> FACTORY =
            new SavedData.Factory<>(DrivingLicenses::new, DrivingLicenses::load, null);
    private static final int MIN_ID = 10_000_000;
    private static final int ID_RANGE = 90_000_000;
    private static final RandomSource RANDOM = RandomSource.create();

    /**
     * @param activated      the holder has opened a licence bound to them; until then no vehicle starts
     * @param revokedUntil   wall-clock milliseconds; revoked while in the future
     */
    public record License(UUID owner, int id, String name, DrivingLicenseGrade grade, boolean activated,
                          long revokedUntil) {
        public boolean revoked() {
            return revokedUntil > System.currentTimeMillis();
        }

        public DrivingLicenseState state() {
            if (!activated) return DrivingLicenseState.INACTIVE;
            return revoked() ? DrivingLicenseState.REVOKED : DrivingLicenseState.ACTIVE;
        }

        License withName(String value) {
            return new License(owner, id, value, grade, activated, revokedUntil);
        }

        License withGrade(DrivingLicenseGrade value) {
            return new License(owner, id, name, value, activated, revokedUntil);
        }

        License activate() {
            return new License(owner, id, name, grade, true, revokedUntil);
        }

        License revokedUntil(long value) {
            return new License(owner, id, name, grade, activated, value);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Id", id);
            tag.putString("Name", name);
            tag.putString("Grade", grade.name());
            tag.putBoolean("Activated", activated);
            tag.putLong("RevokedUntil", revokedUntil);
            return tag;
        }

        static License load(UUID owner, CompoundTag tag) {
            return new License(owner, tag.getInt("Id"), tag.getString("Name"),
                    DrivingLicenseGrade.parse(tag.getString("Grade")).orElse(DrivingLicenseGrade.C),
                    tag.getBoolean("Activated"), tag.getLong("RevokedUntil"));
        }
    }

    private final Map<UUID, License> byOwner = new HashMap<>();
    private final Map<Integer, UUID> byId = new HashMap<>();

    public static DrivingLicenses get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<License> find(UUID owner) {
        return Optional.ofNullable(byOwner.get(owner));
    }

    public Optional<License> findById(int id) {
        return Optional.ofNullable(byId.get(id)).map(byOwner::get);
    }

    /** The holder's licence, issued at grade C with a fresh ID the first time it is needed. */
    public License getOrCreate(UUID owner, String name) {
        License license = byOwner.get(owner);
        if (license == null) {
            int id;
            do {
                id = MIN_ID + RANDOM.nextInt(ID_RANGE);
            } while (byId.containsKey(id));
            license = new License(owner, id, name, DrivingLicenseGrade.C, false, 0);
            put(license);
        } else if (!license.name().equals(name)) {
            license = license.withName(name);
            put(license);
        }
        return license;
    }

    public License activate(License license) {
        return license.activated() ? license : put(license.activate());
    }

    public License setGrade(License license, DrivingLicenseGrade grade) {
        return put(license.withGrade(grade));
    }

    public License revokeUntil(License license, long until) {
        return put(license.revokedUntil(until));
    }

    private License put(License license) {
        byOwner.put(license.owner(), license);
        byId.put(license.id(), license.owner());
        setDirty();
        return license;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        DrivingLicenses licenses = get(player.getServer());
        licenses.find(player.getUUID()).ifPresent(license ->
                licenses.getOrCreate(license.owner(), player.getGameProfile().getName()));
    }

    private static DrivingLicenses load(CompoundTag tag, HolderLookup.Provider registries) {
        DrivingLicenses licenses = new DrivingLicenses();
        CompoundTag owners = tag.getCompound("Licenses");
        for (String key : owners.getAllKeys()) {
            UUID owner;
            try {
                owner = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            License license = License.load(owner, owners.getCompound(key));
            licenses.byOwner.put(owner, license);
            licenses.byId.put(license.id(), owner);
        }
        return licenses;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag owners = new CompoundTag();
        byOwner.forEach((owner, license) -> owners.put(owner.toString(), license.save()));
        tag.put("Licenses", owners);
        return tag;
    }
}
