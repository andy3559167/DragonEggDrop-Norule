package com.norule.dragoneggdrop.tasks;

import com.norule.dragoneggdrop.DragonEggDrop;
import com.norule.dragoneggdrop.api.BattleState;
import com.norule.dragoneggdrop.api.BattleStateChangeEvent;
import com.norule.dragoneggdrop.dragon.DragonTemplate;
import com.norule.dragoneggdrop.dragon.loot.DragonLootTable;
import com.norule.dragoneggdrop.particle.AnimatedParticleSession;
import com.norule.dragoneggdrop.particle.ParticleShapeDefinition;
import com.norule.dragoneggdrop.scheduler.CompatBukkitRunnable;
import com.norule.dragoneggdrop.utils.DEDConstants;
import com.norule.dragoneggdrop.world.EndWorldWrapper;
import com.norule.dragoneggdrop.world.RespawnReason;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a BukkitRunnable that handles the generation and particle display of the
 * loot after the Ender Dragon's death.
 */
public class DragonDeathRunnable extends CompatBukkitRunnable {

    private final DragonEggDrop plugin;

    private AnimatedParticleSession particleSession;

    private final EndWorldWrapper worldWrapper;
    private final DragonTemplate template;
    private final Location portalLocation;

    private int lightningAmount;

    private EnderDragon dragon;
    private boolean respawnDragon = false;

    /**
     * Construct a new DragonDeathRunnable object.
     *
     * @param plugin an instance of the DragonEggDrop plugin
     * @param worldWrapper the world in which the dragon death is taking place
     * @param dragon the dragon dying in this runnable
     */
    public DragonDeathRunnable(@NotNull DragonEggDrop plugin, @NotNull EndWorldWrapper worldWrapper, @NotNull EnderDragon dragon) {
        this.plugin = plugin;
        this.worldWrapper = worldWrapper;
        this.dragon = dragon;
        this.template = worldWrapper.getActiveTemplate();

        FileConfiguration config = plugin.getConfig();
        ParticleShapeDefinition particleShapeDefinition = (template != null ? template.getParticleShapeDefinition() : null);
        this.lightningAmount = config.getInt(DEDConstants.CONFIG_LIGHTNING_AMOUNT);

        // Portal location
        DragonBattle dragonBattle = dragon.getDragonBattle();
        Location endPortalLocation = dragonBattle != null ? dragonBattle.getEndPortalLocation() : null;

        if (endPortalLocation != null) {
            this.portalLocation = endPortalLocation.add(0.5, 4.0, 0.5);
        } else {
            this.portalLocation = new Location(worldWrapper.getWorld(), 0.5, 63, 0.5);
            this.portalLocation.setY(worldWrapper.getWorld().getHighestBlockYAt(0, 0) + 1);
        }

        this.respawnDragon = config.getBoolean(DEDConstants.CONFIG_RESPAWN_ON_DEATH, false);
        this.runTaskTimerCompat(plugin.getTaskScheduler(), this.portalLocation, 0L, 1L);

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.BATTLE_END, BattleState.PARTICLES_START);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);

        if (particleShapeDefinition != null) {
            this.particleSession = particleShapeDefinition.createSession(worldWrapper.getWorld(), portalLocation.getX(), portalLocation.getZ());
        }
    }

    @Override
    public void run() {
        if (particleSession != null) {
            this.particleSession.tick();

            if (!particleSession.shouldStop()) {
                return;
            }
        }

        // Particles finished, place reward

        // Summon Zeus!
        for (int i = 0; i < lightningAmount; i++) {
            this.worldWrapper.getWorld().strikeLightning(portalLocation).setMetadata(DEDConstants.METADATA_LOOT_LIGHTNING, new FixedMetadataValue(plugin, true));
        }

        DragonBattle dragonBattle = dragon.getDragonBattle();
        DragonTemplate effectiveTemplate = (this.template != null ? this.template : this.worldWrapper.getActiveTemplate());
        if (effectiveTemplate == null) {
            effectiveTemplate = this.plugin.resolveTemplateForDragon(dragon);
            if (effectiveTemplate == null) {
                effectiveTemplate = this.plugin.getDragonTemplateRegistry().getRandomTemplate();
            }

            if (effectiveTemplate != null) {
                this.plugin.getLogger().warning("Active dragon template was missing during loot generation in world " + this.worldWrapper.getWorld().getName() + ". Recovered with template " + effectiveTemplate.getId());
            }
        }

        this.worldWrapper.setActiveTemplate(null);
        this.worldWrapper.setDragonDying(false);

        if (effectiveTemplate != null) {
            DragonLootTable lootTable = worldWrapper.hasLootTableOverride() ? worldWrapper.getLootTableOverride() : effectiveTemplate.getLootTable();
            if (lootTable != null) {
                Player killer = findDragonKiller(dragon);
                if (dragonBattle != null) {
                    lootTable.generate(dragonBattle, effectiveTemplate, killer);
                }
                else {
                    lootTable.generate(portalLocation.getBlock(), effectiveTemplate, killer);
                }
            }
            else {
                this.plugin.getLogger().warning("Could not generate loot for template " + effectiveTemplate.getId() + ". Invalid loot table. Is \"loot\" defined in the template?");

                // Let's just generate an egg instead...
                portalLocation.getBlock().setType(Material.DRAGON_EGG);
            }

            this.worldWrapper.setLootTableOverride(null); // Reset the loot table override. Use the template's loot table next instead
        }
        else {
            this.plugin.getLogger().warning("Could not generate dragon loot because no dragon templates are available");
            portalLocation.getBlock().setType(Material.DRAGON_EGG);
        }

        if (respawnDragon && worldWrapper.getWorld().getPlayers().size() > 0) {
            this.worldWrapper.startRespawn(RespawnReason.DEATH);
        }

        BattleStateChangeEvent bscEventCrystals = new BattleStateChangeEvent(dragonBattle, dragon, BattleState.PARTICLES_START, BattleState.LOOT_SPAWN);
        Bukkit.getPluginManager().callEvent(bscEventCrystals);
        this.cancel();
    }

    private Player findDragonKiller(EnderDragon dragon) {
        EntityDamageEvent lastDamageCause = dragon.getLastDamageCause();
        if (!(lastDamageCause instanceof EntityDamageByEntityEvent)) {
            return null;
        }

        Entity damager = ((EntityDamageByEntityEvent) lastDamageCause).getDamager();
        if (damager instanceof Player) {
            return (Player) damager;
        }

        else if (damager instanceof Projectile) {
            ProjectileSource projectileSource = ((Projectile) damager).getShooter();
            if (!(projectileSource instanceof Player)) {
                return null; // Give up
            }

            return (Player) projectileSource;
        }

        return null;
    }

}
