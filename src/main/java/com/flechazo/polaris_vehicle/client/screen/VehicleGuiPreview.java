package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.hkt.Maybe;
import com.flechazo.polaris_vehicle.client.renderer.ItemPreviewRenderContext;
import com.flechazo.polaris_vehicle.item.CrewDollItem;
import com.flechazo.polaris_vehicle.registry.ModEntityTypes;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleCollisionModel;
import com.flechazo.polaris_vehicle.vehicle.collision.VehicleStructureModelManager;
import com.flechazo.polaris_vehicle.vehicle.data.VehicleInstanceData;
import com.flechazo.polaris_vehicle.vehicle.entity.AbstractVehicleEntity;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

final class VehicleGuiPreview {
    private static final float INITIAL_YAW = 135.0F;
    private static final float INITIAL_PITCH = 12.0F;
    private static final float YAW_ROTATION_PER_PIXEL = 2.5F;
    private static final float PITCH_ROTATION_PER_PIXEL = 2.0F;
    private static final float ZOOM_PER_STEP = 0.10F;
    private static final float RENDER_Z = 500.0F;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int DEPTH_BUFFER_BIT = 0x00000100;

    private final Spec spec;
    private Maybe<ResourceLocation> vehicleType = Maybe.none();
    private Maybe<AbstractVehicleEntity> entity = Maybe.none();
    private ItemStack item = ItemStack.EMPTY;
    private float yaw = INITIAL_YAW;
    private float pitch = INITIAL_PITCH;
    private float zoom = 1.0F;
    private boolean dragging;

    VehicleGuiPreview(Spec spec) {
        this.spec = spec;
    }

    static VehicleGuiPreview manufacturing() {
        return new VehicleGuiPreview(manufacturingSpec());
    }

    static Spec manufacturingSpec() {
        return new Spec(
                VehicleManufacturingUiSpec.PREVIEW_X,
                VehicleManufacturingUiSpec.PREVIEW_Y,
                VehicleManufacturingUiSpec.PREVIEW_WIDTH,
                VehicleManufacturingUiSpec.PREVIEW_HEIGHT,
                VehicleManufacturingUiSpec.PREVIEW_MODEL_OFFSET_Y,
                VehicleManufacturingUiSpec.PREVIEW_HORIZONTAL_PADDING,
                VehicleManufacturingUiSpec.PREVIEW_VERTICAL_PADDING,
                0.80F,
                VehicleManufacturingUiSpec.PREVIEW_MIN_PITCH,
                VehicleManufacturingUiSpec.PREVIEW_MAX_PITCH,
                VehicleManufacturingUiSpec.PREVIEW_MIN_ZOOM,
                VehicleManufacturingUiSpec.PREVIEW_MAX_ZOOM
        );
    }

    static VehicleGuiPreview assembly() {
        return new VehicleGuiPreview(new Spec(
                58,
                17,
                137,
                109,
                -3,
                6,
                5,
                0.78F,
                -55.0F,
                55.0F,
                0.55F,
                1.65F
        ));
    }

    void select(Maybe<ResourceLocation> requestedType, Level level) {
        if (sameType(vehicleType, requestedType)) {
            return;
        }
        vehicleType = requestedType;
        item = ItemStack.EMPTY;
        entity = requestedType
                .flatMap(type -> ModEntityTypes.create(type, level))
                .map(vehicle -> {
                    vehicle.markAsClientGuiPreview();
                    return vehicle;
                });
        resetView();
        dragging = false;
    }

    void selectItem(ItemStack requestedItem) {
        ItemStack normalized = requestedItem.copyWithCount(1);
        if (entity.isEmpty() && ItemStack.isSameItemSameComponents(item, normalized)) {
            return;
        }
        vehicleType = Maybe.none();
        entity = Maybe.none();
        item = normalized;
        resetView();
        dragging = false;
    }

