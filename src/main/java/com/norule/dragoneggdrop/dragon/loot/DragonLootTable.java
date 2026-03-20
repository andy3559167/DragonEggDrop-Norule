package com.norule.dragoneggdrop.dragon.loot;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.norule.dragoneggdrop.DragonEggDrop;
import com.norule.dragoneggdrop.commons.util.MathUtil;
import com.norule.dragoneggdrop.dragon.DragonTemplate;
import com.norule.dragoneggdrop.dragon.loot.elements.DragonLootElementCommand;
import com.norule.dragoneggdrop.dragon.loot.elements.DragonLootElementEgg;
import com.norule.dragoneggdrop.dragon.loot.elements.DragonLootElementItem;
import com.norule.dragoneggdrop.dragon.loot.elements.IDragonLootElement;
import com.norule.dragoneggdrop.dragon.loot.pool.ILootPool;
import com.norule.dragoneggdrop.dragon.loot.pool.LootPoolCommand;
import com.norule.dragoneggdrop.dragon.loot.pool.LootPoolItem;
import com.norule.dragoneggdrop.registry.Registerable;
import com.norule.dragoneggdrop.utils.JsonUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a dragon's loot table. These tables are used to randomly generate unique
 * loot for every dragon while being reusable for multiple dragons.
 *
 * @author Parker Hawke - Choco
 */
public class DragonLootTable implements Registerable {

    private double chestChance;
    private String chestName;
    private DragonLootElementEgg egg;

    private final String id;
    private final List<@NotNull ILootPool<@NotNull DragonLootElementCommand>> commandPools;
    private final List<@NotNull ILootPool<@NotNull DragonLootElementItem>> chestPools;

    /**
     * Create a {@link DragonLootTable}.
     *
     * @param id the loot table's unique id
     * @param egg the egg element. If null, no egg will be generated
     * @param commandPools the command loot pools
     * @param chestPools the chest loot pools
     *
     * @see ILootPool
     * @see #fromFile(File)
     */
    public DragonLootTable(@NotNull String id, @Nullable DragonLootElementEgg egg, @Nullable List<@NotNull ILootPool<@NotNull DragonLootElementCommand>> commandPools, @Nullable List<@NotNull ILootPool<@NotNull DragonLootElementItem>> chestPools) {
        this.id = id;
        this.egg = (egg != null) ? egg : new DragonLootElementEgg(0.0);
        this.commandPools = (commandPools != null) ? new ArrayList<>(commandPools) : Collections.EMPTY_LIST;
        this.chestPools = (chestPools != null) ? new ArrayList<>(chestPools) : Collections.EMPTY_LIST;
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }

    /**
     * Get the chance (0.0 - 100.0) that a chest will be generated and item loot pools
     * will be rolled.
     *
     * @return the chest chance
     */
    public double getChestChance() {
        return chestChance;
    }

    /**
     * Get the custom name of the generated chest.
     *
     * @return the chest's name
     */
    @Nullable
    public String getChestName() {
        return chestName;
    }

    /**
     * Get the egg loot element.
     *
     * @return the egg element
     */
    @Nullable
    public DragonLootElementEgg getEgg() {
        return egg;
    }

    /**
     * Get an immutable list of the command loot pools.
     *
     * @return an immutable command pool list
     */
    @NotNull
    public List<@NotNull ILootPool<@NotNull DragonLootElementCommand>> getCommandPools() {
        return ImmutableList.copyOf(commandPools);
    }

    /**
     * Get an immutable list of the chest loot pools.
     *
     * @return an immutable chest pool list
     */
    @NotNull
    public List<@NotNull ILootPool<@NotNull DragonLootElementItem>> getChestPools() {
        return ImmutableList.copyOf(chestPools);
    }

