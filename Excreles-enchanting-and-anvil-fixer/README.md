# EEAF - Excrele's Enchanting and Anvil Fixer


**Version:** 1.0  
**Author:** excrele  
**SpigotMC Page:** [Coming Soon - Drop a review!]  
**Description:** Supercharges Minecraft enchanting with a chance for levels 6-10 (up to vanilla max+5) and smashes anvil limits—no more "too expensive!" blocks. Pay XP for unlimited merges (capped at 10). Plus admin commands for reloads & player boosts!

## Features
- **Overenchant Chance:** 5% default at tables—randomly amps offers to epic levels (e.g., Sharpness X!). Skips 1-level enchants like Silk Touch.
- **Anvil Freedom:** Merge to level 10, fair XP costs (based on added power + rename/repair). Client sees "1 level" to avoid grays.
- **Commands:** Live config reload + temp player boosts (e.g., 50% luck for 1 hour).
- **Lightweight:** One JAR, no dependencies—runs on Spigot 1.21+ (Java 17).

## Installation
1. **Build the Plugin:**
    - Clone/open in IntelliJ (Maven project).
    - Run `mvn clean package` in terminal.
    - Grab `target/eeaf-1.0-SNAPSHOT.jar`.

2. **Server Setup:**
    - Drop JAR in `plugins/` folder.
    - Restart server (or `/plugman load EEAF` if using PlugMan).
    - Check console: "[EEAF] Enabling... Overenchant chance: 5%".
    - Edit `plugins/EEAF/config.yml` for custom chance, then `/eeaf reload`.

3. **Test It:**
    - Enchant: Place lapis/book—hover for glowy high-level offers!
    - Anvil: Merge two max books (e.g., Efficiency V + V → X)—pay XP, no blocks.
    - Boost: `/eeaf boost <yourname> 100 5m` → Instant OP enchants for 5 mins.

## Configuration
`plugins/EEAF/config.yml` (auto-generated):
```yaml
overenchant_chance: 0.05  # 5% chance for level 6-10 boosts. 1.0 = always!