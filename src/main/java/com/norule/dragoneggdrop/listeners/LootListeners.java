package com.norule.dragoneggdrop.listeners;

import com.norule.dragoneggdrop.DragonEggDrop;
import com.norule.dragoneggdrop.dragon.DragonTemplate;
import com.norule.dragoneggdrop.dragon.loot.DragonLootTable;
import com.norule.dragoneggdrop.dragon.loot.elements.DragonLootElementEgg;
import com.norule.dragoneggdrop.placeholder.DragonEggDropPlaceholders;
import com.norule.dragoneggdrop.utils.DEDConstants;
import com.norule.dragoneggdrop.world.DragonBattleRecord;
import com.norule.dragoneggdrop.world.EndWorldWrapper;

import java.util.List;
import java.util.stream.Collectors;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class LootListeners implements Listener {

    private final DragonEggDrop plugin;

    public LootListeners(DragonEggDrop plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        World world = item.getWorld();

        if (world.getEnvironment() != Environment.THE_END || plugin.getConfig().getStringList(DEDConstants.CONFIG_DISABLED_WORLDS).contains(world.getName())) {
            return;
        }

        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.DRAGON_EGG || stack.hasItemMeta()) {
            return;
        }

        DragonBattleRecord previousDragonBattle = EndWorldWrapper.of(world).getPreviousDragonBattle();
        if (previousDragonBattle == null) {
            return;
        }

        DragonLootTable lootTable = previousDragonBattle.getLootTable();
        if (lootTable == null) {
            return;
        }

        DragonLootElementEgg egg = lootTable.getEgg();
        if (egg == null) {
            return;
        }

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) { // Probably impossible
            return;
        }

        DragonTemplate dragonTemplate = previousDragonBattle.getTemplate();

        String name = egg.getName();
        if (name != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name.replace("%dragon%", dragonTemplate.getName())));
        }

        List<@NotNull String> lore = egg.getLore();
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore.stream().map(line -> ChatColor.translateAlternateColorCodes('&', line.replace("%dragon%", dragonTemplate.getName()))).collect(Collectors.toList()));
        }

        stack.setItemMeta(meta);

        List<Player> players = world.getPlayers();
        if (players.size() >= 1) { // Only need it for the world anyways
            DragonEggDropPlaceholders.inject(players.get(0), stack);
        }

        item.setItemStack(stack);
    }

}