    /**
     * Generate loot for the given {@link DragonBattle} and {@link EnderDragon}. All loot
     * pools will be rolled and generated. The egg will be generated first (and put in a
     * chest if necessary), then the chest item pools, followed by the command pools.
     *
     * @param battle the battle for which to generate loot
     * @param template the template for which to generate loot
     * @param killer the player that has slain the dragon. May be null
     */
    public void generate(@NotNull DragonBattle battle, @NotNull DragonTemplate template, @Nullable Player killer) {
        Preconditions.checkArgument(battle != null, "Attempted to generate loot for null dragon battle");
        Preconditions.checkArgument(template != null, "Attempted to generate loot for null dragon template");

        Chest chest = null;
        Location endPortalLocation = battle.getEndPortalLocation();
        if (endPortalLocation == null) {
            return;
        }

        endPortalLocation.add(0, 4, 0);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        DragonEggDrop plugin = DragonEggDrop.getInstance();

        Block block = endPortalLocation.getBlock();
        block.breakNaturally(); // If there's a block already present, break it

        if (random.nextDouble(100) < chestChance) {
            block.setType(Material.CHEST);

            chest = (Chest) block.getState();
            if (chestName != null && !chestName.isEmpty()) {
                chest.setCustomName(chestName);
                chest.update();
            }
        }

        // Generate the egg
        this.egg.generate(battle, template, killer, random, chest);

        // Generate the item loot pools
        this.generateLootPools(chestPools, plugin, battle, template, killer, random, chest);

        // Execute the command loot pools
        this.generateLootPools(commandPools, plugin, battle, template, killer, random, chest);
    }

    /**
     * Generate item loot for this loot table and place it in a chest to be set at the
     * given Block position.
     *
     * @param block the block at which to set the chest
     * @param template the template for which to generate loot.
     * @param player the player for whom to generate the loot. May be null
     */
    public void generate(@NotNull Block block, @NotNull DragonTemplate template, @Nullable Player player) {
        Preconditions.checkArgument(template != null, "Attempted to generate loot for null dragon template");

        block.setType(Material.CHEST);

        Chest chest = (Chest) block.getState();
        this.egg.generate(null, template, player, ThreadLocalRandom.current(), chest);
        this.generateLootPools(chestPools, DragonEggDrop.getInstance(), null, template, player, ThreadLocalRandom.current(), chest);
    }

    /**
     * Write this loot table as a JsonObject.
     *
     * @return the JSON representation
     */
    @NotNull
    public JsonObject asJson() {
        return new JsonObject();
    }

    private <T extends IDragonLootElement> void generateLootPools(@NotNull List<@NotNull ILootPool<@NotNull T>> pools, @NotNull DragonEggDrop plugin, @Nullable DragonBattle battle, @NotNull DragonTemplate template, @Nullable Player killer, @NotNull ThreadLocalRandom random, @Nullable Chest chest) {
        if (pools == null || pools.isEmpty()) {
            return;
        }

        for (ILootPool<T> lootPool : pools) {
            if (random.nextDouble(100) >= lootPool.getChance()) {
                continue;
            }

            int rolls = random.nextInt(lootPool.getMinRolls(), lootPool.getMaxRolls() + 1);
            for (int i = 0; i < rolls; i++) {
                IDragonLootElement loot = lootPool.roll(random);
                if (loot == null) {
                    plugin.getLogger().warning("Attempted to generate null loot element for loot pool with name \"" + lootPool.getName() + "\" (loot table: \"" + id + "\"). Ignoring...");
                    continue;
                }

                loot.generate(battle, template, killer, random, chest);
            }
        }
    }

