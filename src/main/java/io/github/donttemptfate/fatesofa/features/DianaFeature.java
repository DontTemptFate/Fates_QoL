package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
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
	final Setting<Boolean> rareAlert = setting(Setting.bool("diana.rareAlert", "Rare mob alert", true)
			.help("A title and sound when you dig out a Minos Inquisitor or another rare mob."));

	static final Pattern BURROW = Pattern.compile("^(?:You dug out a Griffin Burrow!|You finished the Griffin burrow chain!).*");
	static final Pattern MOB = Pattern.compile("^(?:Oh|Uh oh|Yikes|Oi|Good Grief|Danger|Woah)! You dug out (?:a )?(?<mob>[\\w\\s]+)!$");
	static final Pattern COINS = Pattern.compile("^Wow! You dug out (?<coins>[\\d,.]+) coins!");
	static final Pattern RARE = Pattern.compile("(?:Minos Inquisitor|Sphinx|King Minos|Manticore)\\s*");

	private final ActivitySession session = new ActivitySession(5 * 60_000);
	private int burrows;
	private double coins;
	private final Map<String, Integer> mobs = new LinkedHashMap<>();
	private final Map<String, Integer> drops = new LinkedHashMap<>();

	public DianaFeature() {
		super("diana", "Diana tracker", "Mythological Ritual: burrows, mobs (rare-mob alert), coins and drops with their value.",
				Category.EVENTS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			String t = m.plain();
			long now = m.atMs();
			Pattern bp = o.patterns.get("diana.chat.burrow.dug.colorless");
			if ((bp != null ? bp : BURROW).matcher(t).matches()) {
				burrows++;
				session.add("burrows", 1, now);
				return;
			}
			Matcher mm = MOB.matcher(t);
			if (mm.matches()) {
				String mob = mm.group("mob").trim();
				mobs.merge(mob, 1, Integer::sum);
				session.activity(now);
				if (on(rareAlert) && RARE.matcher(mob).matches()) {
					Alerts.title(mob.toUpperCase(), 0xFFAA00, "dug out!");
					Alerts.ding();
				}
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
				() -> o.state.onSkyBlock() && burrows > 0));
	}

	Card card() {
		Card c = Trackers.card("DIANA", burrows + " BURROWS");
		Card.KeyValues kv = Trackers.rows();
		double ph = session.perHour(burrows);
		if (ph > 0) Trackers.row(kv, "Burrows / hour", Fmt.trimmed(Math.round(ph)), Ui.AQUA);
		if (coins > 0) Trackers.row(kv, "Coins dug", Fmt.shortNum(coins), Ui.GOLD);
		List<Map.Entry<String, Integer>> ms = new ArrayList<>(mobs.entrySet());
		ms.sort((a, b) -> b.getValue() - a.getValue());
		for (int i = 0; i < Math.min(4, ms.size()); i++) Trackers.row(kv, ms.get(i).getKey(), Integer.toString(ms.get(i).getValue()), Ui.WHITE);
		for (Map.Entry<String, Integer> d : drops.entrySet()) {
			double v = Trackers.valueOf(d.getKey(), d.getValue());
			Trackers.row(kv, d.getValue() + "× " + d.getKey(), Double.isNaN(v) ? "—" : Fmt.shortNum(v), Ui.GOLD);
		}
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("DIANA", "64 BURROWS");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Burrows / hour", "210", Ui.AQUA);
		Trackers.row(kv, "Minos Champion", "9", Ui.WHITE);
		Trackers.row(kv, "Minos Inquisitor", "1", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
