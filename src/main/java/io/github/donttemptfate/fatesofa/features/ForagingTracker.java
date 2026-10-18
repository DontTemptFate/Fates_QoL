package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.IslandTrackers;
import io.github.donttemptfate.fatesofa.activity.ItemFlow;
import io.github.donttemptfate.fatesofa.config.Setting;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Foraging (Galatea, the Park): logs gained per hour and their value (from your pickups and sacks), and tree gifts. */
public final class ForagingTracker extends Feature {
	final Display display = new Display(this, "foraging", 0xFF55FF55, Display.lines("logs", "Logs by kind", "total", "All logs and value",
			"gifts", "Tree gifts"), List.of("logs", "total", "gifts"));
	final Tracker tracking = new Tracker(this, "foraging.tracker");
	final Setting<String> words = setting(Setting.text("foraging.words", "Count items whose name contains", "Log, Wood, Stick")
			.help("Comma-separated words; e.g. add \"Sap\" to count tree sap too.").advanced());
	private int gifts;

	public ForagingTracker() {
		super("foraging_tracker", "Foraging tracker", "Logs per hour and their value (from what you pick up and put in sacks), and tree gifts, "
				+ "on Galatea and the Park.", Category.FORAGING, true, Risk.SAFE);
	}

	static boolean here(Island i) {
		return i == Island.GALATEA || i == Island.PARK || i == Island.TORRHUS_CANYON;
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			String[] c = IslandTrackers.chat(m.plain());
			if (c != null && c[0].equals("tree_gift")) gifts++;
		}));
		Hud.register(new HudElement("foraging", "Foraging", this, HudPos.at(Anchor.RIGHT, -4, 70), 18, this::card, this::sample,
				() -> here(o.state.island())).display(display));
	}

	List<ItemFlow.Total> logs() {
		List<ItemFlow.Total> out = new ArrayList<>();
		if (ItemTracker.INSTANCE == null) return out;
		List<String> w = new ArrayList<>();
		for (String x : get(words).split(",")) if (!x.isBlank()) w.add(x.trim().toLowerCase(Locale.ROOT));
		for (ItemFlow.Total t : ItemTracker.INSTANCE.flow.totals()) {
			String n = t.name.toLowerCase(Locale.ROOT);
			if (w.stream().anyMatch(n::contains)) out.add(t);
		}
		out.sort((a, b) -> b.amount - a.amount);
		return out;
	}

	Card card() {
		Card c = Trackers.card(display, "FORAGING", null);
		Card.KeyValues kv = Trackers.rows();
		List<ItemFlow.Total> logs = logs();
		display.each(Map.of(
				"logs", () -> {
					for (ItemFlow.Total t : logs) {
						double ph = t.session.perHour(t.amount);
						Trackers.row(kv, t.name, Trackers.num(display, t.amount) + (ph > 0 && tracking.showPerHour() ? " · " + Trackers.num(display, ph) + "/h" : ""), Ui.WHITE);
					}
				},
				"total", () -> {
					double n = 0, v = 0;
					for (ItemFlow.Total t : logs) {
						n += t.amount;
						String key = t.key != null ? t.key : Trackers.keyForName(t.name);
						double x = key == null ? Double.NaN : Trackers.valueOfKey(key, t.amount, tracking);
						if (!Double.isNaN(x)) v += x;
					}
					if (n > 0) Trackers.row(kv, "All logs", Trackers.num(display, n) + (v > 0 && tracking.showProfit() ? " · " + Trackers.num(display, v) + " coins" : ""), Ui.GOLD);
				},
				"gifts", () -> {
					if (gifts > 0) Trackers.row(kv, "Tree gifts", Integer.toString(gifts), Ui.AQUA);
				}));
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "FORAGING", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Mangrove Log", "4.1k · 9.8k/h", Ui.WHITE);
		Trackers.row(kv, "All logs", "4.1k · 61k coins", Ui.GOLD);
		c.add(kv);
		return c;
	}

	static String fmt(double v) {
		return Fmt.shortNum(v);
	}
}
