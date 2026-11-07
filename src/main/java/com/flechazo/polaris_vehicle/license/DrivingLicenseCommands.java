package com.flechazo.polaris_vehicle.license;

import com.flechazo.polaris_vehicle.PolarisVehicle;
import com.flechazo.polaris_vehicle.item.DrivingLicenseItem;
import com.flechazo.polaris_vehicle.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiFunction;

/** {@code /pid}: licence administration for operators, and {@code view} for everyone (chat links use it). */
@EventBusSubscriber(modid = PolarisVehicle.MODID)
public final class DrivingLicenseCommands {
    private static final SimpleCommandExceptionType UNKNOWN_GRADE =
            new SimpleCommandExceptionType(Component.translatable("command.polaris_vehicle.license.unknown_grade"));
    private static final SimpleCommandExceptionType BAD_DURATION =
            new SimpleCommandExceptionType(Component.translatable("command.polaris_vehicle.license.bad_duration"));
    private static final SimpleCommandExceptionType UNKNOWN_ID =
            new SimpleCommandExceptionType(Component.translatable("command.polaris_vehicle.license.unknown_id"));

    private DrivingLicenseCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pid")
                .then(Commands.literal("view")
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(DrivingLicenseCommands::view)))
                .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("grade", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(DrivingLicenseGrade.values())
                                                        .map(DrivingLicenseGrade::lowerName), builder))
                                        .executes(DrivingLicenseCommands::set))))
                .then(Commands.literal("license").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(DrivingLicenseCommands::give)))
                .then(Commands.literal("up").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(DrivingLicenseCommands::up)))
                .then(Commands.literal("revoke").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(DrivingLicenseCommands::revoke))))
                .then(Commands.literal("unrevoke").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(DrivingLicenseCommands::unrevoke))));
    }

    private static int view(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer viewer = context.getSource().getPlayerOrException();
        var license = DrivingLicenses.get(context.getSource().getServer())
                .findById(IntegerArgumentType.getInteger(context, "id"))
                .orElseThrow(UNKNOWN_ID::create);
        DrivingLicenseService.show(viewer, license);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        DrivingLicenseGrade grade = DrivingLicenseGrade.parse(StringArgumentType.getString(context, "grade"))
                .orElseThrow(UNKNOWN_GRADE::create);
        return forEach(context, (licenses, license) -> {
            license = licenses.setGrade(license, grade);
            return feedback(context, license, "command.polaris_vehicle.license.set",
                    "message.polaris_vehicle.license.grade_changed", grade.name());
        });
    }

    private static int up(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return forEach(context, (licenses, license) -> {
            var next = license.grade().next();
            if (next.isEmpty()) {
                context.getSource().sendFailure(Component.translatable(
                        "command.polaris_vehicle.license.already_top", license.name()));
                return false;
            }
            license = licenses.setGrade(license, next.get());
            return feedback(context, license, "command.polaris_vehicle.license.set",
                    "message.polaris_vehicle.license.grade_changed", next.get().name());
        });
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        long duration = DrivingLicenseService.parseDuration(StringArgumentType.getString(context, "duration"))
                .orElseThrow(BAD_DURATION::create);
        Component length = DrivingLicenseService.duration(duration);
        return forEach(context, (licenses, license) -> {
            license = licenses.revokeUntil(license, System.currentTimeMillis() + duration);
            return feedback(context, license, "command.polaris_vehicle.license.revoked",
                    "message.polaris_vehicle.license.revoked_notice", length);
        });
    }

    private static int unrevoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return forEach(context, (licenses, license) -> {
            if (!license.revoked()) {
                context.getSource().sendFailure(Component.translatable(
                        "command.polaris_vehicle.license.not_revoked", license.name()));
                return false;
            }
            license = licenses.revokeUntil(license, 0);
            return feedback(context, license, "command.polaris_vehicle.license.unrevoked",
                    "message.polaris_vehicle.license.unrevoked_notice");
        });
    }

    private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer receiver = context.getSource().getPlayerOrException();
        return forEach(context, (licenses, license) -> {
            ItemStack stack = new ItemStack(ModItems.DRIVING_LICENSE.get());
            DrivingLicenseItem.bind(stack, license);
            if (!receiver.getInventory().add(stack)) receiver.drop(stack, false);
            String name = license.name();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.polaris_vehicle.license.given", name), true);
            return true;
        });
    }

    /** Applies {@code action} to every target's licence, issuing one where needed. */
    private static int forEach(CommandContext<CommandSourceStack> context,
                               BiFunction<DrivingLicenses, DrivingLicenses.License, Boolean> action)
            throws CommandSyntaxException {
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(context, "targets");
        DrivingLicenses licenses = DrivingLicenses.get(context.getSource().getServer());
        int done = 0;
        for (GameProfile profile : targets) {
            if (action.apply(licenses, licenses.getOrCreate(profile.getId(), profile.getName()))) done++;
        }
        return done;
    }

    private static boolean feedback(CommandContext<CommandSourceStack> context, DrivingLicenses.License license,
                                    String sourceKey, String holderKey, Object... arguments) {
        Object[] withName = new Object[arguments.length + 1];
        withName[0] = license.name();
        System.arraycopy(arguments, 0, withName, 1, arguments.length);
        context.getSource().sendSuccess(() -> Component.translatable(sourceKey, withName), true);
        ServerPlayer holder = context.getSource().getServer().getPlayerList().getPlayer(license.owner());
        if (holder != null) holder.sendSystemMessage(Component.translatable(holderKey, arguments));
        return true;
    }
}
