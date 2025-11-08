package com.excrele.eeaf;

import org.bukkit.ChatColor;
import org.bukkit.Material;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * EEAF - Excrele's Enchanting and Anvil Fixer! (1.21 Patched & Player-Fixed)
 * Randomly boosts table enchants to level 6-10 (config chance), uncaps anvil to 10,
 * and nukes "too expensive!"—pay XP for unlimited power. Fun & balanced!
 * Author: excrele | Namespace: com.excrele.eeaf
 *
 * Modular vibe: One file with sectioned methods—like LEGO blocks for enchants/anvils.
 * Read the headers: Each explains "what/why/how" for easy teen coding adventures!
 */
public final class EEAF extends JavaPlugin implements Listener {

    // Hidden tag for stashing real anvil XP costs (like secret NBT—players can't peek!).
    private NamespacedKey anvilCostKey;

    // Random roller for overenchant luck (feels like loot box pulls!).
    private final Random random = new Random();

    // Overenchant odds from config (5% default—rare but hype!).
    private double overenchantChance;

    @Override
    public void onEnable() {
        // Auto-generate config.yml if missing (sets up our chance tweak).
        saveDefaultConfig();

        // Load the fun % (or 0.05 if server's new—5% keeps it exciting, not OP).
        FileConfiguration config = getConfig();
        overenchantChance = config.getDouble("overenchant_chance", 0.05);

        // Craft our secret cost key (tags items invisibly for anvil tricks).
        anvilCostKey = new NamespacedKey(this, "real_anvil_cost");

        // Plug us into Spigot's event system (now we "hear" enchanting/anvil actions!).
        getServer().getPluginManager().registerEvents(this, this);

        // Console cheer—hypes the load with your chance (check server logs!).
        getLogger().info(ChatColor.GREEN + "Enabling Excrele's Enchanting and Anvil Fixer v1.2 (Player-Patched!)");
        getLogger().info(ChatColor.YELLOW + "Overenchant chance: " + (overenchantChance * 100) + "% – Hunt those level 10s!");
    }

    @Override
    public void onDisable() {
        // Clean shutdown log (good dev habit—says bye when you stop the server).
        getLogger().info(ChatColor.RED + "Disabling Excrele's Enchanting and Anvil Fixer. Power nap time!");
    }

    // ========================================
    // SECTION 1: ENCHANTING TABLE BOOSTS
    // ========================================
    // Triggers before the 3 glowy options pop up. We scan & maybe supercharge one!
    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        // Skip if no item (air or junk—no enchanting empty hands!).
        ItemStack item = event.getItem();
        if (item.getType().isAir() || !item.getType().isItem()) {
            return; // Boring—next!
        }

