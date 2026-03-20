package com.norule.dragoneggdrop.utils;

import com.norule.dragoneggdrop.commons.util.NamespacedKeyUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for resolving Bukkit {@link Attribute} instances.
 */
public final class AttributeUtils {

    private AttributeUtils() { }

    /**
     * Resolve an attribute from modern namespaced ids and legacy enum-like keys.
     *
     * @param value attribute id, e.g. "minecraft:max_health" or "GENERIC_MAX_HEALTH"
     *
     * @return resolved attribute or null
     */
    @Nullable
    public static Attribute parseAttribute(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        for (String candidate : createCandidates(normalized)) {
            NamespacedKey key = NamespacedKeyUtil.fromString(candidate, null);
            if (key == null) {
                continue;
            }

            Attribute attribute = Registry.ATTRIBUTE.get(key);
            if (attribute != null) {
                return attribute;
            }
        }

        return null;
    }

    @NotNull
    private static List<String> createCandidates(@NotNull String value) {
        Set<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, value);

        String keyPart = value.startsWith("minecraft:") ? value.substring("minecraft:".length()) : value;
        addCandidate(candidates, keyPart);

        if (!keyPart.contains(".") && keyPart.contains("_")) {
            int firstUnderscoreIndex = keyPart.indexOf('_');
            String dotted = keyPart.substring(0, firstUnderscoreIndex) + "." + keyPart.substring(firstUnderscoreIndex + 1);
            addCandidate(candidates, dotted);
        }

        if (keyPart.contains(".")) {
            addCandidate(candidates, keyPart.replace('.', '_'));

            int firstDotIndex = keyPart.indexOf('.');
            if (firstDotIndex != -1 && firstDotIndex + 1 < keyPart.length()) {
                addCandidate(candidates, keyPart.substring(firstDotIndex + 1));
            }
        }

        if (keyPart.contains("_")) {
            int firstUnderscoreIndex = keyPart.indexOf('_');
            if (firstUnderscoreIndex != -1 && firstUnderscoreIndex + 1 < keyPart.length()) {
                addCandidate(candidates, keyPart.substring(firstUnderscoreIndex + 1));
            }
        }

        return new ArrayList<>(candidates);
    }

    private static void addCandidate(@NotNull Set<String> candidates, @NotNull String candidate) {
        if (candidate.isEmpty()) {
            return;
        }

        candidates.add(candidate);
        if (!candidate.startsWith("minecraft:")) {
            candidates.add("minecraft:" + candidate);
        }
    }

}
