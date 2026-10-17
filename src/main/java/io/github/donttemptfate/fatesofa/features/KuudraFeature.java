package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.Island;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Kuudra: supplies placed, the Fresh Tools countdown and phase times. */
public final class KuudraFeature extends Feature {
	static final Pattern SUPPLY = Pattern.compile("^(?:\\[[^]]+] )?(?<who>\\w+) recovered one of Elle's supplies! \\((?<n>\\d)/(?<of>\\d)\\)$");
	static final Pattern FRESH = Pattern.compile("^Your Fresh Tools Perk bonus doubles your building speed for the next (?<s>\\d+) seconds!$");
	static final Pattern START = Pattern.compile("^\\[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!$");
	static final Pattern BUILD_DONE = Pattern.compile("^\\[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!$");
	static final Pattern END = Pattern.compile("^\\s*KUUDRA DOWN!\\s*$");

	private int supplies = -1, ofSupplies = 6;
	private long freshUntil, startAt = -1, buildAt = -1, endAt = -1;

	public KuudraFeature() {
		super("kuudra", "Kuudra HUD", "Supplies placed, the Fresh Tools countdown and phase times.", Category.KUUDRA, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		o.state.onIslandChange(i -> {
			if (i == Island.KUUDRA) {
				supplies = -1;
				startAt = buildAt = endAt = -1;
			}
		});
		ChatBus.onChat(m -> guard(() -> {
			if (!o.state.in(Island.KUUDRA)) return;
			String t = m.plain();
			Matcher s = SUPPLY.matcher(t);
			if (s.matches()) {
				supplies = Integer.parseInt(s.group("n"));
				ofSupplies = Integer.parseInt(s.group("of"));
				return;
			}
			Matcher f = FRESH.matcher(t);
			if (f.matches()) freshUntil = m.atMs() + Long.parseLong(f.group("s")) * 1000;
			else if (START.matcher(t).matches()) startAt = m.atMs();
			else if (BUILD_DONE.matcher(t).matches()) buildAt = m.atMs();
			else if (END.matcher(t).matches()) endAt = m.atMs();
		}));
		Hud.register(new HudElement("kuudra", "Kuudra", this, HudPos.at(Anchor.LEFT, 4, 0), 30, this::card, this::sample, () -> o.state.in(Island.KUUDRA)));
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card("KUUDRA", Ofa.get().state.scoreboard() != null ? Ofa.get().state.scoreboard().dungeonFloor() : null);
		if (freshUntil > now) c.add(new Card.Hero(List.of(Card.seg(Fmt.seconds1(freshUntil - now), Ui.Role.HERO_S, Ui.GREEN)), null, 0,
				List.of(Card.seg("FRESH TOOLS", Ui.Role.LABEL, Ui.GRAY))));
		Card.KeyValues kv = Trackers.rows();
		if (supplies >= 0) Trackers.row(kv, "Supplies", supplies + " / " + ofSupplies, supplies >= ofSupplies ? Ui.GREEN : Ui.WHITE);
		if (startAt > 0 && buildAt > 0) Trackers.row(kv, "Supplies + build", Fmt.clock(buildAt - startAt), Ui.WHITE);
		if (startAt > 0 && endAt > 0) Trackers.row(kv, "Run", Fmt.clock(endAt - startAt), Ui.GOLD);
		else if (startAt > 0) Trackers.row(kv, "Run", Fmt.clock(now - startAt), Ui.WHITE);
		if (kv.isEmpty() && freshUntil <= now) return null;
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("KUUDRA", "T5");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Supplies", "4 / 6", Ui.WHITE);
		Trackers.row(kv, "Run", "2:41", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
