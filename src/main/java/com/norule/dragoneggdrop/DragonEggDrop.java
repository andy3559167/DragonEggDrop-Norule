package com.norule.dragoneggdrop;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.norule.dragoneggdrop.commands.CommandDragonEggDrop;
import com.norule.dragoneggdrop.commands.CommandDragonParticle;
import com.norule.dragoneggdrop.commands.CommandDragonRespawn;
import com.norule.dragoneggdrop.commands.CommandDragonTemplate;
import com.norule.dragoneggdrop.commons.util.UpdateChecker;
import com.norule.dragoneggdrop.commons.util.UpdateChecker.UpdateReason;
import com.norule.dragoneggdrop.dragon.DamageHistory;
import com.norule.dragoneggdrop.dragon.DragonTemplate;
import com.norule.dragoneggdrop.dragon.loot.DragonLootTable;
import com.norule.dragoneggdrop.listeners.DamageHistoryListener;
import com.norule.dragoneggdrop.listeners.DragonLifeListeners;
import com.norule.dragoneggdrop.listeners.LootListeners;
import com.norule.dragoneggdrop.listeners.PortalClickListener;
import com.norule.dragoneggdrop.listeners.RespawnListeners;
import com.norule.dragoneggdrop.particle.ParticleShapeDefinition;
import com.norule.dragoneggdrop.particle.condition.ConditionFactory;
import com.norule.dragoneggdrop.placeholder.DragonEggDropPlaceholders;
import com.norule.dragoneggdrop.registry.DragonTemplateRegistry;
import com.norule.dragoneggdrop.registry.HashRegistry;
import com.norule.dragoneggdrop.registry.Registry;
import com.norule.dragoneggdrop.scheduler.ScheduledTaskHandle;
import com.norule.dragoneggdrop.scheduler.ServerTaskScheduler;
import com.norule.dragoneggdrop.utils.DEDConstants;
import com.norule.dragoneggdrop.utils.DataFileUtils;
import com.norule.dragoneggdrop.world.EndWorldWrapper;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.boss.DragonBattle;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
/**
 * DragonEggDrop, reward your players with a dragon egg/loot chest after every ender
 * dragon battle, in grand fashion!
 *
 * @author NinjaStix
 * @author Parker Hawke - Choco
 * @author Andy (Maintainer)
 */
public final class DragonEggDrop extends JavaPlugin {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String CHAT_PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.GRAY + "DED" + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    private static DragonEggDrop instance;

    private DragonTemplateRegistry dragonTemplateRegistry = new DragonTemplateRegistry();
    private Registry<@NotNull DragonLootTable> lootTableRegistry = new HashRegistry<>();
    private Registry<@NotNull ParticleShapeDefinition> particleShapeDefinitionRegistry = new HashRegistry<>();

    private ScheduledTaskHandle updateTask;
    private ServerTaskScheduler taskScheduler;
    private NamespacedKey dragonTemplateKey;
    private File tempDataFile;

    private File dragonTemplateDirectory, lootTableDirectory, particleDirectory;

