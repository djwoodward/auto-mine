# AutoMine

A Java 25 / Maven plugin for **Paper 26.2**. Auto-eat is disabled for each player
until they opt in; preferences persist across reconnects and server restarts.

## Build and install

With [mise](https://mise.jdx.dev/), run:

```sh
mise trust
mise install
mise run build
mise run install
```

The configuration provides Java 25 and Maven 3.9. `build` runs `mvn clean package`.
`install` builds first, then moves the JAR into
`~/docker/minecraft/data/plugins/`, the plugin directory under the Docker server's
mounted `data` directory. It replaces the same-named JAR; restart the server to
load the update. The destination directory must already exist.

To build and install manually:

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
one allowed food item when hunger is at or below 14/20 (seven icons remaining, three missing). It keeps
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
Valuable and harmful foods are excluded by default. When auto-eat needs food but
none is allowed in your inventory, it warns once instead of repeating every check.
The warning resets when auto-eat finds food again, you disable auto-eat, or you
disconnect. Normal hunger rules still apply while you are out of food.

Existing installations retain their configured threshold: set `hunger-threshold: 14`
in `plugins/AutoMine/config.yml` to use the earlier eating threshold.

## Manual verification on Paper

- A new player's `/auto-mine auto-eat status` reports disabled; low hunger consumes no food.
- Enable it, lower hunger to 14 or below, and carry bread: one item is consumed
  per check and hunger/saturation increase. Test both inventory and offhand food.
- Verify a held tool and an occupied offhand are restored after inventory eating.
- Disable it and verify food stops being consumed. Reconnect and restart to check
  that both enabled and disabled preferences persist.
- Check empty inventories, excluded foods, full hunger, Creative, Spectator,
  and active item use: no automatic consumption should happen.
- With another plugin cancelling `PlayerItemConsumeEvent`, verify hunger and item
  counts remain unchanged.

- Run out of allowed food while hungry: verify one warning across repeated checks.
  Restock and let auto-eat consume food, then run out again: verify a new warning.
