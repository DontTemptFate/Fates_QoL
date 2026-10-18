package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.FishingTracker;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fishing: sea creatures caught (per hour), double hooks, trophy fish, an alert for rare catches and a
 * "fish on!" alert when the bobber's "!!!" appears. The player still reels in themselves.
 */
public final class FishingFeature extends Feature {
	static final List<String> RARITIES = List.of("UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
	static final List<String> TIERS = List.of("BRONZE", "SILVER", "GOLD", "DIAMOND");

	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "fishing", 0xFF55FFFF,
			io.github.donttemptfate.fatesofa.core.Display.lines("per_hour", "Catches per hour", "time", "Time fishing", "double", "Double hooks",
					"creatures", "Sea creatures", "trophies", "Trophy fish total", "trophy_list", "Trophy fish by kind"),
			List.of("per_hour", "double", "creatures", "trophies"));
	final io.github.donttemptfate.fatesofa.core.Tracker tracking = new io.github.donttemptfate.fatesofa.core.Tracker(this, "fishing.tracker", false);
	final Setting<Integer> creaturesShown = setting(Setting.integer("fishing.creaturesShown", "Sea creatures listed", 5, 1, 12).group("Display"));
	final Setting<Boolean> rareOnly = setting(Setting.bool("fishing.rareOnly", "List only Rare or better", false).group("Display").advanced());
	final io.github.donttemptfate.fatesofa.core.Alert bite = new io.github.donttemptfate.fatesofa.core.Alert(this, "fishing.bite", "\"Fish on!\" alert",
			"!!!", "", 0xFFFF5555, "minecraft:block.note_block.pling|1|2");
	final io.github.donttemptfate.fatesofa.core.Alert rare = new io.github.donttemptfate.fatesofa.core.Alert(this, "fishing.rare", "Rare catch alert",
			"{name}", "{rarity}", 0xFFFFAA00, "minecraft:block.note_block.pling|1|1.5", "name", "rarity");
	final Setting<String> rareFrom = setting(Setting.choice("fishing.rare.from", "From rarity", "EPIC", RARITIES).group("Rare catch alert"));
	final Setting<Boolean> rareColor = setting(Setting.bool("fishing.rare.rarityColour", "Title in the rarity's colour", true).group("Rare catch alert").advanced());
	final io.github.donttemptfate.fatesofa.core.Alert trophy = new io.github.donttemptfate.fatesofa.core.Alert(this, "fishing.trophy", "Trophy fish alert", false,
			"{fish}", "{tier} trophy fish", 0xFFFFAA00, "minecraft:entity.experience_orb.pickup", "fish", "tier");
	final Setting<String> trophyFrom = setting(Setting.choice("fishing.trophy.from", "From tier", "GOLD", TIERS).group("Trophy fish alert"));
	final io.github.donttemptfate.fatesofa.core.Alert doubleHook = new io.github.donttemptfate.fatesofa.core.Alert(this, "fishing.double", "Double hook alert", false,
			"Double hook!", "", 0xFF55FFFF, "minecraft:entity.experience_orb.pickup|1|1.5");

	public final FishingTracker tracker = new FishingTracker();
	private long lastBiteAt;
	private int lastDoubles;
	private boolean loaded;
	private int tick;

	public FishingFeature() {
		super("fishing", "Fishing tracker", "Sea creatures caught and per hour, double hooks, trophy fish, and alerts for bites, rare catches, trophy fish and double hooks.",
				Category.FISHING, true, Risk.SAFE);
	}

	static int rank(List<String> order, String v) {
		int i = order.indexOf(v == null ? "" : v.toUpperCase(java.util.Locale.ROOT));
		return i < 0 ? -1 : i;
	}

