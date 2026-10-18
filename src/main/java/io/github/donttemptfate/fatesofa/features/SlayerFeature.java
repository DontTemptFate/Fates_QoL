package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.SlayerTracker;
import io.github.donttemptfate.fatesofa.config.Setting;
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
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "slayer", 0xFFFF5555,
			io.github.donttemptfate.fatesofa.core.Display.lines("timer", "Fight timer", "kills", "Kills", "average", "Average kill time",
					"best", "Fastest kill", "per_hour", "Bosses per hour", "drops", "Drops", "worth", "Drops worth", "time", "Time slaying"),
			List.of("timer", "kills", "average", "per_hour", "drops", "worth"));
	final io.github.donttemptfate.fatesofa.core.Tracker tracking = new io.github.donttemptfate.fatesofa.core.Tracker(this, "slayer.tracker");
	final Setting<Integer> dropsShown = setting(Setting.integer("slayer.dropsShown", "Drops listed", 4, 1, 12).group("Display"));
	final Setting<Integer> hideUnder = setting(Setting.integer("slayer.hideUnderK", "Hide drops worth less than (k coins)", 0, 0, 1000).group("Display").advanced()
			.help("0 = list every drop."));
	final io.github.donttemptfate.fatesofa.core.Alert spawned = new io.github.donttemptfate.fatesofa.core.Alert(this, "slayer.spawned", "Boss spawned alert",
			true, "BOSS!", "{boss}", 0xFFFF5555, "minecraft:entity.wither.spawn|0.4|1.6", "boss");
	final io.github.donttemptfate.fatesofa.core.Alert slain = new io.github.donttemptfate.fatesofa.core.Alert(this, "slayer.slain", "Boss slain alert",
			false, "Slain in {time}", "{boss}", 0xFF55FF55, "minecraft:entity.player.levelup|0.5|1.2", "boss", "time");
	final io.github.donttemptfate.fatesofa.core.Alert failed = new io.github.donttemptfate.fatesofa.core.Alert(this, "slayer.failed", "Quest failed alert",
			true, "Quest failed", "{boss}", 0xFFFF5555, "minecraft:entity.villager.no", "boss");
	public final SlayerTracker tracker = new SlayerTracker();
	private int tick;
	private boolean wasFighting;
	private int lastKills, lastFails;

	public SlayerFeature() {
		super("slayer", "Slayer tracker", "Boss fight timer, kills, average kill time, kills per hour and drops with their value, plus spawn, kill and fail alerts.",
				Category.SLAYER, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			tracker.patterns(o.patterns.get("slayer.api.quest.start"), o.patterns.get("slayer.api.quest.complete"), o.patterns.get("slayer.api.quest.failed"));
			tracker.chat(m.plain(), m.atMs());
			events(o, m.atMs());
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 10 != 0 || !o.state.onSkyBlock()) return;
			long now = System.currentTimeMillis();
			tracker.scoreboard(o.state.scoreboard(), now);
			events(o, now);
			if (tick % 20 == 0) tracking.maintain(tracker.session, this::resetCounts, o.state.island(), now);
		}));
		Hud.register(new HudElement("slayer", "Slayer tracker", this, HudPos.at(Anchor.RIGHT, -4, -40), 20, this::card, this::sample,
				() -> o.state.onSkyBlock() && (tracker.questActive() || !tracker.kills().isEmpty())).display(display));
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset counts", this::resetCounts));
	}

	void resetCounts() {
		tracker.reset();
		lastKills = 0;
		lastFails = 0;
	}

	/** Spawn / kill / fail alerts from the tracker's state changes. */
	void events(Ofa o, long now) {
		String boss = tracker.boss() == null ? "Slayer boss" : tracker.boss();
		boolean fighting = tracker.fightingFor(now) >= 0;
		if (fighting && !wasFighting) spawned.fire(Map.of("boss", boss), o.state.island(), now);
		wasFighting = fighting;
		List<SlayerTracker.Kill> kills = tracker.kills();
		if (kills.size() > lastKills) {
			SlayerTracker.Kill k = kills.get(kills.size() - 1);
			slain.fire(Map.of("boss", k.boss() == null ? boss : k.boss(), "time", Fmt.seconds1(k.killMs() - k.spawnMs())), o.state.island(), now);
		}
		lastKills = kills.size();
		if (tracker.fails() > lastFails) failed.fire(Map.of("boss", boss), o.state.island(), now);
		lastFails = tracker.fails();
	}

	Card card() {
		long now = System.currentTimeMillis();
		String boss = tracker.boss();
		Card c = Trackers.card(display, "SLAYER", boss == null ? null : boss.toUpperCase());
		Card.KeyValues kv = Trackers.rows();
		int kills = tracker.kills().size();
		List<Map.Entry<String, Integer>> drops = new ArrayList<>(tracker.drops().entrySet());
		Map<String, Double> worth = new java.util.HashMap<>();
		double total = 0;
		for (Map.Entry<String, Integer> d : drops) {
			double v = Trackers.valueOf(d.getKey(), d.getValue(), tracking);
			worth.put(d.getKey(), v);
			if (!Double.isNaN(v)) total += v;
		}
		double totalWorth = total;
		display.each(Map.of(
				"timer", () -> {
					long f = tracker.fightingFor(now);
					if (f >= 0) c.add(new Card.Hero(List.of(Card.seg(Fmt.clock(f), Ui.Role.HERO_S, Ui.RED)), null, 0, List.of(Card.seg("FIGHTING", Ui.Role.LABEL, Ui.GRAY))));
					else if (tracker.questActive()) c.add(Card.Line.of(Card.seg("Spawning the boss…", Ui.Role.SMALL, Ui.YELLOW)));
				},
				"kills", () -> Trackers.row(kv, "Kills", kills + (tracker.fails() > 0 ? " (" + tracker.fails() + " failed)" : ""), Ui.WHITE),
				"average", () -> {
					double avg = tracker.averageKillMs();
					if (avg > 0) Trackers.row(kv, "Average kill", Fmt.seconds1((long) avg), Ui.WHITE);
				},
				"best", () -> {
					long best = Long.MAX_VALUE;
					for (SlayerTracker.Kill k : tracker.kills()) best = Math.min(best, k.killMs() - k.spawnMs());
					if (best != Long.MAX_VALUE) Trackers.row(kv, "Fastest kill", Fmt.seconds1(best), Ui.GREEN);
				},
				"per_hour", () -> {
					double perHour = tracker.session.perHour(kills);
					if (perHour > 0 && tracking.showPerHour()) Trackers.row(kv, "Bosses / hour", Fmt.trimmed(perHour), Ui.AQUA);
				},
				"drops", () -> {
					List<Map.Entry<String, Integer>> sorted = new ArrayList<>(drops);
					sorted.sort((a, b) -> Double.compare(nz(worth.get(b.getKey())), nz(worth.get(a.getKey()))));
					int shown = 0;
					for (var d : sorted) {
						if (shown >= get(dropsShown)) break;
						double v = nz(worth.get(d.getKey()));
						if (get(hideUnder) > 0 && v < get(hideUnder) * 1000.0) continue;
						Trackers.row(kv, d.getValue() + "× " + d.getKey(), !tracking.showProfit() || Double.isNaN(worth.get(d.getKey())) ? "" : Trackers.num(display, v), Ui.GOLD);
						shown++;
					}
				},
				"worth", () -> {
					if (totalWorth > 0 && tracking.showProfit()) {
						double ph = tracking.showPerHour() ? tracker.session.perHour(totalWorth) : 0;
						Trackers.row(kv, "Drops worth", Trackers.num(display, totalWorth) + (ph > 0 ? " · " + Trackers.num(display, ph) + "/h" : ""), Ui.GOLD);
					}
				},
				"time", () -> {
					if (tracker.session.started() && tracking.showUptime()) Trackers.row(kv, "Slaying for", Fmt.duration(tracker.session.activeMs()), Ui.GRAY);
				}));
		if (!kv.isEmpty()) c.add(kv);
		return c;
	}

	static double nz(Double v) {
		return v == null || Double.isNaN(v) ? 0 : v;
	}

	Card sample() {
		Card c = Trackers.card(display, "SLAYER", "REVENANT HORROR V");
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
