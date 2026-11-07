package com.flechazo.polaris_vehicle.item;

import com.flechazo.polaris_vehicle.license.DrivingLicenseService;
import com.flechazo.polaris_vehicle.license.DrivingLicenses;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A blank licence binds to the first player who opens it; a bound one shows
 * its holder's licence to whoever opens it. Sneak-use shares it to chat.
 */
public final class DrivingLicenseItem extends StaticGeoItem {
    private static final String TAG = "DrivingLicense";
    private static final int SHARE_COOLDOWN_TICKS = 60;

    public record Binding(UUID owner, String name, int id) {
    }

    public DrivingLicenseItem(Properties properties) {
        super(properties, "driving_license_model");
    }

    public static Optional<Binding> binding(ItemStack stack) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(TAG)) return Optional.empty();
        CompoundTag tag = root.getCompound(TAG);
        if (!tag.hasUUID("Owner")) return Optional.empty();
        return Optional.of(new Binding(tag.getUUID("Owner"), tag.getString("Name"), tag.getInt("Id")));
    }

    public static void bind(ItemStack stack, DrivingLicenses.License license) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", license.owner());
        tag.putString("Name", license.name());
        tag.putInt("Id", license.id());
        root.put(TAG, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        DrivingLicenses licenses = DrivingLicenseService.licenses(serverPlayer);
        var binding = binding(stack);
        DrivingLicenses.License license;
        if (binding.isEmpty()) {
            license = licenses.getOrCreate(player.getUUID(), player.getGameProfile().getName());
            bind(stack, license);
        } else {
            license = licenses.find(binding.get().owner())
                    .orElseGet(() -> licenses.getOrCreate(binding.get().owner(), binding.get().name()));
        }
        if (license.owner().equals(player.getUUID())) {
            license = licenses.activate(license);
            if (!license.name().equals(binding(stack).map(Binding::name).orElse(""))) bind(stack, license);
        }
        if (binding.isPresent() && player.isSecondaryUseActive()) {
            if (!player.getCooldowns().isOnCooldown(this)) {
                player.getCooldowns().addCooldown(this, SHARE_COOLDOWN_TICKS);
                DrivingLicenseService.share(serverPlayer, license);
            }
        } else {
            DrivingLicenseService.show(serverPlayer, license);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        binding(stack).ifPresentOrElse(binding -> {
            tooltip.add(Component.translatable("tooltip.polaris_vehicle.driving_license.holder", binding.name())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.polaris_vehicle.driving_license.id", binding.id())
                    .withStyle(ChatFormatting.GRAY));
        }, () -> tooltip.add(Component.translatable("tooltip.polaris_vehicle.driving_license.unbound")
                .withStyle(ChatFormatting.GRAY)));
        if (!VehicleTooltips.details(tooltip, flag)) return;
        tooltip.add(Component.translatable("tooltip.polaris_vehicle.driving_license.usage")
                .withStyle(ChatFormatting.GRAY));
    }
}
