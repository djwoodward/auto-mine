# AutoMine

A Java 25 / Maven plugin for **Paper 26.2**. Auto-eat is disabled for each player
until they opt in; preferences persist across reconnects and server restarts.

## Build and install

1. Install JDK 25 and Maven 3.9+.
2. Run `mvn clean package`.
3. Copy `target/auto-mine-0.1.0.jar` into your Paper server's `plugins/` directory.
4. Start or restart the server.

## Commands

- `/auto-mine auto-eat`: toggle your preference.
- `/auto-mine auto-eat on` or `/auto-mine auto-eat off`: explicitly enable or disable.
- `/auto-mine auto-eat status`: show your preference.

Permission: `automine.autoeat` (available to everyone by default).

## Behavior and configuration

In Survival and Adventure, auto-eat checks once per second and instantly consumes
one allowed food item when hunger is at or below 12/20 (six icons). It keeps
eating on subsequent checks while hunger remains at or below the threshold.
Food must exist in the player's inventory; none is created for free. Offhand food
is preferred, followed by hotbar and inventory order. Active item use is left alone.

Native Paper consumption handles hunger, saturation, food effects and
`PlayerItemConsumeEvent`, including cancellation. Inventory food is temporarily
placed in the offhand and restored within the same server tick. Eating has no
normal animation delay. Plugins that inspect inventory inside consume events
will see that temporary placement.

Edit `plugins/AutoMine/config.yml` and restart to adjust `hunger-threshold`
(0–19), `check-interval-ticks` (minimum 1), or the `foods` allowlist.
Valuable and harmful foods are excluded by default. No allowed food means no action.

## Manual verification on Paper

- A new player's `/auto-mine auto-eat status` reports disabled; low hunger consumes no food.
- Enable it, lower hunger to 12 or below, and carry bread: one item is consumed
  per check and hunger/saturation increase. Test both inventory and offhand food.
- Verify a held tool and an occupied offhand are restored after inventory eating.
- Disable it and verify food stops being consumed. Reconnect and restart to check
  that both enabled and disabled preferences persist.
- Check empty inventories, excluded foods, full hunger, Creative, Spectator,
  and active item use: no automatic consumption should happen.
- With another plugin cancelling `PlayerItemConsumeEvent`, verify hunger and item
  counts remain unchanged.
