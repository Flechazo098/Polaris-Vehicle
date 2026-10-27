package com.flechazo.polaris_vehicle.client.screen;

public final class VehicleManufacturingUiSpec {
    public static final int PREVIEW_X = 58;
    public static final int PREVIEW_Y = 34;
    public static final int PREVIEW_WIDTH = 102;
    public static final int PREVIEW_HEIGHT = 64;
    public static final int PREVIEW_MODEL_OFFSET_Y = 0;
    public static final int PREVIEW_HORIZONTAL_PADDING = 5;
    public static final int PREVIEW_VERTICAL_PADDING = 4;
    public static final float PREVIEW_MIN_PITCH = -55.0F;
    public static final float PREVIEW_MAX_PITCH = 55.0F;
    public static final float PREVIEW_MIN_ZOOM = 0.55F;
    public static final float PREVIEW_MAX_ZOOM = 1.65F;

    public static final int PAGE_X = 223;
    public static final int PAGE_Y = 22;
    public static final int PAGE_WIDTH = 101;
    public static final int PAGE_HEIGHT = 13;
    public static final int LEFT_BUTTON_X = 224;
    public static final int RIGHT_BUTTON_X = 308;
    public static final int PAGE_BUTTON_Y = 21;
    public static final int PAGE_BUTTON_SIZE = 15;

    public static final int LIST_X = 225;
    public static final int LIST_Y = 47;
    public static final int LIST_WIDTH = 88;
    public static final int LIST_HEIGHT = 100;
    public static final int LIST_COLUMNS = 4;
    public static final int LIST_SLOT_X = 227;
    public static final int LIST_SLOT_Y = 48;
    public static final int LIST_SLOT_SIZE = 17;
    public static final int LIST_COLUMN_STEP = 22;
    public static final int LIST_ROW_STEP = 20;
    public static final int COMPACT_ICON_SIZE = 11;
    public static final int COMPACT_ICON_OFFSET = 3;
    public static final int HOTBAR_ICON_SIZE = 15;
    public static final int HOTBAR_ICON_OFFSET = 1;

    public static final int SCROLL_TRACK_X = 315;
    public static final int SCROLL_TRACK_Y = 47;
    public static final int SCROLL_TRACK_WIDTH = 4;
    public static final int SCROLL_TRACK_HEIGHT = 101;
    public static final int SCROLL_THUMB_X = 313;
    public static final int SCROLL_THUMB_MIN_Y = 47;
    public static final int SCROLL_THUMB_MAX_Y = 124;
    public static final int SCROLL_THUMB_WIDTH = 8;
    public static final int SCROLL_THUMB_HEIGHT = 24;
    public static final int SCROLL_TRAVEL = SCROLL_THUMB_MAX_Y - SCROLL_THUMB_MIN_Y;

    public static final int MATERIAL_SLOT_Y = 166;
    public static final int[] MATERIAL_SLOT_X = {237, 265, 294};

    public static int rowCount(int recipeCount) {
        return Math.max(0, (recipeCount + LIST_COLUMNS - 1) / LIST_COLUMNS);
    }

    public static int maxScroll(int recipeCount) {
        int contentHeight = rowCount(recipeCount) * LIST_ROW_STEP;
        return Math.max(0, contentHeight - LIST_HEIGHT);
    }

    public static int thumbY(int scrollOffset, int maxScroll) {
        if (maxScroll <= 0) {
            return SCROLL_THUMB_MIN_Y;
        }
        double ratio = (double) Math.clamp(scrollOffset, 0, maxScroll) / maxScroll;
        return SCROLL_THUMB_MIN_Y + (int) Math.round(ratio * SCROLL_TRAVEL);
    }

    public static int scrollFromThumbY(double thumbY, int maxScroll) {
        if (maxScroll <= 0) {
            return 0;
        }
        double ratio = Math.clamp(
                (thumbY - SCROLL_THUMB_MIN_Y) / SCROLL_TRAVEL,
                0.0,
                1.0
        );
        return (int) Math.round(ratio * maxScroll);
    }

    public static int recipeIndexAt(
            double localX,
            double localY,
            int scrollOffset,
            int recipeCount
    ) {
        if (localX < LIST_X || localX >= LIST_X + LIST_WIDTH
                || localY < LIST_Y || localY >= LIST_Y + LIST_HEIGHT) {
            return -1;
        }
        double contentY = localY + scrollOffset;
        for (int index = 0; index < recipeCount; index++) {
            int column = index % LIST_COLUMNS;
            int row = index / LIST_COLUMNS;
            int slotX = LIST_SLOT_X + column * LIST_COLUMN_STEP;
            int slotY = LIST_SLOT_Y + row * LIST_ROW_STEP;
            if (localX >= slotX && localX < slotX + LIST_SLOT_SIZE
                    && contentY >= slotY && contentY < slotY + LIST_SLOT_SIZE) {
                return index;
            }
        }
        return -1;
    }

}