    void update(VehicleInstanceData data, Level level) {
        select(Maybe.some(data.vehicleType()), level);
        entity.ifPresent(vehicle -> vehicle.applyNetworkData(data));
    }

    void clear() {
        vehicleType = Maybe.none();
        entity = Maybe.none();
        item = ItemStack.EMPTY;
        dragging = false;
    }

    boolean beginDrag(double localX, double localY, int button) {
        if (button != 0 || !hasPreview() || !spec.contains(localX, localY)) {
            return false;
        }
        dragging = true;
        return true;
    }

    boolean drag(int button, double dragX, double dragY) {
        if (button != 0 || !dragging) {
            return false;
        }
        yaw += (float) dragX * YAW_ROTATION_PER_PIXEL;
        if (Math.abs(yaw) >= 3600.0F) {
            yaw %= 360.0F;
        }
        float pitchDelta = (float) dragY * PITCH_ROTATION_PER_PIXEL;
        pitch = spec.clampPitch(pitch + (entity.isEmpty() ? pitchDelta : -pitchDelta));
        return true;
    }

    boolean endDrag(int button) {
        if (button != 0 || !dragging) {
            return false;
        }
        dragging = false;
        return true;
    }

    boolean scroll(double localX, double localY, double amount) {
        if (amount == 0.0 || !hasPreview() || !spec.contains(localX, localY)) {
            return false;
        }
        zoom = spec.clampZoom(zoom + (float) amount * ZOOM_PER_STEP);
        return true;
    }

