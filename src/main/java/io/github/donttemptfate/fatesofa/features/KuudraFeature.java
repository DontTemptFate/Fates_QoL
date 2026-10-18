package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
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

	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "kuudra", io.github.donttemptfate.fatesofa.core.Display.GROUP, 0xFFFFAA00,
			io.github.donttemptfate.fatesofa.core.Display.lines("fresh", "Fresh Tools countdown", "supplies", "Supplies", "build", "Supplies + build time", "run", "Run time", "best", "Best run this session"),
			List.of("fresh", "supplies", "build", "run"), false);
	final io.github.donttemptfate.fatesofa.core.Alert suppliesDone = new io.github.donttemptfate.fatesofa.core.Alert(this, "kuudra.suppliesAlert", "Supplies done alert", false,
			"SUPPLIES DONE", "{time}", 0xFF55FF55, "minecraft:block.note_block.chime", "time");
	final io.github.donttemptfate.fatesofa.core.Alert freshEnd = new io.github.donttemptfate.fatesofa.core.Alert(this, "kuudra.freshAlert", "Fresh Tools ending alert", false,
			"FRESH ENDING", "{seconds}s left", 0xFFFFFF55, "minecraft:block.note_block.bit", "seconds");
	final Setting<Integer> freshWarn = setting(Setting.integer("kuudra.freshAlert.seconds", "Seconds before the end", 3, 1, 10).group("Fresh Tools ending alert"));
	private boolean freshWarned;
	private long bestRun = -1;
	private int tick;
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
				if (supplies >= ofSupplies && startAt > 0)
					suppliesDone.fire(java.util.Map.of("time", Fmt.clock(m.atMs() - startAt)), Island.KUUDRA, m.atMs());
				return;
			}
			Matcher f = FRESH.matcher(t);
			if (f.matches()) {
				freshUntil = m.atMs() + Long.parseLong(f.group("s")) * 1000;
				freshWarned = false;
			}
			else if (START.matcher(t).matches()) startAt = m.atMs();
			else if (BUILD_DONE.matcher(t).matches()) buildAt = m.atMs();
			else if (END.matcher(t).matches()) {
				endAt = m.atMs();
				if (startAt > 0 && (bestRun < 0 || endAt - startAt < bestRun)) bestRun = endAt - startAt;
			}
		}));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 5 != 0 || freshWarned) return;
			long now = System.currentTimeMillis();
			long left = freshUntil - now;
			if (left > 0 && left <= get(freshWarn) * 1000L) {
				freshWarned = true;
				freshEnd.fire(java.util.Map.of("seconds", Long.toString(Math.max(1, left / 1000))), Island.KUUDRA, now);
			}
		}));
		Hud.register(new HudElement("kuudra", "Kuudra", this, HudPos.at(Anchor.TOP_LEFT, 4, 60), 30, this::card, this::sample, () -> o.state.in(Island.KUUDRA)).display(display));
	}

	Card card() {
		long now = System.currentTimeMillis();
		Card c = Trackers.card(display, "KUUDRA", Ofa.get().state.scoreboard() != null ? Ofa.get().state.scoreboard().dungeonFloor() : null);
		Card.KeyValues kv = Trackers.rows();
		display.each(java.util.Map.of(
				"fresh", () -> {
					if (freshUntil > now) c.add(new Card.Hero(List.of(Card.seg(Fmt.seconds1(freshUntil - now), Ui.Role.HERO_S, Ui.GREEN)), null, 0,
							List.of(Card.seg("FRESH TOOLS", Ui.Role.LABEL, Ui.GRAY))));
				},
				"supplies", () -> {
					if (supplies >= 0) Trackers.row(kv, "Supplies", supplies + " / " + ofSupplies, supplies >= ofSupplies ? Ui.GREEN : Ui.WHITE);
				},
				"build", () -> {
					if (startAt > 0 && buildAt > 0) Trackers.row(kv, "Supplies + build", Fmt.clock(buildAt - startAt), Ui.WHITE);
				},
				"run", () -> {
					if (startAt > 0 && endAt > 0) Trackers.row(kv, "Run", Fmt.clock(endAt - startAt), Ui.GOLD);
					else if (startAt > 0) Trackers.row(kv, "Run", Fmt.clock(now - startAt), Ui.WHITE);
				},
				"best", () -> {
					if (bestRun > 0) Trackers.row(kv, "Best run", Fmt.clock(bestRun), Ui.GREEN);
				}));
		if (kv.isEmpty() && freshUntil <= now) return null;
		if (!kv.isEmpty()) c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "KUUDRA", "T5");
		Card.KeyValues kv = Trackers.rows();
		Trackers.row(kv, "Supplies", "4 / 6", Ui.WHITE);
		Trackers.row(kv, "Run", "2:41", Ui.WHITE);
		c.add(kv);
		return c;
	}
}