    @Override
    public void onEnable() {
        instance = this;
        this.taskScheduler = new ServerTaskScheduler(this);
        this.dragonTemplateKey = new NamespacedKey(this, DEDConstants.PDC_DRAGON_TEMPLATE_ID);
        this.saveDefaultConfig();

        // Load default templates and loot tables
        if ((dragonTemplateDirectory = new File(getDataFolder(), "dragons")).mkdirs()) {
            this.saveDefaultDirectory("dragons");
        }
        if ((lootTableDirectory = new File(getDataFolder(), "loot_tables")).mkdirs()) {
            this.saveDefaultDirectory("loot_tables");
        }
        if ((particleDirectory = new File(getDataFolder(), "particles")).mkdirs()) {
            this.saveDefaultDirectory("particles");
        }

        // Load all necessary data into memory
        DataFileUtils.reloadInMemoryData(this, true);

        // Load temp data (reload support)
        this.tempDataFile = new File(getDataFolder(), "tempData.json");
        if (tempDataFile.exists()) {
            this.getLogger().info("Reading temporary data from previous server session...");
            DataFileUtils.readTempData(this, tempDataFile);
            this.tempDataFile.delete();
        }

        this.recoverActiveDragonTemplates();

        // Register events
        this.getLogger().info("Registering event listeners");
        PluginManager manager = Bukkit.getPluginManager();
        manager.registerEvents(new DamageHistoryListener(this), this);
        manager.registerEvents(new DragonLifeListeners(this), this);
        manager.registerEvents(new LootListeners(this), this);
        manager.registerEvents(new PortalClickListener(this), this);
        manager.registerEvents(new RespawnListeners(this), this);

        // Register commands
        this.getLogger().info("Registering command executors and tab completion");
        this.registerCommandSafely("dragoneggdrop", new CommandDragonEggDrop(this));
        this.registerCommandSafely("dragonrespawn", new CommandDragonRespawn(this));
        this.registerCommandSafely("dragontemplate", new CommandDragonTemplate(this));
        this.registerCommandSafely("dragonparticle", new CommandDragonParticle(this));

        // Register external placeholder functionality
        DragonEggDropPlaceholders.registerPlaceholders(this, manager);

        // Enable metrics
        if (getConfig().getBoolean(DEDConstants.CONFIG_METRICS, true)) {
            final int pluginId = 30325; // https://bstats.org/what-is-my-plugin-id
            new Metrics(this, pluginId);
            this.getLogger().info("Successfully enabled metrics. Thanks for keeping these enabled!");
        }

        // Update check
        UpdateChecker.init(this, 133579);
        if (getConfig().getBoolean(DEDConstants.CONFIG_PERFORM_UPDATE_CHECKS, true)) {
            this.updateTask = taskScheduler.runAsyncTimer(0L, 432000L, () -> {
                UpdateChecker.get().requestUpdateCheck().whenComplete((result, exception) -> {
                    if (result.requiresUpdate()) {
                        this.getLogger().info(String.format("An update is available! DragonEggDrop %s may be downloaded on SpigotMC", result.getNewestVersion()));
                        Bukkit.getOnlinePlayers().stream().filter(Player::isOp).forEach(p -> sendMessage(p, "A new version is available for download (Version " + result.getNewestVersion() + ")"));
                        return;
                    }

                    UpdateReason reason = result.getReason();
                    if (reason == UpdateReason.UP_TO_DATE) {
                        this.getLogger().info(String.format("Your version of DragonEggDrop (%s) is up to date!", result.getNewestVersion()));
                    }
                    else if (reason == UpdateReason.UNRELEASED_VERSION) {
                        getLogger().info(String.format("Your version of DragonEggDrop (%s) is more recent than the one publicly available. Are you on a development build?", result.getNewestVersion()));
                    }
                    else {
                        getLogger().warning("Could not check for a new version of DragonEggDrop. Reason: " + reason);
                    }
                });
            }); // 6 hours
        }
    }