    /**
     * Parse a {@link DragonLootTable} instance from a JSON or YAML file. The file extension from
     * the specified file is validated. If the file is not terminated by .json/.yml/.yaml, an
     * {@link IllegalArgumentException} will be thrown.
     *
     * @param file the file from which to parse the loot table
     *
     * @return the parsed loot table
     *
     * @throws IllegalArgumentException if the file is invalid
     * @throws JsonParseException if the parsing at all fails
     */
    @NotNull
    public static DragonLootTable fromFile(@NotNull File file) throws JsonParseException {
        Preconditions.checkArgument(file != null, "file must not be null");

        String fileName = file.getName();
        if (!JsonUtils.hasSupportedFileExtension(fileName)) {
            throw new IllegalArgumentException("Expected .json, .yml or .yaml file. Got " + fileName.substring(fileName.lastIndexOf('.')) + " instead");
        }

        JsonObject root = JsonUtils.readObject(file);

        String id = JsonUtils.getBaseName(fileName);
        String chestName = null;
        double chestChance = root.has("chest") ? 100.0 : 0.0;

        DragonLootElementEgg egg = (root.has("egg") && root.get("egg").isJsonObject()) ? DragonEggDrop.GSON.fromJson(root.getAsJsonObject("egg"), DragonLootElementEgg.class) : new DragonLootElementEgg();

        List<ILootPool<DragonLootElementCommand>> commandPools = new ArrayList<>();
        List<ILootPool<DragonLootElementItem>> chestPools = new ArrayList<>();

        if (root.has("command_pools") && root.get("command_pools").isJsonArray()) {
            JsonArray commandPoolsRoot = root.getAsJsonArray("command_pools");
            for (JsonElement element : commandPoolsRoot) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Invalid command pool. Expected object, got " + element.getClass().getSimpleName());
                }

                commandPools.add(LootPoolCommand.fromJson(element.getAsJsonObject()));
            }
        }

        if (root.has("chest") && root.get("chest").isJsonObject()) {
            JsonObject chestRoot = root.getAsJsonObject("chest");

            if (chestRoot.has("chance")) {
                chestChance = MathUtil.clamp(chestRoot.get("chance").getAsDouble(), 0.0, 100.0);
            }

            if (chestRoot.has("name")) {
                chestName = ChatColor.translateAlternateColorCodes('&', chestRoot.get("name").getAsString());
            }

            if (chestRoot.has("pools")) {
                parseChestPoolsElement(chestRoot.get("pools"), chestPools);
            }
        }

        DragonLootTable lootTable = new DragonLootTable(id, egg, commandPools, chestPools);
        lootTable.chestName = chestName;
        lootTable.chestChance = chestChance;
        return lootTable;
    }

    private static void parseChestPoolsElement(@NotNull JsonElement poolsElement, @NotNull List<ILootPool<DragonLootElementItem>> chestPools) {
        if (poolsElement.isJsonArray()) {
            JsonArray chestPoolsRoot = poolsElement.getAsJsonArray();
            for (JsonElement element : chestPoolsRoot) {
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Invalid item pool. Expected object, got " + element.getClass().getSimpleName());
                }

                chestPools.add(LootPoolItem.fromJson(element.getAsJsonObject()));
            }
            return;
        }

        if (poolsElement.isJsonObject()) {
            JsonObject groupedPoolsRoot = poolsElement.getAsJsonObject();
            for (Entry<String, JsonElement> categoryEntry : groupedPoolsRoot.entrySet()) {
                if (!categoryEntry.getValue().isJsonObject()) {
                    throw new JsonParseException("Invalid grouped pool category \"" + categoryEntry.getKey() + "\". Expected object, got " + categoryEntry.getValue().getClass().getSimpleName());
                }

                JsonObject categoryRoot = categoryEntry.getValue().getAsJsonObject();
                for (Entry<String, JsonElement> poolEntry : categoryRoot.entrySet()) {
                    if (!poolEntry.getValue().isJsonObject()) {
                        throw new JsonParseException("Invalid grouped pool id \"" + poolEntry.getKey() + "\" in category \"" + categoryEntry.getKey() + "\". Expected object, got " + poolEntry.getValue().getClass().getSimpleName());
                    }

                    JsonObject groupedPoolRoot = createGroupedPoolRoot(categoryEntry.getKey(), poolEntry.getKey(), poolEntry.getValue().getAsJsonObject());
                    chestPools.add(LootPoolItem.fromJson(groupedPoolRoot));
                }
            }
            return;
        }

        throw new JsonParseException("Element \"pools\" is of unexpected type. Expected array or object, got " + poolsElement.getClass().getSimpleName());
    }

    @NotNull
    private static JsonObject createGroupedPoolRoot(@NotNull String categoryName, @NotNull String poolId, @NotNull JsonObject groupedPoolData) {
        JsonObject poolRoot = new JsonObject();
        poolRoot.addProperty("name", categoryName + ":" + poolId);

        JsonElement rollsElement = groupedPoolData.has("rolls") ? groupedPoolData.get("rolls") : new JsonPrimitive(1);
        poolRoot.add("rolls", normalizeRollsElement(rollsElement));

        if (groupedPoolData.has("chance")) {
            poolRoot.add("chance", groupedPoolData.get("chance"));
        }

        JsonArray itemsArray = groupedPoolData.has("items")
            ? normalizeGroupedItemsElement(groupedPoolData.get("items"), categoryName, poolId)
            : new JsonArray();

        if (!groupedPoolData.has("items")) {
            itemsArray.add(normalizeGroupedItemObject(groupedPoolData, true, categoryName, poolId));
        }

        if (itemsArray.size() <= 0) {
            throw new JsonParseException("Grouped pool \"" + categoryName + " -> " + poolId + "\" does not define any items");
        }

        poolRoot.add("items", itemsArray);
        return poolRoot;
    }

    @NotNull
    private static JsonElement normalizeRollsElement(@NotNull JsonElement rollsElement) {
        if (rollsElement.isJsonPrimitive()) {
            JsonPrimitive primitive = rollsElement.getAsJsonPrimitive();
            if (primitive.isNumber()) {
                return new JsonPrimitive(Math.max(primitive.getAsInt(), 0));
            }

            if (primitive.isString()) {
                String rollsString = primitive.getAsString().trim();
                int separatorIndex = rollsString.indexOf('-');
                if (separatorIndex != -1) {
                    int min = parsePositiveInt(rollsString.substring(0, separatorIndex), "rolls");
                    int max = parsePositiveInt(rollsString.substring(separatorIndex + 1), "rolls");

                    JsonObject rangeObject = new JsonObject();
                    rangeObject.addProperty("min", Math.min(min, max));
                    rangeObject.addProperty("max", Math.max(min, max));
                    return rangeObject;
                }

                return new JsonPrimitive(parsePositiveInt(rollsString, "rolls"));
            }

            throw new JsonParseException("Invalid \"rolls\" value. Expected number or string, got " + primitive.toString());
        }

        if (rollsElement.isJsonObject()) {
            JsonObject rollsObject = rollsElement.getAsJsonObject();
            int min = rollsObject.has("min") ? parsePositiveInt(rollsObject.get("min").getAsString(), "rolls.min") : 0;
            int max = rollsObject.has("max") ? parsePositiveInt(rollsObject.get("max").getAsString(), "rolls.max") : min;

            JsonObject normalizedRange = new JsonObject();
            normalizedRange.addProperty("min", Math.min(min, max));
            normalizedRange.addProperty("max", Math.max(min, max));
            return normalizedRange;
        }

        throw new JsonParseException("Invalid \"rolls\" value. Expected number, string or object");
    }

    @NotNull
    private static JsonArray normalizeGroupedItemsElement(@NotNull JsonElement itemsElement, @NotNull String categoryName, @NotNull String poolId) {
        JsonArray normalizedItems = new JsonArray();

        if (itemsElement.isJsonArray()) {
            for (JsonElement itemElement : itemsElement.getAsJsonArray()) {
                if (!itemElement.isJsonObject()) {
                    throw new JsonParseException("Invalid grouped item in pool \"" + categoryName + " -> " + poolId + "\". Expected object, got " + itemElement.getClass().getSimpleName());
                }

                normalizedItems.add(normalizeGroupedItemObject(itemElement.getAsJsonObject(), false, categoryName, poolId));
            }

            return normalizedItems;
        }

        if (itemsElement.isJsonObject()) {
            JsonObject itemsObject = itemsElement.getAsJsonObject();

            if (itemsObject.has("type")) {
                normalizedItems.add(normalizeGroupedItemObject(itemsObject, false, categoryName, poolId));
                return normalizedItems;
            }

            for (Entry<String, JsonElement> itemEntry : itemsObject.entrySet()) {
                if (!itemEntry.getValue().isJsonObject()) {
                    throw new JsonParseException("Invalid grouped item id \"" + itemEntry.getKey() + "\" in pool \"" + categoryName + " -> " + poolId + "\". Expected object, got " + itemEntry.getValue().getClass().getSimpleName());
                }

                normalizedItems.add(normalizeGroupedItemObject(itemEntry.getValue().getAsJsonObject(), false, categoryName, poolId));
            }

            return normalizedItems;
        }

        throw new JsonParseException("Invalid grouped items for pool \"" + categoryName + " -> " + poolId + "\". Expected object or array, got " + itemsElement.getClass().getSimpleName());
    }

    @NotNull
    private static JsonObject normalizeGroupedItemObject(@NotNull JsonObject itemObject, boolean stripPoolMetadata, @NotNull String categoryName, @NotNull String poolId) {
        JsonObject normalizedItem = new JsonObject();

        for (Entry<String, JsonElement> entry : itemObject.entrySet()) {
            String key = entry.getKey();
            if (stripPoolMetadata && (key.equals("rolls") || key.equals("chance") || key.equals("name") || key.equals("items"))) {
                continue;
            }

            JsonElement value = entry.getValue();
            if (key.equals("enchantments")) {
                normalizedItem.add(key, normalizeEnchantmentsElement(value, categoryName, poolId));
                continue;
            }

            normalizedItem.add(key, value);
        }

        if (!normalizedItem.has("weight")) {
            normalizedItem.addProperty("weight", 1.0);
        }

        if (!normalizedItem.has("type")) {
            throw new JsonParseException("Missing \"type\" in grouped item pool \"" + categoryName + " -> " + poolId + "\"");
        }

        return normalizedItem;
    }

    @NotNull
    private static JsonElement normalizeEnchantmentsElement(@NotNull JsonElement enchantmentsElement, @NotNull String categoryName, @NotNull String poolId) {
        if (enchantmentsElement.isJsonObject()) {
            return enchantmentsElement;
        }

        JsonObject normalizedEnchantments = new JsonObject();

        if (enchantmentsElement.isJsonArray()) {
            for (JsonElement enchantmentElement : enchantmentsElement.getAsJsonArray()) {
                if (enchantmentElement.isJsonPrimitive() && enchantmentElement.getAsJsonPrimitive().isString()) {
                    addEnchantmentFromString(normalizedEnchantments, enchantmentElement.getAsString(), categoryName, poolId);
                    continue;
                }

                if (enchantmentElement.isJsonObject()) {
                    for (Entry<String, JsonElement> enchantmentEntry : enchantmentElement.getAsJsonObject().entrySet()) {
                        normalizedEnchantments.add(enchantmentEntry.getKey(), enchantmentEntry.getValue());
                    }
                    continue;
                }

                throw new JsonParseException("Invalid enchantment entry in grouped item pool \"" + categoryName + " -> " + poolId + "\". Expected string or object");
            }

            return normalizedEnchantments;
        }

        if (enchantmentsElement.isJsonPrimitive() && enchantmentsElement.getAsJsonPrimitive().isString()) {
            addEnchantmentFromString(normalizedEnchantments, enchantmentsElement.getAsString(), categoryName, poolId);
            return normalizedEnchantments;
        }

        throw new JsonParseException("Invalid enchantments element in grouped item pool \"" + categoryName + " -> " + poolId + "\". Expected object, string or array");
    }

    private static void addEnchantmentFromString(@NotNull JsonObject enchantmentsObject, @NotNull String rawValue, @NotNull String categoryName, @NotNull String poolId) {
        String enchantmentEntry = rawValue.trim();
        int separatorIndex = enchantmentEntry.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex >= enchantmentEntry.length() - 1) {
            throw new JsonParseException("Invalid enchantment format \"" + rawValue + "\" in grouped item pool \"" + categoryName + " -> " + poolId + "\". Expected \"<namespaced_key>: <level>\"");
        }

        String enchantmentKey = enchantmentEntry.substring(0, separatorIndex).trim();
        String enchantmentLevel = enchantmentEntry.substring(separatorIndex + 1).trim();

        if (enchantmentKey.isEmpty()) {
            throw new JsonParseException("Invalid enchantment key in grouped item pool \"" + categoryName + " -> " + poolId + "\"");
        }

        int rangeSeparatorIndex = enchantmentLevel.indexOf('-');
        if (rangeSeparatorIndex != -1) {
            int min = parsePositiveInt(enchantmentLevel.substring(0, rangeSeparatorIndex), "enchantment range min");
            int max = parsePositiveInt(enchantmentLevel.substring(rangeSeparatorIndex + 1), "enchantment range max");

            JsonObject rangeObject = new JsonObject();
            rangeObject.addProperty("min", Math.min(min, max));
            rangeObject.addProperty("max", Math.max(min, max));
            enchantmentsObject.add(enchantmentKey, rangeObject);
            return;
        }

        enchantmentsObject.addProperty(enchantmentKey, parsePositiveInt(enchantmentLevel, "enchantment level"));
    }

    private static int parsePositiveInt(@NotNull String value, @NotNull String fieldName) {
        try {
            return Math.max(Integer.parseInt(value.trim()), 0);
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid integer value for \"" + fieldName + "\": " + value);
        }
    }

    /**
     * Load and parse all DragonLootTable objects from the dragons folder.
     *
     * @param plugin the plugin instance
     *
     * @return all parsed DragonLootTable objects
     */
    @NotNull
    public static List<@NotNull DragonLootTable> loadLootTables(@NotNull DragonEggDrop plugin) {
        Preconditions.checkArgument(plugin != null, "plugin must not be null");

        Logger logger = plugin.getLogger();
        List<DragonLootTable> lootTables = new ArrayList<>();

        // Return empty list if the folder was just created
        if (plugin.getLootTableDirectory().mkdir()) {
            return lootTables;
        }

        boolean suggestLinter = false;

        for (File file : plugin.getLootTableDirectory().listFiles((file, name) -> JsonUtils.hasSupportedFileExtension(name))) {
            if (file.getName().contains(" ")) {
                logger.warning("Dragon loot table files must not contain spaces (File=\"" + file.getName() + "\")! Ignoring...");
                continue;
            }

            try {
                DragonLootTable lootTable = DragonLootTable.fromFile(file);

                // Checking for existing templates
                if (lootTables.stream().anyMatch(t -> t.getId().matches(lootTable.getId()))) {
                    logger.warning("Duplicate dragon loot table with file name " + file.getName() + ". Ignoring...");
                    continue;
                }

                lootTables.add(lootTable);
            } catch (JsonParseException e) {
                logger.warning("Could not load loot table \"" + file.getName() + "\"");
                logger.warning(e.getMessage());
                suggestLinter = true;
            }
        }

        if (suggestLinter) {
            logger.warning("Ensure all values are correct and run the file through a JSON/YAML validator");
        }

        return lootTables;
    }

}
