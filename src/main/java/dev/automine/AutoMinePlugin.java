package dev.automine;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoMinePlugin extends JavaPlugin {
    private NamespacedKey enabledKey;
    private final Set<Material> foods = EnumSet.noneOf(Material.class);
    private final Set<UUID> warnedNoFood = new HashSet<>();
    private int threshold;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enabledKey = new NamespacedKey(this, "auto_eat_enabled");
        threshold = Math.clamp(getConfig().getInt("hunger-threshold", 14), 0, 19);
        for (String name : getConfig().getStringList("foods")) {
            Material material = Material.matchMaterial(name);
            if (material != null && material.isEdible()) {
                foods.add(material);
            } else {
                getLogger().warning("Ignoring invalid food: " + name);
            }
        }
        Objects.requireNonNull(getCommand("auto-mine")).setExecutor(this);
        long interval = Math.max(1L, getConfig().getLong("check-interval-ticks", 20L));
        getServer().getScheduler().runTaskTimer(this, () -> {
            warnedNoFood.removeIf(id -> getServer().getPlayer(id) == null);
            for (Player player : getServer().getOnlinePlayers()) {
                tryEat(player);
            }
        }, interval, interval);
    }

    private boolean isEnabled(Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(enabledKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2 || !args[0].equalsIgnoreCase("auto-eat")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use auto-eat.");
            return true;
        }
        if (!player.hasPermission("automine.autoeat")) {
            player.sendMessage("You do not have permission to use auto-eat.");
            return true;
        }
        String action = args.length == 1 ? "toggle" : args[1].toLowerCase(Locale.ROOT);
        boolean enabled = isEnabled(player);
        switch (action) {
            case "toggle" -> enabled = !enabled;
            case "on" -> enabled = true;
            case "off" -> enabled = false;
            case "status" -> { }
            default -> { return false; }
        }
        player.getPersistentDataContainer().set(enabledKey, PersistentDataType.BYTE,
                (byte) (enabled ? 1 : 0));
        if (!enabled) {
            warnedNoFood.remove(player.getUniqueId());
        }
        player.sendMessage("Auto-eat is " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("automine.autoeat")) {
            return List.of();
        }
        if (args.length == 1) {
            return "auto-eat".startsWith(args[0].toLowerCase(Locale.ROOT))
                    ? List.of("auto-eat") : List.of();
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("auto-eat")) {
            return List.of();
        }
        String prefix = args[1].toLowerCase(Locale.ROOT);
        return List.of("on", "off", "status").stream()
                .filter(option -> option.startsWith(prefix)).toList();
    }

    private void tryEat(Player player) {
        if (!isEnabled(player) || !player.hasPermission("automine.autoeat")
                || player.isDead() || !player.isValid() || player.hasActiveItem()
                || player.getFoodLevel() > threshold
                || (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        // Consume from the offhand first, then storage slots (hotbar included).
        if (isFood(inventory.getItemInOffHand())) {
            warnedNoFood.remove(player.getUniqueId());
            consume(player);
            return;
        }
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack food = inventory.getItem(slot);
            if (!isFood(food)) {
                continue;
            }
            warnedNoFood.remove(player.getUniqueId());
            // Use the native consumption path for saturation, effects, and consume events.
            // No scheduled delay: the inventory swap and restoration happen in one tick.
            ItemStack previousOffhand = inventory.getItemInOffHand();
            inventory.setItem(slot, null);
            inventory.setItemInOffHand(food);
            try {
                consume(player);
            } finally {
                ItemStack remainder = inventory.getItemInOffHand();
                inventory.setItemInOffHand(previousOffhand);
                // Other plugins may have filled the original slot during consumption.
                if (inventory.getItem(slot) == null || inventory.getItem(slot).isEmpty()) {
                    inventory.setItem(slot, remainder);
                } else if (!remainder.isEmpty()) {
                    inventory.addItem(remainder).values().forEach(leftover ->
                            player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                }
            }
            return;
        }
        if (warnedNoFood.add(player.getUniqueId())) {
            player.sendMessage("Auto-eat: you are out of allowed food! Restock your inventory.");
        }
    }

    private boolean isFood(ItemStack item) {
        return item != null && !item.isEmpty() && foods.contains(item.getType());
    }

    private void consume(Player player) {
        player.startUsingItem(EquipmentSlot.OFF_HAND);
        if (player.hasActiveItem()) {
            try {
                player.completeUsingActiveItem();
            } finally {
                player.clearActiveItem();
            }
        }
    }
}
