package com.nyarutoru.nekoplugin.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building Adventure Components.
 * Replaces legacy § color codes with type-safe Component API.
 */
public final class ComponentUtils {

    private ComponentUtils() {
    }

    // ==================== Basic Text Builders ====================

    /**
     * Create a simple text component with color.
     */
    public static Component text(String text, NamedTextColor color) {
        return Component.text(text).color(color);
    }

    /**
     * Create a bold text component with color.
     */
    public static Component bold(String text, NamedTextColor color) {
        return Component.text(text)
                .color(color)
                .decoration(TextDecoration.BOLD, true);
    }

    /**
     * Create a text component with color and no italic (for item names/lore).
     */
    public static Component noItalic(String text, NamedTextColor color) {
        return Component.text(text)
                .color(color)
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Create a bold, non-italic text component (for item display names).
     */
    public static Component displayName(String text, NamedTextColor color) {
        return Component.text(text)
                .color(color)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false);
    }

    // ==================== Common Patterns ====================

    /**
     * Create a label-value pair: "Key: Value" (gray key, white value).
     */
    public static Component label(String key, String value) {
        return Component.text(key + ": ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value)
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Create a label-value pair with custom value color.
     */
    public static Component label(String key, String value, NamedTextColor valueColor) {
        return Component.text(key + ": ")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(value)
                        .color(valueColor)
                        .decoration(TextDecoration.ITALIC, false));
    }

    /**
     * Create a success message: "✔ text" (green).
     */
    public static Component success(String text) {
        return Component.text("✔ ")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(text)
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, false));
    }

    /**
     * Create an error message: "✖ text" (red).
     */
    public static Component error(String text) {
        return Component.text("✖ ")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(text)
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, false));
    }

    /**
     * Create info text (gray, non-italic for lore).
     */
    public static Component info(String text) {
        return Component.text(text)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Create a highlight text (yellow, non-italic).
     */
    public static Component highlight(String text) {
        return Component.text(text)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }

    // ==================== Action Bar Patterns ====================

    /**
     * Create an "ACTIVE" status message for action bars.
     */
    public static Component activeStatus(String toolName) {
        return Component.text("✔ ")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(toolName + " ")
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("ACTIVE")
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true));
    }

    /**
     * Create a "disabled" status message for action bars.
     */
    public static Component disabledStatus(String toolName, String reason) {
        return Component.text("✖ ")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(toolName + " ")
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("disabled ")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("(" + reason + ")")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    /**
     * Create a timeout cancellation message.
     */
    public static Component timeoutMessage() {
        return Component.text("✖ ")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("Activation cancelled ")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("(timeout)")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false));
    }

    /**
     * Create a progress bar component for activation.
     * 
     * @param current Current progress value
     * @param max     Maximum progress value
     * @param filled  Character for filled portion
     * @param empty   Character for empty portion
     */
    public static Component progressBar(int current, int max, char filled, char empty) {
        Component bar = Component.text("⚡ ")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text("[")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.BOLD, false));

        for (int i = 0; i < max; i++) {
            bar = bar.append(Component.text(String.valueOf(i < current ? filled : empty))
                    .color(i < current ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.BOLD, false));
        }

        bar = bar.append(Component.text("] ")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, false))
                .append(Component.text(String.valueOf(current))
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("/")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text(String.valueOf(max))
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false));

        return bar;
    }

    // ==================== Lore Builders ====================

    /**
     * Create a lore list from strings (all gray, non-italic).
     */
    public static List<Component> lore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(info(line));
        }
        return lore;
    }

    /**
     * Create an empty line for lore spacing.
     */
    public static Component emptyLine() {
        return Component.empty();
    }

    // ==================== Tier Color Mapping ====================

    /**
     * Get the NamedTextColor for a drawer tier level.
     */
    public static NamedTextColor tierColor(int tier) {
        return switch (tier) {
            case 1, 4 -> NamedTextColor.GRAY;
            case 2 -> NamedTextColor.GOLD;
            case 3 -> NamedTextColor.WHITE;
            case 5 -> NamedTextColor.YELLOW;
            case 6 -> NamedTextColor.AQUA;
            case 7 -> NamedTextColor.GREEN;
            case 8 -> NamedTextColor.DARK_PURPLE;
            case 9 -> NamedTextColor.DARK_RED;
            case 10 -> NamedTextColor.RED;
            default -> NamedTextColor.WHITE;
        };
    }
}
