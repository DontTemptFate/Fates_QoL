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
	final Setting<Boolean> alert = setting(Setting.bool("hoppity.alert", "Egg alert", true));

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
			Pattern sp = o.patterns.get("misc.chocolatefactory.egg.spawned");
			Matcher s = (sp != null ? sp : SPAWNED).matcher(m.plain());
			if (s.matches()) {
				synchronized (waiting) {
					waiting.put(s.group("meal"), m.atMs());
				}
				if (on(alert)) {
					Alerts.title(s.group("meal") + " egg", 0xFF55FF, "Hoppity's Hunt");
					Alerts.ding();
				}
				return;
			}
			Pattern fp = o.patterns.get("misc.chocolatefactory.egg.found");
			Matcher f = (fp != null ? fp : FOUND).matcher(m.plain());
			if (f.matches()) {
				synchronized (waiting) {
					waiting.remove(f.group("meal"));
				}
				found++;
			}
		}));
		Hud.register(new HudElement("hoppity", "Hoppity's eggs", this, HudPos.at(Anchor.TOP_RIGHT, -4, 4), 15, this::card, this::sample,
				() -> o.state.onSkyBlock() && !waiting.isEmpty()));
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card("HOPPITY", found + " FOUND");
		Card.KeyValues kv = Trackers.rows();
		synchronized (waiting) {
			for (Map.Entry<String, Long> e : waiting.entrySet()) Trackers.row(kv, e.getKey() + " egg", "up " + Fmt.duration(now - e.getValue()), Ui.PINK);
		}
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("HOPPITY", "14 FOUND");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Breakfast egg", "up 2m 10s", Ui.PINK);
		c.add(kv);
		return c;
	}
}
