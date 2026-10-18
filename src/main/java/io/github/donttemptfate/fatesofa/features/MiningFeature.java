package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.TabWidgets;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mining: commissions and powder (with powder per hour) on the HUD while in the mining islands. */
public final class MiningFeature extends Feature {
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "mining", 0xFF55FFFF,
			io.github.donttemptfate.fatesofa.core.Display.lines("commissions", "Commissions", "powder", "Powder", "gained", "Powder gained",
					"time", "Time mining"),
			List.of("commissions", "powder"));
	final io.github.donttemptfate.fatesofa.core.Tracker tracking = new io.github.donttemptfate.fatesofa.core.Tracker(this, "mining.tracker", false);
	final Setting<Boolean> hideDone = setting(Setting.bool("mining.hideDone", "Hide finished commissions", false).group("Display").advanced());
	final Setting<String> powders = setting(Setting.lines("mining.powders", "Powders shown",
			io.github.donttemptfate.fatesofa.core.Display.lines("Mithril", "Mithril", "Gemstone", "Gemstone", "Glacite", "Glacite"),
			List.of("Mithril", "Gemstone", "Glacite")).group("Display").advanced());
	final io.github.donttemptfate.fatesofa.core.Alert done = new io.github.donttemptfate.fatesofa.core.Alert(this, "mining.doneAlert", "Commission done alert",
			true, "{name}", "commission done", 0xFF55FF55, "minecraft:entity.player.levelup|0.6|1.4", "name");
	private final Map<String, Double> startPowder = new HashMap<>();
	private final Map<String, Double> lastPowder = new HashMap<>();
	private final java.util.Set<String> doneSeen = new java.util.HashSet<>();
	private boolean commissionsKnown;
	private final ActivitySession session = new ActivitySession(5 * 60_000);
	private int tick;

	public MiningFeature() {
		super("mining", "Mining HUD", "Commissions and powder (with powder per hour) while mining, and an alert when a commission is done.",
				Category.MINING, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 != 0) return;
			long now = System.currentTimeMillis();
			tracking.maintain(session, this::resetCounts, o.state.island(), now);
			if (!inMines()) {
				commissionsKnown = false;
				return;
			}
			Map<String, Double> p = TabWidgets.powders(o.state.tab());
			for (Map.Entry<String, Double> e : p.entrySet()) {
				startPowder.putIfAbsent(e.getKey(), e.getValue());
				Double last = lastPowder.put(e.getKey(), e.getValue());
				if (last != null && e.getValue() > last) session.activity(now);
			}
			List<TabWidgets.Commission> com = TabWidgets.commissions(o.state.tab());
			for (TabWidgets.Commission cm : com) {
				if (!cm.done()) {
					doneSeen.remove(cm.name());
					continue;
				}
				// only commissions that finish while you watch (not the ones already done when you arrive)
				if (doneSeen.add(cm.name()) && commissionsKnown) done.fire(Map.of("name", cm.name()), o.state.island(), now);
			}
			commissionsKnown = !com.isEmpty();
		}));
		Hud.register(new HudElement("mining", "Mining", this, HudPos.at(Anchor.RIGHT, -4, -10), 20, this::card, this::sample, this::inMines)
				.display(display));
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset powder counts", this::resetCounts));
	}

	void resetCounts() {
		startPowder.clear();
		startPowder.putAll(lastPowder);
		session.reset();
	}

	boolean inMines() {
		Island i = Ofa.get().state.island();
		return i != null && i.isMining();
	}

	Card card() {
		Ofa o = Ofa.get();
		Card c = Trackers.card(display, "MINING", null);
		Card.KeyValues kv = Trackers.rows();
		var shownPowders = io.github.donttemptfate.fatesofa.config.Values.lines(get(powders), powders.options);
		display.each(Map.of(
				"commissions", () -> {
					for (TabWidgets.Commission cm : TabWidgets.commissions(o.state.tab())) {
						if (cm.done() && on(hideDone)) continue;
						Trackers.row(kv, cm.name(), cm.progress(), cm.done() ? Ui.GREEN : Ui.WHITE);
					}
				},
				"powder", () -> {
					for (Map.Entry<String, Double> e : lastPowder.entrySet()) {
						if (shownPowders.stream().noneMatch(sp -> e.getKey().startsWith(sp))) continue;
						double gained = e.getValue() - startPowder.getOrDefault(e.getKey(), e.getValue());
						double ph = tracking.showPerHour() ? session.perHour(gained) : 0;
						Trackers.row(kv, e.getKey() + " powder", Trackers.num(display, e.getValue()) + (ph > 0 ? " · +" + Trackers.num(display, ph) + "/h" : ""), Ui.AQUA);
					}
				},
				"gained", () -> {
					for (Map.Entry<String, Double> e : lastPowder.entrySet()) {
						double gained = e.getValue() - startPowder.getOrDefault(e.getKey(), e.getValue());
						if (gained > 0 && shownPowders.stream().anyMatch(sp -> e.getKey().startsWith(sp)))
							Trackers.row(kv, e.getKey() + " gained", "+" + Trackers.num(display, gained), Ui.AQUA);
					}
				},
				"time", () -> {
					if (session.started() && tracking.showUptime()) Trackers.row(kv, "Mining for", Fmt.duration(session.activeMs()), Ui.GRAY);
				}));
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "MINING", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Goblin Slayer", "45%", Ui.WHITE);
		Trackers.row(kv, "Mithril Miner", "DONE", Ui.GREEN);
		Trackers.row(kv, "Mithril powder", "1.23M · +84k/h", Ui.AQUA);
		c.add(kv);
		return c;
	}
}
