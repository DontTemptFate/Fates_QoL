package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Hoppity's Hunt: which chocolate eggs have appeared and not been found yet, with an alert when one appears. */
public final class HoppityFeature extends Feature {
	static final Map<String, String> MEALS = io.github.donttemptfate.fatesofa.core.Display.lines("Breakfast", "Breakfast", "Brunch", "Brunch",
			"Lunch", "Lunch", "Déjeuner", "Déjeuner", "Dinner", "Dinner", "Supper", "Supper");

	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "hoppity",
			io.github.donttemptfate.fatesofa.core.Display.GROUP, 0xFFFF55FF,
			io.github.donttemptfate.fatesofa.core.Display.lines("waiting", "Eggs waiting", "found", "Found this session"),
			java.util.List.of("waiting"), false);
	final Setting<Boolean> showAge = setting(Setting.bool("hoppity.age", "Show how long each egg has been up", true).group("Display").advanced());
	final io.github.donttemptfate.fatesofa.core.Alert alert = new io.github.donttemptfate.fatesofa.core.Alert(this, "hoppity.alert", "Egg alert",
			"{meal} egg", "Hoppity's Hunt", 0xFFFF55FF, "minecraft:block.note_block.pling|1|1.6", "meal");
	final Setting<String> alertMeals = setting(Setting.lines("hoppity.alert.meals", "Alert for", MEALS, java.util.List.copyOf(MEALS.keySet()))
			.group("Egg alert"));

	static final Pattern SPAWNED = Pattern.compile("^HOPPITY'S HUNT A Chocolate (?<meal>[\\wé]+) Egg has appeared!$");
	static final Pattern FOUND = Pattern.compile("^HOPPITY'S HUNT You found a Chocolate (?<meal>[\\wé]+) Egg.*$");

	private final Map<String, Long> waiting = new LinkedHashMap<>();
	private int found;

	public HoppityFeature() {
		super("hoppity", "Hoppity's eggs", "Chocolate eggs that have appeared and aren't found yet, with an alert when one appears.",
				Category.EVENTS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			Pattern sp = o.patterns.get("misc.chocolatefactory.egg.spawned", "meal");
			Matcher s = (sp != null ? sp : SPAWNED).matcher(m.plain());
			if (s.matches()) {
				String meal = s.group("meal");
				synchronized (waiting) {
					waiting.put(meal, m.atMs());
				}
				if (io.github.donttemptfate.fatesofa.config.Values.lines(get(alertMeals), alertMeals.options).contains(meal) || !MEALS.containsKey(meal))
					alert.fire(Map.of("meal", meal), o.state.island(), m.atMs());
				return;
			}
			Pattern fp = o.patterns.get("misc.chocolatefactory.egg.found", "meal");
			Matcher f = (fp != null ? fp : FOUND).matcher(m.plain());
			if (f.matches()) {
				synchronized (waiting) {
					waiting.remove(f.group("meal"));
				}
				found++;
			}
		}));
		Hud.register(new HudElement("hoppity", "Hoppity's eggs", this, HudPos.at(Anchor.TOP_RIGHT, -4, 4), 15, this::card, this::sample,
				() -> o.state.onSkyBlock() && !waiting.isEmpty()).display(display));
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card(display, "HOPPITY", display.shows("found") ? found + " FOUND" : null);
		Card.KeyValues kv = Trackers.rows();
		if (display.shows("waiting")) {
			synchronized (waiting) {
				for (Map.Entry<String, Long> e : waiting.entrySet())
					Trackers.row(kv, e.getKey() + " egg", on(showAge) ? "up " + Fmt.duration(now - e.getValue()) : "", Ui.PINK);
			}
		}
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "HOPPITY", "14 FOUND");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Breakfast egg", "up 2m 10s", Ui.PINK);
		c.add(kv);
		return c;
	}
}
