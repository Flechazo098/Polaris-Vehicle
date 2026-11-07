package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.network.DrivingLicenseViewPacket;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** The licence card, drawn pixel-for-pixel from its 160x94 artwork at the largest whole scale that fits. */
public final class DrivingLicenseScreen extends Screen {
    private static final int WIDTH = 160, HEIGHT = 94;
    private static final ResourceLocation CARD = texture("card");
    private static final ResourceLocation REVOKED = texture("revoked");
    private static final ResourceLocation INACTIVE = texture("inactive");
    private static final ResourceLocation[] DIGITS = new ResourceLocation[10];
    // Card-pixel layout of the artwork.
    private static final int PORTRAIT_LEFT = 11, PORTRAIT_TOP = 23, PORTRAIT_RIGHT = 48, PORTRAIT_BOTTOM = 72;
    private static final int NAME_LEFT = 9, NAME_TOP = 77, NAME_WIDTH = 41, NAME_HEIGHT = 9;
    private static final int GRADE_X = 73, GRADE_Y = 15, GRADE_SIZE = 48;
    private static final int ID_X = 75, ID_Y = 81, DIGIT_WIDTH = 4, DIGIT_HEIGHT = 6, DIGIT_PITCH = 5;
    private static final int NAME_COLOUR = 0xFF4A3B33;
    private static final int NAME_CAP_HEIGHT = 6, NAME_PADDING = 1;
    private static final float NAME_MIN_SIZE = 0.6F;
    private static final double MARQUEE_HOLD_SECONDS = 1.2, MARQUEE_SPEED = 12.0;

    static {
        for (int digit = 0; digit < DIGITS.length; digit++) DIGITS[digit] = texture("digit_" + digit);
    }

    private final DrivingLicenseViewPacket license;
    private final long openedAt = System.nanoTime();
    private LivingEntity portrait;
    private int scale, left, top;

    private DrivingLicenseScreen(DrivingLicenseViewPacket license) {
        super(Component.translatable("item.polaris_vehicle.driving_license"));
        this.license = license;
    }

    public static void open(DrivingLicenseViewPacket license) {
        Minecraft.getInstance().setScreen(new DrivingLicenseScreen(license));
    }

    private static ResourceLocation texture(String name) {
        return PolarisVehicle.id("textures/gui/driving_license/" + name + ".png");
    }

    @Override
    protected void init() {
        scale = Math.max(1, Math.min((width - 16) / WIDTH, (height - 16) / HEIGHT));
        left = (width - WIDTH * scale) / 2;
        top = (height - HEIGHT * scale) / 2;
        if (portrait == null && minecraft != null && minecraft.level != null) {
            // The holder as seen in the inventory; a stand-in keeps their skin when they are not nearby.
            var nearby = minecraft.level.getPlayerByUUID(license.owner());
            portrait = nearby != null ? nearby
                    : new RemotePlayer(minecraft.level, new GameProfile(license.owner(), license.name()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(scale, scale, 1);
        graphics.blit(CARD, 0, 0, 0, 0, WIDTH, HEIGHT, WIDTH, HEIGHT);
        pose.popPose();

        if (portrait != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics,
                    left + PORTRAIT_LEFT * scale, top + PORTRAIT_TOP * scale,
                    left + PORTRAIT_RIGHT * scale, top + PORTRAIT_BOTTOM * scale,
                    21 * scale, 0.0625F, mouseX, mouseY, portrait);
        }

        pose.pushPose();
        pose.translate(left, top, 200);
        pose.scale(scale, scale, 1);
        ResourceLocation grade = switch (license.state()) {
            case ACTIVE -> texture("grade_" + license.grade().lowerName());
            case REVOKED -> REVOKED;
            case INACTIVE -> INACTIVE;
        };
        graphics.blit(grade, GRADE_X, GRADE_Y, 0, 0, GRADE_SIZE, GRADE_SIZE, GRADE_SIZE, GRADE_SIZE);
        String id = String.format("%08d", license.id());
        for (int i = 0; i < id.length(); i++) {
            graphics.blit(DIGITS[id.charAt(i) - '0'], ID_X + i * DIGIT_PITCH, ID_Y, 0, 0,
                    DIGIT_WIDTH, DIGIT_HEIGHT, DIGIT_WIDTH, DIGIT_HEIGHT);
        }
        pose.popPose();
        renderName(graphics);
    }

    /**
     * Draws the holder's name with every glyph pixel on a whole screen pixel, capitals no taller than the ID digits.
     * Long names step down a pixel at a time to a floor, and names that still overflow scroll back and forth.
     */
    private void renderName(GuiGraphics graphics) {
        String name = license.name();
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        double cardPixel = scale * guiScale;
        int textWidth = Math.max(1, font.width(name));
        int barLeft = left + (NAME_LEFT + NAME_PADDING) * scale;
        int barRight = left + (NAME_LEFT + NAME_WIDTH - NAME_PADDING) * scale;
        int barTop = top + NAME_TOP * scale;
        int barBottom = barTop + NAME_HEIGHT * scale;
        int room = (int) Math.round((barRight - barLeft) * guiScale);
        int largest = Math.max(1, (int) Math.floor(cardPixel * NAME_CAP_HEIGHT / 7.0));
        int smallest = Math.max(1, Math.round(largest * NAME_MIN_SIZE));
        int glyph = Math.clamp(room / textWidth, smallest, largest);
        int drawnWidth = textWidth * glyph;
        int x = drawnWidth <= room ? (room - drawnWidth) / 2 : -marqueeOffset(drawnWidth - room, cardPixel);
        int y = (int) Math.round((barBottom - barTop) * guiScale - 7 * glyph) / 2;

        graphics.enableScissor(barLeft, barTop, barRight, barBottom);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(barLeft + x / guiScale, barTop + y / guiScale, 200);
        pose.scale((float) (glyph / guiScale), (float) (glyph / guiScale), 1);
        graphics.drawString(font, name, 0, 0, NAME_COLOUR, false);
        pose.popPose();
        graphics.disableScissor();
    }

    private int marqueeOffset(int overflow, double cardPixel) {
        double travel = overflow / (MARQUEE_SPEED * cardPixel);
        double cycle = 2.0 * (MARQUEE_HOLD_SECONDS + travel);
        double t = ((System.nanoTime() - openedAt) / 1.0E9) % cycle;
        double progress;
        if (t < MARQUEE_HOLD_SECONDS) {
            progress = 0.0;
        } else if (t < MARQUEE_HOLD_SECONDS + travel) {
            progress = smoothstep((t - MARQUEE_HOLD_SECONDS) / travel);
        } else if (t < 2.0 * MARQUEE_HOLD_SECONDS + travel) {
            progress = 1.0;
        } else {
            progress = 1.0 - smoothstep((t - 2.0 * MARQUEE_HOLD_SECONDS - travel) / travel);
        }
        return (int) Math.round(overflow * progress);
    }

    private static double smoothstep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
