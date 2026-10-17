package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.Election;
import io.github.donttemptfate.fatesofa.activity.SkyBlockTime;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;

import java.util.List;

/** Upcoming SkyBlock events (Jacob's contest, Dark Auction, Spooky Festival, Cult of the Fallen Star…) and the mayor. */
public final class EventTimers extends Feature {
	final Setting<Integer> count = setting(Setting.integer("events.count", "Events shown", 5, 1, 8));
	final Setting<Boolean> mayor = setting(Setting.bool("events.mayor", "Show the mayor", true));
	private Election election;

	public EventTimers() {
		super("event_timers", "Event timers", "A HUD card with the next SkyBlock events and the current mayor.", Category.EVENTS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		election = new Election(Ofa.get().io);
		Hud.register(new HudElement("events", "Event timers", this, HudPos.at(Anchor.LEFT, 4, 0), 5, this::card, this::card,
				() -> Ofa.get().state.onSkyBlock()));
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card("EVENTS", SkyBlockTime.at(now).label().replaceAll(", Year \\d+", ""));
		Card.KeyValues kv = Trackers.rows();
		List<SkyBlockTime.Event> ev = SkyBlockTime.upcoming(now);
		for (int i = 0; i < Math.min(get(count), ev.size()); i++) {
			SkyBlockTime.Event e = ev.get(i);
			boolean running = e.running(now);
			Trackers.row(kv, e.name(), running ? "now · " + Fmt.duration(e.endMs() - now) + " left" : "in " + Fmt.duration(e.startMs() - now),
					running ? Ui.GREEN : Ui.WHITE);
		}
		c.add(kv);
		if (on(mayor) && election != null) {
			Election.Mayor m = election.mayor(now);
			if (m != null) {
				c.add(Card.Line.of(Card.seg("Mayor " + m.name() + (m.minister() != null ? " · Minister " + m.minister() : ""), Ui.Role.SMALL, Ui.GOLD)));
			}
		}
		return c;
	}
}
