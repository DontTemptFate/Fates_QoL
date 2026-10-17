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
	private volatile String timeLeft;

	public RiftFeature() {
		super("rift", "Rift timer", "Your Rift time left as a large HUD number, plus motes.", Category.RIFT, true, Risk.SAFE);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onActionBar(m -> guard(() -> {
			if (!o.state.in(Island.RIFT)) return;
			Matcher mm = TIME.matcher(m.plain());
			if (mm.matches()) timeLeft = mm.group("riftTime");
		}));
		Hud.register(new HudElement("rift", "Rift timer", this, HudPos.at(Anchor.TOP, 0, 24), 30, this::card, this::sample,
				() -> o.state.in(Island.RIFT) && timeLeft != null));
	}

	Card card() {
		Card c = Trackers.card("RIFT TIME", null);
		c.add(new Card.Hero(List.of(Card.seg(timeLeft, Ui.Role.HERO_S, timeLeft.startsWith("0m") || !timeLeft.contains("m") ? Ui.RED : Ui.GREEN)), null, 0, null));
		var sb = Ofa.get().state.scoreboard();
		if (sb != null && sb.motes() != null) c.add(Card.Line.of(Card.seg("Motes " + io.github.donttemptfate.fates.common.text.Fmt.shortNum(sb.motes()), Ui.Role.SMALL, Ui.PINK)));
		return c;
	}

	Card sample() {
		Card c = Trackers.card("RIFT TIME", null);
		c.add(new Card.Hero(List.of(Card.seg("7m 42s", Ui.Role.HERO_S, Ui.GREEN)), null, 0, null));
		return c;
	}
}
