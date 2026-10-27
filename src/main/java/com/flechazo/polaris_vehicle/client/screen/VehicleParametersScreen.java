package com.flechazo.polaris_vehicle.client.screen;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.network.VehicleParametersUpdatePacket;
import com.flechazo.polaris_vehicle.vehicle.data.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
public final class VehicleParametersScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int ROWS_PER_PAGE = 4;
    private static final List<ResourceLocation> VEHICLES = VehicleTypeParameters.defaultsView().keySet()
            .stream()
            .sorted()
            .toList();
    private static final List<ResourceLocation> TIRES = VehicleTireParameters
            .defaultsView().keySet().stream().sorted().toList();
    private static final List<Field> TIRE_FIELDS = List.of(field("max_durability", 1, 1_000_000_000));
    private static final List<Field> PHYSICS_FIELDS = List.of(
            field("mass_kilograms", 100.0, 20_000.0),
            field("center_of_mass_height_ratio", 0.05, 0.75),
            field("suspension_rest_length", 0.02, 2.0),
            field("suspension_max_droop", 0.01, 1.0),
            field("suspension_natural_frequency_hertz", 0.5, 8.0),
            field("compression_damping_ratio", 0.0, 3.0),
            field("rebound_damping_ratio", 0.0, 3.0),
            field("lateral_grip_response", 0.1, 30.0),
            field("tire_grip_multiplier", 0.1, 3.0),
            field("maximum_steering_angle_degrees", 1.0, 80.0),
            field("maximum_step_height", 0.0, 2.0),
            field("rolling_resistance_coefficient", 0.0, 1.0),
            field("aerodynamic_drag_coefficient", 0.0, 5.0),
            field("steering_limit_start_ratio", 0.0, 1.0),
            field("minimum_steering_scale", 0.0, 1.0)
    );
    private static final List<Field> POWERTRAIN_FIELDS = List.of(
            field("body_weight_penalty", 0.0, 200.0),
            field("engine_horsepower_multiplier", 0.1, 5.0),
            field("engine_acceleration_multiplier", 0.1, 5.0),
            field("base_max_speed_modifier", -100.0, 100.0),
            field("base_acceleration_modifier", -50.0, 50.0),
            field("reverse_speed_ratio", 0.05, 1.0),
            field("minimum_reverse_speed", 0.0, 30.0),
            field("maximum_reverse_speed", 0.0, 50.0),
            field("braking_meters_per_second_squared", 0.1, 50.0)
    );

    private final Screen parent;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<EditBox> visibleInputs = new ArrayList<>();
    private int vehicleIndex;
    private int tireIndex;
    private Group group = Group.PHYSICS;
    private int page;
    private Component error = Component.empty();
    private final List<AbstractWidget> writeControls = new ArrayList<>();

    public VehicleParametersScreen(Screen parent) {
        super(Component.translatable("screen.polaris_vehicle.vehicle_parameters.title"));
        this.parent = parent;
        loadVehicle();
    }

    @Override
    protected void init() {
        visibleInputs.clear();
        writeControls.clear();
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changeVehicle(-1))
                .bounds(center - 155, 20, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changeVehicle(1))
                .bounds(center + 131, 20, 24, 20).build());
        addRenderableWidget(Button.builder(group.title(), ignored -> {
            storeVisibleValues();
            group = Group.values()[(group.ordinal() + 1) % Group.values().length];
            page = 0;
            rebuildWidgets();
        }).bounds(center - 70, 45, 140, 20).build());

        List<Field> fields = fields();
        int first = page * ROWS_PER_PAGE;
        int last = Math.min(first + ROWS_PER_PAGE, fields.size());
        for (int index = first; index < last; index++) {
            Field field = fields.get(index);
            int y = 75 + (index - first) * 25;
            EditBox input = new EditBox(font, center + 35, y, 75, 20, field.title());
            input.setMaxLength(32);
            input.setValue(values.getOrDefault(key(group, field.name), "0"));
            input.setTooltip(Tooltip.create(Component.literal(
                    "[" + field.minimum + ", " + field.maximum + "]"
            )));
            input.setEditable(canEdit());
            visibleInputs.add(input);
            addRenderableWidget(input);
            writeControls.add(addRenderableWidget(Button.builder(Component.translatable("screen.polaris_vehicle.vehicle_parameters.reset_field"), ignored -> {
                var defaults = VehicleParameterEditorData
                        .defaults(kind(), currentId()).getAsJsonObject();
                var fieldsJson = group == Group.TIRES ? defaults : defaults.getAsJsonObject(group == Group.PHYSICS ? "physics" : "powertrain");
                input.setValue(fieldsJson.get(field.name).getAsString());
            }).bounds(center + 115, y, 40, 20).build()));
        }

        int pages = pageCount(fields);
        addRenderableWidget(Button.builder(Component.literal("<"), ignored -> changePage(-1))
                .bounds(center - 70, height - 52, 30, 20).build()).active = pages > 1;
        addRenderableWidget(Button.builder(Component.literal(">"), ignored -> changePage(1))
                .bounds(center + 40, height - 52, 30, 20).build()).active = pages > 1;
        writeControls.add(addRenderableWidget(Button.builder(Component.translatable("screen.polaris_vehicle.vehicle_parameters.save"),
                ignored -> save()).bounds(center - 155, height - 27, 73, 20).build()));
        writeControls.add(addRenderableWidget(Button.builder(Component.translatable("screen.polaris_vehicle.vehicle_parameters.reset_current"),
                ignored -> confirmReset(false)).bounds(center - 76, height - 27, 73, 20).build()));
        writeControls.add(addRenderableWidget(Button.builder(Component.translatable("screen.polaris_vehicle.vehicle_parameters.reset_all"),
                ignored -> confirmReset(true)).bounds(center + 3, height - 27, 73, 20).build()));
        writeControls.forEach(control -> control.active = canEdit());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                ignored -> onClose()).bounds(center + 82, height - 27, 73, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, title, center, 7, 0xFFFFFFFF);
        graphics.drawCenteredString(
                font,
                Component.translatable((group == Group.TIRES ? "item.polaris_vehicle." : "entity.polaris_vehicle.") + currentId().getPath()),
                center,
                26,
                0xFFFFFFFF
        );
        List<Field> fields = fields();
        int first = page * ROWS_PER_PAGE;
        int last = Math.min(first + ROWS_PER_PAGE, fields.size());
        for (int index = first; index < last; index++) {
            Field field = fields.get(index);
            int y = 81 + (index - first) * 25;
            graphics.drawString(font, field.title(), center - 155, y, 0xFFE0E0E0, false);
        }
        graphics.drawCenteredString(font, (page + 1) + " / " + pageCount(fields), center, height - 46, 0xFFB0B0B0);
        if (!canEdit()) {
            graphics.drawCenteredString(font, Component.translatable("screen.polaris_vehicle.vehicle_parameters.read_only"),
                    center, height - 66, 0xFFFFCC66);
        }
        if (canEdit() && !error.getString().isEmpty()) {
            graphics.drawCenteredString(font, error, center, height - 66, 0xFFFF5555);
        }
    }

    private boolean canEdit() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    @Override
    public void tick() {
        boolean allowed = canEdit();
        visibleInputs.forEach(input -> input.setEditable(allowed));
        writeControls.forEach(control -> control.active = allowed);
    }

    private void save() {
        if (!canEdit()) return;
        storeVisibleValues();
        error = Pathway.tryOf(() -> group == Group.TIRES
                        ? VehicleTireParameters.CODEC.encodeStart(JsonOps.INSTANCE,
                        new VehicleTireParameters(tireDurability())).getOrThrow()
                        : VehicleTypeParameters.CODEC.encodeStart(JsonOps.INSTANCE, buildValue()).getOrThrow())
                .map(json -> new VehicleParametersUpdatePacket(currentId(), GSON.toJson(json), kind(), "save"))
                .peekChecked(VehicleParametersUpdatePacket::sendToServer)
                .map(ignored -> Component.translatable("message.polaris_vehicle.vehicle_parameters.saving")
                        .withStyle(ChatFormatting.YELLOW))
                .recover(RuntimeException.class,
                        exception -> Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED))
                .run().getOrThrow();
    }

    private String kind() {
        return group == Group.TIRES ? "tire" : "vehicle";
    }

    private ResourceLocation currentId() {
        return group == Group.TIRES ? TIRES.get(tireIndex) : VEHICLES.get(vehicleIndex);
    }

    private int tireDurability() {
        double value = number(Group.TIRES, "max_durability");
        if (!Double.isFinite(value) || value != Math.rint(value) || value < 1 || value > 1_000_000_000)
            throw new IllegalArgumentException(Component.translatable("screen.polaris_vehicle.vehicle_parameters.invalid_durability").getString());
        return (int) value;
    }

    private void confirmReset(boolean all) {
        if (!canEdit()) return;
        storeVisibleValues();
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                new VehicleParametersUpdatePacket(currentId(), "", kind(), all ? "reset_all" : "reset").sendToServer();
                if (all) loadVehicle(true);
                else {
                    var defaults = VehicleParameterEditorData.defaults(kind(), currentId()).getAsJsonObject();
                    if (group == Group.TIRES)
                        values.put(key(Group.TIRES, "max_durability"), defaults.get("max_durability").getAsString());
                    else for (Group target : List.of(Group.PHYSICS, Group.POWERTRAIN)) {
                        var fieldsJson = defaults.getAsJsonObject(target == Group.PHYSICS ? "physics" : "powertrain");
                        fieldsJson.entrySet().forEach(entry -> values.put(key(target, entry.getKey()), entry.getValue().getAsString()));
                    }
                }
                error = Component.translatable("screen.polaris_vehicle.vehicle_parameters.resetting");
            }
            minecraft.setScreen(this);
        }, Component.translatable("screen.polaris_vehicle.vehicle_parameters." + (all ? "reset_all" : "reset_current")),
                Component.translatable("screen.polaris_vehicle.vehicle_parameters." + (all ? "reset_all_confirm" : "reset_current_confirm"))));
    }

    private VehicleTypeParameters buildValue() {
        VehiclePhysicalParameters physics = new VehiclePhysicalParameters(
                number(Group.PHYSICS, "mass_kilograms"),
                number(Group.PHYSICS, "center_of_mass_height_ratio"),
                number(Group.PHYSICS, "suspension_rest_length"),
                number(Group.PHYSICS, "suspension_max_droop"),
                number(Group.PHYSICS, "suspension_natural_frequency_hertz"),
                number(Group.PHYSICS, "compression_damping_ratio"),
                number(Group.PHYSICS, "rebound_damping_ratio"),
                number(Group.PHYSICS, "lateral_grip_response"),
                number(Group.PHYSICS, "tire_grip_multiplier"),
                number(Group.PHYSICS, "maximum_steering_angle_degrees"),
                number(Group.PHYSICS, "maximum_step_height"),
                number(Group.PHYSICS, "rolling_resistance_coefficient"),
                number(Group.PHYSICS, "aerodynamic_drag_coefficient"),
                number(Group.PHYSICS, "steering_limit_start_ratio"),
                number(Group.PHYSICS, "minimum_steering_scale")
        );
        VehiclePowertrainParameters powertrain = new VehiclePowertrainParameters(
                number(Group.POWERTRAIN, "body_weight_penalty"),
                number(Group.POWERTRAIN, "engine_horsepower_multiplier"),
                number(Group.POWERTRAIN, "engine_acceleration_multiplier"),
                number(Group.POWERTRAIN, "base_max_speed_modifier"),
                number(Group.POWERTRAIN, "base_acceleration_modifier"),
                number(Group.POWERTRAIN, "reverse_speed_ratio"),
                number(Group.POWERTRAIN, "minimum_reverse_speed"),
                number(Group.POWERTRAIN, "maximum_reverse_speed"),
                number(Group.POWERTRAIN, "braking_meters_per_second_squared")
        );
        VehicleTypeParameters value = new VehicleTypeParameters(physics, powertrain);
        VehicleTypeParameters.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return value;
    }

    private void loadVehicle() {
        loadVehicle(false);
    }

    private void loadVehicle(boolean defaults) {
        values.clear();
        VehicleTypeParameters data = defaults ? VehicleTypeParameters.defaultsView().get(VEHICLES.get(vehicleIndex))
                : VehicleTypeParameters.resolve(VEHICLES.get(vehicleIndex));
        for (var id : TIRES) {
            var builtin = VehicleTireParameters.defaultsView().get(id);
            var tire = defaults ? builtin : VehicleTireParameters.resolve(id, builtin.maxDurability());
            values.put("TIRES." + id + ".max_durability", Integer.toString(tire.maxDurability()));
        }
        VehiclePhysicalParameters p = data.physics();
        put(Group.PHYSICS, PHYSICS_FIELDS, List.of(
                p.massKilograms(), p.centerOfMassHeightRatio(),
                p.suspensionRestLength(), p.suspensionMaxDroop(),
                p.suspensionNaturalFrequencyHertz(), p.compressionDampingRatio(), p.reboundDampingRatio(),
                p.lateralGripResponse(), p.tireGripMultiplier(), p.maximumSteeringAngleDegrees(),
                p.maximumStepHeight(), p.rollingResistanceCoefficient(),
                p.aerodynamicDragCoefficient(), p.steeringLimitStartRatio(), p.minimumSteeringScale()
        ));
        VehiclePowertrainParameters t = data.powertrain();
        put(Group.POWERTRAIN, POWERTRAIN_FIELDS, List.of(
                t.bodyWeightPenalty(), t.engineHorsepowerMultiplier(), t.engineAccelerationMultiplier(),
                t.baseMaxSpeedModifier(), t.baseAccelerationModifier(), t.reverseSpeedRatio(),
                t.minimumReverseSpeed(), t.maximumReverseSpeed(), t.brakingMetersPerSecondSquared()
        ));
    }

    private void put(Group target, List<Field> fields, List<Double> numbers) {
        for (int index = 0; index < fields.size(); index++) {
            values.put(key(target, fields.get(index).name), Double.toString(numbers.get(index)));
        }
    }

    private void storeVisibleValues() {
        List<Field> fields = fields();
        int first = page * ROWS_PER_PAGE;
        for (int index = 0; index < visibleInputs.size(); index++) {
            values.put(key(group, fields.get(first + index).name), visibleInputs.get(index).getValue());
        }
    }

    private double number(Group target, String name) {
        String raw = values.get(key(target, name));
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + ": " + raw);
        }
    }

    private void changeVehicle(int direction) {
        storeVisibleValues();
        if (group == Group.TIRES) tireIndex = Math.floorMod(tireIndex + direction, TIRES.size());
        else vehicleIndex = Math.floorMod(vehicleIndex + direction, VEHICLES.size());
        page = 0;
        error = Component.empty();
        loadVehicle();
        rebuildWidgets();
    }

    private void changePage(int direction) {
        storeVisibleValues();
        page = Math.floorMod(page + direction, pageCount(fields()));
        rebuildWidgets();
    }

    private List<Field> fields() {
        return switch (group) {
            case PHYSICS -> PHYSICS_FIELDS;
            case POWERTRAIN -> POWERTRAIN_FIELDS;
            case TIRES -> TIRE_FIELDS;
        };
    }

    private static int pageCount(List<Field> fields) {
        return (fields.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE;
    }

    private String key(Group group, String field) {
        return group.name() + "." + (group == Group.TIRES ? TIRES.get(tireIndex) + "." : "") + field;
    }

    private static Field field(String name, double minimum, double maximum) {
        return new Field(name, minimum, maximum);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private enum Group {
        PHYSICS("screen.polaris_vehicle.vehicle_parameters.physics"),
        POWERTRAIN("screen.polaris_vehicle.vehicle_parameters.powertrain"),
        TIRES("screen.polaris_vehicle.vehicle_parameters.tires");

        private final String translationKey;

        Group(String translationKey) {
            this.translationKey = translationKey;
        }

        Component title() {
            return Component.translatable(translationKey);
        }
    }

    private record Field(String name, double minimum, double maximum) {
        Component title() {
            return Component.translatable("screen.polaris_vehicle.vehicle_parameters.field." + name);
        }
    }
}
