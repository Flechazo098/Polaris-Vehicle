package com.flechazo.polaris_vehicle.client.renderer;

public final class ItemPreviewRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    public static void render(Runnable renderCall) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            renderCall.run();
        } finally {
            int remaining = DEPTH.get() - 1;
            if (remaining == 0) {
                DEPTH.remove();
            } else {
                DEPTH.set(remaining);
            }
        }
    }
}
