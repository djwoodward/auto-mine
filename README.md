# auto-mine

A Java 25 / Maven plugin for **Paper 26.2**. Auto-eat is enabled by default for
players without a saved preference. Use `/auto-mine auto-eat off` to opt out;
preferences persist across reconnects and server restarts. Existing saved
preferences are preserved.

When you pick up an XP orb, the Mending item missing the most durability points
gets repaired first. Items in either hand, equipped armor, and items in the
hotbar and main inventory all use the same ranking. XP left after repairs goes
to your experience bar.

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

- `/auto-mine run-fast`: toggle progressive sprint speed.
- `/auto-mine run-fast on` or `/auto-mine run-fast off`: explicitly enable or disable.
- `/auto-mine run-fast status`: show your preference.

Permissions: `auto-mine.autoeat` and `auto-mine.runfast` (both available to everyone
by default). Each feature has an independent saved preference.

When upgrading from the previous unhyphenated plugin name, move your existing
configuration into `plugins/auto-mine/` and update explicit permission grants to
`auto-mine.autoeat`. The plugin name also determines the player preference
namespace, so players must enable auto-eat again after this rename.

## Behavior and configuration

In Survival and Adventure, auto-eat checks once per second and instantly consumes
one allowed food item when hunger is at or below 19/20 (half an icon missing). It keeps
eating on subsequent checks while hunger remains at or below the threshold.
Food must exist in the player's inventory; none is created for free. Offhand food
is preferred, followed by hotbar and inventory order. Active item use is left alone.

Native Paper consumption handles hunger, saturation, food effects and
`PlayerItemConsumeEvent`, including cancellation. Inventory food is temporarily
placed in the offhand and restored within the same server tick. Eating has no
normal animation delay. Plugins that inspect inventory inside consume events
will see that temporary placement.

Edit `plugins/auto-mine/config.yml` and restart to adjust `hunger-threshold`
(0–19), `check-interval-ticks` (minimum 1), or the `foods` allowlist.
Valuable and harmful foods are excluded by default. While auto-eat is enabled in
Survival or Adventure, having no allowed food in your inventory triggers a chat
warning, even above the hunger threshold or during active item use. It warns once
instead of repeating every check.
The warning resets when auto-eat finds food again, you disable auto-eat, or you
disconnect. Normal hunger rules still apply while you are out of food.

Existing installations retain their configured threshold: set `hunger-threshold: 19`
in `plugins/auto-mine/config.yml` to eat when half an icon is missing, or use `18`
to wait until one full icon is missing. Restart the server after changing it.

Run-fast is enabled by default. In Survival and Adventure, every 20 ticks (one
second at 20 TPS) of continuous moving sprint adds 33⅓% to normal sprint speed,
up to a 200% bonus (3× normal sprint speed) after 6 seconds. The bonus decays
at twice the buildup rate when you stop sprinting, or after five ticks without
movement while sprinting. At the default settings, a full bonus takes about
3 seconds to wear off. If you resume sprinting sooner, you keep the remaining
bonus and build speed from there.
Short gaps between movement packets preserve the run. Sprint jumping counts; swimming, flying, gliding, riding, and excluded game
modes do not. Teleporting, disconnecting, dying, disabling the feature, or stopping
the plugin also clears the bonus. Other movement attributes and potion effects
remain in place; run-fast applies its own temporary multiplier.

Set `run-fast.speed-increase` (default `0.3333333333333333`) and `run-fast.maximum-bonus`
(default `2.0`) to nonnegative fractions and restart to change the acceleration
and cap. Existing configs use these defaults when the keys are absent.
For existing installations with these keys already set, update `speed-increase` to
`0.3333333333333333` and `maximum-bonus` to `2.0` in
`plugins/auto-mine/config.yml`, then restart.

## Manual verification on Paper

- Pick up a small XP orb while holding a damaged Mending item and carrying a
  more damaged one. Verify the stored item repairs first. Swap the items and
  verify the more damaged item still repairs first. Repeat with equipped gear,
  include an elytra and a bow, and verify leftover XP reaches the bar after
  repairs. Check that items without Mending stay damaged.
- With another plugin cancelling `PlayerItemMendEvent` for a stored item,
  verify the item stays damaged and its XP remains available.

- A new player's `/auto-mine auto-eat status` reports enabled; low hunger consumes food.
- Enable it, lower hunger to 19 or below, and carry bread: one item is consumed
  per check and hunger/saturation increase. Test both inventory and offhand food.
- Verify a held tool and an occupied offhand are restored after inventory eating.
- Disable it and verify food stops being consumed. Reconnect and restart to check
  that both enabled and disabled preferences persist.
- Check empty inventories, excluded foods, full hunger, Creative, Spectator,
  and active item use: no automatic consumption should happen.
- With another plugin cancelling `PlayerItemConsumeEvent`, verify hunger and item
  counts remain unchanged.

- Enable auto-eat with no allowed food at full hunger: verify one chat warning
  across repeated checks, including during active item use.
  Restock above the hunger threshold, wait for a check, then remove the food:
  verify a new warning. Repeat while hungry and after consuming the last item.

- A new player's `/auto-mine run-fast status` reports enabled. Sprint continuously:
  verify increases at 1, 2, and 3 seconds, reaching triple sprint speed at 6 seconds.
- Keep sprinting past 6 seconds, including sprint jumping: verify the capped
  speed stays steady without periodic drops.
- Stop sprinting at full speed for one second, then resume. Verify the sprint
  starts at roughly 2⅓× normal sprint speed and builds back to the cap.
- Stop sprinting for three seconds at full boost and verify the next sprint
  starts at normal speed. Stand still against a wall while sprinting and verify
  the bonus starts to decay after five ticks. Teleport while boosted and verify
  the bonus clears immediately.
- Disable run-fast while boosted: verify immediate reset. Reconnect and restart
  to confirm the preference persists independently of auto-eat.
- Check Creative, Spectator, swimming, flight, gliding, riding, death, permission
  removal, and plugin shutdown: verify no bonus remains. Check that potion effects
  and other plugins' movement modifiers survive a run-fast reset.
- Adjust the increment and cap, restart, and verify the new progression and cap.
