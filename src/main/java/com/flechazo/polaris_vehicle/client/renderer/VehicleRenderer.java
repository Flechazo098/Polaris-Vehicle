package com.flechazo.polaris_vehicle.client.renderer;

import com.flechazo.polaris_vehicle.client.model.*;
import com.flechazo.polaris_vehicle.config.VehicleClientConfig;
import com.flechazo.polaris_vehicle.registry.ModItems;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleMaintenanceState;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class VehicleRenderer<T extends AbstractVehicleEntity> extends GeoEntityRenderer<T> {
    private final VehicleModel<T> vehicleModel;
    private final Map<ResourceLocation, Map<ResourceLocation, TireRenderLayer<T>>> tireRenderLayers;
    private final TireRenderLayer<T> fallbackTires;
    private final Map<T, VehicleTireLayerSelection<TireRenderLayer<T>>> tireSelections = new WeakHashMap<>();
    private final Map<T, Integer> lodStates = new WeakHashMap<>();
    private final GeoEntityRenderer<T> controlRenderer;
    private final JackItemRenderer jackRenderer;

    public VehicleRenderer(EntityRendererProvider.Context context) {
        this(context, new VehicleModel<>());
    }

    VehicleRenderer(EntityRendererProvider.Context context, VehicleModel<T> vehicleModel) {
        super(context, vehicleModel);
        this.vehicleModel = vehicleModel;
        this.tireRenderLayers = createTireRenderLayers(context);
        var fallbackModel = new VehicleTireModel<T>();
        this.fallbackTires = new TireRenderLayer<>(fallbackModel,
                new VehicleComponentRenderer<>(context, fallbackModel, 2));
        this.controlRenderer = new VehicleComponentRenderer<>(
                context,
                new VehicleControlModel<>(),
                1
        );
        this.jackRenderer = new JackItemRenderer();
        shadowRadius = 1.25F;
    }

    @Override
    public void renderCubesOfBone(PoseStack stack, GeoBone bone,
                                  VertexConsumer buffer, int light, int overlay, int colour) {
        if (bone.isHidden()) return;
        if (bone.getCubes().isEmpty()) return;
        VehicleMeshSubmission.render(stack, bone, buffer, light, overlay, colour);
    }

    @Override
    public void render(
            T vehicle,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        var client = Minecraft.getInstance();
        var config = VehicleClientConfig.get();
        boolean fullDetail = vehicle.isClientGuiPreview() || !config.vehicleLodEnabled()
                || (client.player != null && vehicle.hasPassenger(client.player));
        double distanceSquared = client.gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(vehicle.getPosition(partialTick));
        int lod = VehicleLod.select(distanceSquared,
                lodStates.getOrDefault(vehicle, 0), fullDetail, config.vehicleLodDistanceScale());
        if (!vehicle.isClientGuiPreview()) lodStates.put(vehicle, lod);
        int previousLod = vehicleModel.lod();
        vehicleModel.setLod(lod);
        VehicleMaintenanceState maintenanceState = vehicle.maintenanceState();
        if (!vehicle.isClientGuiPreview()
                && maintenanceState == VehicleMaintenanceState.JACKED) {
            vehicle.jackAnchorPosition().ifPresent(anchor -> renderJack(
                    vehicle,
                    anchor,
                    partialTick,
                    poseStack,
                    bufferSource,
                    packedLight
            ));
        }
        poseStack.pushPose();
        try {
            Vec3 carriedOffset = vehicle.carriedRenderOffset(partialTick);
            poseStack.translate(carriedOffset.x, carriedOffset.y, carriedOffset.z);
            poseStack.mulPose(vehicle.vehicleBodyRenderRotation(partialTick));
            VehicleFirstPersonDriverBody.render(vehicle, poseStack, bufferSource, packedLight);
            super.render(vehicle, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            if (lod < 2) controlRenderer.render(
                    vehicle,
                    entityYaw,
                    partialTick,
                    poseStack,
                    bufferSource,
                    packedLight
            );
            var selection = tireSelections.get(vehicle);
            if (selection == null) {
                selection = new VehicleTireLayerSelection<>(tireRenderLayers, fallbackTires);
                tireSelections.put(vehicle, selection);
            }
            int presence = 0;
            for (int i = 0; i < VehicleTireLayerSelection.WHEEL_SLOTS.size(); i++) {
                if (vehicle.isWheelInstalled(VehicleTireLayerSelection.WHEEL_SLOTS.get(i))) presence |= 1 << i;
            }
            for (TireRenderLayer<T> layer : selection.select(vehicle.vehicleTypeId(), vehicle.vehicleData().parts(), presence)) {
                layer.renderer().render(
                        vehicle,
                        entityYaw,
                        partialTick,
                        poseStack,
                        bufferSource,
                        packedLight
                );
            }
        } finally {
            vehicleModel.setLod(previousLod);
            poseStack.popPose();
        }
    }

    private static <T extends AbstractVehicleEntity> Map<ResourceLocation, Map<ResourceLocation, TireRenderLayer<T>>>
    createTireRenderLayers(EntityRendererProvider.Context context) {
        Map<ResourceLocation, Map<ResourceLocation, TireRenderLayer<T>>> layers = new HashMap<>();
        int componentIndex = 3;
        for (VehicleTireAppearance appearance : VehicleTireAppearances.ALL) {
            VehicleTireModel<T> model = new VehicleTireModel<>(appearance);
            layers.computeIfAbsent(appearance.vehicleType(), ignored -> new HashMap<>())
                    .put(appearance.tireType(), new TireRenderLayer<>(
                            model,
                            new VehicleComponentRenderer<>(context, model, componentIndex++)
                    ));
        }
        layers.replaceAll((type, byTire) -> Map.copyOf(byTire));
        return Map.copyOf(layers);
    }

    private record TireRenderLayer<T extends AbstractVehicleEntity>(
            VehicleTireModel<T> model,
            GeoEntityRenderer<T> renderer
    ) {
    }

    private void renderJack(
            T vehicle,
            Vec3 anchor,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight
    ) {
        poseStack.pushPose();
        try {
            Vec3 relativeAnchor = anchor.subtract(vehicle.getPosition(partialTick));
            poseStack.translate(
                    relativeAnchor.x - 0.5,
                    relativeAnchor.y - 0.51,
                    relativeAnchor.z - 0.5
            );
            jackRenderer.renderPlaced(
                    new ItemStack(ModItems.PORTABLE_JACK.get()),
                    vehicle.jackLiftAnimationTick(partialTick),
                    poseStack,
                    bufferSource,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );
        } finally {
            poseStack.popPose();
        }
    }

    private static final class VehicleComponentRenderer<T extends AbstractVehicleEntity> extends GeoEntityRenderer<T> {
        @Override
        public void renderCubesOfBone(PoseStack stack, GeoBone bone,
                                      VertexConsumer buffer, int light, int overlay, int colour) {
            if (bone.isHidden()) return;
            if (bone.getCubes().isEmpty()) return;
            VehicleMeshSubmission.render(stack, bone, buffer, light, overlay, colour);
        }

        private final int componentIndex;

        VehicleComponentRenderer(
                EntityRendererProvider.Context context,
                GeoModel<T> model,
                int componentIndex
        ) {
            super(context, model);
            if (componentIndex <= 0) {
                throw new IllegalArgumentException("Component index must be positive");
            }
            this.componentIndex = componentIndex;
        }

        @Override
        public long getInstanceId(T vehicle) {
            return ((long) vehicle.getId() << 32) | Integer.toUnsignedLong(componentIndex);
        }
    }
}
