package io.github.donttemptfate.fatesofa.features;

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

/** Rift: time left (big, so it's not missed) and motes. */
public final class RiftFeature extends Feature {
	static final Pattern TIME = Pattern.compile(".*?(?<riftTime>(?:\\d+m ?)?\\d+s) ?ф Left.*");
	static final Pattern PARTS = Pattern.compile("(?:(?<m>\\d+)m ?)?(?<s>\\d+)s");
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "rift", io.github.donttemptfate.fatesofa.core.Display.GROUP, 0xFFFF55FF,
			io.github.donttemptfate.fatesofa.core.Display.lines("time", "Time left", "motes", "Motes"), List.of("time", "motes"));
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> redBelow = setting(io.github.donttemptfate.fatesofa.config.Setting
			.integer("rift.redBelow", "Turn red below (minutes)", 1, 0, 30).group("Display"));
	final io.github.donttemptfate.fatesofa.core.Alert low = new io.github.donttemptfate.fatesofa.core.Alert(this, "rift.lowAlert", "Time running out alert", true,
			"RIFT TIME", "{time} left", 0xFFFF5555, "minecraft:block.note_block.bass|1|0.8", "time");
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> lowAt = setting(io.github.donttemptfate.fatesofa.config.Setting
			.integer("rift.lowAlert.seconds", "Alert at (seconds left)", 60, 10, 600).group("Time running out alert"));
	private volatile String timeLeft;
	private boolean warned;

	public RiftFeature() {
		super("rift", "Rift timer", "Your Rift time left as a large HUD number, plus motes, and an alert before it runs out.", Category.RIFT, true, Risk.SAFE);
	}

	/** "7m 42s" → seconds, or -1. */
	static int seconds(String t) {
		if (t == null) return -1;
		Matcher m = PARTS.matcher(t.trim());
		if (!m.matches()) return -1;
		return (m.group("m") == null ? 0 : Integer.parseInt(m.group("m")) * 60) + Integer.parseInt(m.group("s"));
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onActionBar(m -> guard(() -> {
			if (!o.state.in(Island.RIFT)) return;
			Matcher mm = TIME.matcher(m.plain());
			if (!mm.matches()) return;
			timeLeft = mm.group("riftTime");
			int secs = seconds(timeLeft);
			if (secs > get(lowAt) + 5) warned = false;
			if (secs >= 0 && secs <= get(lowAt) && !warned) {
				warned = true;
				low.fire(java.util.Map.of("time", timeLeft), Island.RIFT, m.atMs());
			}
		}));
		Hud.register(new HudElement("rift", "Rift timer", this, HudPos.at(Anchor.TOP, 0, 24), 30, this::card, this::sample,
				() -> o.state.in(Island.RIFT) && timeLeft != null).display(display));
	}

	Card card() {
		Card c = Trackers.card(display, "RIFT TIME", null);
		int secs = seconds(timeLeft);
		display.each(java.util.Map.of(
				"time", () -> c.add(new Card.Hero(List.of(Card.seg(timeLeft, Ui.Role.HERO_S, secs >= 0 && secs < get(redBelow) * 60 ? Ui.RED : Ui.GREEN)), null, 0, null)),
				"motes", () -> {
					var sb = Ofa.get().state.scoreboard();
					if (sb != null && sb.motes() != null) c.add(Card.Line.of(Card.seg("Motes " + Trackers.num(display, sb.motes()), Ui.Role.SMALL, Ui.PINK)));
				}));
		return c;
	}

	Card sample() {
		Card c = Trackers.card(display, "RIFT TIME", null);
		c.add(new Card.Hero(List.of(Card.seg("7m 42s", Ui.Role.HERO_S, Ui.GREEN)), null, 0, null));
		return c;
	}
}
