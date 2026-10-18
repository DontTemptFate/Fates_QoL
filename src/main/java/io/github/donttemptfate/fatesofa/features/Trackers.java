package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.Fmt;
import io.github.donttemptfate.fatescommon.ui.Card;
import io.github.donttemptfate.fatescommon.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.RepoItem;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared bits for tracker HUD cards: the card shape and item-name → value lookups. */
final class Trackers {
	private Trackers() {}

	static Card card(String title, String right) {
		Card c = new Card(0.80f, 110, 190);
		c.add(new Card.Line(List.of(Card.seg(title, Ui.Role.LABEL, Ui.accent())),
				right == null ? List.of() : List.of(Card.seg(right, Ui.Role.LABEL, Ui.GRAY))));
		return c;
	}

	/** A tracker card in the player's Display settings (background, accent colour). */
	static Card card(io.github.donttemptfate.fatesofa.core.Display d, String title, String right) {
		Card c = new Card(d.alpha(), 110, 190);
		c.add(new Card.Line(List.of(Card.seg(title, Ui.Role.LABEL, d.accent(System.currentTimeMillis()))),
				right == null ? List.of() : List.of(Card.seg(right, Ui.Role.LABEL, Ui.GRAY))));
		return c;
	}

	/** A number in the Display's style: short (1.2M) or full (1,234,567). */
	static String num(io.github.donttemptfate.fatesofa.core.Display d, double v) {
		return d.compact() ? Fmt.shortNum(v) : Fmt.full(Math.round(v));
	}

	/** Value of {@code amount} of a named item in the tracker's price mode, or NaN. */
	static double valueOf(String name, int amount, io.github.donttemptfate.fatesofa.core.Tracker t) {
		String key = keyForName(name);
		return key == null ? Double.NaN : valueOfKey(key, amount, t);
	}

	/** Value of {@code amount} of an item key in the tracker's price mode, or NaN. */
	static double valueOfKey(String key, double amount, io.github.donttemptfate.fatesofa.core.Tracker t) {
		var prices = Ofa.get().prices;
		long now = System.currentTimeMillis();
		switch (t.priceMode()) {
			case "npc" -> {
				Double n = prices.npcPrice(key);
				return n == null ? Double.NaN : n * amount;
			}
			case "bazaar_instant", "bazaar_offer" -> {
				var q = prices.bazaarQuote(key, now);
				if (q != null) return (t.priceMode().equals("bazaar_instant") ? q.instantSell() : q.instantBuy()) * amount;
			}
			default -> {
			}
		}
		Price p = prices.value(key, now);
		return p == null ? Double.NaN : p.value() * amount;
	}

	/** Draws a card left of a menu window (after the quick-button column), shrunk to fit. */
	static void beside(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s, net.minecraft.client.gui.GuiGraphicsExtractor g, Card c) {
		var a = (io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor) s;
		int reserve = QuickCommandsFeature.INSTANCE == null ? 0 : QuickCommandsFeature.INSTANCE.leftReserve(s);
		int edge = a.fatesofa$leftPos() - reserve;
		float scale = Math.min(1f, Math.max(0.5f, (edge - 8) / (float) c.width()));
		c.draw(g, Math.max(2, edge - Math.round(c.width() * scale) - 6), a.fatesofa$topPos(), scale);
	}

	static Card.KeyValues rows() {
		return new Card.KeyValues(1);
	}

	static void row(Card.KeyValues kv, String key, String value, int color) {
		kv.row(Card.seg(key, Ui.Role.SMALL, Ui.GRAY), Card.seg(value, Ui.Role.SMALL, color));
	}

	private static Map<String, String> nameToKey = new HashMap<>();
	private static Object builtFor;

	/** NEU key for an item's display name ("Revenant Viscera" → REVENANT_VISCERA), or null. */
	static String keyForName(String name) {
		Ofa o = Ofa.get();
		var repo = o.repo.items();
		if (repo != builtFor) {
			Map<String, String> m = new HashMap<>();
			for (RepoItem it : repo.all()) m.putIfAbsent(it.plainName().toLowerCase(Locale.ROOT), it.key());
			nameToKey = m;
			builtFor = repo;
		}
		return nameToKey.get(name.toLowerCase(Locale.ROOT).trim());
	}

	/** Value of {@code amount} of a named item, or NaN. */
	static double valueOf(String name, int amount) {
		String key = keyForName(name);
		if (key == null) return Double.NaN;
		Price p = Ofa.get().prices.value(key, System.currentTimeMillis());
		return p == null ? Double.NaN : p.value() * amount;
	}

	static String perHour(double v) {
		return Fmt.shortNum(v) + "/h";
	}
}
