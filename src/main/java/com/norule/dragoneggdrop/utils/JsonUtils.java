package com.norule.dragoneggdrop.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.norule.dragoneggdrop.DragonEggDrop;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Various utility methods used to fetch values from a {@link JsonObject}.
 *
 * @author Parker Hawke - Choco
 */
public final class JsonUtils {

    private JsonUtils() { }

    /**
     * Get a field with the given name from the provided {@link JsonObject} and cast it using the
     * provided function. If a value with the required name is not present, a {@link JsonParseException}
     * will be thrown.
     *
     * @param root the object from which to fetch the value
     * @param name the name of the value
     * @param caster the casting function
     *
     * @param <T> the type of object to return
     *
     * @return the fetched value.
     *
     * @throws JsonParseException if the value does not exist
     */
    @NotNull
    public static <T> T getRequiredField(@NotNull JsonObject root, @NotNull String name, @NotNull Function<@NotNull JsonElement, @NotNull T> caster) {
        if (!root.has(name)) {
            throw new JsonParseException("Missing element \"" + name + "\". This element is required.");
        }

        return caster.apply(root.get(name));
    }

    /**
     * Get a field with the given name from the provided {@link JsonObject} and cast it using the
     * provided function. If a value with the required name is not present, the provided default
     * value will be returned.
     *
     * @param root the object from which to fetch the value
     * @param name the name of the value
     * @param caster the casting function
     * @param defaultValue the value to return if an entry with the given name is not present
     *
     * @param <T> the type of object to return
     *
     * @return the fetched value.
     *
     * @throws JsonParseException if the value does not exist
     */
    @NotNull
    public static <T> T getOptionalField(@NotNull JsonObject root, @NotNull String name, @NotNull Function<@NotNull JsonElement, @NotNull T> caster, @NotNull T defaultValue) {
        if (!root.has(name)) {
            return defaultValue;
        }

        return caster.apply(root.get(name));
    }

    /**
     * Get the base file name without extension.
     *
     * @param fileName file name with extension
     *
     * @return base name
     */
    @NotNull
    public static String getBaseName(@NotNull String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex == -1 ? fileName : fileName.substring(0, extensionIndex);
    }

    /**
     * Check whether this file name is supported as a structured data file.
     *
     * @param fileName file name
     *
     * @return true if the extension is json, yml or yaml
     */
    public static boolean hasSupportedFileExtension(@NotNull String fileName) {
        return isJsonFile(fileName) || isYamlFile(fileName);
    }

    public static boolean isJsonFile(@NotNull String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".json");
    }

    public static boolean isYamlFile(@NotNull String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".yml") || normalized.endsWith(".yaml");
    }

    /**
     * Read a root object from JSON or YAML.
     *
     * @param file the source file
     *
     * @return root object
     */
    @NotNull
    public static JsonObject readObject(@NotNull File file) {
        String fileName = file.getName();
        if (isJsonFile(fileName)) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                return DragonEggDrop.GSON.fromJson(reader, JsonObject.class);
            } catch (IOException e) {
                throw new JsonParseException(e.getMessage(), e.getCause());
            }
        }

        if (isYamlFile(fileName)) {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            JsonElement root = toJsonElement(configuration);
            if (!root.isJsonObject()) {
                throw new JsonParseException("Expected root object while reading \"" + fileName + "\"");
            }

            return root.getAsJsonObject();
        }

        throw new IllegalArgumentException("Expected .json, .yml or .yaml file. Got \"" + fileName + "\" instead");
    }

    @NotNull
    private static JsonElement toJsonElement(@Nullable Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }

        if (value instanceof ConfigurationSection) {
            return toJsonElement(((ConfigurationSection) value).getValues(false));
        }

        if (value instanceof Map) {
            JsonObject object = new JsonObject();
            ((Map<?, ?>) value).forEach((key, entryValue) -> object.add(String.valueOf(key), toJsonElement(entryValue)));
            return object;
        }

        if (value instanceof Iterable) {
            JsonArray array = new JsonArray();
            ((Iterable<?>) value).forEach(element -> array.add(toJsonElement(element)));
            return array;
        }

        if (value instanceof Number) {
            return new JsonPrimitive((Number) value);
        }

        if (value instanceof Boolean) {
            return new JsonPrimitive((Boolean) value);
        }

        return new JsonPrimitive(String.valueOf(value));
    }

}