    void render(GuiGraphics graphics, int screenLeft, int screenTop, float partialTick) {
        entity.ifPresent(vehicle -> {
            int left = screenLeft + spec.x();
            int top = screenTop + spec.y();
            graphics.flush();
            graphics.enableScissor(left, top, left + spec.width(), top + spec.height());
            try {
                renderVehicle(graphics, left, top, vehicle, partialTick);
            } finally {
                graphics.disableScissor();
            }
        });
        if (entity.isEmpty() && !item.isEmpty()) {
            int left = screenLeft + spec.x();
            int top = screenTop + spec.y();
            graphics.flush();
            graphics.enableScissor(left, top, left + spec.width(), top + spec.height());
            try {
                renderItem(graphics, left, top, item, partialTick);
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private boolean hasPreview() {
        return entity.isDefined() || !item.isEmpty();
    }

    private void renderItem(
            GuiGraphics graphics,
            int left,
            int top,
            ItemStack stack,
            float partialTick
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        try {
            RenderSystem.clear(DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            poseStack.translate(
                    left + spec.width() / 2.0F,
                    top + spec.height() / 2.0F + spec.modelOffsetY(),
                    RENDER_Z
            );
            float scale = Math.min(spec.width(), spec.height()) * 0.48F
                    * spec.defaultScale() * zoom;
            poseStack.scale(scale, -scale, scale);
            poseStack.mulPose(new Quaternionf()
                    .rotateX((float) Math.toRadians(pitch))
                    .rotateY((float) Math.toRadians(yaw)));
            Lighting.setupForEntityInInventory();
            ItemPreviewRenderContext.render(() -> minecraft.getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    graphics.bufferSource(),
                    minecraft.level,
                    0
            ));
            graphics.flush();
        } finally {
            RenderSystem.clear(DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            poseStack.popPose();
            Lighting.setupFor3DItems();
        }
    }

    private void renderVehicle(
            GuiGraphics graphics,
            int left,
            int top,
            AbstractVehicleEntity vehicle,
            float partialTick
    ) {
        AABB bounds = modelBounds(vehicle);
        float fitScale = spec.fitScale(
                bounds.getXsize(),
                bounds.getYsize(),
                bounds.getZsize()
        );
        renderEntity(
                graphics,
                left + spec.width() / 2.0F,
                top + spec.height() / 2.0F + spec.modelOffsetY(),
                fitScale * spec.defaultScale() * zoom,
                bounds,
                vehicle,
                partialTick
        );
    }

    private void renderEntity(
            GuiGraphics graphics,
            float centerX,
            float centerY,
            float scale,
            AABB bounds,
            AbstractVehicleEntity vehicle,
            float partialTick
    ) {
        PoseStack poseStack = graphics.pose();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf previousCamera = new Quaternionf(dispatcher.cameraOrientation());
        poseStack.pushPose();
        try {
            RenderSystem.clear(DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            poseStack.translate(centerX, centerY, RENDER_Z);
            poseStack.scale(scale, scale, -scale);
            poseStack.mulPose(new Quaternionf()
                    .rotateZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(pitch))
                    .rotateY((float) Math.toRadians(yaw)));
            poseStack.translate(
                    -(bounds.minX + bounds.maxX) * 0.5,
                    -(bounds.minY + bounds.maxY) * 0.5,
                    -(bounds.minZ + bounds.maxZ) * 0.5
            );
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(
                    vehicle,
                    0.0,
                    0.0,
                    0.0,
                    0.0F,
                    partialTick,
                    poseStack,
                    graphics.bufferSource(),
                    FULL_BRIGHT
            ));
            graphics.flush();
        } finally {
            RenderSystem.clear(DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            dispatcher.setRenderShadow(true);
            dispatcher.overrideCameraOrientation(previousCamera);
            poseStack.popPose();
            Lighting.setupFor3DItems();
        }
    }

    private static AABB modelBounds(AbstractVehicleEntity vehicle) {
        return VehicleStructureModelManager.CLIENT.find(vehicle.vehicleTypeId())
                .map(VehicleCollisionModel::localBounds)
                .fold(() -> fallbackBounds(vehicle), bounds -> bounds);
    }

    private static AABB fallbackBounds(AbstractVehicleEntity vehicle) {
        double halfWidth = Math.max(0.5, vehicle.getBbWidth() * 0.5);
        return new AABB(
                -halfWidth,
                0.0,
                -halfWidth,
                halfWidth,
                Math.max(0.5, vehicle.getBbHeight()),
                halfWidth
        );
    }

    private static boolean sameType(
            Maybe<ResourceLocation> first,
            Maybe<ResourceLocation> second
    ) {
        return first.fold(
                second::isEmpty,
                firstType -> second.fold(() -> false, firstType::equals)
        );
    }

    private void resetView() {
        yaw = item.getItem() instanceof CrewDollItem
                ? 0 : INITIAL_YAW;
        pitch = INITIAL_PITCH;
        zoom = 1.0F;
    }

    record Spec(
            int x,
            int y,
            int width,
            int height,
            int modelOffsetY,
            int horizontalPadding,
            int verticalPadding,
            float defaultScale,
            float minimumPitch,
            float maximumPitch,
            float minimumZoom,
            float maximumZoom
    ) {
        Spec {
            if (width <= 0 || height <= 0
                    || horizontalPadding < 0 || verticalPadding < 0
                    || defaultScale <= 0.0F
                    || minimumPitch > maximumPitch
                    || minimumZoom <= 0.0F
                    || minimumZoom > maximumZoom) {
                throw new IllegalArgumentException("Invalid vehicle preview specification");
            }
        }

        boolean contains(double localX, double localY) {
            return localX >= x && localX < x + width
                    && localY >= y && localY < y + height;
        }

        float clampPitch(float pitch) {
            return Math.clamp(pitch, minimumPitch, maximumPitch);
        }

        float clampZoom(float zoom) {
            return Math.clamp(zoom, minimumZoom, maximumZoom);
        }

        float fitScale(double modelWidth, double modelHeight, double modelDepth) {
            double horizontalDiameter = Math.hypot(
                    Math.max(1.0E-4, modelWidth),
                    Math.max(1.0E-4, modelDepth)
            );
            return (float) Math.min(
                    (width - horizontalPadding * 2.0) / horizontalDiameter,
                    (height - verticalPadding * 2.0) / Math.max(1.0E-4, modelHeight)
            );
        }
    }
}
