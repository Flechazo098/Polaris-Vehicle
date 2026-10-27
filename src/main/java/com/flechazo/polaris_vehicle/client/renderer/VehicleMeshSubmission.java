package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.compat.render.AcceleratedVehicleCompat;
import com.flechazo.polaris_vehicle.client.compat.render.ClientRenderCompatRegistry;
import com.flechazo.polaris_vehicle.client.model.PreparedVehicleMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;

import java.util.List;
import java.util.WeakHashMap;

public final class VehicleMeshSubmission {
    private record Entry(List<GeoCube> cubes, PreparedVehicleMesh mesh) {
    }

    private static final WeakHashMap<GeoBone, Entry> MESHES = new WeakHashMap<>();

    public static void render(PoseStack stack, GeoBone bone, VertexConsumer buffer, int light, int overlay, int color) {
        if (bone.isHidden() || bone.getCubes().isEmpty()) return;
        // Preserve AR's single per-bone cached submission, including its own
        // material/Iris processing. Do not split that mesh or prepare a second
        // copy on the CPU while the GPU path is already available.
        if (ClientRenderCompatRegistry.isActive(ClientRenderCompatRegistry.ACCELERATED_RENDERING)
                && AcceleratedVehicleCompat.render(stack, bone, buffer, light, overlay, color)) return;
        var entry = MESHES.get(bone);
        if (entry == null || !entry.cubes().equals(bone.getCubes())) {
            entry = new Entry(List.copyOf(bone.getCubes()), PreparedVehicleMesh.bake(bone));
            MESHES.put(bone, entry);
        }
        entry.mesh().render(stack.last().pose(), stack.last().normal(), buffer, light, overlay, color);
    }
}
