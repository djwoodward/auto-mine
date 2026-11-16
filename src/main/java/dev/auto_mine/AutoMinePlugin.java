package dev.auto_mine;

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
    private NamespacedKey runFastKey;
    private RunFast runFast;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        enabledKey = new NamespacedKey(this, "auto_eat_enabled");
        runFastKey = new NamespacedKey(this, "run_fast_enabled");
        runFast = new RunFast(this);
        threshold = Math.clamp(getConfig().getInt("hunger-threshold", 19), 0, 19);
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

    @Override
    public void onDisable() {
        if (runFast != null) {
            getServer().getOnlinePlayers().forEach(runFast::reset);
        }
    }

    boolean isRunFastEnabled(Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(runFastKey, PersistentDataType.BYTE, (byte) 1) == 1;
    }

    private boolean isEnabled(Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(enabledKey, PersistentDataType.BYTE, (byte) 1) == 1;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2 || (!args[0].equalsIgnoreCase("auto-eat")
                && !args[0].equalsIgnoreCase("run-fast"))) {
            return false;
        }
        boolean running = args[0].equalsIgnoreCase("run-fast");
        String feature = running ? "Run-fast" : "Auto-eat";
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use " + feature.toLowerCase(Locale.ROOT) + ".");
            return true;
        }
        if (!player.hasPermission(running ? "auto-mine.runfast" : "auto-mine.autoeat")) {
            player.sendMessage("You do not have permission to use " + feature.toLowerCase(Locale.ROOT) + ".");
            return true;
        }
        String action = args.length == 1 ? "toggle" : args[1].toLowerCase(Locale.ROOT);
        boolean enabled = running ? isRunFastEnabled(player) : isEnabled(player);
        switch (action) {
            case "toggle" -> enabled = !enabled;
            case "on" -> enabled = true;
            case "off" -> enabled = false;
            case "status" -> { }
            default -> { return false; }
        }
        player.getPersistentDataContainer().set(running ? runFastKey : enabledKey, PersistentDataType.BYTE,
                (byte) (enabled ? 1 : 0));
        if (!enabled) {
            if (running) {
                runFast.reset(player);
            } else {
                warnedNoFood.remove(player.getUniqueId());
            }
        }
        player.sendMessage(feature + " is " + (enabled ? "enabled." : "disabled."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("auto-eat", "run-fast").stream()
                    .filter(option -> sender.hasPermission(option.equals("run-fast")
                            ? "auto-mine.runfast" : "auto-mine.autoeat"))
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("auto-eat")
                && !args[0].equalsIgnoreCase("run-fast"))
                || !sender.hasPermission(args[0].equalsIgnoreCase("run-fast")
                        ? "auto-mine.runfast" : "auto-mine.autoeat")) {
            return List.of();
        }
        String prefix = args[1].toLowerCase(Locale.ROOT);
        return List.of("on", "off", "status").stream()
                .filter(option -> option.startsWith(prefix)).toList();
    }

    private void tryEat(Player player) {
        if (!isEnabled(player) || !player.hasPermission("auto-mine.autoeat")
                || player.isDead() || !player.isValid()
                || (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        boolean hasFood = isFood(inventory.getItemInOffHand());
        if (!hasFood) {
            for (ItemStack item : inventory.getStorageContents()) {
                if (isFood(item)) {
                    hasFood = true;
                    break;
                }
            }
        }
        if (!hasFood) {
            if (warnedNoFood.add(player.getUniqueId())) {
                player.sendMessage("Auto-eat: you are out of food! Restock your inventory.");
            }
            return;
        }
        warnedNoFood.remove(player.getUniqueId());
        if (player.hasActiveItem() || player.getFoodLevel() > threshold) {
            return;
        }
        // Consume from the offhand first, then storage slots (hotbar included).
        if (isFood(inventory.getItemInOffHand())) {
            consume(player);
            return;
        }
        for (int slot = 0; slot < inventory.getStorageContents().length; slot++) {
            ItemStack food = inventory.getItem(slot);
            if (!isFood(food)) {
                continue;
            }
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
