package com.flechazo.polaris_vehicle.client;

import com.flechazo.hkt.Maybe;
import com.flechazo.hkt.business.util.OptionalOps;
import com.flechazo.polaris_vehicle.PolarisVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PolarisVehicle.MODID, value = Dist.CLIENT)
public final class VehicleTooltipWrapping {

    static final int MAX_WIDTH = 200;

    private static final String CLOSING = "、，。．：；！？）」』】〉》”’%,.:;!?)]}·";

    private static final String OPENING = "（「『【〈《“‘([{";

    private VehicleTooltipWrapping() {
    }

    private record Glyph(String text, Style style) {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        if (!PolarisVehicle.MODID.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).getNamespace())) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        List<Component> lines = event.getToolTip();
        List<Component> wrapped = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (i == 0 || font.width(line) <= MAX_WIDTH) {
                wrapped.add(line);
            } else {
                wrapped.addAll(wrap(font, line));
            }
        }
        lines.clear();
        lines.addAll(wrapped);
    }

    static List<Component> wrap(Font font, Component line) {
        List<Glyph> glyphs = new ArrayList<>();
        line.visit((style, text) -> {
            text.codePoints().forEach(codePoint -> glyphs.add(new Glyph(Character.toString(codePoint), style)));
            return OptionalOps.fromMaybe(Maybe.none());
        }, Style.EMPTY);
        int indentLength = 0;
        while (indentLength < glyphs.size() && glyphs.get(indentLength).text().equals(" ")) indentLength++;
        List<Glyph> indent = glyphs.subList(0, indentLength);
        int available = MAX_WIDTH - width(font, indent);

        List<List<Glyph>> tokens = tokens(glyphs.subList(indentLength, glyphs.size()));
        List<Component> result = new ArrayList<>();
        List<Glyph> current = new ArrayList<>();
        int currentWidth = 0;
        for (List<Glyph> token : tokens) {
            boolean space = token.size() == 1 && token.getFirst().text().equals(" ");
            if (space && current.isEmpty()) continue;
            int tokenWidth = width(font, token);
            if (!current.isEmpty() && currentWidth + tokenWidth > available && !space) {
                result.add(component(indent, trimEnd(current)));
                current = new ArrayList<>();
                currentWidth = 0;
            }
            current.addAll(token);
            currentWidth += tokenWidth;
        }
        if (!current.isEmpty()) result.add(component(indent, trimEnd(current)));
        return result;
    }

    private static List<List<Glyph>> tokens(List<Glyph> glyphs) {
        List<List<Glyph>> tokens = new ArrayList<>();
        List<Glyph> pendingOpening = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            String text = glyph.text();
            if (OPENING.contains(text)) {
                pendingOpening.add(glyph);
                continue;
            }
            if (CLOSING.contains(text) && !tokens.isEmpty() && pendingOpening.isEmpty()) {
                tokens.getLast().add(glyph);
                continue;
            }
            List<Glyph> token = new ArrayList<>(pendingOpening);
            pendingOpening.clear();
            token.add(glyph);
            if (!text.equals(" ") && !isCjk(text)) {
                while (i + 1 < glyphs.size()) {
                    String next = glyphs.get(i + 1).text();
                    if (next.equals(" ") || isCjk(next) || OPENING.contains(next)) break;
                    token.add(glyphs.get(++i));
                }
            }
            tokens.add(token);
        }
        if (!pendingOpening.isEmpty()) tokens.add(new ArrayList<>(pendingOpening));
        return tokens;
    }

    private static boolean isCjk(String text) {
        int codePoint = text.codePointAt(0);
        return codePoint >= 0x2E80 && !CLOSING.contains(text) && !OPENING.contains(text);
    }

    private static List<Glyph> trimEnd(List<Glyph> glyphs) {
        int end = glyphs.size();
        while (end > 0 && glyphs.get(end - 1).text().equals(" ")) end--;
        return glyphs.subList(0, end);
    }

    private static int width(Font font, List<Glyph> glyphs) {
        int total = 0;
        for (Glyph glyph : glyphs) total += font.width(Component.literal(glyph.text()).withStyle(glyph.style()));
        return total;
    }

    private static MutableComponent component(List<Glyph> indent, List<Glyph> glyphs) {
        List<Glyph> all = new ArrayList<>(indent);
        all.addAll(glyphs);
        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        for (Glyph glyph : all) {
            if (runStyle != null && !glyph.style().equals(runStyle)) {
                result.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = glyph.style();
            run.append(glyph.text());
        }
        if (runStyle != null) result.append(Component.literal(run.toString()).withStyle(runStyle));
        return result;
    }
}
