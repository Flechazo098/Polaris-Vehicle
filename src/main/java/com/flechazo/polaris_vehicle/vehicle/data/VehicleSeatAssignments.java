package com.flechazo.polaris_vehicle.vehicle.data;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.core.Pathway;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.*;

public record VehicleSeatAssignments(Map<UUID, Integer> assignments) {
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            value -> {
                try {
                    return DataResult.success(UUID.fromString(value));
                } catch (IllegalArgumentException exception) {
                    return DataResult.error(() -> "Invalid passenger UUID: " + value);
                }
            },
            UUID::toString
    );
    private static final Codec<Entry> ENTRY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("passenger").forGetter(Entry::passenger),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("seat").forGetter(Entry::seat)
    ).apply(instance, Entry::new));
    public static final Codec<VehicleSeatAssignments> CODEC = ENTRY_CODEC.listOf()
            .fieldOf("entries")
            .codec()
            .xmap(VehicleSeatAssignments::fromEntries, VehicleSeatAssignments::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleSeatAssignments> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VehicleSeatAssignments decode(RegistryFriendlyByteBuf buffer) {
                    int size = buffer.readVarInt();
                    Map<UUID, Integer> decoded = new LinkedHashMap<>(size);
                    for (int index = 0; index < size; index++) {
                        decoded.put(buffer.readUUID(), buffer.readVarInt());
                    }
                    return new VehicleSeatAssignments(decoded);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        VehicleSeatAssignments assignments
                ) {
                    List<Entry> entries = assignments.entries();
                    buffer.writeVarInt(entries.size());
                    for (Entry entry : entries) {
                        buffer.writeUUID(entry.passenger());
                        buffer.writeVarInt(entry.seat());
                    }
                }
            };

    public static VehicleSeatAssignments empty() {
        return new VehicleSeatAssignments(Map.of());
    }

    public Maybe<Integer> seatFor(UUID passenger) {
        return Maybe.ofNullable(assignments.get(passenger));
    }

    public VehicleSeatAssignments assign(UUID passenger, int seat) {
        if (seat < 0) {
            throw new IllegalArgumentException("Seat index may not be negative");
        }
        Map<UUID, Integer> updated = new LinkedHashMap<>(assignments);
        updated.entrySet().removeIf(entry -> entry.getValue() == seat);
        updated.put(passenger, seat);
        return new VehicleSeatAssignments(updated);
    }

    public VehicleSeatAssignments without(UUID passenger) {
        return Pathway.just(assignments)
                .filter(current -> current.containsKey(passenger))
                .map(current -> {
                    Map<UUID, Integer> updated = new LinkedHashMap<>(current);
                    updated.remove(passenger);
                    return new VehicleSeatAssignments(updated);
                })
                .getOrElse(this);
    }

    private List<Entry> entries() {
        return assignments.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(entry -> entry.passenger().toString()))
                .toList();
    }

    private static VehicleSeatAssignments fromEntries(List<Entry> entries) {
        return new VehicleSeatAssignments(Pathway.listPath(entries)
                .foldLeft(new LinkedHashMap<UUID, Integer>(), (assignments, entry) -> {
                    assignments.put(entry.passenger(), entry.seat());
                    return assignments;
                }));
    }

    private record Entry(UUID passenger, int seat) {
    }
}
