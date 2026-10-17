package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.SlayerTracker;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Slayer session: boss, fight timer, kills, average kill time, kills per hour, drops and their value. */
public final class SlayerFeature extends Feature {
	public static SlayerFeature INSTANCE;
	public final SlayerTracker tracker = new SlayerTracker();
	private int tick;

	public SlayerFeature() {
		super("slayer", "Slayer tracker", "Boss fight timer, kills, average kill time, kills per hour and drops with their value.",
				Category.SLAYER, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			tracker.patterns(o.patterns.get("slayer.api.quest.start"), o.patterns.get("slayer.api.quest.complete"), o.patterns.get("slayer.api.quest.failed"));
			tracker.chat(m.plain(), m.atMs());
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 10 == 0 && o.state.onSkyBlock()) tracker.scoreboard(o.state.scoreboard(), System.currentTimeMillis());
		}));
		Hud.register(new HudElement("slayer", "Slayer tracker", this, HudPos.at(Anchor.RIGHT, -4, -40), 20, this::card, this::sample,
				() -> o.state.onSkyBlock() && (tracker.questActive() || !tracker.kills().isEmpty())));
	}

	Card card() {
		long now = System.currentTimeMillis();
		String boss = tracker.boss();
		Card c = Trackers.card("SLAYER", boss == null ? null : boss.toUpperCase());
		long f = tracker.fightingFor(now);
		if (f >= 0) c.add(new Card.Hero(List.of(Card.seg(Fmt.clock(f), Ui.Role.HERO_S, Ui.RED)), null, 0, List.of(Card.seg("FIGHTING", Ui.Role.LABEL, Ui.GRAY))));
		else if (tracker.questActive()) c.add(Card.Line.of(Card.seg("Spawning the boss…", Ui.Role.SMALL, Ui.YELLOW)));
		Card.KeyValues kv = Trackers.rows();
		int kills = tracker.kills().size();
		Trackers.row(kv, "Kills", kills + (tracker.fails() > 0 ? " (" + tracker.fails() + " failed)" : ""), Ui.WHITE);
		double avg = tracker.averageKillMs();
		if (avg > 0) Trackers.row(kv, "Average kill", Fmt.seconds1((long) avg), Ui.WHITE);
		double perHour = tracker.session.perHour(kills);
		if (perHour > 0) Trackers.row(kv, "Bosses / hour", Fmt.trimmed(perHour), Ui.AQUA);
		double total = 0;
		List<Map.Entry<String, Integer>> drops = new ArrayList<>(tracker.drops().entrySet());
		for (Map.Entry<String, Integer> d : drops) {
			double v = Trackers.valueOf(d.getKey(), d.getValue());
			if (!Double.isNaN(v)) total += v;
		}
		drops.sort((a, b) -> Double.compare(nz(Trackers.valueOf(b.getKey(), b.getValue())), nz(Trackers.valueOf(a.getKey(), a.getValue()))));
		for (int i = 0; i < Math.min(4, drops.size()); i++) {
			var d = drops.get(i);
			double v = Trackers.valueOf(d.getKey(), d.getValue());
			Trackers.row(kv, d.getValue() + "× " + d.getKey(), Double.isNaN(v) ? "—" : Fmt.shortNum(v), Ui.GOLD);
		}
		if (total > 0) Trackers.row(kv, "Drops worth", Fmt.shortNum(total) + (tracker.session.perHour(total) > 0 ? " · " + Trackers.perHour(tracker.session.perHour(total)) : ""), Ui.GOLD);
		c.add(kv);
		return c;
	}

	static double nz(double v) {
		return Double.isNaN(v) ? 0 : v;
	}

	Card sample() {
		Card c = Trackers.card("SLAYER", "REVENANT HORROR V");
		c.add(new Card.Hero(List.of(Card.seg("0:23", Ui.Role.HERO_S, Ui.RED)), null, 0, List.of(Card.seg("FIGHTING", Ui.Role.LABEL, Ui.GRAY))));
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Kills", "12", Ui.WHITE);
		Trackers.row(kv, "Average kill", "24.6s", Ui.WHITE);
		Trackers.row(kv, "Bosses / hour", "41", Ui.AQUA);
		Trackers.row(kv, "3× Revenant Viscera", "96k", Ui.GOLD);
		c.add(kv);
		return c;
	}
}