	static int rarityColour(String rarity) {
		return switch (rarity) {
			case "MYTHIC" -> 0xFF55FF;
			case "LEGENDARY" -> 0xFFAA00;
			case "EPIC" -> 0xAA00AA;
			case "RARE" -> 0x5555FF;
			default -> 0x55FF55;
		};
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.repo.onLoaded(() -> {
			tracker.load(o.repo.skyhanni("SeaCreatures"));
			loaded = true;
		});
		ChatBus.onChat(m -> guard(() -> {
			if (!loaded) {
				tracker.load(o.repo.skyhanni("SeaCreatures"));
				loaded = true;
			}
			int trophiesBefore = tracker.trophies().values().stream().mapToInt(Integer::intValue).sum();
			FishingTracker.Creature c = tracker.chat(m.plain(), m.atMs());
			long now = m.atMs();
			if (c != null && rank(RARITIES, c.rarity()) >= rank(RARITIES, get(rareFrom))) {
				rare.fire(Map.of("name", c.name(), "rarity", c.rarity()), o.state.island(), now, on(rareColor) ? rarityColour(c.rarity()) : -1);
			}
			if (tracker.doubleHooks() > lastDoubles) {
				lastDoubles = tracker.doubleHooks();
				doubleHook.fire(Map.of(), o.state.island(), now);
			}
			java.util.regex.Matcher tm = FishingTracker.TROPHY_PATTERN.matcher(m.plain().trim());
			if (tm.matches() && tracker.trophies().values().stream().mapToInt(Integer::intValue).sum() > trophiesBefore
					&& rank(TIERS, tm.group("tier")) >= rank(TIERS, get(trophyFrom)))
				trophy.fire(Map.of("fish", tm.group("name"), "tier", tm.group("tier")), o.state.island(), now);
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			bite(mc);
			if (++tick % 20 == 0) tracking.maintain(tracker.session, tracker::reset, o.state.island(), System.currentTimeMillis());
		}));
		Hud.register(new HudElement("fishing", "Fishing tracker", this, HudPos.at(Anchor.RIGHT, -4, 20), 20, this::card, this::sample,
				() -> o.state.onSkyBlock() && tracker.total() + tracker.trophies().size() > 0).display(display));
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset counts", () -> {
			tracker.reset();
			lastDoubles = 0;
		}));
	}

	void bite(Minecraft mc) {
		if (!bite.wanted() || mc.player == null || mc.level == null) return;
		FishingHook hook = mc.player.fishing;
		if (hook == null) return;
		long now = System.currentTimeMillis();
		if (now - lastBiteAt < 1500) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !e.hasCustomName()) continue;
			if (e.distanceToSqr(hook) > 4) continue;
			String n = LegacyText.clean(e.getCustomName().getString());
			if (n.equals("!!!")) {
				lastBiteAt = now;
				bite.fire(Map.of(), Ofa.get().state.island(), now);
				return;
			}
		}
	}

	Card card() {
		Card c = Trackers.card(display, "FISHING", tracker.total() + " CAUGHT");
		Card.KeyValues kv = Trackers.rows();
		display.each(Map.of(
				"per_hour", () -> {
					double perHour = tracker.session.perHour(tracker.total());
					if (perHour > 0 && tracking.showPerHour()) Trackers.row(kv, "Per hour", Trackers.num(display, Math.round(perHour)), Ui.AQUA);
				},
				"time", () -> {
					if (tracker.session.started() && tracking.showUptime()) Trackers.row(kv, "Fishing for", Fmt.duration(tracker.session.activeMs()), Ui.GRAY);
				},
				"double", () -> {
					if (tracker.doubleHooks() > 0) Trackers.row(kv, "Double hooks", Integer.toString(tracker.doubleHooks()), Ui.WHITE);
				},
				"creatures", () -> {
					List<Map.Entry<String, Integer>> list = new ArrayList<>(tracker.creatures().entrySet());
					if (on(rareOnly)) list.removeIf(e -> rank(RARITIES, tracker.rarityOf(e.getKey())) < rank(RARITIES, "RARE"));
					list.sort((a, b) -> b.getValue() - a.getValue());
					for (int i = 0; i < Math.min(get(creaturesShown), list.size()); i++)
						Trackers.row(kv, list.get(i).getKey(), Integer.toString(list.get(i).getValue()), Ui.WHITE);
				},
				"trophies", () -> {
					int trophies = tracker.trophies().values().stream().mapToInt(Integer::intValue).sum();
					if (trophies > 0) Trackers.row(kv, "Trophy fish", Integer.toString(trophies), Ui.GOLD);
				},
				"trophy_list", () -> {
					for (var e : tracker.trophies().entrySet()) Trackers.row(kv, e.getKey(), Integer.toString(e.getValue()), Ui.GOLD);
				}));
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "FISHING", "48 CAUGHT");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Per hour", "212", Ui.AQUA);
		Trackers.row(kv, "Double hooks", "6", Ui.WHITE);
		Trackers.row(kv, "Night Squid", "21", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
