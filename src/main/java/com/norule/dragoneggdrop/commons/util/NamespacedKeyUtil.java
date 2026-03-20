package com.norule.dragoneggdrop.commons.util;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for {@link NamespacedKey}.
 */
public final class NamespacedKeyUtil {

    private NamespacedKeyUtil() { }

    /**
     * Parse a namespaced key from text.
     *
     * @param value the text value
     * @param plugin fallback namespace provider
     *
     * @return parsed key, or null if invalid
     */
    @Nullable
    public static NamespacedKey fromString(@Nullable String value, @Nullable Plugin plugin) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            int separatorIndex = normalized.indexOf(':');
            if (separatorIndex == -1) {
                if (plugin != null) {
                    return new NamespacedKey(plugin, normalized);
                }

                return NamespacedKey.minecraft(normalized);
            }

            String namespace = normalized.substring(0, separatorIndex);
            String key = normalized.substring(separatorIndex + 1);
            if (namespace.isEmpty() || key.isEmpty()) {
                return null;
            }

            return new NamespacedKey(namespace, key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

}
