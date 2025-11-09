package com.excrele.eeaf;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.util.*;

/**
 * EEAF - Excrele's Enchanting and Anvil Fixer! (v1.3: Commands + Boosts Added!)
 * Boosts table enchants to 6-10 levels (global or per-player temp boost), uncaps anvil to 10,
 * kills "too expensive!"—plus /reload & /boost commands for admin fun.
 * Author: excrele | Namespace: com.excrele.eeaf
 *
 * Modular magic: Sections for enchants/anvils/commands/boosts—like snap-together blocks.
 * Each chunk: "What it does, why it's cool, how it works." Easy for teen devs to remix!
 */
public final class EEAF extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // Secret anvil cost tag (hides real XP on items—player-proof!).
    private NamespacedKey anvilCostKey;

    // Global dice for random rolls (like RNG in loot chests!).
    private final Random random = new Random();

    // Global overenchant % from config (5% default—tweakable!).
    private double overenchantChance;

    // NEW: Boost storage—Maps for temp player luck (UUID key = safe even offline).
    // Chance map: Player ID → Their special % (overrides global).
    private final Map<UUID, Double> playerBoosts = new HashMap<>();
    // Expiration map: Player ID → End timestamp (millis—auto-clean!).
    private final Map<UUID, Long> boostExpirations = new HashMap<>();

    @Override
    public void onEnable() {
        // Auto-create config.yml (with chance—edits reload live!).
        saveDefaultConfig();

        // Load global chance (5% if missing—keeps it rare & exciting!).
        FileConfiguration config = getConfig();
        overenchantChance = config.getDouble("overenchant_chance", 0.05);

        // Secret key for anvil costs (invisible item notes!).
        anvilCostKey = new NamespacedKey(this, "real_anvil_cost");

        // Hook events (enchanting/anvil clicks) & commands (our new powers!).
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("eeaf").setExecutor(this); // Registers /eeaf
        this.getCommand("eeaf").setTabCompleter(this); // Smart auto-complete!

        // NEW: Start cleanup scheduler (runs every 5 mins—zaps old boosts quietly).
        new BoostCleanupTask().runTaskTimer(this, 0L, 6000L); // 6000 ticks = 5 mins.

        // Console hype (with chance—check logs on start!).
        getLogger().info(ChatColor.GREEN + "Enabling Excrele's Enchanting and Anvil Fixer v1.3 (Commands Unlocked!)");
        getLogger().info(ChatColor.YELLOW + "Global overenchant: " + (overenchantChance * 100) + "% – Boosts ready for heroes!");
    }

    @Override
    public void onDisable() {
        // Clear boosts on shutdown (no leftovers—clean slate!).
        playerBoosts.clear();
        boostExpirations.clear();
        getLogger().info(ChatColor.RED + "Disabling EEAF. Boosts cleared—sweet dreams!");
    }

    // ========================================
    // SECTION 1: ENCHANTING TABLE BOOSTS (WITH PLAYER OVERRIDES!)
    // ========================================
    // Pops before glowy options—checks for player boost, then amps if lucky!
    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();
        if (item.getType().isAir() || !item.getType().isItem()) {
            return; // No item? Skip the magic.
        }

        // 1.21: Player via view (hidden in event—sneaky!).
        Player player = (Player) event.getView().getPlayer();
        UUID playerId = player.getUniqueId();

        // NEW: Grab effective chance (boost if active, else global).
        double effectiveChance = overenchantChance;
        if (playerBoosts.containsKey(playerId)) {
            effectiveChance = playerBoosts.get(playerId);
            // Quick expire check (double-duty with scheduler!).
            if (boostExpirations.getOrDefault(playerId, 0L) < System.currentTimeMillis()) {
                removeBoost(playerId); // Gone? Clean it.
                effectiveChance = overenchantChance;
            }
        }

        // Loop offers (3 shiny choices—pick one to supercharge?).
        var offers = event.getOffers();
        for (var offer : offers) {
            if (offer.getCost() == 0 || offer.getEnchantment() == null) {
                continue; // Free/blank? Meh.
            }

            Enchantment ench = offer.getEnchantment();
            if (ench.getMaxLevel() == 1) {
                continue; // 1-level? No boost needed.
            }

            // Roll with effective chance (boosted player = more wins!).
            if (random.nextDouble() < effectiveChance) {
                int vanillaMax = ench.getMaxLevel();
                int newLevel = vanillaMax + 1 + random.nextInt(10 - vanillaMax); // 6-10 random!

                // Reflection: Unlock & edit offer (Bukkit's secret sauce!).
                try {
                    Field levelField = org.bukkit.enchantments.EnchantmentOffer.class.getDeclaredField("enchantedLevel");
                    levelField.setAccessible(true);
                    levelField.set(offer, newLevel);

                    int newCost = offer.getCost() + random.nextInt(4) + 2; // +XP for power.
                    Field costField = org.bukkit.enchantments.EnchantmentOffer.class.getDeclaredField("cost");
                    costField.setAccessible(true);
                    costField.set(offer, newCost);

                    getLogger().info("Boosted " + ench.getKey().getKey() + " to " + newLevel +
                            " for " + player.getName() + " (chance: " + (effectiveChance * 100) + "%)");
                } catch (Exception ex) {
                    getLogger().warning("Boost fail: " + ex.getMessage());
                }
            }
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        // Post-boost flair spot (e.g., add particles later—boosts already applied!).
    }

    // ========================================
    // SECTION 2: ANVIL UNLOCKS (UNCHANGED - SOLID!)
    // ========================================
    // Preps result: Uncaps to 10, fakes low cost, stashes real XP.
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        Player player = (Player) event.getView().getPlayer();
        AnvilInventory inv = (AnvilInventory) event.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) {
            return;
        }

        // Uncapped enchants (stack to 10 or 1 for boring ones).
        Map<Enchantment, Integer> uncappedEnchants = new HashMap<>();
        left.getEnchantments().forEach((ench, lvl) -> uncappedEnchants.put(ench, lvl));
        right.getEnchantments().forEach((ench, rightLvl) -> {
            int current = uncappedEnchants.getOrDefault(ench, 0);
            int newLvl = current + rightLvl;
            if (ench.getMaxLevel() == 1) {
                newLvl = Math.min(newLvl, 1);
            } else {
                newLvl = Math.min(newLvl, 10);
            }
            uncappedEnchants.put(ench, newLvl);
        });

        result.removeEnchantments();
        uncappedEnchants.forEach((ench, lvl) -> {
            if (lvl > 0) {
                result.addUnsafeEnchantment(ench, lvl);
            }
        });

        int realCost = calculateAnvilCost(left, right, result);
        if (realCost <= 0) {
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(anvilCostKey, PersistentDataType.INTEGER, realCost);
            result.setItemMeta(meta);
        }

        // 1.21 tricks (deprecations ok for now—low cost show!).
        event.getView().setProperty(InventoryView.Property.REPAIR_COST, 1);
        inv.setRepairCost(1);
        inv.setMaximumRepairCost(1000);

        event.setResult(result);
    }

    // Anvil cost calc (levels + bonuses—fair game math!).
    private int calculateAnvilCost(ItemStack left, ItemStack right, ItemStack result) {
        int cost = 0;
        for (int lvl : right.getEnchantments().values()) {
            cost += lvl;
        }
        if (left.getType() == right.getType() && left.getDurability() > 0) {
            cost += 2;
        }
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null && resultMeta.hasDisplayName()) {
            cost += 1;
        }
        return Math.max(1, cost);
    }

    // Click takeover: Charges real XP, hands OP item.
    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.ANVIL ||
                event.getRawSlot() != 2 || event.getClick() != ClickType.LEFT) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return;
        }

        Integer realCost = meta.getPersistentDataContainer().get(anvilCostKey, PersistentDataType.INTEGER);
        if (realCost == null || realCost <= 0) {
            return;
        }

        event.setCancelled(true);

        if (player.getLevel() < realCost) {
            player.sendMessage(ChatColor.RED + "XP low! Need " + realCost + " levels.");
            return;
        }

        player.setLevel(player.getLevel() - realCost);

        AnvilInventory inv = (AnvilInventory) event.getInventory();
        inv.setItem(0, new ItemStack(Material.AIR));
        inv.setItem(1, new ItemStack(Material.AIR));

        ItemStack cleanResult = result.clone();
        ItemMeta cleanMeta = cleanResult.getItemMeta();
        if (cleanMeta != null) {
            cleanMeta.getPersistentDataContainer().remove(anvilCostKey);
            cleanResult.setItemMeta(cleanMeta);
        }
        player.setItemOnCursor(cleanResult);

        int totalLevels = cleanResult.getEnchantments().values().stream().mapToInt(i -> i).sum();
        player.sendMessage(ChatColor.GREEN + "Anvil win! Got " + cleanResult.getType().name() +
                " for " + realCost + " XP (" + totalLevels + " levels total).");

        getLogger().info(player.getName() + " anvil'd: " + realCost + " XP.");
    }

    // ========================================
    // SECTION 3: NEW COMMANDS (RELOAD + BOOST!)
    // ========================================
    // Handles /eeaf subcommands (reload/config refresh; boost/temp luck spike).
    // Why? Live tweaks without restarts—admin superpowers!
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("eeaf.admin")) {
            sender.sendMessage(ChatColor.RED + "No perm! Need eeaf.admin.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/eeaf reload | boost <player> <percent> <time>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("eeaf.reload")) {
                sender.sendMessage(ChatColor.RED + "No eeaf.reload perm!");
                return true;
            }
            reloadConfig(); // Fresh config load.
            overenchantChance = getConfig().getDouble("overenchant_chance", 0.05); // Update global.
            sender.sendMessage(ChatColor.GREEN + "EEAF reloaded! Global chance: " + (overenchantChance * 100) + "%");
            getLogger().info("Config reloaded by " + sender.getName());
            return true;
        }

        if (args[0].equalsIgnoreCase("boost") && args.length >= 4) {
            if (!sender.hasPermission("eeaf.boost")) {
                sender.sendMessage(ChatColor.RED + "No eeaf.boost perm!");
                return true;
            }
            // Parse: boost <player> <percent> <time>
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && Bukkit.getPlayer(args[1]) == null) {
                sender.sendMessage(ChatColor.RED + "Player '" + args[1] + "' not found!");
                return true;
            }

            double boostPercent;
            try {
                boostPercent = Double.parseDouble(args[2]) / 100.0; // E.g., "50" → 0.5
                if (boostPercent < 0 || boostPercent > 1) {
                    sender.sendMessage(ChatColor.RED + "Percent: 0-100 only!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Invalid percent: Use number (e.g., 25 for 25%).");
                return true;
            }

            long durationMillis = parseTimeToMillis(args[3]); // "1h30m" → millis
            if (durationMillis <= 0) {
                sender.sendMessage(ChatColor.RED + "Invalid time! Use e.g., 1d, 2h, 30m (d=days, h=hours, m=minutes).");
                return true;
            }

            UUID targetId = target.getUniqueId();
            long expiration = System.currentTimeMillis() + durationMillis;
            playerBoosts.put(targetId, boostPercent);
            boostExpirations.put(targetId, expiration);

            String timeStr = formatMillisToTime(durationMillis); // Human-readable.
            sender.sendMessage(ChatColor.GREEN + "Boosted " + target.getName() + " to " + (boostPercent * 100) + "% for " + timeStr + "!");
            getLogger().info(sender.getName() + " boosted " + target.getName() + ": " + (boostPercent * 100) + "% for " + timeStr);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Usage: /eeaf reload | boost <player> <percent> <time>");
        return true;
    }

    // Tab-complete: Helps type /eeaf boost <tab> (players list).
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
            completions.add("boost");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("boost")) {
            // List online/offline players (fuzzy match).
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }

    // NEW HELPER: Parses time string to milliseconds (e.g., "1d2h30m" → total ms).
    // Why? Easy input like chat—splits d/h/m, multiplies (days=86.4M ms, etc.).
    // Cool for: Flexible boosts (5m test vs. 7d event!).
    private long parseTimeToMillis(String timeStr) {
        long total = 0;
        // Regex split: Grab numbers + units (e.g., "1d" → 1 day).
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)([dhm])").matcher(timeStr.toLowerCase());
        while (matcher.find()) {
            int num = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "d": total += num * 86400000L; break; // Day ms.
                case "h": total += num * 3600000L; break; // Hour ms.
                case "m": total += num * 60000L; break; // Min ms.
            }
        }
        return total;
    }

    // NEW HELPER: Formats ms back to "1h 30m" for messages (readable!).
    private String formatMillisToTime(long millis) {
        long days = millis / 86400000L;
        long hours = (millis % 86400000L) / 3600000L;
        long mins = ((millis % 86400000L) % 3600000L) / 60000L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (mins > 0) sb.append(mins).append("m");
        return sb.toString().trim();
    }

    // NEW HELPER: Removes a player's boost (called on expire).
    private void removeBoost(UUID playerId) {
        playerBoosts.remove(playerId);
        boostExpirations.remove(playerId);
    }

    // ========================================
    // SECTION 4: BOOST CLEANUP TASK (SCHEDULER!)
    // ========================================
    // Runs every 5 mins: Scans expirations, cleans old boosts (no memory leaks!).
    // Why? Auto-maintenance—boosts vanish without manual cleanup.
    private class BoostCleanupTask extends BukkitRunnable {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, Long> entry : boostExpirations.entrySet()) {
                if (entry.getValue() < now) {
                    toRemove.add(entry.getKey());
                }
            }
            for (UUID id : toRemove) {
                removeBoost(id);
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.YELLOW + "Your EEAF boost expired—back to normal luck!");
                }
            }
            if (!toRemove.isEmpty()) {
                getLogger().info("Cleaned " + toRemove.size() + " expired boosts.");
            }
        }
    }
}