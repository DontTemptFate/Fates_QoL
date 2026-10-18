# Changelog

Newest first. A release tag `v<version>` publishes the section with that version's heading.

## 0.1.0-beta.1

First public test build.

- **Settings:** nearly 600 options, all searchable, in collapsible groups with Advanced and Changed views.
  Includes a colour picker (opacity, rainbow, hex), a sound picker (volume, pitch, test), island chips, line
  lists you can reorder, text templates, your own chat rules, key binds inside OFA, and presets.
- **Every HUD card:** where it shows, background, accent colour, number style, and which lines in what order.
- **Trackers:** reset timing, per-hour rates, idle pause, the price used to value drops, and a Reset button.
- **Alerts** for garden visitors, Jacob's contest, finished commissions, trophy fish, double hooks, rare catches,
  slayer spawn / kill / fail, 270 / 300 score, crypts, failed puzzles, Kuudra supplies and Fresh Tools, Rift time,
  low health, and upcoming events.
- **Item value with upgrades:** enchants, stars, recombobulator, potato books, gems, reforge, scrolls, Art of
  War / Peace, drill parts, dyes / skins and pet items, with a breakdown on Shift. Pets are valued by level.
  `/ofa networth` shows the change since the previous day.
- **Protection:**
  - locks for drop / NPC sell / salvage / trade / auction / bazaar
  - a second click for valuable sells and drops
  - an NPC-sale sanity check
  - an auction price check (too low, or an extra zero)
  - trade window totals
- **Data:**
  - auction values from the fatesflipper.com price file (clean-copy price, pets by level)
  - item data checked daily
  - update notices (notice only)
- **Fixes:** clicks and sliders on 26.3 (SDL input); chat repeats fold into one line; the settings page list scrolls.
- **For bug reports:** Diagnostics shows timings and has Copy diagnostics, and crash reports include a "Fates OFA"
  line.

## 0.1.0-alpha.1

First build: items and storage, activities, dungeons and Kuudra, planner, waypoints, quick buttons, storage
panel, secret waypoints (helper), play recorder.
