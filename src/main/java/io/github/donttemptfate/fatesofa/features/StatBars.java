package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.activity.ActionBar;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;

import java.util.List;

/** Health and mana as clear bars with numbers (from the action bar), placed wherever you like. */
public final class StatBars extends Feature {
	private volatile ActionBar last;
	private volatile long lastAt;

	public StatBars() {
		super("stat_bars", "Health & mana bars", "Health (with defense) and mana as bars with numbers, from SkyBlock's action bar.",
				Category.COMBAT, false, Risk.SAFE);
	}

	@Override
	public void init() {
		ChatBus.onActionBar(m -> guard(() -> {
			ActionBar a = ActionBar.parse(m.plain());
			if (a != null) {
				last = a;
				lastAt = m.atMs();
			}
		}));
		Hud.register(new HudElement("stat_bars", "Health & mana", this, HudPos.at(Anchor.BOTTOM, 0, -56), 50, this::card,
				() -> build(new ActionBar(1234, 1500, 820, 300, 450, 0)),
				() -> Ofa.get().state.onSkyBlock() && last != null && System.currentTimeMillis() - lastAt < 5000));
	}

	Card card() {
		ActionBar a = last;
		return a == null ? null : build(a);
	}

	static Card build(ActionBar a) {
		Card c = new Card(0.70f, 140, 182);
		if (!Double.isNaN(a.health())) {
			c.add(new Card.Line(List.of(Card.seg(Fmt.shortNum(a.health()) + " / " + Fmt.shortNum(a.maxHealth()) + " ❤", Ui.Role.SMALL, Ui.RED)),
					Double.isNaN(a.defense()) ? List.of() : List.of(Card.seg(Fmt.shortNum(a.defense()) + " ❈", Ui.Role.SMALL, Ui.GREEN))));
			c.add(new Card.Bar(a.maxHealth() > 0 ? a.health() / a.maxHealth() : 0, a.health() > a.maxHealth() ? Ui.GOLD : Ui.RED));
		}
		if (!Double.isNaN(a.mana())) {
			c.add(Card.Line.of(Card.seg(Fmt.shortNum(a.mana()) + " / " + Fmt.shortNum(a.maxMana()) + " ✎" + (a.overflow() > 0 ? "  +" + Fmt.shortNum(a.overflow()) + " ʬ" : ""),
					Ui.Role.SMALL, Ui.AQUA)));
			c.add(new Card.Bar(a.maxMana() > 0 ? a.mana() / a.maxMana() : 0, Ui.AQUA));
		}
		return c;
	}
}
