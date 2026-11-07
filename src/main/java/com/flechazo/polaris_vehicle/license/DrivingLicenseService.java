package com.flechazo.polaris_vehicle.license;

import com.flechazo.polaris_vehicle.config.VehicleLicenseConfig;
import com.flechazo.polaris_vehicle.network.DrivingLicenseViewPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DrivingLicenseService {
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([dhms])");
    private static final long SECOND = 1000L, MINUTE = 60 * SECOND, HOUR = 60 * MINUTE, DAY = 24 * HOUR;

    private DrivingLicenseService() {
    }

    public static DrivingLicenses licenses(ServerPlayer player) {
        return DrivingLicenses.get(player.server);
    }

    /** Shows {@code license} to {@code viewer}. */
    public static void show(ServerPlayer viewer, DrivingLicenses.License license) {
        new DrivingLicenseViewPacket(license.owner(), license.name(), license.id(), license.grade(), license.state())
                .sendTo(viewer);
    }

    /** Why {@code driver} may not start a {@code vehicleType} engine, if they may not. */
    public static Optional<Component> startRefusal(ServerPlayer driver, ResourceLocation vehicleType) {
        VehicleLicenseConfig config = VehicleLicenseConfig.get();
        if (!config.enabled()) return Optional.empty();
        var license = licenses(driver).find(driver.getUUID()).filter(DrivingLicenses.License::activated);
        if (license.isEmpty()) {
            return Optional.of(Component.translatable("message.polaris_vehicle.license.not_activated"));
        }
        if (license.get().revoked()) {
            return Optional.of(Component.translatable("message.polaris_vehicle.license.revoked",
                    duration(license.get().revokedUntil() - System.currentTimeMillis())));
        }
        DrivingLicenseGrade required = config.requiredGrade(vehicleType);
        if (!license.get().grade().allows(required)) {
            return Optional.of(Component.translatable("message.polaris_vehicle.license.grade_too_low",
                    required.name(), license.get().grade().name()));
        }
        return Optional.empty();
    }

    /** Posts {@code license} to chat as {@code sender}; clicking it opens the card. */
    public static void share(ServerPlayer sender, DrivingLicenses.License license) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("item.polaris_vehicle.driving_license").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.polaris_vehicle.driving_license.holder", license.name())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.polaris_vehicle.driving_license.grade", gradeText(license))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.polaris_vehicle.driving_license.id", license.id())
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("chat.polaris_vehicle.license.click").withStyle(ChatFormatting.DARK_GRAY));
        MutableComponent hover = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) hover.append("\n");
            hover.append(lines.get(i));
        }
        Component link = Component.translatable("chat.polaris_vehicle.license.link").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/pid view " + license.id())));
        sender.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("chat.type.text", sender.getDisplayName(), link), false);
    }

    public static Component gradeText(DrivingLicenses.License license) {
        return switch (license.state()) {
            case ACTIVE -> Component.literal(license.grade().name()).withStyle(ChatFormatting.GOLD);
            case REVOKED -> Component.translatable("tooltip.polaris_vehicle.driving_license.revoked")
                    .withStyle(ChatFormatting.RED);
            case INACTIVE -> Component.translatable("tooltip.polaris_vehicle.driving_license.inactive")
                    .withStyle(ChatFormatting.DARK_GRAY);
        };
    }

    /** Parses durations such as {@code 30m}, {@code 2h} or {@code 1d12h}; empty when malformed or zero. */
    public static Optional<Long> parseDuration(String text) {
        Matcher matcher = DURATION_PART.matcher(text.toLowerCase(java.util.Locale.ROOT));
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) return Optional.empty();
            end = matcher.end();
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException tooLong) {
                return Optional.empty();
            }
            long unit = switch (matcher.group(2)) {
                case "d" -> DAY;
                case "h" -> HOUR;
                case "m" -> MINUTE;
                default -> SECOND;
            };
            if (amount > Long.MAX_VALUE / unit / 4) return Optional.empty();
            total += amount * unit;
        }
        return end == text.length() && total > 0 ? Optional.of(total) : Optional.empty();
    }

    public static Component duration(long millis) {
        long seconds = Math.max(1, (millis + SECOND - 1) / SECOND);
        long[] parts = {seconds / 86400, seconds / 3600 % 24, seconds / 60 % 60, seconds % 60};
        String[] keys = {"d", "h", "m", "s"};
        MutableComponent text = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] > 0) text.append(Component.translatable("time.polaris_vehicle." + keys[i], parts[i]));
        }
        return text;
    }
}
