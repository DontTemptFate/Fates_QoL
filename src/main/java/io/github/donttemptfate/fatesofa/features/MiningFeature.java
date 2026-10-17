package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.TabWidgets;
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
	private final Map<String, Double> startPowder = new HashMap<>();
	private final Map<String, Double> lastPowder = new HashMap<>();
	private final ActivitySession session = new ActivitySession(5 * 60_000);
	private int tick;

	public MiningFeature() {
		super("mining", "Mining HUD", "Commissions and powder (with powder per hour) while mining.", Category.MINING, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 != 0 || !inMines()) return;
			Map<String, Double> p = TabWidgets.powders(o.state.tab());
			long now = System.currentTimeMillis();
			for (Map.Entry<String, Double> e : p.entrySet()) {
				startPowder.putIfAbsent(e.getKey(), e.getValue());
				Double last = lastPowder.put(e.getKey(), e.getValue());
				if (last != null && e.getValue() > last) session.activity(now);
			}
		}));
		Hud.register(new HudElement("mining", "Mining", this, HudPos.at(Anchor.RIGHT, -4, -10), 20, this::card, this::sample, this::inMines));
	}

	boolean inMines() {
		Island i = Ofa.get().state.island();
		return i != null && i.isMining();
	}

	Card card() {
		Ofa o = Ofa.get();
		Card c = Trackers.card("MINING", null);
		Card.KeyValues kv = Trackers.rows();
		List<TabWidgets.Commission> com = TabWidgets.commissions(o.state.tab());
		for (TabWidgets.Commission cm : com) Trackers.row(kv, cm.name(), cm.progress(), cm.done() ? Ui.GREEN : Ui.WHITE);
		for (Map.Entry<String, Double> e : lastPowder.entrySet()) {
			double gained = e.getValue() - startPowder.getOrDefault(e.getKey(), e.getValue());
			double ph = session.perHour(gained);
			Trackers.row(kv, e.getKey() + " powder", Fmt.shortNum(e.getValue()) + (ph > 0 ? " · +" + Trackers.perHour(ph) : ""), Ui.AQUA);
		}
		if (kv.isEmpty()) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("MINING", null);
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Goblin Slayer", "45%", Ui.WHITE);
		Trackers.row(kv, "Mithril Miner", "DONE", Ui.GREEN);
		Trackers.row(kv, "Mithril powder", "1.23M · +84k/h", Ui.AQUA);
		c.add(kv);
		return c;
	}
}
