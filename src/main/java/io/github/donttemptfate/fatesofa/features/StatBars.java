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
	final io.github.donttemptfate.fatesofa.core.Display display = new io.github.donttemptfate.fatesofa.core.Display(this, "statbars", io.github.donttemptfate.fatesofa.core.Display.GROUP, 0xFFFF5555,
			io.github.donttemptfate.fatesofa.core.Display.lines("health", "Health", "defense", "Defense", "mana", "Mana", "overflow", "Overflow mana"),
			List.of("health", "defense", "mana", "overflow"));
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> width = setting(io.github.donttemptfate.fatesofa.config.Setting
			.integer("statbars.width", "Bar width", 182, 80, 260).group("Display"));
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> healthColour = setting(io.github.donttemptfate.fatesofa.config.Setting
			.color("statbars.healthColour", "Health colour", 0xFFFF5555).group("Display"));
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> manaColour = setting(io.github.donttemptfate.fatesofa.config.Setting
			.color("statbars.manaColour", "Mana colour", 0xFF55FFFF).group("Display"));
	final io.github.donttemptfate.fatesofa.config.Setting<Boolean> numbers = setting(io.github.donttemptfate.fatesofa.config.Setting
			.bool("statbars.numbers", "Show the numbers", true).group("Display"));
	final io.github.donttemptfate.fatesofa.core.Alert lowHealth = new io.github.donttemptfate.fatesofa.core.Alert(this, "statbars.lowAlert", "Low health alert", false,
			"LOW HEALTH", "{health} ❤", 0xFFFF5555, "minecraft:entity.arrow.hit_player|1|0.7", "health");
	final io.github.donttemptfate.fatesofa.config.Setting<Integer> lowPercent = setting(io.github.donttemptfate.fatesofa.config.Setting
			.integer("statbars.lowAlert.percent", "Below (% of max health)", 25, 5, 90).group("Low health alert"));
	private volatile ActionBar last;
	private volatile long lastAt;
	private boolean low;

	public StatBars() {
		super("stat_bars", "Health & mana bars", "Health (with defense) and mana as bars with numbers, from SkyBlock's action bar, with an optional low-health alert.",
				Category.COMBAT, false, Risk.SAFE);
	}

	@Override
	public void init() {
		ChatBus.onActionBar(m -> guard(() -> {
			ActionBar a = ActionBar.parse(m.plain());
			if (a == null) return;
			last = a;
			lastAt = m.atMs();
			if (!Double.isNaN(a.health()) && a.maxHealth() > 0) {
				boolean nowLow = a.health() / a.maxHealth() * 100 < get(lowPercent);
				if (nowLow && !low) lowHealth.fire(java.util.Map.of("health", Fmt.shortNum(a.health())), Ofa.get().state.island(), m.atMs());
				low = nowLow;
			}
		}));
		Hud.register(new HudElement("stat_bars", "Health & mana", this, HudPos.at(Anchor.BOTTOM, 0, -56), 50, this::card,
				() -> build(new ActionBar(1234, 1500, 820, 300, 450, 0)),
				() -> Ofa.get().state.onSkyBlock() && last != null && System.currentTimeMillis() - lastAt < 5000).display(display));
	}

	Card card() {
		ActionBar a = last;
		return a == null ? null : build(a);
	}

	Card build(ActionBar a) {
		int w = store() == null ? 182 : get(width);
		Card c = new Card(display.alpha(), Math.min(140, w), w);
		boolean nums = store() == null || on(numbers);
		int hc = store() == null ? Ui.RED : get(healthColour) | 0xFF000000;
		int mc = store() == null ? Ui.AQUA : get(manaColour) | 0xFF000000;
		display.each(java.util.Map.of(
				"health", () -> {
					if (Double.isNaN(a.health())) return;
					boolean def = display.shows("defense") && !Double.isNaN(a.defense());
					if (nums || def) c.add(new Card.Line(nums ? List.of(Card.seg(Trackers.num(display, a.health()) + " / " + Trackers.num(display, a.maxHealth()) + " ❤", Ui.Role.SMALL, hc)) : List.of(),
							def ? List.of(Card.seg(Trackers.num(display, a.defense()) + " ❈", Ui.Role.SMALL, Ui.GREEN)) : List.of()));
					c.add(new Card.Bar(a.maxHealth() > 0 ? a.health() / a.maxHealth() : 0, a.health() > a.maxHealth() ? Ui.GOLD : hc));
				},
				"mana", () -> {
					if (Double.isNaN(a.mana())) return;
					boolean ov = display.shows("overflow") && a.overflow() > 0;
					if (nums) c.add(Card.Line.of(Card.seg(Trackers.num(display, a.mana()) + " / " + Trackers.num(display, a.maxMana()) + " ✎" + (ov ? "  +" + Trackers.num(display, a.overflow()) + " ʬ" : ""),
							Ui.Role.SMALL, mc)));
					c.add(new Card.Bar(a.maxMana() > 0 ? a.mana() / a.maxMana() : 0, mc));
				}));
		return c;
	}
}
