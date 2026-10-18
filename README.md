# Fates OFA — One For All

A Hypixel SkyBlock quality-of-life mod for Fabric, **Minecraft 26.2 and 26.3**. It puts the tools you'd
normally need several mods for into one: item values, storage, activity trackers, dungeons, Kuudra and a
progression planner.

- **It never plays for you.** OFA shows things and stops your own mistakes. It never clicks, moves, aims
  or repeats actions. A quick button, hotkey or alias sends **at most one command per press**, which you
  choose yourself.
- **No account.** It needs no login, no token and no Fates subscription. It never reads your Minecraft
  session.
- **Open source:** GPL-3.0-only, written clean-room. Other mods were studied by how they behave, never by
  reading their source.

Status: **alpha** — expect rough edges, and please report them (see below).

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) and **Fabric API** for your Minecraft version.
2. Download `fatesofa-<version>+mc26.2.jar` or `+mc26.3.jar` from
   [Releases](https://github.com/DontTemptFate/Fates_QoL/releases). Each release lists `SHA256SUMS`.
   Pick the jar that matches your Minecraft version.
3. Put it in `.minecraft/mods`, start the game, and type `/ofa`.

[Mod Menu](https://modrinth.com/mod/modmenu) is optional: its Configure button opens OFA's settings.

## What's in it

| Area | Highlights |
|---|---|
| Items | Prices under tooltips (bazaar, auction lowest BIN / median / sales a day, NPC), **worth with upgrades** and what an item is made of (hold Shift), pet level progress and value, rarity backgrounds, the item list beside inventories with recipes / used-in |
| Storage | Every ender chest page and backpack remembered: `/ofa storage`, `/ofa find`, and a panel beside Hypixel's storage menus with page values and one search across all of them |
| Protection | Lock items or slots so they can't be dropped, sold, salvaged, traded or listed. A second click is needed for valuable sells and drops, for NPC sales far below value, and for auction prices that look like a typo. The trade window shows totals for both sides |
| Activities | Garden, mining, fishing, slayer, Diana, Hoppity, Rift, Kuudra, Bingo and event timers, each a HUD card with per-hour rates and alerts |
| Dungeons | Clean map, score estimate (S / S+), splits, key / crypt / puzzle alerts. Helpers (off by default): terminal highlights, Three Weirdos, and secret waypoints learned while you play |
| Planner | Cheapest Magical Power you're missing, crafts from what you own, `/ofa networth` with daily change |
| Quality of life | Quick buttons above menus, hotkeys and aliases, chat cleanup with your own rules, waypoints, health / mana bars |
| Settings | Nearly 600 options, all searchable, in collapsible groups, with Advanced and Changed views. Colour picker, sound picker, island picker, line lists, key binds, presets, and settings export / import |

### HELPER features

A few dungeon features are marked **HELPER**: terminal highlights, the Three Weirdos answer and secret
waypoints. They are common in the SkyBlock community but not covered by Hypixel's written rules, so they
are **off by default**. They only show hints; you do every click.

## Commands

| Command | What it does |
|---|---|
| `/ofa` or `/ofa settings` | Open the settings |
| `/ofa help` | List the commands |
| `/ofa hud` | Move, resize or hide HUD cards |
| `/ofa status` | What OFA detects: island, area, profile, data |
| `/ofa version` | Your version and whether a newer one is out |
| `/ofa storage` | Browse every remembered storage page |
| `/ofa find <text>` | Search your storage |
| `/ofa recipe <item>` | Recipe page for an item |
| `/ofa planner` | Progression planner |
| `/ofa networth` | Networth from what OFA has seen, by category |
| `/ofa runs` | Your dungeon runs: fastest, best score and average per floor |
| `/ofa quick` | Edit quick buttons, hotkeys and aliases |
| `/ofa wp add [name]` · `remove <name>` · `clear` | Your waypoints on this island |
| `/ofa rooms` · `export` · `import` · `update` | Learned dungeon secrets: sharing and the shared pack |
| `/ofa record` · `on` · `off` | Record what OFA reads, for bug reports (no player chat; names hidden) |
| `/ofa repo` · `update` | Item data status / check for new data now |
| `/ofa reset slayer` | Reset the slayer tracker |
| `/ofa feature <id> on` · `off` | Switch one feature |
| `/ofa export` · `import` | Settings to / from the clipboard |

## Data and privacy

OFA only downloads public data files and **sends nothing about you**:

- Items, recipes and constants come from [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO)
  and [SkyHanni-REPO](https://github.com/hannibal002/SkyHanni-REPO) (MIT). By default OFA checks once a day.
- Bazaar, NPC and election data come from Hypixel's public API, with no key.
- Auction values come from the public price file on [fatesflipper.com](https://fatesflipper.com/public/prices.json).
- The shared dungeon room pack comes from this repository's `data` branch, built from share files players
  chose to post.
- Update notices come from `latest.json` on the `data` branch. Nothing is ever downloaded or installed
  for you.

Every network feature can be switched off in **Data & Prices**.

## Reporting a problem

Open an [issue](https://github.com/DontTemptFate/Fates_QoL/issues) and include:

- `/ofa` → **Diagnostics** → **Copy diagnostics**: version, mods, features on, errors and timings. It
  contains no account data.
- Optionally, a recording: `/ofa record on`, reproduce the problem, `/ofa record off`. Then attach the file
  from `config/fatesofa/recordings/`.
- Crash reports already include a "Fates OFA" line.

## Building

Builds run on GitHub Actions with JDK 25. Locally:

- `./gradlew build -Pmc=26.2` or `-Pmc=26.3` produces `build/libs/fatesofa-<version>+mc<mc>.jar`.
- `./gradlew runClientGametest -Pmc=26.3` runs the in-game test on a real client.

Releases are made by pushing a tag `v<version>`, for example `v0.1.0-beta.1`. The release workflow then:

- builds both jars and writes `SHA256SUMS`;
- publishes a GitHub Release (marked as a pre-release when the version has `-alpha` or `-beta`) with the
  notes from `CHANGELOG.md`;
- updates `latest.json`.

## Credits

- Item and recipe data: NotEnoughUpdates-REPO and SkyHanni-REPO (MIT).
- Hypixel Mod API protocol (MIT, Hypixel Inc.).
- Chakra Petch font (SIL Open Font License 1.1, Cadson Demak).

Not affiliated with Hypixel or Mojang.
