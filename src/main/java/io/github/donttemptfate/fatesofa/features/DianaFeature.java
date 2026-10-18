package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.Drops;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.ActivitySession;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.Alerts;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Mythological Ritual (Diana): burrows dug, mobs dug out (alert for rare ones), coins and drops with value. */
public final class DianaFeature extends Feature {
	static final Map<String, String> RARE_MOBS = io.github.donttemptfate.fatesofa.core.Display.lines("Minos Inquisitor", "Minos Inquisitor",
			"Sphinx", "Sphinx", "King Minos", "King Minos", "Manticore", "Manticore", "Minos Champion", "Minos Champion");

	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "diana", 0xFFFFAA00,
			io.github.donttemptfate.fatesofa.core.Display.lines("per_hour", "Burrows per hour", "coins", "Coins dug", "mobs", "Mobs dug out",
					"since", "Burrows since an Inquisitor", "drops", "Drops", "worth", "Drops worth", "time", "Time digging"),
			List.of("per_hour", "coins", "mobs", "since", "drops"));
	final io.github.donttemptfate.fatesofa.core.Tracker tracking = new io.github.donttemptfate.fatesofa.core.Tracker(this, "diana.tracker");
	final Setting<Integer> mobsShown = setting(Setting.integer("diana.mobsShown", "Mobs listed", 4, 1, 10).group("Display"));
	final io.github.donttemptfate.fatesofa.core.Alert rare = new io.github.donttemptfate.fatesofa.core.Alert(this, "diana.rare", "Rare mob alert",
			"{mob}", "dug out!", 0xFFFFAA00, "minecraft:entity.ender_dragon.growl|0.5|1.4", "mob");
	final Setting<String> rareWhich = setting(Setting.lines("diana.rare.which", "Alert for", RARE_MOBS,
			List.of("Minos Inquisitor", "Sphinx", "King Minos", "Manticore")).group("Rare mob alert"));

	static final Pattern BURROW = Pattern.compile("^(?:You dug out a Griffin Burrow!|You finished the Griffin burrow chain!).*");
	static final Pattern MOB = Pattern.compile("^(?:Oh|Uh oh|Yikes|Oi|Good Grief|Danger|Woah)! You dug out (?:a )?(?<mob>[\\w\\s]+)!$");
	static final Pattern COINS = Pattern.compile("^Wow! You dug out (?<coins>[\\d,.]+) coins!");

	private final ActivitySession session = new ActivitySession(5 * 60_000);
	private int burrows;
	private int sinceInquisitor;
	private double coins;
	private final Map<String, Integer> mobs = new LinkedHashMap<>();
	private final Map<String, Integer> drops = new LinkedHashMap<>();
	private int tick;

	public DianaFeature() {
		super("diana", "Diana tracker", "Mythological Ritual: burrows, mobs (rare-mob alert), coins and drops with their value.",
				Category.EVENTS, true, Risk.SAFE);
	}

	@Override
	public List<Action> actions() {
		return List.of(new Action("Reset counts", this::resetCounts));
	}

	void resetCounts() {
		session.reset();
		burrows = 0;
		coins = 0;
		mobs.clear();
		drops.clear();
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 == 0) tracking.maintain(session, this::resetCounts, o.state.island(), System.currentTimeMillis());
		}));
		ChatBus.onChat(m -> guard(() -> {
			String t = m.plain();
			long now = m.atMs();
			Pattern bp = o.patterns.get("diana.chat.burrow.dug.colorless");
			if ((bp != null ? bp : BURROW).matcher(t).matches()) {
				burrows++;
				sinceInquisitor++;
				session.add("burrows", 1, now);
				return;
			}
			Matcher mm = MOB.matcher(t);
			if (mm.matches()) {
				String mob = mm.group("mob").trim();
				mobs.merge(mob, 1, Integer::sum);
				session.activity(now);
				if (mob.startsWith("Minos Inquisitor")) sinceInquisitor = 0;
				if (io.github.donttemptfate.fatesofa.config.Values.lines(get(rareWhich), rareWhich.options).stream().anyMatch(mob::startsWith))
					rare.fire(Map.of("mob", mob.toUpperCase()), o.state.island(), now);
				return;
			}
			Matcher cm = COINS.matcher(t);
			if (cm.find()) {
				coins += Fmt.parse(cm.group("coins"));
				session.activity(now);
				return;
			}
			if (session.started() && !session.idle(now)) {
				Drops.Drop d = Drops.parse(t.replace("You dug out a ", "").replace("You dug out ", ""));
				if (d != null) drops.merge(d.item(), d.amount(), Integer::sum);
			}
		}));
		Hud.register(new HudElement("diana", "Diana tracker", this, HudPos.at(Anchor.RIGHT, -4, 60), 20, this::card, this::sample,
				() -> o.state.onSkyBlock() && burrows > 0).display(display));
	}

	Card card() {
		Card c = Trackers.card(display, "DIANA", burrows + " BURROWS");
		Card.KeyValues kv = Trackers.rows();
		display.each(Map.of(
				"per_hour", () -> {
					double ph = session.perHour(burrows);
					if (ph > 0 && tracking.showPerHour()) Trackers.row(kv, "Burrows / hour", Fmt.trimmed(Math.round(ph)), Ui.AQUA);
				},
				"coins", () -> {
					if (coins > 0) Trackers.row(kv, "Coins dug", Trackers.num(display, coins), Ui.GOLD);
				},
				"mobs", () -> {
					List<Map.Entry<String, Integer>> ms = new ArrayList<>(mobs.entrySet());
					ms.sort((a, b) -> b.getValue() - a.getValue());
					for (int i = 0; i < Math.min(get(mobsShown), ms.size()); i++) Trackers.row(kv, ms.get(i).getKey(), Integer.toString(ms.get(i).getValue()), Ui.WHITE);
				},
				"since", () -> {
					if (burrows > 0) Trackers.row(kv, "Since Inquisitor", sinceInquisitor + " burrows", Ui.GRAY);
				},
				"drops", () -> {
					for (Map.Entry<String, Integer> d : drops.entrySet()) {
						double v = Trackers.valueOf(d.getKey(), d.getValue(), tracking);
						Trackers.row(kv, d.getValue() + "× " + d.getKey(), Double.isNaN(v) || !tracking.showProfit() ? "" : Trackers.num(display, v), Ui.GOLD);
					}
				},
				"worth", () -> {
					double total = coins;
					for (Map.Entry<String, Integer> d : drops.entrySet()) {
						double v = Trackers.valueOf(d.getKey(), d.getValue(), tracking);
						if (!Double.isNaN(v)) total += v;
					}
					if (total > 0 && tracking.showProfit()) {
						double ph = tracking.showPerHour() ? session.perHour(total) : 0;
						Trackers.row(kv, "Total worth", Trackers.num(display, total) + (ph > 0 ? " · " + Trackers.num(display, ph) + "/h" : ""), Ui.GOLD);
					}
				},
				"time", () -> {
					if (session.started() && tracking.showUptime()) Trackers.row(kv, "Digging for", Fmt.duration(session.activeMs()), Ui.GRAY);
				}));
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "DIANA", "64 BURROWS");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Burrows / hour", "210", Ui.AQUA);
		Trackers.row(kv, "Minos Champion", "9", Ui.WHITE);
		Trackers.row(kv, "Minos Inquisitor", "1", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