        // Grab the 3 offers (those shiny choices on the table—full of potential!).
        var offers = event.getOffers();
        for (var offer : offers) {
            // Ignore free or blank slots (no enchant = no fun).
            if (offer.getCost() == 0 || offer.getEnchantment() == null) {
                continue;
            }

            Enchantment ench = offer.getEnchantment();
            // Skip 1-level enchants (Silk Touch maxes at 1—boosting does zilch!).
            if (ench.getMaxLevel() == 1) {
                continue;
            }

            // Dice roll! Under chance? Upgrade to epic (6-10 based on vanilla max).
            if (random.nextDouble() < overenchantChance) {
                int vanillaMax = ench.getMaxLevel(); // E.g., 5 for Sharpness—base power.
                int newLevel = vanillaMax + 1 + random.nextInt(10 - vanillaMax); // Random high: 6-10!

                // Reflection trick: Peek into Bukkit's private code to swap levels (like modding the menu!).
                // Why reflection? Offers are "sealed"—we unlock & edit for custom glow.
                try {
                    Field levelField = org.bukkit.enchantments.EnchantmentOffer.class.getDeclaredField("enchantedLevel");
                    levelField.setAccessible(true); // Open the locked door.
                    levelField.set(offer, newLevel); // Stamp on the super level!

                    // Fair price bump: +2-5 XP (earned power, not free lunch).
                    int newCost = offer.getCost() + random.nextInt(4) + 2;
                    Field costField = org.bukkit.enchantments.EnchantmentOffer.class.getDeclaredField("cost");
                    costField.setAccessible(true);
                    costField.set(offer, newCost);

                    // Log the epic moment (console sees who got lucky!).
                    getLogger().info("Overenchanted " + ench.getKey().getKey() + " to level " + newLevel +
                            " for " + ((Player) event.getView().getPlayer()).getName()); // 1.21 fix: View hides player!
                } catch (Exception ex) {
                    // Glitch? Log & shrug—enchanting still works, no crash.
                    getLogger().warning("Boost hiccup on " + ench.getKey().getKey() + ": " + ex.getMessage());
                }
            }
        }
    }

    // Post-enchant safety (boosts already stuck—add fireworks here later?).
    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        // Chill spot for extras (e.g., sound effects). All good!
    }

    // ========================================
    // SECTION 2: ANVIL UNLOCKS (1.21 DEPRECATION-AWARE)
    // ========================================
    // Previews the anvil result. Uncaps levels, calcs fair XP, fakes low cost (no gray-out!).
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        // No preview? Bail (empty anvil = no party).
        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        // 1.21: Player's in the view (like spying through the inventory glass).
        Player player = (Player) event.getView().getPlayer();

        AnvilInventory inv = (AnvilInventory) event.getInventory();
        ItemStack left = inv.getItem(0); // Main item (left—your sword?).
        ItemStack right = inv.getItem(1); // Add-on (right—book for enchants!).
        if (left == null || right == null || left.getType().isAir() || right.getType().isAir()) {
            return; // Solo items? Vanilla handles.
        }

        // Uncapped merge: Stack to 10 (or 1 for meh enchants—keeps balance).
        Map<Enchantment, Integer> uncappedEnchants = new HashMap<>();

        // Base from left.
        left.getEnchantments().forEach((ench, lvl) -> uncappedEnchants.put(ench, lvl));

        // Stack right's levels (add, then cap smart).
        right.getEnchantments().forEach((ench, rightLvl) -> {
            int current = uncappedEnchants.getOrDefault(ench, 0);
            int newLvl = current + rightLvl;
            if (ench.getMaxLevel() == 1) {
                newLvl = Math.min(newLvl, 1); // Boring max.
            } else {
                newLvl = Math.min(newLvl, 10); // Power cap—woo!
            }
            uncappedEnchants.put(ench, newLvl);
        });

        // Slap on result (ditch old caps, keep name/dura).
        result.removeEnchantments();
        uncappedEnchants.forEach((ench, lvl) -> {
            if (lvl > 0) {
                result.addUnsafeEnchantment(ench, lvl); // Force high—ignore rules!
            }
        });

        // Real cost calc (game-like: Levels + repair/rename bonuses—see method!).
        int realCost = calculateAnvilCost(left, right, result);
        if (realCost <= 0) {
            return; // Free ride? Cool.
        }

        // Secret-stash the cost (PDC = hidden item data, like enchanted book lore but invisible).
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(anvilCostKey, PersistentDataType.INTEGER, realCost);
            result.setItemMeta(meta);
        }

        // Client trick: Show "1 level" (beats gray-out). Deprecations noted—works for 1.21!
        event.getView().setProperty(InventoryView.Property.REPAIR_COST, 1); // Show low.
        inv.setRepairCost(1); // Server low (override later).
        inv.setMaximumRepairCost(1000); // Huge cap—no blocks.

        event.setResult(result); // Flash the OP preview!
    }

    // Helper method: Fair XP math (sum sacrifice levels + bonuses—like anvil's brain!).
    // Modular: Easy tweak (e.g., x2 for harder mode). Ignores prior work for simplicity.
    private int calculateAnvilCost(ItemStack left, ItemStack right, ItemStack result) {
        int cost = 0;

        // Power tax: Each level from right adds 1 XP (V book = 5, X = 10—scales nice!).
        for (int lvl : right.getEnchantments().values()) {
            cost += lvl;
        }

        // Repair perk: +2 if same type & damaged (heals your tool—feels rewarding!).
        if (left.getType() == right.getType() && left.getDurability() > 0) {
            cost += 2;
        }

        // Name fee: +1 for custom tags ("Doomblade" costs extra—flavor!).
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta != null && resultMeta.hasDisplayName()) {
            cost += 1;
        }

        return Math.max(1, cost); // At least 1—nothing gratis!
    }

    // Click handler: Steals the result, charges real XP, clears slots—like custom anvil logic!
    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        // Target: Anvil result slot, left-click only (standard grab).
        if (event.getInventory().getType() != InventoryType.ANVIL ||
                event.getRawSlot() != 2 || event.getClick() != ClickType.LEFT) {
            return; // Wrong spot? Vanilla's turn.
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return; // Phantom click—poof.
        }

        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return; // No secrets? Normal flow.
        }

        // Snag hidden cost (our calc tag).
        Integer realCost = meta.getPersistentDataContainer().get(anvilCostKey, PersistentDataType.INTEGER);
        if (realCost == null || realCost <= 0) {
            return; // Untagged? Let it ride.
        }

        // Boss move: Cancel vanilla, we rule now!
        event.setCancelled(true);

        // Wallet check (balance—can't forge without levels!).
        if (player.getLevel() < realCost) {
            player.sendMessage(ChatColor.RED + "XP short! Grind " + realCost + " more levels for this.");
            return;
        }

        // Deduct & deliver (full toll paid).
        player.setLevel(player.getLevel() - realCost);

        // Wipe inputs (anvil's "consume" rule—gone!).
        AnvilInventory inv = (AnvilInventory) event.getInventory();
        inv.setItem(0, new ItemStack(Material.AIR));
        inv.setItem(1, new ItemStack(Material.AIR));

        // Clean handoff (clone & erase tag—no traces!).
        ItemStack cleanResult = result.clone();
        ItemMeta cleanMeta = cleanResult.getItemMeta();
        if (cleanMeta != null) {
            cleanMeta.getPersistentDataContainer().remove(anvilCostKey);
            cleanResult.setItemMeta(cleanMeta);
        }
        player.setItemOnCursor(cleanResult);

        // Win chat (hype the haul—total levels for flex!).
        int totalLevels = cleanResult.getEnchantments().values().stream().mapToInt(i -> i).sum();
        player.sendMessage(ChatColor.GREEN + "Anvil hacked! Grabbed " + cleanResult.getType().name() +
                " for " + realCost + " XP (" + totalLevels + " enchant levels total—OP!)");

        // Log for ops (track the power plays).
        getLogger().info(player.getName() + " EEAF'd an anvil: " + realCost + " XP for epic gear.");
    }
}