package com.norule.dragoneggdrop.listeners;

import com.norule.dragoneggdrop.DragonEggDrop;
import com.norule.dragoneggdrop.api.BattleState;
import com.norule.dragoneggdrop.api.BattleStateChangeEvent;
import com.norule.dragoneggdrop.dragon.DamageHistory;
import com.norule.dragoneggdrop.dragon.DragonTemplate;
import com.norule.dragoneggdrop.placeholder.DragonEggDropPlaceholders;
import com.norule.dragoneggdrop.scheduler.CompatBukkitRunnable;
import com.norule.dragoneggdrop.tasks.DragonDeathRunnable;
import com.norule.dragoneggdrop.utils.ActionBarUtil;
import com.norule.dragoneggdrop.utils.DEDConstants;
import com.norule.dragoneggdrop.world.DragonBattleRecord;
import com.norule.dragoneggdrop.world.EndWorldWrapper;
import com.norule.dragoneggdrop.world.PortalCrystal;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public final class DragonLifeListeners implements Listener {

    private static final ItemStack END_CRYSTAL_ITEM = new ItemStack(Material.END_CRYSTAL);

    private final DragonEggDrop plugin;

    public DragonLifeListeners(@NotNull DragonEggDrop plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    private void onDragonSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        World world = dragon.getWorld();
        if (world.getEnvironment() != Environment.THE_END) {
            return;
        }

        DragonBattle dragonBattle = dragon.getDragonBattle();
        if (dragonBattle == null) {
            return;
        }

        if (plugin.getConfig().getStringList(DEDConstants.CONFIG_DISABLED_WORLDS).contains(world.getName())) {
            // These need to be reset in case there was a template from before
            BossBar bossBar = dragonBattle.getBossBar();
            dragon.setCustomName(null);
            bossBar.setTitle(dragon.getName());
            bossBar.setStyle(BarStyle.SOLID);
            bossBar.setColor(BarColor.PINK);
            return;
        }

        EndWorldWrapper worldWrapper = EndWorldWrapper.of(world);
        DragonTemplate template = worldWrapper.getRespawningTemplate();
        if (plugin.getConfig().getBoolean(DEDConstants.CONFIG_STRICT_COUNTDOWN) && worldWrapper.isRespawnInProgress()) {
            worldWrapper.stopRespawn();
        }

        worldWrapper.setActiveTemplate((template != null) ? template : (template = plugin.getDragonTemplateRegistry().getRandomTemplate()));
        worldWrapper.setRespawningTemplate(null);

        if (template == null) { // Theoretically impossible but we're going to be absolutely certain here
            return;
        }

        template.applyToBattle(dragon, dragonBattle);

        if (template.shouldAnnounceSpawn()) {
            List<@NotNull String> announcement = template.getSpawnAnnouncement();
            // Cannot use p::sendMessage here. Compile-error with Maven. Compiler assumes the wrong method overload
            Bukkit.getOnlinePlayers().forEach(p -> announcement.forEach(m -> p.sendMessage(ChatColor.translateAlternateColorCodes('&', DragonEggDropPlaceholders.inject(p, m)))));
        }

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.DRAGON_RESPAWNING, BattleState.BATTLE_COMMENCED);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);
    }

    @EventHandler
    private void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) {
            return;
        }

        EnderDragon dragon = (EnderDragon) event.getEntity();
        World world = event.getEntity().getWorld();
        if (plugin.getConfig().getStringList(DEDConstants.CONFIG_DISABLED_WORLDS).contains(world.getName())) {
            return;
        }

        DragonBattle dragonBattle = dragon.getDragonBattle();
        EndWorldWrapper worldWrapper = EndWorldWrapper.of(world);
        worldWrapper.setDragonDying(true);

        // Record the battle
        DragonTemplate dragonTemplate = worldWrapper.getActiveTemplate();
        if (dragonTemplate == null) {
            dragonTemplate = plugin.resolveTemplateForDragon(dragon);
            if (dragonTemplate == null) {
                dragonTemplate = plugin.getDragonTemplateRegistry().getRandomTemplate();
            }

            if (dragonTemplate != null) {
                worldWrapper.setActiveTemplate(dragonTemplate);
                plugin.getLogger().warning("Active dragon template was missing on death in world " + world.getName() + ". Recovered with template " + dragonTemplate.getId());
            }
        }

        if (dragonTemplate != null) {
            DragonBattleRecord record = new DragonBattleRecord(worldWrapper, dragonTemplate, DamageHistory.forEntity(dragon), System.currentTimeMillis(), worldWrapper.getLootTableOverride());
            worldWrapper.recordDragonBattle(record);
        }

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.BATTLE_COMMENCED, BattleState.BATTLE_END);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);

        new CompatBukkitRunnable() {
            @Override
            public void run() {
                if (dragon.getDeathAnimationTicks() >= 185) { // Dragon is dead at 200
                    new DragonDeathRunnable(plugin, worldWrapper, dragon);
                    this.cancel();
                }
            }
        }.runTaskTimerCompat(plugin.getTaskScheduler(), dragon.getLocation(), 0L, 1L);
    }

    @EventHandler
    private void onAttemptRespawn(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || block.getType() != Material.BEDROCK || item == null || item.getType() != Material.END_CRYSTAL
                || plugin.getConfig().getBoolean(DEDConstants.CONFIG_ALLOW_CRYSTAL_RESPAWNS) || player.hasPermission(DEDConstants.PERMISSION_OVERRIDE_CRYSTALS)) {
            return;
        }

        World world = player.getWorld();
        if (plugin.getConfig().getStringList(DEDConstants.CONFIG_DISABLED_WORLDS).contains(world.getName())) {
            return;
        }

        List<@NotNull EnderCrystal> crystals = PortalCrystal.getAllSpawnedCrystals(world);

        // Check for 3 crystals because PlayerInteractEvent is fired first
        if (crystals.size() < 3) {
            return;
        }

        DragonBattle battle = world.getEnderDragonBattle();
        if (battle == null) {
            return;
        }

        Location endPortalLocation = battle.getEndPortalLocation();
        if (endPortalLocation == null) {
            return;
        }

        Vector portalLocationVector = endPortalLocation.toVector();
        for (EnderCrystal crystal : crystals) {
            Location location = crystal.getLocation();
            location.getBlock().setType(Material.AIR); // Remove fire

            Item droppedCrystal = world.dropItem(location, END_CRYSTAL_ITEM);
            droppedCrystal.setVelocity(crystal.getLocation().toVector().subtract(portalLocationVector).normalize().multiply(0.15).setY(0.5));

            crystal.remove();
        }

        ActionBarUtil.sendActionBar(ChatColor.RED + "You cannot manually respawn a dragon!", player, false);
        player.sendMessage(ChatColor.RED + "You cannot manually respawn a dragon!");
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.5F);
        event.setCancelled(true);
    }

}
