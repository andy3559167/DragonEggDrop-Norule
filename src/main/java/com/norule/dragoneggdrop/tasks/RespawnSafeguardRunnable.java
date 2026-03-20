package com.norule.dragoneggdrop.tasks;

import com.google.common.base.Preconditions;
import com.norule.dragoneggdrop.DragonEggDrop;
import com.norule.dragoneggdrop.scheduler.CompatBukkitRunnable;
import com.norule.dragoneggdrop.world.EndWorldWrapper;

import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.boss.DragonBattle.RespawnPhase;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a BukkitRunnable that ensures the respawn state of the Ender Dragon due to
 * issues in vanilla dragon respawning mechanics. If a dragon has not respawned within 40
 * seconds (800 ticks), the respawning process will restart.
 */
final class RespawnSafeguardRunnable extends CompatBukkitRunnable {

    // Respawn takes about 30 seconds. Timeout at 35 seconds
    private static final long TIMEOUT_PERIOD_TICKS = 700L;
    private static final long RETRY_PERIOD_TICKS = 200L;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final DragonEggDrop plugin;
    private final EndWorldWrapper world;
    private final DragonBattle battle;
    private final int retryAttempt;

    private RespawnSafeguardRunnable(@NotNull DragonEggDrop plugin, @NotNull World world, @NotNull DragonBattle battle, int retryAttempt, long delayTicks) {
        this.plugin = plugin;
        this.world = EndWorldWrapper.of(world);
        this.battle = battle;
        this.retryAttempt = retryAttempt;

        Location endPortalLocation = battle.getEndPortalLocation();
        Location schedulerLocation = endPortalLocation != null ? endPortalLocation : new Location(world, 0.5D, 64.0D, 0.5D);
        this.runTaskLaterCompat(plugin.getTaskScheduler(), schedulerLocation, delayTicks);
    }

    @Override
    public void run() {
        World bukkitWorld = world.getWorld();
        Collection<@NotNull EnderCrystal> crystals = bukkitWorld.getEntitiesByClass(EnderCrystal.class);
        EnderDragon enderDragon = battle.getEnderDragon();

        // Respawn has completed.
        if (enderDragon != null && !enderDragon.isDead()) {
            crystals.forEach(c -> {
                c.setInvulnerable(false);
                c.setBeamTarget(null);
            });
            return;
        }

        RespawnPhase phase = battle.getRespawnPhase();
        boolean stillRespawning = phase != RespawnPhase.NONE && phase != RespawnPhase.END;
        if (stillRespawning && retryAttempt < MAX_RETRY_ATTEMPTS) {
            this.plugin.getLogger().warning("Dragon respawn is still in phase " + phase + " after safeguard timeout. Retrying check...");
            new RespawnSafeguardRunnable(this.plugin, bukkitWorld, this.battle, retryAttempt + 1, RETRY_PERIOD_TICKS);
            return;
        }

        this.plugin.getLogger().warning("Something went wrong! Had to forcibly reset dragon battle...");

        this.battle.resetCrystals();
        crystals.forEach(Entity::remove); // Remove pre-existing crystals

        this.world.startRespawn(0);
    }

    /**
     * Commence a new RespawnSafeguardRunnable. This should only be invoked in a
     * RespawnRunnable.
     *
     * @param plugin the plugin instance
     * @param world the battle's world
     * @param battle the battle to check
     *
     * @return the running RespawnSafeguardRunnable instance
     */
    @NotNull
    protected static RespawnSafeguardRunnable newTimeout(@NotNull DragonEggDrop plugin, @NotNull World world, @NotNull DragonBattle battle) {
        Preconditions.checkArgument(plugin != null, "plugin must not be null");
        Preconditions.checkArgument(world != null, "world must not be null");
        Preconditions.checkArgument(battle != null, "battle must not be null");

        return new RespawnSafeguardRunnable(plugin, world, battle, 0, TIMEOUT_PERIOD_TICKS);
    }

}
