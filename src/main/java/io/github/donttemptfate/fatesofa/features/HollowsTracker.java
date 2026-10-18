package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.IslandTrackers;
import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.core.Alert;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Display;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Tracker;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crystal Hollows and Glacite Mineshafts: crystals found (from the tab list), treasure chests opened, corpses looted
 * by type and mineshafts found, per hour. Coordinates people share in chat already become waypoints.
 */
public final class HollowsTracker extends Feature {
	final Display display = new Display(this, "hollows", 0xFFAA55FF, Display.lines("crystals", "Crystals", "chests", "Treasure chests", "corpses",
			"Corpses looted", "mineshafts", "Mineshafts found", "time", "Time tracked"), List.of("crystals", "chests", "corpses", "mineshafts"));
	final Tracker tracking = new Tracker(this, "hollows.tracker", false);
	final Alert mineshaft = new Alert(this, "hollows.mineshaftAlert", "Mineshaft found alert", true, "MINESHAFT!", "a portal is nearby", 0xFF55FFFF,
			"minecraft:block.amethyst_block.chime|1|1.2");

	final ActivitySession session = new ActivitySession(5 * 60_000);
	private int chests, mineshafts;
	private final Map<String, Integer> corpses = new LinkedHashMap<>();
	private int tick;

	public HollowsTracker() {
		super("hollows_tracker", "Crystal Hollows & Mineshaft", "Crystals found, treasure chests, corpses looted by type and mineshafts found, per hour.",
				Category.MINING, true, Risk.SAFE);
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset counts", this::reset));
	}

	void reset() {
		chests = mineshafts = 0;
		corpses.clear();
		session.reset();
	}

	static boolean here(Island i) {
		return i == Island.CRYSTAL_HOLLOWS || i == Island.MINESHAFT || i == Island.DWARVEN_MINES;
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			String[] c = IslandTrackers.chat(m.plain());
			if (c == null) return;
			switch (c[0]) {
				case "ch_chest" -> {
					chests++;
					session.add("chest", 1, m.atMs());
				}
				case "corpse" -> {
					corpses.merge(c[1].charAt(0) + c[1].substring(1).toLowerCase(), 1, Integer::sum);
					session.add("corpse", 1, m.atMs());
				}
				case "mineshaft" -> {
					mineshafts++;
					session.add("shaft", 1, m.atMs());
					mineshaft.fire(Map.of(), o.state.island(), m.atMs());
				}
				default -> {
				}
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 == 0) tracking.maintain(session, this::reset, o.state.island(), System.currentTimeMillis());
		}));
		Hud.register(new HudElement("hollows", "Crystal Hollows & Mineshaft", this, HudPos.at(Anchor.RIGHT, -4, 30), 19, this::card, this::sample,
				() -> here(o.state.island())).display(display));
	}

	Card card() {
		Ofa o = Ofa.get();
		Card c = Trackers.card(display, "HOLLOWS", null);
		Card.KeyValues kv = Trackers.rows();
		display.each(Map.of(
				"crystals", () -> {
					var cr = IslandTrackers.crystals(o.state.tab());
					if (cr.isEmpty()) return;
					long found = cr.values().stream().filter(v -> !v.startsWith("Not")).count();
					StringBuilder missing = new StringBuilder();
					for (var e : cr.entrySet()) if (e.getValue().startsWith("Not")) missing.append(missing.length() > 0 ? ", " : "").append(e.getKey());
					Trackers.row(kv, "Crystals", found + "/" + cr.size() + (missing.length() > 0 ? " · need " + missing : ""), found == cr.size() ? Ui.GREEN : Ui.WHITE);
				},
				"chests", () -> {
					if (chests > 0) Trackers.row(kv, "Chests", chests + perHour(chests), Ui.GOLD);
				},
				"corpses", () -> {
					for (var e : corpses.entrySet()) Trackers.row(kv, e.getKey() + " corpses", e.getValue() + perHour(e.getValue()), Ui.AQUA);
				},
				"mineshafts", () -> {
					if (mineshafts > 0) Trackers.row(kv, "Mineshafts", mineshafts + perHour(mineshafts), Ui.AQUA);
				},
				"time", () -> {
					if (session.started() && tracking.showUptime()) Trackers.row(kv, "Tracked for", Fmt.duration(session.activeMs()), Ui.GRAY);
				}));
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	/** For the in-game test. */
	public String testSummary() {
		return chests + "|" + corpses + "|" + mineshafts;
	}

	String perHour(double n) {
		double ph = session.perHour(n);
		return ph > 0 && tracking.showPerHour() ? " · " + Fmt.trimmed(Math.round(ph * 10) / 10.0) + "/h" : "";
	}

	Card sample() {
		Card c = Trackers.card(display, "HOLLOWS", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Crystals", "3/5 · need Amber, Topaz", Ui.WHITE);
		Trackers.row(kv, "Chests", "41 · 62/h", Ui.GOLD);
		Trackers.row(kv, "Tungsten corpses", "6 · 9/h", Ui.AQUA);
		c.add(kv);
		return c;
	}
}