    @Override
    public void onDisable() {
        if (updateTask != null) {
            this.updateTask.cancel();
        }

        if (tempDataFile != null) {
            try {
                DataFileUtils.writeTempData(tempDataFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Clear the world wrappers
        EndWorldWrapper.getAll().forEach(EndWorldWrapper::stopRespawn);
        EndWorldWrapper.clear();

        this.particleShapeDefinitionRegistry.clear();
        this.lootTableRegistry.clear();
        this.dragonTemplateRegistry.clear();

        ConditionFactory.clear();
        DamageHistory.clearGlobalDamageHistory();
    }

    /**
     * Get the dragon template registry.
     *
     * @return the dragon template registry.
     */
    @NotNull
    public DragonTemplateRegistry getDragonTemplateRegistry() {
        return dragonTemplateRegistry;
    }

    /**
     * Get the loot table registry for all dragon loot tables.
     *
     * @return the loot table registry
     */
    @NotNull
    public Registry<@NotNull DragonLootTable> getLootTableRegistry() {
        return lootTableRegistry;
    }

    /**
     * Get the particle shape definition registry.
     *
     * @return the particle shapde definition registry
     */
    @NotNull
    public Registry<@NotNull ParticleShapeDefinition> getParticleShapeDefinitionRegistry() {
        return particleShapeDefinitionRegistry;
    }

    /**
     * Get the cross-platform task scheduler.
     *
     * @return task scheduler
     */
    @NotNull
    public ServerTaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    @NotNull
    public NamespacedKey getDragonTemplateKey() {
        return dragonTemplateKey;
    }

    /**
     * Get the directory in which dragon templates are located.
     *
     * @return the dragon templates directory
     */
    @NotNull
    public File getDragonTemplateDirectory() {
        return dragonTemplateDirectory;
    }

    /**
     * Get the directory in which loot tables are located.
     *
     * @return the loot table directory
     */
    @NotNull
    public File getLootTableDirectory() {
        return lootTableDirectory;
    }

    /**
     * Get the directory in which particle shape definitions are located.
     *
     * @return the particle shape definition directory
     */
    @NotNull
    public File getParticleDirectory() {
        return particleDirectory;
    }

    /**
     * Get the DragonEggDrop instance.
     *
     * @return this instance
     */
    @NotNull
    public static DragonEggDrop getInstance() {
        return instance;
    }

    /**
     * Send a message to a command sender with the DragonEggDrop chat prefix.
     *
     * @param sender the sender to which the message should be sent
     * @param message the message to send
     *
     * @param <T> command sender type
     */
    @NotNull
    public static <T extends CommandSender> void sendMessage(@NotNull T sender, @NotNull String message) {
        Preconditions.checkArgument(sender != null, "sender must not be null");
        Preconditions.checkArgument(message != null, "message must not be null");

        sender.sendMessage(CHAT_PREFIX + message);
    }

    private void recoverActiveDragonTemplates() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != Environment.THE_END) {
                continue;
            }

            if (getConfig().getStringList(DEDConstants.CONFIG_DISABLED_WORLDS).contains(world.getName())) {
                continue;
            }

            DragonBattle battle = world.getEnderDragonBattle();
            if (battle == null) {
                continue;
            }

            EnderDragon dragon = battle.getEnderDragon();
            if (dragon == null || dragon.isDead()) {
                continue;
            }

            EndWorldWrapper wrapper = EndWorldWrapper.of(world);
            DragonTemplate template = wrapper.getActiveTemplate();
            if (template == null) {
                template = this.resolveTemplateForDragon(dragon);
                if (template == null) {
                    template = this.dragonTemplateRegistry.getRandomTemplate();
                }

                if (template == null) {
                    continue;
                }

                wrapper.setActiveTemplate(template);
                this.getLogger().warning("Recovered active dragon template for existing dragon in world " + world.getName() + " after startup sync with template " + template.getId());
            }

            template.applyToBattle(dragon, battle);
        }
    }

    @Nullable
    public DragonTemplate resolveTemplateForDragon(@NotNull EnderDragon dragon) {
        Preconditions.checkArgument(dragon != null, "dragon must not be null");

        String persistedTemplateId = dragon.getPersistentDataContainer().get(this.dragonTemplateKey, PersistentDataType.STRING);
        if (persistedTemplateId != null) {
            DragonTemplate persistedTemplate = this.dragonTemplateRegistry.get(persistedTemplateId);
            if (persistedTemplate != null) {
                return persistedTemplate;
            }
        }

        String normalizedCustomName = normalizeTemplateKey(dragon.getCustomName());
        String normalizedName = normalizeTemplateKey(dragon.getName());

        for (DragonTemplate template : this.dragonTemplateRegistry.values()) {
            if (matchesTemplate(template, normalizedCustomName) || matchesTemplate(template, normalizedName)) {
                return template;
            }
        }

        return null;
    }

    private boolean matchesTemplate(@NotNull DragonTemplate template, @Nullable String normalizedDragonName) {
        if (normalizedDragonName == null || normalizedDragonName.isEmpty()) {
            return false;
        }

        String normalizedTemplateId = normalizeTemplateKey(template.getId());
        if (normalizedTemplateId != null && normalizedTemplateId.equals(normalizedDragonName)) {
            return true;
        }

        String normalizedTemplateName = normalizeTemplateKey(template.getName());
        return normalizedTemplateName != null && normalizedTemplateName.equals(normalizedDragonName);
    }

    @Nullable
    private String normalizeTemplateKey(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String stripped = ChatColor.stripColor(value);
        if (stripped == null) {
            return null;
        }

        String normalized = stripped.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        normalized = normalized.replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }
    private void saveDefaultDirectory(@NotNull String directory) {
        Preconditions.checkArgument(directory != null, "directory must not be null");

        try (JarFile jar = new JarFile(getFile())) {
            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();

                if (!name.startsWith(directory + "/") || entry.isDirectory()) {
                    continue;
                }

                this.saveResource(name, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void registerCommandSafely(@NotNull String commandString, @NotNull CommandExecutor executor) {
        PluginCommand command = getCommand(commandString);
        if (command == null) {
            return;
        }

        command.setExecutor(executor);

        if (executor instanceof TabCompleter) {
            command.setTabCompleter((TabCompleter) executor);
        }
    }

}

