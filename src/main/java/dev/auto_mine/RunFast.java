package dev.auto_mine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

final class RunFast implements Listener {
    private static final int SPEED_INTERVAL_TICKS = 20;
    private static final int IDLE_RESET_TICKS = 5;

    private final NamespacedKey modifierKey;
    private final Map<UUID, Progress> progress = new HashMap<>();
    private final double increment;
    private final double maximum;

    RunFast(AutoMinePlugin plugin) {
        modifierKey = new NamespacedKey(plugin, "run_fast_speed");
        increment = setting(plugin, "run-fast.speed-increase", 0.1);
        maximum = setting(plugin, "run-fast.maximum-bonus", 1.0);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                tick(player, plugin.isRunFastEnabled(player));
            }
        }, 1L, 1L);
    }

    private static double setting(AutoMinePlugin plugin, String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        return Double.isFinite(value) && value >= 0 ? value : fallback;
    }

    private void tick(Player player, boolean enabled) {
        if (!enabled || !player.hasPermission("auto-mine.runfast") || !player.isSprinting()
                || player.isDead() || !player.isValid() || player.isInsideVehicle()
                || player.isFlying() || player.isGliding() || player.isSwimming()
                || (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE)) {
            reset(player);
            return;
        }
        Location location = player.getLocation();
        Progress state = progress.get(player.getUniqueId());
        if (state == null) {
            reset(player);
            progress.put(player.getUniqueId(), new Progress(location));
            return;
        }
        double dx = location.getX() - state.location.getX();
        double dz = location.getZ() - state.location.getZ();
        if (!location.getWorld().equals(state.location.getWorld())) {
            reset(player);
            return;
        }
        if (dx * dx + dz * dz < 0.000001) {
            // Movement packets do not necessarily arrive every server tick.
            // Preserve the run across short gaps, but do not charge up while idle.
            if (++state.idleTicks >= IDLE_RESET_TICKS) {
                reset(player);
            }
            return;
        }
        state.location = location;
        // Include the short packet gap in elapsed running time once movement resumes.
        state.ticks += state.idleTicks + 1;
        state.idleTicks = 0;
        if (state.ticks >= SPEED_INTERVAL_TICKS) {
            state.ticks %= SPEED_INTERVAL_TICKS;
            if (state.bonus >= maximum || increment == 0) {
                return;
            }
            state.bonus = Math.min(maximum, ++state.steps * increment);
            AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(modifierKey);
                speed.addTransientModifier(new AttributeModifier(modifierKey, state.bonus,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    void reset(Player player) {
        progress.remove(player.getUniqueId());
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(modifierKey);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        reset(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        reset(event.getPlayer());
    }

    private static final class Progress {
        private Location location;
        private int ticks;
        private int idleTicks;
        private int steps;
        private double bonus;

        private Progress(Location location) {
            this.location = location;
        }
    }
}
