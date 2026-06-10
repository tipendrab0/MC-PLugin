package com.mha.plugin.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Adventure text helpers for Paper 1.21+.
 * Paper removed {@code TextUtil.actionBar(Player, String)} — use Component instead.
 */
public final class TextUtil {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private TextUtil() {
    }

    public static Component legacy(final String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        final String normalized = text.replace('&', '§');
        return LEGACY.deserialize(normalized);
    }

    public static void actionBar(final Player player, final String text) {
        actionBar(player, legacy(text));
    }

    public static void actionBar(final Player player, final Component component) {
        if (player != null && component != null) {
            player.sendActionBar(component);
        }
    }

    public static String plainText(final Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
